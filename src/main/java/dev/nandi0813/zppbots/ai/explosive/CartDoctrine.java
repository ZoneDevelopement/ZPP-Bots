package dev.nandi0813.zppbots.ai.explosive;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CartDoctrine {

    private CartDoctrine() {
    }

    public static boolean isGuideDoctrine(BotContext ctx) {
        return ctx.settings().cartDoctrine() == BotSettings.CartDoctrine.GUIDE;
    }

    public static Reason shouldCart(BotContext ctx, LivingEntity target, double dist, int sinceTargetPearled, int sinceTargetCartMiss) {
        if (target == null || !Ent.alive(target)) {
            return Reason.NONE;
        }
        return decide(ctx.tuning(), isGuideDoctrine(ctx), hp(ctx.player()), hp(target), dist, sinceTargetPearled,
                sinceTargetCartMiss);
    }

    static Reason decide(BotTuning.Profile profile, boolean guide, float myHp, float theirHp, double dist,
            int sinceTargetPearled, int sinceTargetCartMiss) {
        float finishHp = profile.number(TuningKey.CART_DOCTRINE_FINISH_HP).floatValue();
        float enterExchangeHp = profile.number(TuningKey.CART_DOCTRINE_ENTER_EXCHANGE_HP).floatValue();
        float gapWorth = profile.number(TuningKey.CART_DOCTRINE_GAP_WORTH).floatValue();
        double meleeRange = profile.number(TuningKey.CART_DOCTRINE_MELEE_RANGE).doubleValue();
        if (!guide) {
            return Reason.RELENTLESS;
        } else if (theirHp <= finishHp) {
            return Reason.FINISH;
        } else if (sinceTargetPearled <= profile.integer(TuningKey.CART_DOCTRINE_PEARL_PUNISH_TICKS)) {
            return Reason.PUNISH_PEARL;
        } else if (sinceTargetCartMiss <= profile.integer(TuningKey.CART_DOCTRINE_MISS_PUNISH_TICKS)) {
            return Reason.PUNISH_MISS;
        } else if (myHp < enterExchangeHp && myHp <= theirHp) {
            return Reason.NONE;
        } else if (dist <= meleeRange && myHp - theirHp < gapWorth) {
            return Reason.NONE;
        } else {
            return myHp + gapWorth >= theirHp ? Reason.OPEN_GAP : Reason.NONE;
        }
    }

    public static boolean selfDamageOk(BotContext ctx, LivingEntity target, Reason why) {
        float myHp = hp(ctx.player());
        return why == Reason.FINISH && hasTotem(ctx.player())
                ? myHp >= ctx.tuning().number(TuningKey.CART_DOCTRINE_SELF_DAMAGE_TOTEM_HP).floatValue()
                : myHp >= ctx.tuning().number(TuningKey.CART_DOCTRINE_SELF_DAMAGE_HP).floatValue();
    }

    public static double selfMinDistance(BotContext ctx) {
        float myHp = hp(ctx.player());
        if (myHp >= ctx.tuning().number(TuningKey.CART_DOCTRINE_SELF_DIST_HEALTHY_HP).floatValue()) {
            return ctx.tuning().number(TuningKey.CART_DOCTRINE_SELF_DIST_HEALTHY).doubleValue();
        } else {
            return myHp >= ctx.tuning().number(TuningKey.CART_DOCTRINE_SELF_DIST_MID_HP).floatValue()
                    ? ctx.tuning().number(TuningKey.CART_DOCTRINE_SELF_DIST_MID).doubleValue()
                    : ctx.tuning().number(TuningKey.CART_DOCTRINE_SELF_DIST_LOW).doubleValue();
        }
    }

    public static double selfMinDistance(BotContext ctx, Reason why) {
        double d = selfMinDistance(ctx);
        return why == Reason.FINISH
                ? Math.min(ctx.tuning().number(TuningKey.CART_DOCTRINE_FINISH_DIST_CAP).doubleValue(), d)
                : d;
    }

    public static boolean isFinishWindow(BotContext ctx, LivingEntity target) {
        return target != null && Ent.alive(target)
                ? hp(target) <= ctx.tuning().number(TuningKey.CART_DOCTRINE_FINISH_HP).floatValue()
                        && hasTotem(ctx.player())
                : false;
    }

    private static float hp(LivingEntity entity) {
        return (float) (entity.getHealth() + entity.getAbsorptionAmount());
    }

    private static boolean hasTotem(Player bot) {
        ItemStack off = bot.getInventory().getItemInOffHand();
        return off != null && off.getType() == Material.TOTEM_OF_UNDYING;
    }

    public enum Reason {
        FINISH,
        OPEN_GAP,
        PUNISH_PEARL,
        PUNISH_MISS,
        RELENTLESS,
        NONE
    }
}
