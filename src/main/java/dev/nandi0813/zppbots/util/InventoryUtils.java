package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryUtils {

    private static final String HOTBAR_SELECT_REJECTED = "HOTBAR_SELECT_REJECTED";

    public static final int SIZE = 41;

    private InventoryUtils() {
    }

    public static int selected(BotContext ctx) {
        return ctx.player().getInventory().getHeldItemSlot();
    }

    public static void select(BotContext ctx, int slot) {
        if (slot >= 0 && slot <= 8) {
            ctx.hands().selectSlot(slot);
        } else {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            String caller = st.length > 2 ? st[2].toString() : "?";
            ctx.log().decision("InventoryUtils", HOTBAR_SELECT_REJECTED, "ignored out-of-range hotbar select " + slot + " from " + caller);
        }
    }

    public static int moveToHotbarSafe(BotContext ctx, int sourceSlot) {
        if (sourceSlot < 9) {
            return sourceSlot;
        }
        PlayerInventory inv = ctx.player().getInventory();
        int dest = -1;
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.isEmpty(inv.getItem(i))) {
                dest = i;
                break;
            }
        }
        if (dest < 0) {
            for (int i = 8; i >= 0; i--) {
                if (!isProtected(inv.getItem(i))) {
                    dest = i;
                    break;
                }
            }
        }
        if (dest < 0) {
            dest = 8;
        }
        ItemStack source = inv.getItem(sourceSlot);
        ItemStack target = inv.getItem(dest);
        ItemStack moving = source == null ? null : source.clone();
        ItemStack displaced = target == null ? null : target.clone();
        inv.setItem(dest, moving);
        inv.setItem(sourceSlot, displaced);
        return dest;
    }

    public static void swap(BotContext ctx, int a, int b) {
        PlayerInventory inv = ctx.player().getInventory();
        ItemStack first = inv.getItem(a);
        ItemStack second = inv.getItem(b);
        inv.setItem(a, second == null ? null : second.clone());
        inv.setItem(b, first == null ? null : first.clone());
    }

    public static boolean isProtected(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return false;
        }
        Material type = stack.getType();
        if (type == Material.MACE
                || ItemKinds.isSword(type)
                || ItemKinds.isAxe(type)
                || type == Material.BOW
                || type == Material.CROSSBOW
                || ItemKinds.isSpear(type)) {
            return true;
        }
        if (type == Material.TRIDENT && EnchantmentUtils.hasEnchantAtLeast(stack, "riptide", 3)) {
            return true;
        }
        return type == Material.ELYTRA || EnchantmentUtils.hasEnchantAtLeast(stack, "lunge", 3);
    }
}
