package dev.nandi0813.zppbots.team.perf;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.platform.BotGuards;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class BotAiTierResolver {

    private static final double SLEEP_DIST = 48.0;
    private static final double LIGHT_DIST = 40.0;
    private static final double NEARBY_PLAYER = 90.0;
    private static final double AIR_CHASE_DIST = 50.0;

    private BotAiTierResolver() {
    }

    public static BotAiTier resolve(BotContext ctx, LivingEntity target, double dist, boolean macePvpActive, boolean specialPvpExecuting) {
        if (macePvpActive || specialPvpExecuting) {
            return BotAiTier.FULL;
        } else if (target == null) {
            return humanWithin(ctx, NEARBY_PLAYER) ? BotAiTier.LIGHT : BotAiTier.SLEEP;
        } else if (isAirborneTarget(ctx, target) && dist <= AIR_CHASE_DIST) {
            return BotAiTier.FULL;
        } else if (dist > SLEEP_DIST) {
            return BotAiTier.SLEEP;
        } else {
            return dist > LIGHT_DIST ? BotAiTier.LIGHT : BotAiTier.FULL;
        }
    }

    private static boolean isAirborneTarget(BotContext ctx, LivingEntity target) {
        if (target.isOnGround()) {
            return false;
        } else if (target.isGliding()) {
            return true;
        } else {
            return target.isRiptiding() ? true : Ent.pos(target).y() - Ent.pos(ctx.player()).y() > 4.0;
        }
    }

    /** Humans are the players that are not bots . */
    private static boolean humanWithin(BotContext ctx, double range) {
        Player bot = ctx.player();
        for (Player p : ctx.world().players()) {
            if (p != bot && !BotGuards.isBot(p.getUniqueId()) && Ent.alive(p) && p.getGameMode() != GameMode.SPECTATOR
                    && Ent.dist(bot, p) <= range) {
                return true;
            }
        }
        return false;
    }
}
