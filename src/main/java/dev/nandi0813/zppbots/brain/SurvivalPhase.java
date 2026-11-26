package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.team.perf.BotAiTier;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.Player;

final class SurvivalPhase {

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;

    SurvivalPhase(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    /** A committed eat holds slots, weapons and movement until the bot drops under its break HP. */
    boolean eatLocked() {
        double breakHp = ctx.difficulty().healCommitBreakHp;
        return brain.healingController.isCommittedEating() && !(breakHp > 0 && bot.getHealth() <= breakHp);
    }

    boolean tickSurvival() {
        BotSettings es = ctx.settings();
        boolean inCombat = brain.target != null;
        boolean crystalling = brain.crystalPvpController.isExecuting() || brain.cpvpMode;
        boolean safeStyle = es.playStyle() == BotSettings.PlayStyle.SAFE;
        boolean bubbleBadSpot = brain.comboLockHits >= ctx.tuning().integer(TuningKey.SURVIVAL_COMBO_LOCK_HITS) || bot.getHealth() <= (float) (es.retreatHealthHearts() * 2.0);
        if ((brain.bubbleWebController.isActive()
                        || brain.target != null && !brain.cpvpMode && !crystalling && safeStyle && bubbleBadSpot && brain.bubbleWebController.canStart())
                && brain.bubbleWebController.tick(brain.target, brain.healingController, brain.enderPearlController)) {
            return true;
        } else {
            boolean comboLocked = brain.comboLockHits >= ctx.tuning().integer(TuningKey.SURVIVAL_COMBO_LOCK_HITS);
            boolean elytraExecuting = brain.elytraMaceController.isExecuting()
                    || brain.windBurstElytraController.isExecuting()
                    || brain.rocketlessElytraController.isExecuting()
                    || brain.elytraSpearController.isExecuting();
            brain.elytraOffense = brain.target != null
                    && brain.aiTier == BotAiTier.FULL
                    && !brain.cpvpMode
                    && es.useElytraMace()
                    && (elytraExecuting || comboLocked && brain.elytraMaceController.hasGearForCombo());
            boolean holdHealForOffense = brain.elytraOffense
                    && brain.offhandManager.hasTotemInOffhand()
                    && (elytraExecuting || !brain.melee.targetIsEating())
                    && !brain.healingController.starving();
            float retreatHp = (float) (es.retreatHealthHearts() * 2.0);
            if (brain.target != null
                    && !crystalling
                    && !eatLocked()
                    && brain.comboBreakController.tick(brain.target, brain.fightState, brain.enderPearlController)) {
                return true;
            } else {
                boolean criticalHp = bot.getHealth() <= ctx.tuning().number(TuningKey.SURVIVAL_CRITICAL_HP).floatValue();
                if (brain.target != null
                        && !crystalling
                        && (!holdHealForOffense || criticalHp)
                        && bot.getHealth() <= retreatHp
                        && !brain.melee.targetIsEating()
                        && !brain.melee.totemSacrificeActive(brain.target)
                        && es.useEnderPearl()
                        && !eatLocked()
                        && brain.enderPearlController.tryThrow(brain.target)) {
                    brain.shieldController.reset();
                    brain.healingController.requestSyncHeal();
                    return true;
                } else {
                    boolean targetHealingNow = brain.target != null && brain.melee.targetIsEating();
                    brain.healPunishWindow = 0;
                    if (targetHealingNow && !crystalling && !holdHealForOffense) {
                        if (bot.getHealth() > ctx.tuning().number(TuningKey.SURVIVAL_HEAL_PUNISH_MIN_HP).floatValue()) {
                            brain.healPunishWindow = ctx.tuning().integer(TuningKey.SURVIVAL_HEAL_PUNISH_TICKS);
                        } else {
                            brain.healingController.requestSyncHeal();
                        }
                    }

                    if (brain.target != null
                            && !crystalling
                            && !holdHealForOffense
                            && bot.getHealth() <= retreatHp
                            && !targetHealingNow
                            && brain.target.getHealth() >= ctx.tuning().number(TuningKey.SURVIVAL_EMERGENCY_WEB_TARGET_HP).floatValue()
                            && !brain.healingController.isHealing()) {
                        brain.cobwebTrapController.tryEmergencyWeb(brain.target);
                    }

                    if (brain.healingController.starving() && !brain.healingController.isHealing() && brain.melee.hasFoodInInventory()) {
                        brain.spearMaceController.reset();
                        brain.elytraMaceController.reset();
                        brain.windBurstElytraController.reset();
                        brain.rocketlessElytraController.reset();
                        brain.elytraSpearController.reset();
                        brain.pearlCatchController.reset();
                        brain.maceController.reset();
                    }

                    if (holdHealForOffense) {
                        brain.healingController.abortHealing();
                        brain.healing = false;
                    } else if (brain.melee.totemSacrificeActive(brain.target) && !brain.healingController.starving()) {
                        brain.healingController.abortHealing();
                        brain.healing = false;
                    } else {
                        brain.healing = brain.healingController.tick(inCombat && !crystalling, brain.target, crystalling);
                    }

                    if (brain.healing) {
                        brain.shieldController.reset();
                    }

                    if (brain.healing && brain.healingController.isRetreating() && !crystalling) {
                        brain.bunnyHopController.tick(true);
                        brain.rangedController.reset();
                        brain.shieldController.reset();
                        return true;
                    } else if (ctx.squad().tickSurround(ctx, brain.pathFollower::chase)) {
                        return true;
                    } else {
                        if (brain.target != null && brain.restockController.isExecuting()) {
                            if (brain.restockController.tick(brain.target)) {
                                return true;
                            }
                        } else if (brain.target != null && !brain.healing && !crystalling) {
                            if (brain.restockController.tick(brain.target)) {
                                return true;
                            }

                            if (!brain.restockController.isExecuting() && brain.shulkerRaidController.tick(brain.target)) {
                                return true;
                            }
                        }

                        if (brain.shulkerRaidController.isExecuting() && (brain.healing || crystalling || brain.restockController.isExecuting())) {
                            brain.shulkerRaidController.reset();
                        }

                        return false;
                    }
                }
            }
        }
    }
}
