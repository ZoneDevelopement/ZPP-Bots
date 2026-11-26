package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.ai.gear.ItemEvaluator;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MeleeWeaponGuard {

    private static final Map<UUID, Long> staleWindows = new ConcurrentHashMap<>();

    private MeleeWeaponGuard() {
    }

    public static void allowStaleWindow(BotContext ctx, int ticks) {
        staleWindows.put(ctx.player().getUniqueId(), ctx.world().gameTime() + ticks);
    }

    public static void clearStaleWindow(BotContext ctx) {
        staleWindows.remove(ctx.player().getUniqueId());
    }

    public static void forget(BotContext ctx) {
        clearStaleWindow(ctx);
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return false;
        }
        if (ItemEvaluator.isMace(stack)) {
            return true;
        }
        return !ItemKinds.isPickaxe(stack) && !ItemKinds.isShovel(stack) && !ItemKinds.isHoe(stack)
                && ItemEvaluator.isWeapon(stack);
    }

    public static void ensureWeapon(BotContext ctx) {
        UUID id = ctx.player().getUniqueId();
        Long until = staleWindows.get(id);
        if (until != null) {
            if (ctx.world().gameTime() <= until) {
                return;
            }
            staleWindows.remove(id);
        }
        if (ctx.player().isHandRaised()) {
            return;
        }
        PlayerInventory inv = ctx.player().getInventory();
        if (isMeleeWeapon(inv.getItemInMainHand())) {
            return;
        }
        int best = -1;
        double bestScore = 0.0;
        for (int i = 0; i < 36; i++) {
            double score = ItemEvaluator.scoreWeapon(inv.getItem(i));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best < 0) {
            for (int i = 0; i < 36; i++) {
                double score = ItemEvaluator.scoreMace(inv.getItem(i));
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
        }
        if (best < 0) {
            for (int i = 0; i < 9; i++) {
                if (ItemKinds.isEmpty(inv.getItem(i))) {
                    InventoryUtils.select(ctx, i);
                    return;
                }
            }
        } else {
            InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, best));
        }
    }
}
