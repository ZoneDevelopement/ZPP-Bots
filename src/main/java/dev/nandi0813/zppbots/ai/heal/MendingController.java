package dev.nandi0813.zppbots.ai.heal;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.LedgeGuard;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class MendingController {

    private static final float THROW_PITCH = 78.0F;
    private static final float THROW_PITCH_STATIONARY = 90.0F;
    private static final int SAFE_WALK_DROP = 2;
    private static final int WINDUP_TICKS = 3;
    private static final int THROW_INTERVAL = 2;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final BotContext ctx;
    private final Player bot;
    private int throwCooldown = 0;
    private boolean isMending = false;

    public MendingController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick(LivingEntity threat) {
        if (!needsMending()) {
            stopMending();
            return false;
        } else {
            int slot = findBottleSlot();
            if (slot < 0) {
                stopMending();
                return false;
            } else {
                boolean entering = !isMending;
                isMending = true;
                PlayerInventory inv = bot.getInventory();
                slot = InventoryUtils.moveToHotbarSafe(ctx, slot);
                InventoryUtils.select(ctx, slot);
                float yaw = bot.getLocation().getYaw();
                if (threat != null && Ent.alive(threat)) {
                    double ax = bot.getLocation().getX() - threat.getLocation().getX();
                    double az = bot.getLocation().getZ() - threat.getLocation().getZ();
                    if (ax * ax + az * az > 1.0E-4) {
                        yaw = (float) Math.toDegrees(Math.atan2(-ax, az));
                    }
                }

                double lookX = -Math.sin(Math.toRadians(yaw));
                double lookZ = Math.cos(Math.toRadians(yaw));
                boolean canAdvance = !LedgeGuard.wouldFallDangerously(ctx, lookX, lookZ, SAFE_WALK_DROP);
                ctx.controls().setSneaking(false);
                ctx.controls().look(yaw, canAdvance ? THROW_PITCH : THROW_PITCH_STATIONARY);
                if (canAdvance) {
                    ctx.controls().moveForward();
                    ctx.controls().setSprinting(false);
                } else {
                    ctx.controls().stopMoving();
                }

                if (entering) {
                    throwCooldown = WINDUP_TICKS;
                }

                if (throwCooldown > 0) {
                    throwCooldown--;
                    return true;
                } else {
                    if (ItemKinds.is(inv.getItem(slot), Material.EXPERIENCE_BOTTLE)) {
                        ctx.hands().swing(EquipmentSlot.HAND);
                        ctx.hands().useItem(EquipmentSlot.HAND);
                    }

                    throwCooldown = THROW_INTERVAL;
                    return true;
                }
            }
        }
    }

    public boolean isMending() {
        return isMending;
    }

    public void abortMending() {
        stopMending();
    }

    private void stopMending() {
        if (isMending) {
            ctx.controls().setSneaking(false);
            ctx.controls().stopMoving();
        }

        isMending = false;
        throwCooldown = 0;
    }

    public boolean hasCriticalPiece() {
        return anyPieceAtOrBelow(ctx.tuning().number(TuningKey.MEND_CRITICAL).floatValue());
    }

    public boolean wantsToMend() {
        return needsMending() && findBottleSlot() >= 0;
    }

    private boolean needsMending() {
        return anyPieceAtOrBelow(isMending
                ? ctx.tuning().number(TuningKey.MEND_REPAIRED).floatValue()
                : ctx.tuning().number(TuningKey.MEND_LOW_DURABILITY).floatValue());
    }

    private boolean anyPieceAtOrBelow(float threshold) {
        for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
            ItemStack piece = bot.getEquipment().getItem(armorSlot);
            if (!ItemKinds.isEmpty(piece)) {
                int maxDamage = ItemKinds.maxDamage(piece);
                if (maxDamage > 0 && EnchantmentUtils.hasEnchantAtLeast(piece, "mending", 1)
                        && remainingAtOrBelow(maxDamage, ItemKinds.damage(piece), threshold)) {
                    return true;
                }
            }
        }

        return false;
    }

    static boolean remainingAtOrBelow(int maxDamage, int damage, float threshold) {
        int remaining = maxDamage - damage;
        return (float) remaining / maxDamage <= threshold;
    }

    private int findBottleSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? 0 : 9;
            int to = pass == 0 ? 9 : 36;

            for (int i = from; i < to; i++) {
                if (ItemKinds.is(inv.getItem(i), Material.EXPERIENCE_BOTTLE)) {
                    return i;
                }
            }
        }

        return -1;
    }
}
