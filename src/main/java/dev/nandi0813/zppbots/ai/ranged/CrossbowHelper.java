package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;

import java.util.List;

public final class CrossbowHelper {

    private CrossbowHelper() {
    }

    public static ItemStack selectedStack(BotContext ctx) {
        return ctx.player().getInventory().getItem(InventoryUtils.selected(ctx));
    }

    public static boolean isCharged(BotContext ctx) {
        return ItemKinds.isCrossbowCharged(selectedStack(ctx));
    }

    public static void clearCharge(BotContext ctx) {
        int slot = InventoryUtils.selected(ctx);
        ItemStack stack = ctx.player().getInventory().getItem(slot);
        if (ItemKinds.isCrossbowCharged(stack)) {
            ItemStack cleared = stack.clone();
            if (cleared.getItemMeta() instanceof CrossbowMeta meta) {
                meta.setChargedProjectiles(List.of());
                cleared.setItemMeta(meta);
            }
            ctx.player().getInventory().setItem(slot, cleared);
        }
    }

    public static boolean fireIfCharged(BotContext ctx) {
        ItemStack stack = selectedStack(ctx);
        if (ItemKinds.is(stack, Material.CROSSBOW) && ItemKinds.isCrossbowCharged(stack)) {
            ctx.hands().useItem(EquipmentSlot.HAND);
            clearCharge(ctx);
            return true;
        } else {
            return false;
        }
    }
}
