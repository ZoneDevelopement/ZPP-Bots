package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class SwapEngine {

    private static final double WC_SAFE_RADIUS = 8.0;
    private static final double WC_SAFE_COS = 0.866;

    private SwapEngine() {
    }

    public static void staleSwapAttack(BotContext ctx, int postSlot, LivingEntity target) {
        InventoryUtils.select(ctx, postSlot);
        ctx.hands().attack(target);
        ctx.hands().swing(EquipmentSlot.HAND);
    }

    public static void staleSwapAttackWindowed(BotContext ctx, int postSlot, LivingEntity target) {
        MeleeWeaponGuard.allowStaleWindow(ctx, 1);
        staleSwapAttack(ctx, postSlot, target);
        MeleeWeaponGuard.clearStaleWindow(ctx);
    }

    public static void syncedAttack(BotContext ctx, int slot, LivingEntity target) {
        InventoryUtils.select(ctx, slot);
        ctx.hands().attack(target);
        ctx.hands().swing(EquipmentSlot.HAND);
    }

    public static boolean fireWindCharge(BotContext ctx, int wcSlot, float pitch) {
        if (wcSlot < 0 || wcSlot > 8) {
            return false;
        }
        Player bot = ctx.player();
        if (bot.hasCooldown(new ItemStack(Material.WIND_CHARGE))) {
            return false;
        }
        InventoryUtils.select(ctx, wcSlot);
        ctx.controls().look(bot.getLocation().getYaw(), pitch);
        if (!bot.getInventory().getItemInMainHand().getType().equals(Material.WIND_CHARGE)) {
            return false;
        }
        if (aimedAtSomeone(ctx)) {
            return false;
        }
        return useMainHand(ctx);
    }

    private static boolean aimedAtSomeone(BotContext ctx) {
        Player bot = ctx.player();
        Vec3 eye = Ent.eyePos(bot);
        Vec3 look = Ent.look(bot).normalize();
        for (LivingEntity e : ctx.world().entities(LivingEntity.class, Ent.box(bot).inflate(WC_SAFE_RADIUS), x -> true)) {
            if (e != bot) {
                Vec3 to = Ent.box(e).getCenter().subtract(eye);
                double d = to.length();
                if (!(d < 0.1) && !(d > WC_SAFE_RADIUS) && look.dot(to.normalize()) >= WC_SAFE_COS) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean fireWindChargeSelf(BotContext ctx, int wcSlot, float pitch) {
        if (wcSlot < 0 || wcSlot > 8) {
            return false;
        }
        Player bot = ctx.player();
        if (bot.hasCooldown(new ItemStack(Material.WIND_CHARGE))) {
            return false;
        }
        InventoryUtils.select(ctx, wcSlot);
        ctx.controls().look(bot.getLocation().getYaw(), pitch);
        if (!bot.getInventory().getItemInMainHand().getType().equals(Material.WIND_CHARGE)) {
            return false;
        }
        return useMainHand(ctx);
    }

    public static boolean fireWindChargeDown(BotContext ctx, int wcSlot) {
        return fireWindCharge(ctx, wcSlot, 80.0F);
    }

    public static boolean fireWindChargeUp(BotContext ctx, int wcSlot) {
        return fireWindCharge(ctx, wcSlot, -90.0F);
    }

    public static boolean throwPearl(BotContext ctx, int pearlSlot) {
        if (pearlSlot < 0 || pearlSlot > 8) {
            return false;
        }
        InventoryUtils.select(ctx, pearlSlot);
        if (!ctx.player().getInventory().getItemInMainHand().getType().equals(Material.ENDER_PEARL)) {
            return false;
        }
        return useMainHand(ctx);
    }

    public static boolean sameTickJumpAndWc(BotContext ctx, int wcSlot) {
        ctx.controls().jumpOnce();
        return fireWindChargeDown(ctx, wcSlot);
    }

    public static boolean crouchBoostFire(BotContext ctx, int wcSlot) {
        ctx.controls().setSneaking(true);
        return fireWindChargeDown(ctx, wcSlot);
    }

    public static void useDown(BotContext ctx, EquipmentSlot hand) {
        ctx.hands().useItem(hand);
    }

    public static void useUp(BotContext ctx) {
        ctx.hands().releaseUseItem();
    }

    // Returns true once the use call ran, whether or not the item was consumed
    private static boolean useMainHand(BotContext ctx) {
        try {
            ctx.hands().useItem(EquipmentSlot.HAND);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
