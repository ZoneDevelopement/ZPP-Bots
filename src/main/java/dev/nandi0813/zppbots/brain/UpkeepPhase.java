package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.ai.mace.MaceDiveForecast;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.team.perf.BotAiTier;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.LedgeGuard;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class UpkeepPhase {

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;

    UpkeepPhase(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    boolean tickUpkeep() {
        if (brain.webDrainController.tick(brain.target)) {
            return true;
        } else {
            if (brain.aiTier != BotAiTier.SLEEP
                    && !brain.crystalPvpController.tickDetap(brain.target)
                    && brain.crystalPvpController.tickDangerAvoid(brain.target)) {
                return true;
            }

            boolean criticalArmour = brain.mendingController.hasCriticalPiece();
            brain.mendIntent = brain.mendingController.wantsToMend()
                    && bot.getHealth() >= ctx.tuning().number(TuningKey.UPKEEP_MEND_INTENT_MIN_HP).floatValue()
                    && !brain.cpvpMode
                    && !brain.crystalPvpController.isExecuting();
            if (brain.mendDisengageTicks > 0) {
                brain.mendDisengageTicks--;
            }

            if (brain.mendDisengageCooldown > 0) {
                brain.mendDisengageCooldown--;
            }

            if (brain.mendIntent
                    && brain.target != null
                    && Ent.alive(brain.target)
                    && brain.dist < ctx.tuning().number(TuningKey.UPKEEP_MEND_DISENGAGE_RANGE).doubleValue()
                    && bot.getHealth() >= ctx.tuning().number(TuningKey.UPKEEP_MEND_INTENT_MIN_HP).floatValue()
                    && !brain.specialExecuting
                    && !brain.mendingController.isMending()
                    && brain.mendDisengageTicks == 0
                    && brain.mendDisengageCooldown == 0) {
                brain.mendDisengageTicks = ctx.tuning().integer(TuningKey.UPKEEP_MEND_DISENGAGE_TICKS);
                brain.mendDisengageCooldown = ctx.tuning().integer(TuningKey.UPKEEP_MEND_DISENGAGE_COOLDOWN);
            }

            if (brain.mendDisengageTicks > 0
                    && brain.target != null
                    && Ent.alive(brain.target)
                    && brain.dist < ctx.tuning().number(TuningKey.UPKEEP_MEND_DISENGAGE_RANGE).doubleValue()
                    && bot.getHealth() >= ctx.tuning().number(TuningKey.UPKEEP_MEND_DISENGAGE_MIN_HP).floatValue()
                    && !brain.cpvpMode
                    && !brain.crystalPvpController.isExecuting()
                    && !brain.specialExecuting) {
                double awayX = bot.getLocation().getX() - brain.target.getLocation().getX();
                double awayZ = bot.getLocation().getZ() - brain.target.getLocation().getZ();
                if (awayX * awayX + awayZ * awayZ > 1.0E-4) {
                    float awayYaw = (float) Math.toDegrees(Math.atan2(-awayX, awayZ));
                    double lookX = -Math.sin(Math.toRadians(awayYaw));
                    double lookZ = Math.cos(Math.toRadians(awayYaw));
                    if (!LedgeGuard.wouldFallDangerously(ctx, lookX, lookZ, 3)) {
                        ctx.controls().look(awayYaw, 0.0F);
                        ctx.controls().setSprinting(true);
                        ctx.controls().moveForward();
                        return true;
                    }
                }

                brain.mendDisengageTicks = 0;
            }

            double mendGap = criticalArmour ? ctx.tuning().number(TuningKey.UPKEEP_MEND_GAP_CRITICAL).doubleValue() : ctx.tuning().number(TuningKey.UPKEEP_MEND_GAP).doubleValue();
            float mendHp = criticalArmour ? ctx.tuning().number(TuningKey.UPKEEP_MEND_HP_CRITICAL).floatValue() : ctx.tuning().number(TuningKey.UPKEEP_MEND_HP).floatValue();
            double abortGap = criticalArmour ? ctx.tuning().number(TuningKey.UPKEEP_ABORT_GAP_CRITICAL).doubleValue() : ctx.tuning().number(TuningKey.UPKEEP_ABORT_GAP).doubleValue();
            float abortHp = criticalArmour ? ctx.tuning().number(TuningKey.UPKEEP_ABORT_HP_CRITICAL).floatValue() : ctx.tuning().number(TuningKey.UPKEEP_ABORT_HP).floatValue();
            boolean combatMendLull = brain.target != null
                    && Ent.alive(brain.target)
                    && brain.dist >= mendGap
                    && bot.getHealth() >= mendHp
                    && !brain.specialExecuting;
            if (brain.target != null && Ent.alive(brain.target) && !combatMendLull) {
                if (brain.mendingController.isMending() && (brain.dist < abortGap || bot.getHealth() < abortHp)) {
                    brain.mendingController.abortMending();
                }
            } else if (brain.mendingController.tick(brain.target)) {
                return true;
            }

            brain.targeting.tickStanceWatchdog();
            if (!brain.healingController.isHealing()
                    && !brain.cartController.isExecuting()
                    && !brain.crystalPvpController.isExecuting()
                    && !brain.anchorPvpController.isExecuting()
                    && !brain.spearMaceController.isExecuting()
                    && !brain.elytraMaceController.isExecuting()
                    && !brain.windBurstElytraController.isExecuting()
                    && !brain.rocketlessElytraController.isExecuting()
                    && !brain.elytraSpearController.isExecuting()
                    && !brain.tridentMaceController.isExecuting()
                    && !brain.pearlCatchController.isExecuting()
                    && (!brain.restockController.isExecuting() || !bot.isGliding())
                    && !brain.potionController.isSplashing()
                    && !brain.jumpedForCrit) {
                if (brain.tickCount % 20 == 0) {
                    brain.armorManager.equipFromInventory();
                }

                brain.offhandManager.tick(brain.offhandWantShield);
                brain.loadoutManager.tick();
            }

            boolean isHealingNow = brain.healingController.isHealing();
            double potDist = brain.target != null ? Ent.dist(bot, brain.target) : Double.MAX_VALUE;
            if (!brain.potionController.isSplashing()
                    && !brain.potionController.isDrinking()
                    && brain.healingController.tickPrefightGapple()) {
                return true;
            }

            boolean potDrinking = brain.potionController.tick(brain.target, potDist, !isHealingNow);
            if (potDrinking && !isHealingNow) {
                brain.rangedController.reset();
                brain.shieldController.reset();
                return true;
            } else {
                isHealingNow = ctx.settings().shieldOnMaceDive();
                boolean movementFrozen = !ctx.match().canMove();
                MaceDiveForecast.Dive dive = brain.diveForecast.soonest();
                potDrinking = dive != null && dive.ticks() <= ctx.tuning().integer(TuningKey.UPKEEP_DIVE_SHIELD_LEAD_TICKS);
                if (potDrinking || brain.target != null && brain.melee.isIncomingFallAttack(brain.target)) {
                    brain.fallDiver = potDrinking ? dive.diver() : brain.target;
                    brain.fallDiveActive = true;
                    brain.fallShieldHoldTicks = 0;
                    if (!movementFrozen && brain.dodgeTicksLeft <= ctx.tuning().integer(TuningKey.UPKEEP_MACE_DODGE_REFRESH)) {
                        brain.melee.startMaceDodge(brain.fallDiver);
                    }
                }

                if (brain.fallDiveActive) {
                    if (brain.fallDiver == null || !Ent.alive(brain.fallDiver)) {
                        brain.fallDiver = brain.target;
                    }

                    if (isHealingNow && brain.melee.shouldHoldFallShield(brain.fallDiver) && ++brain.fallShieldHoldTicks <= ctx.tuning().integer(TuningKey.UPKEEP_FALL_SHIELD_MAX_HOLD)) {
                        brain.fallShieldTicksLeft = ctx.tuning().integer(TuningKey.UPKEEP_FALL_SHIELD_TICKS);
                    } else {
                        brain.fallDiveActive = false;
                        brain.fallShieldHoldTicks = 0;
                    }
                }

                if (!isHealingNow) {
                    brain.fallShieldTicksLeft = 0;
                    brain.melee.stopFallAttackShield();
                } else if (brain.fallShieldTicksLeft > 0) {
                    if (brain.fallDiver != null && brain.fallDiver != brain.target) {
                        brain.melee.faceEntity(brain.fallDiver);
                    } else if (brain.target != null) {
                        brain.melee.lookAtTarget();
                    }

                    brain.melee.tickFallAttackShield();
                    brain.fallShieldTicksLeft--;
                }

                if (!isHealingNow || brain.fallShieldTicksLeft <= 0) {
                    brain.melee.stopFallAttackShield();
                }

                if (brain.maceTotemUp) {
                    if (brain.dodgeTicksLeft > 0 && !movementFrozen) {
                        brain.melee.tickMaceDodgeMovement();
                        brain.dodgeTicksLeft--;
                    }

                    brain.maceTotemGuard.reassert();
                    return true;
                } else if (brain.dodgeTicksLeft > 0 && !movementFrozen) {
                    brain.melee.tickMaceDodgeMovement();
                    brain.dodgeTicksLeft--;
                    return true;
                } else {
                    return false;
                }
            }
        }
    }
}
