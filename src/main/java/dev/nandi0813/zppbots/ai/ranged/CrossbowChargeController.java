package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class CrossbowChargeController {

    private static final int MAX_CHARGE_TICKS = 40;

    private final BotContext ctx;
    private final Player bot;
    private int chargingSlot = -1;
    private int chargeTicks = 0;

    public CrossbowChargeController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isCharging() {
        return chargingSlot >= 0;
    }

    public boolean tick() {
        if (!hasArrows()) {
            stop();
            return false;
        } else {
            if (chargingSlot < 0) {
                int slot = findUnchargedHotbarCrossbow();
                if (slot < 0) {
                    return false;
                }

                chargingSlot = slot;
                chargeTicks = 0;
            }

            ItemStack cb = bot.getInventory().getItem(chargingSlot);
            if (ItemKinds.is(cb, Material.CROSSBOW) && !ItemKinds.isCrossbowCharged(cb) && ++chargeTicks <= MAX_CHARGE_TICKS) {
                if (bot.isHandRaised() && bot.getActiveItem().getType() != Material.CROSSBOW) {
                    if (bot.getActiveItemHand() != EquipmentSlot.OFF_HAND) {
                        stop();
                        return false;
                    }

                    ctx.hands().releaseUseItem();
                }

                InventoryUtils.select(ctx, chargingSlot);
                if (!bot.isHandRaised()) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }

                return true;
            } else {
                stop();
                return false;
            }
        }
    }

    public void reset() {
        stop();
    }

    private void stop() {
        if (chargingSlot >= 0 && bot.isHandRaised() && bot.getActiveItem().getType() == Material.CROSSBOW) {
            ctx.hands().releaseUseItem();
        }

        chargingSlot = -1;
        chargeTicks = 0;
    }

    private int findUnchargedHotbarCrossbow() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (ItemKinds.is(s, Material.CROSSBOW) && !ItemKinds.isCrossbowCharged(s)) {
                return i;
            }
        }

        return -1;
    }

    private boolean hasArrows() {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isArrow(bot.getInventory().getItem(i))) {
                return true;
            }
        }

        return ItemKinds.isArrow(bot.getInventory().getItem(40));
    }
}
