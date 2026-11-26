package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ElytraFlight {

    private static final int GROUND_GRACE_TICKS = 2;
    private static final Map<UUID, Integer> GROUNDED = new ConcurrentHashMap<>();

    private ElytraFlight() {
    }

    public static boolean isUsable(ItemStack stack) {
        return ItemKinds.is(stack, Material.ELYTRA) && ItemKinds.damage(stack) < ItemKinds.maxDamage(stack) - 1;
    }

    public static boolean worn(BotContext ctx) {
        return isUsable(ctx.player().getEquipment().getItem(EquipmentSlot.CHEST));
    }

    public static boolean hasElytra(BotContext ctx) {
        if (worn(ctx)) {
            return true;
        }
        PlayerInventory inv = ctx.player().getInventory();
        for (int i = 0; i < 36; i++) {
            if (isUsable(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean equip(BotContext ctx) {
        if (worn(ctx)) {
            return true;
        }
        PlayerInventory inv = ctx.player().getInventory();
        ItemStack chest = ctx.player().getEquipment().getItem(EquipmentSlot.CHEST);
        for (int i = 0; i < 36; i++) {
            if (isUsable(inv.getItem(i))) {
                ItemStack elytra = inv.getItem(i).clone();
                inv.setItem(i, ItemKinds.isEmpty(chest) ? null : chest.clone());
                ctx.player().getEquipment().setItem(EquipmentSlot.CHEST, elytra);
                return true;
            }
        }
        return false;
    }

    public static boolean beginGlide(BotContext ctx) {
        Player bot = ctx.player();
        if (bot.isGliding()) {
            return true;
        }
        if (!equip(ctx)) {
            return false;
        }
        if (bot.isInWater() || bot.isInLava()) {
            return false;
        }
        ctx.hands().startGliding();
        if (!bot.isGliding() && !bot.isOnGround()) {
            bot.setGliding(true);
        }
        return bot.isGliding();
    }

    public static void stopGlide(BotContext ctx) {
        if (ctx.player().isGliding()) {
            ctx.player().setGliding(false);
        }
    }

    public static boolean guard(BotContext ctx) {
        Player bot = ctx.player();
        UUID id = bot.getUniqueId();
        if (!bot.isGliding()) {
            GROUNDED.remove(id);
            return false;
        }
        if (!worn(ctx)) {
            bot.setGliding(false);
            GROUNDED.remove(id);
            return true;
        }
        if (!bot.isOnGround()) {
            GROUNDED.remove(id);
            return false;
        }
        int down = GROUNDED.merge(id, 1, Integer::sum);
        if (down < GROUND_GRACE_TICKS) {
            return false;
        }
        bot.setGliding(false);
        GROUNDED.remove(id);
        return true;
    }

    public static void forget(BotContext ctx) {
        GROUNDED.remove(ctx.player().getUniqueId());
    }
}
