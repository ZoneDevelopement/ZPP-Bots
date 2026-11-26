package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.MeleeWeaponGuard;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Target choice: enemies of the match only, ranked by {@link #targetPriority}, split across the squad. */
final class Targeting {

    private static final String FORCED_STANCE_RECOVERY = "FORCED_STANCE_RECOVERY";

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;

    Targeting(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    LivingEntity findTarget() {
        BotSettings es = ctx.settings();
        LivingEntity lastHurtBy = ctx.match().lastAttackerOf(bot, ctx.tick());
        if (lastHurtBy instanceof Player lastAttacker
                && Ent.alive(lastAttacker)
                && lastAttacker.getGameMode() != GameMode.SPECTATOR
                && (!es.ignoreCreative() || lastAttacker.getGameMode() != GameMode.CREATIVE)
                && lastAttacker != bot) {
            brain.revengeTargetId = lastAttacker.getUniqueId();
        }

        if (es.revengeMode() && brain.revengeTargetId != null) {
            Player attacker = resolveRevenge(es);
            if (attacker != null) {
                Player share = pinnedTarget(es);
                if (share != null && share != attacker && ctx.squad().isSplitTarget(attacker)) {
                    if (shareOrCloser(share, attacker) != share) {
                        return attacker;
                    }
                } else {
                    Player near = nearestTeamTarget(es);
                    if (near == null || near == attacker || !closerWins(attacker, near, true)) {
                        return attacker;
                    }
                }
            }
        }

        return resolveSpecificTeamTarget(es);
    }

    void tickStanceWatchdog() {
        if (brain.target != null && Ent.alive(brain.target)) {
            ItemStack main = bot.getInventory().getItemInMainHand();
            boolean consumableHeld = ItemKinds.isFood(main) || ItemKinds.is(main, Material.POTION) || ItemKinds.is(main, Material.EXPERIENCE_BOTTLE);
            if (!consumableHeld) {
                brain.stanceStuckTicks = 0;
                brain.consumableHeldTicks = 0;
            } else {
                brain.consumableHeldTicks++;
                if (bot.isHandRaised()) {
                    brain.stanceStuckTicks = 0;
                } else {
                    brain.stanceStuckTicks++;
                }

                if (brain.stanceStuckTicks >= 50 || brain.consumableHeldTicks >= 300) {
                    ctx.log().decision("Targeting", FORCED_STANCE_RECOVERY, "wedged holding " + main.getType() + " in combat (" + brain.stanceStuckTicks + " ticks idle, "
                            + brain.consumableHeldTicks + " held) - forced recovery.", true);
                    brain.stanceStuckTicks = 0;
                    brain.consumableHeldTicks = 0;
                    brain.healingController.forceAbortWithLockout(80);
                    brain.mendingController.abortMending();
                    brain.predictionWebController.reset();
                    brain.headWebController.reset();
                    MeleeWeaponGuard.ensureWeapon(ctx);
                }
            }
        } else {
            brain.stanceStuckTicks = 0;
            brain.consumableHeldTicks = 0;
        }
    }

    /** The best-ranked enemy, chosen among the match's enemies only. */
    LivingEntity findAutoTarget(BotSettings es) {
        return nearestPlayer(es);
    }

    boolean enemyWithin(double r) {
        BotSettings es = ctx.settings();
        for (Player p : ctx.match().enemies()) {
            if (Ent.dist(bot, p) <= r && eligible(p, es) && p != bot) {
                return true;
            }
        }

        return false;
    }

    Player resolveRevenge(BotSettings es) {
        if (brain.revengeTargetId == null) {
            return null;
        } else {
            Player sp = Bukkit.getPlayer(brain.revengeTargetId);
            if (sp != null && sp.getGameMode() != GameMode.SPECTATOR && (!es.ignoreCreative() || sp.getGameMode() != GameMode.CREATIVE)) {
                if (!Ent.alive(sp)) {
                    return null;
                } else if (!ctx.match().isEnemy(sp)) {
                    brain.revengeTargetId = null;
                    return null;
                } else {
                    return sp;
                }
            } else {
                brain.revengeTargetId = null;
                return null;
            }
        }
    }

    Player resolveSpecificTeamTarget(BotSettings es) {
        return shareOrCloser(pinnedTarget(es), nearestPlayer(es));
    }

    Player shareOrCloser(Player share, Player nearest) {
        if (share != null && nearest != null && nearest != share) {
            return closerWins(share, nearest, false) ? nearest : share;
        } else {
            brain.closerThanShare = null;
            return share != null ? share : nearest;
        }
    }

    boolean closerWins(Player preferred, Player nearest, boolean revenge) {
        double dPref = Ent.dist(bot, preferred);
        double dNear = Ent.dist(bot, nearest);
        String name = nearest.getName();
        boolean was = name.equals(revenge ? brain.closerThanRevenge : brain.closerThanShare);
        BotTuning.Profile profile = ctx.tuning();
        double closeRange = profile.number(TuningKey.TARGET_CLOSE_RANGE).doubleValue();
        double closeKeep = profile.number(TuningKey.TARGET_CLOSE_KEEP_MARGIN).doubleValue();
        double closeSwitch = profile.number(TuningKey.TARGET_CLOSE_SWITCH_MARGIN).doubleValue();
        double farKeep = profile.number(TuningKey.TARGET_FAR_KEEP_MARGIN).doubleValue();
        double farSwitch = profile.number(TuningKey.TARGET_FAR_SWITCH_MARGIN).doubleValue();
        boolean closer = dNear <= closeRange && dPref > dNear + (was ? closeKeep : closeSwitch)
                || dPref > dNear + (was ? farKeep : farSwitch);
        if (revenge) {
            brain.closerThanRevenge = closer ? name : null;
        } else {
            brain.closerThanShare = closer ? name : null;
        }

        return closer;
    }

    Player nearestTeamTarget(BotSettings es) {
        return nearestPlayer(es);
    }

    /** The target the squad's balancer pinned to this bot, if it is still a valid enemy. */
    Player pinnedTarget(BotSettings es) {
        Player sp = ctx.squad().assignedTarget(ctx);
        return sp != null && eligible(sp, es) ? sp : null;
    }

    Player nearestPlayer(BotSettings es) {
        Player best = null;
        double bestPriority = Double.MAX_VALUE;

        for (Player p : ctx.match().enemies()) {
            if (eligible(p, es)) {
                double priority = targetPriority(p);
                if (priority < bestPriority) {
                    bestPriority = priority;
                    best = p;
                }
            }
        }

        return best;
    }

    private boolean eligible(Player p, BotSettings es) {
        return Ent.alive(p)
                && p.getGameMode() != GameMode.SPECTATOR
                && (!es.ignoreCreative() || p.getGameMode() != GameMode.CREATIVE)
                && ctx.match().isEnemy(p);
    }

    double targetPriority(Player p) {
        boolean consuming = false;
        if (p.isHandRaised()) {
            ItemStack used = p.getActiveItem();
            consuming = ItemKinds.isFood(used) || ItemKinds.is(used, Material.POTION);
        }

        return targetPriority(ctx.tuning(), Ent.dist(bot, p), consuming, p.getHealth());
    }

    static double targetPriority(BotTuning.Profile profile, double dist, boolean consuming, double health) {
        double score = dist;
        if (consuming) {
            score -= profile.number(TuningKey.TARGET_CONSUMING_BONUS).doubleValue();
        }

        if (health <= profile.number(TuningKey.TARGET_LOW_HP).doubleValue()) {
            score -= profile.number(TuningKey.TARGET_LOW_HP_BONUS).doubleValue();
        }

        return score;
    }
}
