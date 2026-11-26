package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.Predicate;

public final class WebPlacer {

    private WebPlacer() {
    }

    public static int equipWeb(BotContext ctx) {
        PlayerInventory inv = ctx.player().getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.COBWEB)) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.COBWEB)) {
                return InventoryUtils.moveToHotbarSafe(ctx, i);
            }
        }
        return -1;
    }

    public static boolean hasWeb(BotContext ctx) {
        PlayerInventory inv = ctx.player().getInventory();
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.COBWEB)) {
                return true;
            }
        }
        return false;
    }

    public static boolean placeAt(BotContext ctx, BlockPos pos) {
        ItemStack web = ctx.player().getInventory().getItemInMainHand();
        if (!ItemKinds.is(web, Material.COBWEB) || !ctx.world().isAir(pos)) {
            return false;
        }
        lookAt(ctx, pos);
        ctx.hands().useItemOn(EquipmentSlot.HAND, pos, Direction.UP, Vec3.atCenterOf(pos));
        ctx.hands().swing(EquipmentSlot.HAND);
        return ctx.world().type(pos) == Material.COBWEB;
    }

    public static void lookAt(BotContext ctx, BlockPos pos) {
        ctx.controls().lookAt(Vec3.atCenterOf(pos));
    }

    public static int equipWebCutter(BotContext ctx) {
        int slot = findIn(ctx, s -> ItemKinds.is(s, Material.SHEARS));
        if (slot < 0) {
            slot = findIn(ctx, ItemKinds::isSword);
        }
        if (slot < 0) {
            return -1;
        }
        slot = slot >= 9 ? InventoryUtils.moveToHotbarSafe(ctx, slot) : slot;
        InventoryUtils.select(ctx, slot);
        return slot;
    }

    public static boolean hasShears(BotContext ctx) {
        return findIn(ctx, s -> ItemKinds.is(s, Material.SHEARS)) >= 0;
    }

    private static int findIn(BotContext ctx, Predicate<ItemStack> match) {
        PlayerInventory inv = ctx.player().getInventory();
        for (int i = 0; i < 9; i++) {
            if (match.test(inv.getItem(i))) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (match.test(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }
}
