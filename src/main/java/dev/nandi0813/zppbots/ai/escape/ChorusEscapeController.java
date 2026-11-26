package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.WebDetect;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ChorusEscapeController {

    private final BotContext ctx;
    private final Player bot;
    private boolean eating = false;
    private int eatTicks = 0;
    private int cooldown = 0;

    public ChorusEscapeController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public void reset() {
        if (eating && bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        eating = false;
        eatTicks = 0;
    }

    public boolean tick() {
        if (cooldown > 0) {
            cooldown--;
        }

        if (eating) {
            ctx.controls().stopMoving();
            if (++eatTicks >= ctx.tuning().integer(TuningKey.CHORUS_ESCAPE_EAT_WINDUP)) {
                ItemStack held = bot.getInventory().getItemInMainHand();
                if (ItemKinds.is(held, Material.CHORUS_FRUIT)) {
                    try {
                        // finishes the eat early, like finishUsingItem; teleports and consumes the fruit
                        bot.completeUsingActiveItem();
                    } catch (Exception e) {
                        held.setAmount(held.getAmount() - 1);
                    }
                }

                if (bot.isHandRaised()) {
                    ctx.hands().releaseUseItem();
                }

                eating = false;
                eatTicks = 0;
                cooldown = ctx.tuning().integer(TuningKey.CHORUS_ESCAPE_COOLDOWN);
                return false;
            } else {
                if (!bot.isHandRaised()) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }

                return true;
            }
        } else if (cooldown > 0) {
            return false;
        } else if (!ctx.settings().chorusEscape()) {
            return false;
        } else if (!isTrapped()) {
            return false;
        } else {
            int slot = findChorusSlot();
            if (slot < 0) {
                return false;
            } else {
                InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, slot));
                ctx.controls().stopMoving();
                if (bot.isHandRaised()) {
                    ctx.hands().releaseUseItem();
                }

                ctx.hands().useItem(EquipmentSlot.HAND);
                eating = true;
                eatTicks = 0;
                return true;
            }
        }
    }

    private boolean isTrapped() {
        BlockPos feet = Ent.blockPos(bot);
        if (!WebDetect.touchingWeb(bot)) {
            return false;
        } else {
            int blocked = 0;

            for (BlockPos n : new BlockPos[]{feet.north(), feet.south(), feet.east(), feet.west()}) {
                if (isSolid(n) || isSolid(n.above())) {
                    blocked++;
                }
            }

            return boxed(ctx.tuning(), blocked);
        }
    }

    static boolean boxed(BotTuning.Profile profile, int blocked) {
        return blocked >= profile.integer(TuningKey.CHORUS_ESCAPE_BOX_SIDES);
    }

    private boolean isSolid(BlockPos p) {
        return ctx.world().blocksMotion(p);
    }

    private int findChorusSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.CHORUS_FRUIT)) {
                return i;
            }
        }

        return -1;
    }
}
