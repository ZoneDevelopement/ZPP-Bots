package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.WaterRules;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

public final class FireEscapeController {

    private final BotContext ctx;
    private final Player bot;
    private boolean active = false;
    private int pickupTimer = 0;
    private BlockPos waterPos = null;
    private int bucketSlot = -1;

    public FireEscapeController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isActive() {
        return active;
    }

    public boolean tick() {
        if (active) {
            if (--pickupTimer <= 0) {
                collectWater();
            }
            return true;
        }
        if (!needsEscape()) {
            return false;
        }
        int slot = findWaterBucket();
        if (slot < 0) {
            return false;
        }
        BlockPos feet = Ent.blockPos(bot);
        if (WaterRules.evaporates(ctx, feet)) {
            return false;
        }
        Material state = ctx.world().type(feet);
        if (!state.isAir() && state != Material.LAVA) {
            return false;
        }
        // The block is not set directly; here the bucket is used looking straight down like a client
        int hotbar = InventoryUtils.moveToHotbarSafe(ctx, slot);
        InventoryUtils.select(ctx, hotbar);
        ctx.controls().look(bot.getLocation().getYaw(), 90.0F);
        if (!ctx.hands().useItem(EquipmentSlot.HAND)) {
            return false;
        }
        active = true;
        waterPos = feet;
        bucketSlot = hotbar;
        pickupTimer = ctx.tuning().integer(TuningKey.FIRE_ESCAPE_PICKUP_DELAY);
        return true;
    }

    /**
     * Walks toward the nearest solid column out of lava and holds jump, so a bot that fell in climbs out.
     * shortcut: no water bucket needed, but no path search either, so walls can stall it; upgrade if playtests show it stuck.
     */
    public boolean walkOutOfLava() {
        if (!bot.isInLava() || bot.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
            return false;
        }
        BlockPos exit = nearestExit(Ent.blockPos(bot), ctx.world()::isLava, ctx.world()::blocksMotion);
        if (exit != null) {
            ctx.controls().lookAt(Vec3.atCenterOf(exit));
        }
        ctx.controls().setForward(1.0F).setSprinting(false).setJumping(true);
        return true;
    }

    /** Nearest column within three blocks that is clear of lava and motion blocks and stands on solid ground. */
    static BlockPos nearestExit(BlockPos feet, Predicate<BlockPos> lava, Predicate<BlockPos> solid) {
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int distance = dx * dx + dz * dz;
                if (distance == 0 || distance >= bestDistance) {
                    continue;
                }
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos base = new BlockPos(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    if (!lava.test(base)
                            && !solid.test(base)
                            && !lava.test(base.above())
                            && !solid.test(base.above())
                            && solid.test(base.below())
                            && !lava.test(base.below())) {
                        best = base;
                        bestDistance = distance;
                        break;
                    }
                }
            }
        }
        return best;
    }

    private boolean needsEscape() {
        return (bot.getFireTicks() > 0 || bot.isInLava()) && !bot.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    private void collectWater() {
        if (waterPos != null && ctx.world().type(waterPos) == Material.WATER) {
            PlayerInventory inv = bot.getInventory();
            int slot = bucketSlot >= 0 && isSingleBucket(inv.getItem(bucketSlot)) ? bucketSlot : -1;

            for (int i = 0; slot < 0 && i < 36; i++) {
                if (isSingleBucket(inv.getItem(i))) {
                    slot = i;
                }
            }

            if (slot >= 0) {
                InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, slot));
                ctx.controls().look(bot.getLocation().getYaw(), 90.0F);
                ctx.hands().useItem(EquipmentSlot.HAND);
            }
        }

        active = false;
        waterPos = null;
        bucketSlot = -1;
    }

    private static boolean isSingleBucket(ItemStack stack) {
        return ItemKinds.is(stack, Material.BUCKET) && stack.getAmount() == 1;
    }

    private int findWaterBucket() {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), Material.WATER_BUCKET)) {
                return i;
            }
        }
        return -1;
    }
}
