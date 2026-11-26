package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.ai.mace.MaceSmashGates;
import dev.nandi0813.zppbots.ai.mace.StunSlamDummy;
import dev.nandi0813.zppbots.ai.mace.TridentMaceController;
import dev.nandi0813.zppbots.ai.melee.WeaponSelector;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.settings.DifficultyParams;
import dev.nandi0813.zppbots.team.perf.BotAiTier;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.BotMath;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.MeleeWallGuard;
import dev.nandi0813.zppbots.util.SwapEngine;
import dev.nandi0813.zppbots.util.WebDetect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Random;
import java.util.function.BooleanSupplier;

final class MeleeCombat {

    private static final String STUN_STRIKE = "STUN_STRIKE";

    /** Set by a stun strike, cleared when the punish window closes, so one shield disable gets one strike. */
    private boolean stunStruck;

    /** The attack choice of one tick. */
    enum Move {
        AXE_FLICK,
        AXE_DISABLE,
        STUN_STRIKE,
        AUTO_CRIT,
        MIRROR_PUNISH,
        ANSWER_SWING,
        COMBO,
        CRIT_PHASE2,
        SWEEP,
        PIN_FLEEING,
        CRIT_JUMP,
        FALLBACK,
        HOLD
    }

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;

    MeleeCombat(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    /** getAttackStrengthScale(0.0F). */
    private static float charge(Player p) {
        return Ent.attackStrength(p, 0.0F);
    }

    private static ItemStack mainHand(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        return eq == null ? null : eq.getItemInMainHand();
    }

    boolean tickMeleeOpeners() {
        brain.rangedController.reset();
        if (targetIsInWeb() && !botIsInWeb()) {
            brain.noKbHoldW = 0;
            brain.sprintHitHold = 0;
        }

        if (!brain.enderPearlController.suppressesWebs() && brain.predictionWebController.tick(brain.target)) {
            return true;
        } else if (!brain.enderPearlController.suppressesWebs() && brain.headWebController.tick(brain.target)) {
            return true;
        } else if (brain.cobwebTrapController.tick(brain.target, brain.critThisCycle, brain.enderPearlController.suppressesWebs())) {
            return true;
        } else if (brain.hitReact != 0 && !brain.elytraOffense && !brain.tridentMaceController.isExecuting() && runHitReaction()) {
            return true;
        } else {
            boolean knockedBack = brain.knockbackTicks < ctx.tuning().integer(TuningKey.MELEE_KNOCKBACK_MAX_TICKS)
                    && (brain.kbYieldWindow > 0 || isKnockedBack())
                    && !brain.tridentMaceController.isExecuting()
                    && !brain.elytraOffense;
            if (knockedBack) {
                if (brain.noKbHoldW > 0 && !isHardIncomingHit()) {
                    brain.noKbHoldW--;
                    brain.knockbackTicks++;
                    brain.inCombo = false;
                    aimAtTarget();
                    ctx.controls().setMovement(1.0F, 0.0F);
                    if (brain.critSwingHold == 0) {
                        ctx.controls().setSprinting(true);
                    }

                    return true;
                } else {
                    brain.knockbackTicks++;
                    brain.inCombo = false;
                    brain.swingHold = 0;
                    brain.targetLandWindow = 0;
                    brain.critFollowUpWindow = 0;
                    brain.maceController.reset();
                    brain.tridentMaceController.reset();
                    brain.pathFollower.reset();
                    aimAtTarget();
                    if (brain.counterStunWindow > 0) {
                        ctx.controls().setMovement(-1.0F, 0.0F);
                        brain.sprintHitHold = 0;
                        sprintOffForCrit();
                    } else {
                        ctx.controls().stopMoving();
                        ctx.controls().setSprinting(false);
                    }

                    return true;
                }
            } else {
                brain.knockbackTicks = 0;
                if (!brain.cpvpMode) {
                    tryJumpReset();
                }

                if (brain.weapon == WeaponSelector.WeaponType.SWORD
                        && !brain.inCombo
                        && bot.isOnGround()
                        && brain.crouchDodgeTicks == 0
                        && charge(bot) < ctx.tuning().number(TuningKey.MELEE_CROUCH_DODGE_MAX_CHARGE).floatValue()
                        && brain.dist <= brain.attackRange + ctx.tuning().number(TuningKey.MELEE_READ_RANGE_MARGIN).doubleValue()
                        && incomingCritDive()) {
                    brain.crouchDodgeTicks = ctx.tuning().integer(TuningKey.MELEE_CROUCH_DODGE_TICKS);
                }

                boolean fireworksForElytraMace = brain.elytraMaceController.hasFireworksForCombo();
                if (brain.aiTier == BotAiTier.FULL
                        && !brain.healing
                        && !brain.cpvpMode
                        && !brain.spearMaceOffered
                        && !brain.lastMaceComboWasSpear
                        && brain.maySpecial(brain.spearMaceController.isExecuting())) {
                    brain.spearMaceOffered = true;
                    if (brain.spearMaceController.tick(brain.target, brain.dist)) {
                        brain.lastMaceComboWasSpear = true;
                        return true;
                    }
                }

                if (brain.aiTier == BotAiTier.FULL
                        && !brain.healing
                        && !brain.cpvpMode
                        && ctx.settings().useElytraMace()
                        && brain.maySpecial(brain.elytraMaceController.isExecuting())
                        && brain.elytraMaceController.tick(brain.target, brain.dist)) {
                    brain.lastMaceComboWasSpear = false;
                    return true;
                } else if (brain.aiTier == BotAiTier.FULL
                        && !brain.healing
                        && !brain.cpvpMode
                        && ctx.settings().useElytraMace()
                        && brain.windBurstElytraController.tick(brain.target, brain.dist)) {
                    brain.lastMaceComboWasSpear = false;
                    return true;
                } else if (brain.aiTier == BotAiTier.FULL
                        && !brain.healing
                        && !brain.cpvpMode
                        && ctx.settings().useElytraMace()
                        && !fireworksForElytraMace
                        && brain.maySpecial(brain.rocketlessElytraController.isExecuting())
                        && brain.rocketlessElytraController.tick(brain.target, brain.dist)) {
                    brain.lastMaceComboWasSpear = false;
                    return true;
                } else if (brain.aiTier == BotAiTier.FULL
                        && !brain.healing
                        && !brain.cpvpMode
                        && ctx.settings().useElytraSpear()
                        && brain.maySpecial(brain.elytraSpearController.isExecuting())
                        && brain.elytraSpearController.tick(brain.target, brain.dist)) {
                    brain.lastMaceComboWasSpear = false;
                    return true;
                } else if (brain.aiTier == BotAiTier.FULL
                        && !brain.healing
                        && !brain.cpvpMode
                        && (brain.tridentMaceController.isExecuting() || TridentMaceController.canAttempt(ctx))
                        && brain.maySpecial(brain.tridentMaceController.isExecuting())
                        && brain.tridentMaceController.tick(brain.target, brain.dist)) {
                    return true;
                } else if (brain.aiTier == BotAiTier.FULL
                        && !brain.healing
                        && !brain.cpvpMode
                        && !brain.spearMaceOffered
                        && brain.maySpecial(brain.spearMaceController.isExecuting())
                        && brain.spearMaceController.tick(brain.target, brain.dist)) {
                    brain.lastMaceComboWasSpear = true;
                    return true;
                } else {
                    return brain.aiTier == BotAiTier.FULL
                                    && !brain.healing
                                    && !brain.cpvpMode
                                    && !fireworksForElytraMace
                                    && brain.maySpecial(brain.pearlCatchController.isExecuting())
                                    && brain.pearlCatchController.tick(brain.target, brain.dist)
                            ? true
                            : brain.aiTier == BotAiTier.FULL
                                    && !brain.healing
                                    && !brain.cpvpMode
                                    && brain.maySpecial(brain.maceController.isExecuting())
                                    && brain.maceController.tick(brain.target, brain.dist);
                }
            }
        }
    }

    boolean tickMeleeReads() {
        brain.blocking = false;
        boolean swordFight = brain.weapon == WeaponSelector.WeaponType.SWORD || brain.weapon == WeaponSelector.WeaponType.AXE;
        if (!brain.healing && swordFight) {
            boolean usingMainHandItem = bot.isHandRaised() && bot.getActiveItemHand() == EquipmentSlot.HAND;
            boolean shieldAvail = brain.offhandManager.hasShieldInOffhand() && !usingMainHandItem;
            if (comboShieldLock()) {
                brain.shieldController.reset();
                brain.blocking = false;
            } else {
                BotSettings.PlayStyle style = ctx.settings().playStyle();
                boolean sacrificeNow = (style == BotSettings.PlayStyle.SAFE || style == BotSettings.PlayStyle.DEFENSIVE)
                        && shieldAvail
                        && brain.dist <= ctx.tuning().number(TuningKey.MELEE_SACRIFICE_RANGE).doubleValue()
                        && !brain.cpvpMode
                        && WeaponSelector.getType(mainHand(brain.target)) == WeaponSelector.WeaponType.AXE;
                brain.shieldController.setSacrifice(sacrificeNow);
                if (brain.shieldBaitCooldown > 0) {
                    brain.shieldBaitCooldown--;
                } else if (shieldAvail && brain.dist <= ctx.tuning().number(TuningKey.MELEE_BAIT_RANGE).doubleValue() && !brain.cpvpMode) {
                    float myCharge = charge(bot);
                    float theirCharge = brain.target instanceof Player pl ? charge(pl) : 1.0F;
                    if (myCharge >= ctx.tuning().number(TuningKey.MELEE_BAIT_MIN_CHARGE).floatValue() && myCharge <= ctx.tuning().number(TuningKey.MELEE_BAIT_MAX_CHARGE).floatValue() && theirCharge >= ctx.tuning().number(TuningKey.MELEE_BAIT_TARGET_CHARGE).floatValue() && ctx.settings().roll()) {
                        brain.shieldController.requestBait(ctx.tuning().integer(TuningKey.MELEE_BAIT_TICKS_MIN) + ctx.random().nextInt(ctx.tuning().integer(TuningKey.MELEE_BAIT_TICKS_SPAN)));
                        brain.shieldBaitCooldown = ctx.tuning().integer(TuningKey.MELEE_BAIT_COOLDOWN);
                    }
                }

                brain.shieldController.tick(brain.target, shieldAvail);
                brain.blocking = brain.shieldController.isBlocking();
            }
        } else {
            brain.shieldController.reset();
        }

        brain.targetEating = targetIsEating();
        if (brain.target != null) {
            LivingEntity target = brain.target;
            if (target != brain.readTarget) {
                brain.readTarget = target;
                brain.oppJumpResets = 0;
                brain.comboCadenceBoost = 0.0F;
                brain.jrJitter = 0;
                brain.targetHitObserve = 0;
                brain.prevTargetOnGround = target.isOnGround();
                brain.prevTargetBlocking = brain.targetBlocking();
                brain.prevTargetHurtTime = target.getNoDamageTicks();
                brain.prevTargCharge = target instanceof Player rp ? charge(rp) : 0.0F;
            }

            boolean landedNow = !brain.prevTargetOnGround && target.isOnGround();
            brain.prevTargetOnGround = target.isOnGround();
            if (landedNow) {
                brain.targetLandWindow = ctx.tuning().integer(TuningKey.MELEE_LAND_WINDOW_TICKS);
            } else if (brain.targetLandWindow > 0) {
                brain.targetLandWindow--;
            }

            if (brain.critFollowUpWindow > 0) {
                brain.critFollowUpWindow--;
            }

            if (brain.critChainWindow > 0) {
                brain.critChainWindow--;
            }

            brain.axeController.tickWindows();
            boolean targetBlockingNow = brain.targetBlocking();
            if (brain.prevTargetBlocking
                    && !targetBlockingNow
                    && Ent.dist(bot, target) <= brain.attackRange + ctx.tuning().number(TuningKey.MELEE_READ_RANGE_MARGIN).doubleValue()
                    && ctx.settings().roll()) {
                brain.mirrorPunishWindow = ctx.tuning().integer(TuningKey.MELEE_MIRROR_PUNISH_TICKS);
            }

            brain.prevTargetBlocking = targetBlockingNow;
            if (brain.mirrorPunishWindow > 0) {
                brain.mirrorPunishWindow--;
            }

            ItemStack offh = bot.getInventory().getItemInOffHand();
            boolean shieldDisabledNow = ItemKinds.is(offh, Material.SHIELD) && bot.hasCooldown(offh);
            if (shieldDisabledNow
                    && !brain.prevShieldDisabled
                    && Ent.dist(bot, target) <= brain.attackRange + ctx.tuning().number(TuningKey.MELEE_AUTO_CRIT_RANGE_MARGIN).doubleValue()
                    && ctx.settings().roll()) {
                brain.autoCritWindow = ctx.tuning().integer(TuningKey.MELEE_AUTO_CRIT_TICKS);
            }

            brain.prevShieldDisabled = shieldDisabledNow;
            brain.shieldBrokenNow = shieldDisabledNow;
            if (shieldDisabledNow) {
                ctx.controls().setSprinting(false);
            }

            brain.healingController.setShieldBroken(shieldDisabledNow);
            if (brain.autoCritWindow > 0) {
                brain.autoCritWindow--;
            }

            if (targetArmorMissing(target) && Ent.dist(bot, target) <= ctx.tuning().number(TuningKey.MELEE_ARMOR_PUNISH_RANGE).doubleValue()) {
                brain.armorPunishWindow = ctx.tuning().integer(TuningKey.MELEE_ARMOR_PUNISH_TICKS);
            } else if (brain.armorPunishWindow > 0) {
                brain.armorPunishWindow--;
            }

            brain.targetPingMs = target instanceof Player sp2 ? sp2.getPing() : 0;
            brain.targetHighPing = brain.targetPingMs > ctx.tuning().integer(TuningKey.MELEE_HIGH_PING_MS);
            if (target instanceof Player tp) {
                float tCharge = charge(tp);
                if (brain.prevTargCharge - tCharge >= ctx.tuning().number(TuningKey.MELEE_ANSWER_SWING_CHARGE_DROP).floatValue()
                        && Ent.dist(bot, target) <= brain.attackRange + ctx.tuning().number(TuningKey.MELEE_READ_RANGE_MARGIN).doubleValue()
                        && ctx.settings().roll()) {
                    brain.answerSwingWindow = ctx.tuning().integer(TuningKey.MELEE_ANSWER_SWING_TICKS);
                }

                brain.prevTargCharge = tCharge;
            }

            if (brain.answerSwingWindow > 0) {
                brain.answerSwingWindow--;
            }

            if (ctx.settings().playStyle() == BotSettings.PlayStyle.BALANCED
                    && target.getHealth() + ctx.tuning().number(TuningKey.MELEE_OVEREXTEND_HP_LEAD).floatValue() < bot.getHealth()
                    && opponentAggressive()) {
                brain.overextendWindow = ctx.tuning().integer(TuningKey.MELEE_OVEREXTEND_TICKS);
            } else if (brain.overextendWindow > 0) {
                brain.overextendWindow--;
            }

            int targetHurt = target.getNoDamageTicks();
            boolean targetFreshHurt = targetHurt > brain.prevTargetHurtTime;
            brain.prevTargetHurtTime = targetHurt;
            if (targetFreshHurt) {
                brain.targetHitObserve = ctx.tuning().integer(TuningKey.MELEE_HIT_OBSERVE_TICKS);
            } else if (brain.targetHitObserve > 0) {
                brain.targetHitObserve--;
                Vec3 tv = ctx.match().velocityOf(target);
                double drift = Math.sqrt(tv.x() * tv.x() + tv.z() * tv.z());
                if (!target.isOnGround() && tv.y() > ctx.tuning().number(TuningKey.MELEE_JUMP_RESET_MIN_RISE).doubleValue() && drift < ctx.tuning().number(TuningKey.MELEE_JUMP_RESET_MAX_DRIFT).doubleValue()) {
                    if (brain.oppJumpResets < ctx.tuning().integer(TuningKey.MELEE_JUMP_RESETS_MAX)) {
                        brain.oppJumpResets++;
                    }

                    brain.targetHitObserve = 0;
                } else if (!target.isOnGround() && tv.y() > ctx.tuning().number(TuningKey.MELEE_AIRBORNE_RISE).doubleValue()) {
                    brain.comboCadenceBoost = Math.max(0.0F, brain.comboCadenceBoost - ctx.tuning().number(TuningKey.MELEE_CADENCE_STEP).floatValue());
                    brain.targetHitObserve = 0;
                } else if (brain.targetHitObserve == 0 && target.isOnGround()) {
                    brain.comboCadenceBoost = Math.min(ctx.tuning().number(TuningKey.MELEE_CADENCE_MAX).floatValue(), brain.comboCadenceBoost + ctx.tuning().number(TuningKey.MELEE_CADENCE_STEP).floatValue());
                }
            }
        }

        return false;
    }

    void tickMeleeExchange() {
        LivingEntity target = brain.target;
        if (!brain.targetEating) {
            float cycleCharge = charge(bot);
            if (cycleCharge < ctx.tuning().number(TuningKey.MELEE_CYCLE_RESET_CHARGE).floatValue()) {
                brain.wTapThisCycle = true;
                brain.critThisCycle = true;
                brain.comboThisCycle = true;
                brain.reactionCountdown = -1;
                brain.hitSelectThisCycle = ctx.random().nextInt(100) < ctx.tuning().integer(TuningKey.MELEE_HIT_SELECT_CHANCE);
                brain.earlyCritThisCycle = !brain.hitSelectThisCycle && ctx.difficulty().reactionMaxTicks <= ctx.tuning().integer(TuningKey.MELEE_EARLY_CRIT_MAX_REACTION) && ctx.settings().roll();
                brain.jrJitter = brain.oppJumpResets >= ctx.tuning().integer(TuningKey.MELEE_JR_JITTER_MIN_RESETS) ? ctx.random().nextInt(ctx.tuning().integer(TuningKey.MELEE_JR_JITTER_SPAN)) : 0;
                brain.hitSelectHold = 0;
            }
        }

        if (brain.dist > brain.attackRange || !bot.hasLineOfSight(target)) {
            if (ctx.settings().techniques().pearlGrapple
                    && ctx.settings().pearlChasing()
                    && !brain.spearMaceController.canCloseGap(brain.dist)
                    && brain.dist > ctx.tuning().number(TuningKey.MELEE_GRAPPLE_MIN_DIST).doubleValue()
                    && brain.dist < ctx.tuning().number(TuningKey.MELEE_GRAPPLE_MAX_DIST).doubleValue()
                    && bot.getHealth() > ctx.tuning().number(TuningKey.MELEE_GRAPPLE_MIN_HP).floatValue()
                    && bot.hasLineOfSight(target)
                    && !brain.cobwebTrapController.placedWebRecently()
                    && brain.enderPearlController.tryThrowToward(target)) {
                if (bot.getHealth() <= Ent.maxHealth(bot) - ctx.tuning().number(TuningKey.MELEE_GRAPPLE_HEAL_MISSING_HP).floatValue()) {
                    brain.healingController.requestSyncHeal();
                }

                lookAtTarget();
                return;
            }

            brain.releaseAfterHit = 0;
            brain.sprintHitHold = 0;
            boolean punishWindow = brain.targetEating || brain.armorPunishWindow > 0 || brain.overextendWindow > 0;
            boolean maceStyleKit = brain.weapon == WeaponSelector.WeaponType.MACE
                    || brain.spearMaceController.isExecuting()
                    || brain.elytraMaceController.isExecuting()
                    || brain.rocketlessElytraController.isExecuting()
                    || brain.elytraSpearController.isExecuting()
                    || brain.windBurstElytraController.isExecuting()
                    || brain.tridentMaceController.isExecuting()
                    || brain.maceController.isExecuting();
            boolean openingWalk = brain.fightState.isOpening()
                    && !punishWindow
                    && !maceStyleKit
                    && (brain.weapon == WeaponSelector.WeaponType.SWORD || brain.weapon == WeaponSelector.WeaponType.AXE);
            brain.releaseAfterHit = 0;
            brain.sprintHitHold = 0;
            if (openingWalk) {
                double approachStrafe = ctx.difficulty().strafeAngle;
                boolean targetMoving = ctx.match().velocityOf(target).horizontalDistanceSqr() > ctx.tuning().number(TuningKey.MELEE_TARGET_MOVING_SPEED_SQR).doubleValue();
                brain.approachStyle.tick(brain.dist, approachStrafe, targetMoving);
                if (brain.approachStyle.paused() && !bot.isInWater()) {
                    brain.pathFollower.reset();
                    ctx.controls().stopMoving();
                    ctx.controls().setSprinting(false);
                    lookAtTarget();
                    return;
                }

                Vec3 walkGoal = brain.approachStyle.goal(target, Ent.pos(bot), brain.dist);
                Vec3 spread = ctx.settings().chaseSpread() ? ctx.squad().chaseSpread().offsetFor(ctx, target, brain.dist) : null;
                brain.pathFollower.chase(spread != null ? walkGoal.add(spread) : walkGoal, Ent.pos(target));
            } else {
                Vec3 spread = ctx.settings().chaseSpread() ? ctx.squad().chaseSpread().offsetFor(ctx, target, brain.dist) : null;
                if (spread != null) {
                    brain.pathFollower.chase(Ent.pos(target).add(spread), Ent.pos(target));
                } else {
                    brain.pathFollower.chase(target, Ent.pos(target));
                }
            }

            lookAtTarget();
            if (!brain.pathFollower.isSwimNav()) {
                if (punishWindow) {
                    ctx.controls().setSprinting(true);
                } else if (openingWalk) {
                    double approachStrafe = ctx.difficulty().strafeAngle;
                    ctx.controls().setSprinting(brain.approachStyle.shouldSprint(brain.dist, approachStrafe) && !brain.blocking);
                } else {
                    float charge = charge(bot);
                    boolean nearAttackZone = brain.dist <= brain.attackRange + ctx.tuning().number(TuningKey.MELEE_WTAP_ZONE_MARGIN).doubleValue();
                    boolean wtapping = nearAttackZone && brain.wTap.shouldStopSprint(charge) && brain.wTapThisCycle;
                    ctx.controls().setSprinting(!wtapping && !brain.blocking);
                }
            }

            if (!brain.cpvpMode && !brain.pathFollower.isSwimNav() && (!(brain.dist < ctx.tuning().number(TuningKey.MELEE_WEB_NO_HOP_RANGE).doubleValue()) || !targetIsInWeb())) {
                brain.bunnyHopController.tick(!openingWalk || brain.approachStyle.shouldHop(brain.dist));
            }

            if (!brain.cpvpMode && ctx.settings().placeBlocksMoving() && brain.buildFight.scaffoldKit()) {
                brain.jumpPlaceController.tick(target, brain.dist);
            } else {
                brain.jumpPlaceController.reset();
            }
        } else if (bot.isInWater() && !bot.isOnGround()) {
            brain.pathFollower.chase(target, Ent.pos(target));
            lookAtTarget();
        } else {
            brain.pathFollower.reset();
            aimAtTarget();
            if (targetIsInWeb() && !botIsInWeb()) {
                ctx.controls().stopMoving();
                ctx.controls().setSprinting(false);
            } else if (brain.releaseAfterHit > 0 && brain.sprintHitHold == 0 && !targetFleeing()) {
                brain.releaseAfterHit--;
                ctx.controls().setMovement(0.0F, 0.0F);
                ctx.controls().setSprinting(false);
            } else {
                updateCombatMovement(brain.blocking);
            }

            if (!brain.cpvpMode) {
                brain.bunnyHopController.tick(false);
            }
        }

        if (!brain.blocking && !brain.healing && (!brain.cpvpMode || brain.weapon == WeaponSelector.WeaponType.AXE)) {
            tryAttack(brain.weapon);
        }
    }

    void lookAtTarget() {
        if (brain.target != null) {
            Vec3 eye = Ent.eyePos(bot);
            Vec3 t = Ent.pos(brain.target);
            float[] angles = BotMath.getAngles(eye.x(), eye.y(), eye.z(), t.x(), Ent.eyePos(brain.target).y(), t.z());
            float headYaw = BotMath.smoothYawToward(bot.getLocation().getYaw(), angles[0], ctx.tuning().number(TuningKey.MELEE_AIM_HEAD_MAX_ROT).floatValue());
            ctx.controls().look(BotMath.wrapDegrees(headYaw), angles[1]);
        }
    }

    void aimAtTarget() {
        Vec3 eye = Ent.eyePos(bot);
        Vec3 t = Ent.pos(brain.target);
        float[] angles = BotMath.getAngles(eye.x(), eye.y(), eye.z(), t.x(), Ent.eyePos(brain.target).y(), t.z());
        float yaw = angles[0];
        float pitch = angles[1];
        brain.straightYaw = yaw;
        brain.straightPitch = pitch;
        if (brain.pendingAttack) {
            ctx.controls().look(yaw, pitch);
            brain.meleeForwardImpulse = 1.0F;
            brain.meleeStrafeImpulse = ctx.settings().strafeInCombo() ? brain.strafeController.attackTickStrafe() : 0.0F;
            brain.pendingAttack = false;
        } else {
            float baseAngle = (float) ctx.difficulty().strafeAngle;
            float strafeAngleDeg = brain.strafeController.getYawOffset(brain.tickCount, baseAngle);
            float rad = (float) Math.toRadians(strafeAngleDeg);
            brain.meleeForwardImpulse = (float) Math.cos(rad);
            brain.meleeStrafeImpulse = (float) Math.sin(rad);
            float lookYaw = BotMath.smoothYawToward(bot.getLocation().getYaw(), yaw, ctx.tuning().number(TuningKey.MELEE_AIM_HEAD_MAX_ROT).floatValue());
            ctx.controls().look(BotMath.wrapDegrees(lookYaw), pitch);
        }
    }

    void meleeBack() {
        if (MeleeWallGuard.deflect(ctx, -1.0F, 0.0F)[0] != 0.0F) {
            ctx.controls().moveBackward();
        }
    }

    void updateCombatMovement(boolean blocking) {
        double dist = Ent.dist(bot, brain.target);
        float charge = charge(bot);
        boolean wtapping = brain.wTap.shouldStopSprint(charge);
        if (brain.feintCooldown > 0) {
            brain.feintCooldown--;
        }

        float targCharge = brain.target instanceof Player p ? charge(p) : 0.0F;
        if (brain.feintTimer > 0) {
            brain.feintTimer--;
            switch (brain.feintDir) {
                case -1:
                    ctx.controls().moveLeft();
                    break;
                case 1:
                    ctx.controls().moveRight();
                    break;
                default:
                    ctx.controls().moveBackward();
            }

            ctx.controls().setSprinting(false);
        } else if (brain.feintCooldown <= 0
                && dist <= brain.attackRange
                && charge >= ctx.tuning().number(TuningKey.MELEE_FEINT_MIN_CHARGE).floatValue()
                && charge <= ctx.tuning().number(TuningKey.MELEE_FEINT_MAX_CHARGE).floatValue()
                && targCharge < ctx.tuning().number(TuningKey.MELEE_FEINT_TARGET_MAX_CHARGE).floatValue()
                && ctx.settings().roll()) {
            brain.feintTimer = ctx.tuning().integer(TuningKey.MELEE_FEINT_TICKS_MIN)
                    + ctx.random().nextInt(ctx.tuning().integer(TuningKey.MELEE_FEINT_TICKS_SPAN));
            brain.feintCooldown = ctx.tuning().integer(TuningKey.MELEE_FEINT_COOLDOWN);
            brain.feintDir = ctx.random().nextInt(3) - 1;
        } else {
            if (brain.inCombo) {
                if (wtapping && wtapHolds(dist, brain.swingReach.get(brain.attackRange))) {
                    if (opponentAggressive()) {
                        meleeBack();
                    } else {
                        ctx.controls().setMovement(0.0F, 0.0F);
                    }

                    ctx.controls().setSprinting(false);
                } else if (wtapping) {
                    meleeMove(1.0F, 0.0F);
                    ctx.controls().setSprinting(false);
                } else {
                    float fwd = dist > comboHoldDistance(ctx.tuning(), brain.swingReach.get(brain.attackRange)) ? 1.0F : 0.0F;
                    float side = 0.0F;
                    if (ctx.settings().strafeInCombo()) {
                        float rad = (float) Math.toRadians(brain.strafeController.getYawOffset(
                                brain.tickCount, ctx.tuning().number(TuningKey.MELEE_COMBO_STRAFE_ANGLE).floatValue()));
                        side = (float) Math.sin(rad) * ctx.tuning().number(TuningKey.MELEE_COMBO_STRAFE_SCALE).floatValue();
                        if (fwd == 0.0F) {
                            fwd = Math.abs((float) Math.cos(rad))
                                    * ctx.tuning().number(TuningKey.MELEE_COMBO_STRAFE_FORWARD).floatValue();
                        }
                    }

                    meleeMove(fwd, side);
                    ctx.controls().setSprinting(!blocking);
                }
            } else {
                meleeMove(brain.meleeForwardImpulse, brain.meleeStrafeImpulse);
                ctx.controls().setSprinting(!wtapping && !blocking);
            }
        }
    }

    boolean isPureSwordKit() {
        EntityEquipment eq = bot.getEquipment();
        if (eq != null && ItemKinds.is(eq.getChestplate(), Material.ELYTRA)) {
            return false;
        } else {
            PlayerInventory inv = bot.getInventory();

            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!ItemKinds.isEmpty(s)
                        && (s.getType() == Material.MACE
                                || s.getType() == Material.ELYTRA
                                || s.getType() == Material.END_CRYSTAL
                                || s.getType() == Material.RESPAWN_ANCHOR
                                || s.getType() == Material.TNT_MINECART)) {
                    return false;
                }
            }

            return true;
        }
    }

    boolean targetIsEating() {
        if (brain.target != null && brain.target.isHandRaised()) {
            ItemStack used = brain.target.getActiveItem();
            return ItemKinds.isFood(used) || ItemKinds.is(used, Material.POTION);
        } else {
            return false;
        }
    }

    void releaseHotbarShield() {
        if (brain.shieldHoldTicks > 0
                && bot.isHandRaised()
                && bot.getActiveItemHand() == EquipmentSlot.HAND
                && ItemKinds.is(bot.getActiveItem(), Material.SHIELD)) {
            ctx.hands().releaseUseItem();
        }

        brain.shieldHoldTicks = 0;
    }

    boolean hasFoodInInventory() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isFood(inv.getItem(i))) {
                return true;
            }
        }

        return false;
    }

    boolean targetArmorMissing(LivingEntity t) {
        if (!(t instanceof Player)) {
            return false;
        } else {
            EntityEquipment eq = t.getEquipment();
            if (eq == null) {
                return false;
            }

            int empty = 0;
            if (ItemKinds.isEmpty(eq.getHelmet())) {
                empty++;
            }

            if (ItemKinds.isEmpty(eq.getChestplate())) {
                empty++;
            }

            if (ItemKinds.isEmpty(eq.getLeggings())) {
                empty++;
            }

            if (ItemKinds.isEmpty(eq.getBoots())) {
                empty++;
            }

            return empty >= ctx.tuning().integer(TuningKey.MELEE_ARMOR_MISSING_PIECES);
        }
    }

    boolean totemSacrificeActive(LivingEntity t) {
        return t != null
                && Ent.alive(t)
                && t.getHealth() <= ctx.tuning().number(TuningKey.MELEE_TOTEM_SACRIFICE_HP).floatValue()
                && bot.getHealth() <= (float) (ctx.settings().retreatHealthHearts() * 2.0)
                && brain.offhandManager.hasTotemInOffhand()
                && Ent.dist(bot, t) <= brain.attackRange
                        + ctx.tuning().number(TuningKey.MELEE_TOTEM_SACRIFICE_RANGE_MARGIN).doubleValue();
    }

    float comboFireAt() {
        return comboFireAt(ctx.tuning(), brain.comboCadenceBoost, brain.targetHighPing);
    }

    static float comboFireAt(BotTuning.Profile profile, float comboCadenceBoost, boolean targetHighPing) {
        float at = profile.number(TuningKey.MELEE_COMBO_FIRE_BASE).floatValue() + comboCadenceBoost
                + (targetHighPing ? profile.number(TuningKey.MELEE_COMBO_HIGH_PING_BONUS).floatValue() : 0.0F);
        return Math.min(at, 1.0F);
    }

    // Stops at 2.7 under a raised reach attribute; this bot keeps vanilla reach, so never stop outside the sampled swing reach.
    static double comboHoldDistance(BotTuning.Profile profile, double swingReach) {
        return Math.min(
                profile.number(TuningKey.MELEE_COMBO_HOLD_MAX).doubleValue(),
                swingReach - profile.number(TuningKey.MELEE_COMBO_HOLD_REACH_MARGIN).doubleValue());
    }

    // The W-tap latch clears only on a swing, so holding outside swing reach would stall forever.
    static boolean wtapHolds(double dist, double swingReach) {
        return dist <= swingReach;
    }

    boolean shouldHoldSwing() {
        if (!brain.hitSelectThisCycle) {
            return false;
        } else if (brain.hitSelectHold >= ctx.tuning().integer(TuningKey.MELEE_HIT_SELECT_MAX_HOLD) + brain.jrJitter) {
            return false;
        } else if (brain.target instanceof Player tp) {
            boolean imminent = charge(tp) >= ctx.tuning().number(TuningKey.MELEE_HIT_SELECT_IMMINENT_CHARGE).floatValue();
            return (imminent || brain.oppJumpResets >= ctx.tuning().integer(TuningKey.MELEE_HIT_SELECT_MIN_RESETS))
                    && Ent.dist(bot, brain.target)
                            <= brain.attackRange + ctx.tuning().number(TuningKey.MELEE_HIT_SELECT_RANGE_MARGIN).doubleValue();
        } else {
            return false;
        }
    }

    boolean targetFleeing() {
        if (brain.target == null) {
            return false;
        } else {
            Vec3 tv = targetVelocity();
            double sp = Math.sqrt(tv.x() * tv.x() + tv.z() * tv.z());
            if (sp < ctx.tuning().number(TuningKey.MELEE_FLEE_MIN_SPEED).doubleValue()) {
                return false;
            } else {
                double dx = brain.target.getLocation().getX() - bot.getLocation().getX();
                double dz = brain.target.getLocation().getZ() - bot.getLocation().getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                return len < 0.01
                        ? false
                        : (tv.x() * dx + tv.z() * dz) / (sp * len) > ctx.tuning().number(TuningKey.MELEE_FLEE_DOT).doubleValue();
            }
        }
    }

    boolean targetFinishable() {
        return brain.target != null
                && brain.target.getHealth() + brain.target.getAbsorptionAmount()
                        <= ctx.tuning().number(TuningKey.MELEE_FINISH_HP).floatValue()
                && targetFleeing();
    }

    boolean targetIsInWeb() {
        if (brain.target == null) {
            return false;
        } else {
            BlockPos pos = BlockPos.containing(
                    brain.target.getLocation().getX(), brain.target.getLocation().getY(), brain.target.getLocation().getZ());
            return ctx.world().type(pos) == Material.COBWEB;
        }
    }

    boolean botIsInWeb() {
        return WebDetect.touchingWeb(bot);
    }

    int rollReactionDelay() {
        return rollReactionDelay(ctx.difficulty(), ctx.random());
    }

    static int rollReactionDelay(DifficultyParams params, Random rng) {
        return params.rollReactionTicks(rng);
    }

    boolean readyToFire(float charge, float threshold) {
        if (charge < threshold) {
            return false;
        } else {
            if (brain.reactionCountdown == -1) {
                brain.reactionCountdown = rollReactionDelay();
            }

            if (brain.reactionCountdown > 0) {
                brain.reactionCountdown--;
                return false;
            } else {
                brain.reactionCountdown = -1;
                return true;
            }
        }
    }

    float[] pickSwingRotation() {
        float[] r = pickSwingRotation(
                ctx.tuning(), brain.straightYaw, brain.straightPitch, (float) ctx.difficulty().missChance, ctx.random());
        brain.swingIsMiss = r[2] != 0.0F;
        return new float[] {r[0], r[1]};
    }

    /** Returns {yaw, pitch, miss ? 1 : 0}. */
    static float[] pickSwingRotation(BotTuning.Profile profile, float yaw, float pitch, float missChance, Random rng) {
        if (!(missChance <= 0.0F) && !(rng.nextFloat() >= missChance)) {
            float yawOff = (rng.nextFloat() - 0.5F) * profile.number(TuningKey.MELEE_MISS_YAW_SPREAD).floatValue();
            float pitOff = (rng.nextFloat() - 0.5F) * profile.number(TuningKey.MELEE_MISS_PITCH_SPREAD).floatValue();
            return new float[] {
                BotMath.wrapDegrees(yaw + yawOff), Math.max(-90.0F, Math.min(90.0F, pitch + pitOff)), 1.0F
            };
        } else {
            return new float[] {yaw, pitch, 0.0F};
        }
    }

    void updateAdaptiveStyle(LivingEntity target) {
        if (ctx.settings().isAdaptive()) {
            if (target != null) {
                double dx = bot.getLocation().getX() - target.getLocation().getX();
                double dz = bot.getLocation().getZ() - target.getLocation().getZ();
                double len = Math.hypot(dx, dz);
                double signal = 0.0;
                if (len > 0.01) {
                    Vec3 v = ctx.match().velocityOf(target);
                    double closing = (v.x() * dx + v.z() * dz) / len;
                    double closingSpeed = ctx.tuning().number(TuningKey.MELEE_ADAPTIVE_CLOSING_SPEED).doubleValue();
                    if (closing > closingSpeed) {
                        signal++;
                    } else if (closing < -closingSpeed) {
                        signal--;
                    }
                }

                if (brain.comboLockHits > 0) {
                    signal += ctx.tuning().number(TuningKey.MELEE_ADAPTIVE_COMBO_SIGNAL).doubleValue();
                }

                if (targetIsEating()) {
                    signal--;
                }

                brain.adaptiveScore = brain.adaptiveScore * ctx.tuning().number(TuningKey.MELEE_ADAPTIVE_DECAY).doubleValue()
                        + signal * ctx.tuning().number(TuningKey.MELEE_ADAPTIVE_GAIN).doubleValue();
                float botHp = (float) bot.getHealth();
                float targetHp = (float) target.getHealth();
                boolean losing = botHp <= ctx.tuning().number(TuningKey.MELEE_ADAPTIVE_LOSING_HP).floatValue()
                        || botHp + ctx.tuning().number(TuningKey.MELEE_ADAPTIVE_LOSING_HP_GAP).floatValue() < targetHp
                        || brain.comboLockHits >= ctx.tuning().integer(TuningKey.MELEE_ADAPTIVE_LOSING_COMBO_HITS);
                BotSettings.PlayStyle resolved;
                double styleThreshold = ctx.tuning().number(TuningKey.MELEE_ADAPTIVE_STYLE_THRESHOLD).doubleValue();
                if (losing) {
                    resolved = BotSettings.PlayStyle.SAFE;
                } else if (brain.adaptiveScore > styleThreshold) {
                    resolved = BotSettings.PlayStyle.AGGRESSIVE;
                } else if (brain.adaptiveScore < -styleThreshold) {
                    resolved = BotSettings.PlayStyle.AGGRESSIVE;
                } else {
                    resolved = BotSettings.PlayStyle.DEFAULT;
                }

                ctx.settings().setAdaptiveResolved(resolved);
            }
        }
    }

    void critSwingNow(LivingEntity target) {
        float[] rot = pickSwingRotation();
        ctx.controls().look(rot[0], rot[1]);
        sprintOffForCrit();
        if (brain.swingIsMiss) {
            ctx.hands().swing(EquipmentSlot.HAND);
            bot.resetCooldown();
        } else {
            boolean inReach = Ent.dist(bot, target) <= brain.attackRange + ctx.tuning().number(TuningKey.MELEE_CRIT_REACH_MARGIN).doubleValue()
                    && bot.hasLineOfSight(target);
            if (inReach) {
                ctx.hands().attack(target);
            }

            ctx.hands().swing(EquipmentSlot.HAND);
        }

        brain.pendingAttack = true;
        brain.wTap.onAttack();
        brain.strafeController.onAttack();
        afterAttackBlockhit();
    }

    void orientAndSwing() {
        float[] rot = pickSwingRotation();
        ctx.controls().look(rot[0], rot[1]);
        if (brain.swingIsMiss) {
            ctx.hands().swing(EquipmentSlot.HAND);
            bot.resetCooldown();
        } else {
            ctx.hands().attack(brain.target);
            ctx.hands().swing(EquipmentSlot.HAND);
        }

        brain.pendingAttack = true;
        brain.wTap.onAttack();
        brain.strafeController.onAttack();
        afterAttackBlockhit();
    }

    void tryAttack(WeaponSelector.WeaponType weapon) {
        if (brain.target != null) {
            ItemStack heldItem = bot.getInventory().getItemInMainHand();
            WeaponSelector.WeaponType heldType = WeaponSelector.getType(heldItem);
            boolean meleeCapable = heldType == WeaponSelector.WeaponType.SWORD
                    || heldType == WeaponSelector.WeaponType.AXE
                    || heldType == WeaponSelector.WeaponType.MACE
                    || heldType == WeaponSelector.WeaponType.SPEAR
                    || heldType == WeaponSelector.WeaponType.EMPTY
                    || ItemKinds.is(heldItem, Material.TRIDENT);
            if (meleeCapable) {
                if (brain.specials.isFacingTarget()) {
                    if (bot.hasLineOfSight(brain.target)) {
                        if (weapon != WeaponSelector.WeaponType.MACE || MaceSmashGates.botMayUseSmashFallBonus(ctx)) {
                            float charge = charge(bot);
                            brain.swingReach.tick(charge, brain.attackRange, ctx.difficulty().reachFlips);
                            if (Ent.dist(bot, brain.target) > brain.swingReach.get(brain.attackRange)) {
                                brain.jumpedForCrit = false;
                            } else {
                                boolean targetInWeb = targetIsInWeb();
                                Move move = decideMove(weapon, charge);
                                switch (move) {
                                    case AXE_FLICK:
                                        doAxeFlick(weapon, charge, targetInWeb);
                                        break;
                                    case AXE_DISABLE:
                                        doAxeDisable(weapon, charge, targetInWeb);
                                        break;
                                    case AUTO_CRIT:
                                        doAutoCrit(weapon, charge, targetInWeb);
                                        break;
                                    case MIRROR_PUNISH:
                                        doMirrorPunish(weapon, charge, targetInWeb);
                                        break;
                                    case ANSWER_SWING:
                                        doAnswerSwing(weapon, charge, targetInWeb);
                                        break;
                                    case COMBO:
                                        doCombo(weapon, charge, targetInWeb);
                                        break;
                                    case CRIT_PHASE2:
                                        doCritPhase2(weapon, charge, targetInWeb);
                                        break;
                                    case SWEEP:
                                        doSweep(weapon, charge, targetInWeb);
                                        break;
                                    case PIN_FLEEING:
                                        doPinFleeing(weapon, charge, targetInWeb);
                                        break;
                                    case CRIT_JUMP:
                                        doCritJump(weapon, charge, targetInWeb);
                                        break;
                                    case STUN_STRIKE:
                                        doStunStrike(charge);
                                        break;
                                    case FALLBACK:
                                        doFallback(weapon, charge, targetInWeb);
                                    case HOLD:
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    int flickAxeSlot() {
        for (int i = 0; i < 9; i++) {
            if (WeaponSelector.getType(bot.getInventory().getItem(i)) == WeaponSelector.WeaponType.AXE) {
                return i;
            }
        }

        return -1;
    }

    /** Plain inputs of decideMove, so the decision table can be tested without a body. */
    record MoveInputs(
            WeaponSelector.WeaponType weapon,
            float charge,
            boolean targetBlocking,
            boolean seesShield,
            boolean shouldDisable,
            boolean hasFlickAxe,
            boolean targetInWeb,
            boolean botInWeb,
            boolean inCombo,
            boolean jumpedForCrit,
            int autoCritWindow,
            int mirrorPunishWindow,
            int answerSwingWindow,
            boolean onGround,
            boolean canCrit,
            boolean beingCombod,
            boolean targetFinishable,
            int nearbyEnemies) {}

    /** The move plus the combo-state side effects decideMove applies, in order: clear first, then maybeStartCombo. */
    record Decision(Move move, boolean clearCombo, boolean tryStartCombo) {}

    Move decideMove(WeaponSelector.WeaponType weapon, float charge) {
        if (stunStrikeReady(charge)) {
            return Move.STUN_STRIKE;
        }

        MoveInputs in = new MoveInputs(
                weapon,
                charge,
                brain.targetBlocking(),
                brain.axeController.seesShieldNow(),
                brain.axeController.shouldDisableNow(charge),
                flickAxeSlot() >= 0,
                targetIsInWeb(),
                botIsInWeb(),
                brain.inCombo,
                brain.jumpedForCrit,
                brain.autoCritWindow,
                brain.mirrorPunishWindow,
                brain.answerSwingWindow,
                bot.isOnGround(),
                canAttemptCrit(),
                brain.fightState.beingCombod(),
                targetFinishable(),
                countNearbyEnemies());
        Decision d = decide(ctx.tuning(), in, () -> {
            int chainNow = brain.fightState.incomingComboHits();
            if (chainNow != brain.uppercutRolledChain) {
                brain.uppercutRolledChain = chainNow;
                brain.uppercutThisChain = ctx.random().nextDouble() < ctx.difficulty().accuracy;
            }

            return brain.uppercutThisChain;
        });
        if (d.clearCombo()) {
            brain.inCombo = false;
            brain.comboHitsLeft = 0;
        }

        if (d.tryStartCombo()) {
            maybeStartCombo();
        }

        return d.move();
    }

    static Decision decide(BotTuning.Profile profile, MoveInputs in, BooleanSupplier uppercutRoll) {
        WeaponSelector.WeaponType weapon = in.weapon();
        float charge = in.charge();
        float strongCharge = profile.number(TuningKey.MELEE_STRONG_CHARGE).floatValue();
        boolean sword = weapon == WeaponSelector.WeaponType.SWORD;
        boolean axe = weapon == WeaponSelector.WeaponType.AXE;
        if (sword && in.targetBlocking() && in.seesShield() && in.shouldDisable() && in.hasFlickAxe()) {
            return new Decision(Move.AXE_FLICK, false, false);
        } else if (axe && in.targetBlocking() && in.shouldDisable()) {
            return new Decision(Move.AXE_DISABLE, false, false);
        }

        boolean inCombo = in.inCombo();
        boolean clear = false;
        if ((sword || axe) && in.targetInWeb() && !in.botInWeb()) {
            inCombo = false;
            clear = true;
            if (in.jumpedForCrit()) {
                return new Decision(Move.CRIT_PHASE2, true, false);
            }

            if (in.canCrit()) {
                return new Decision(Move.CRIT_JUMP, true, false);
            }
        }

        if (sword
                && in.autoCritWindow() > 0
                && !inCombo
                && !in.jumpedForCrit()
                && in.onGround()
                && in.canCrit()
                && charge >= strongCharge) {
            return new Decision(Move.AUTO_CRIT, clear, false);
        } else if (sword
                && in.mirrorPunishWindow() > 0
                && !inCombo
                && !in.jumpedForCrit()
                && !in.targetBlocking()
                && in.onGround()
                && charge >= strongCharge) {
            return new Decision(Move.MIRROR_PUNISH, clear, false);
        } else if (sword
                && in.answerSwingWindow() > 0
                && !inCombo
                && !in.jumpedForCrit()
                && !in.targetBlocking()
                && in.onGround()
                && charge >= strongCharge) {
            return new Decision(Move.ANSWER_SWING, clear, false);
        }

        if (!in.jumpedForCrit() && sword && in.beingCombod() && in.canCrit() && uppercutRoll.getAsBoolean()) {
            return new Decision(Move.CRIT_JUMP, true, false);
        }

        if (!in.jumpedForCrit() && in.targetFinishable()) {
            inCombo = false;
            clear = true;
            if (in.canCrit()) {
                return new Decision(Move.CRIT_JUMP, true, false);
            }

            if (sword && in.onGround() && charge >= strongCharge) {
                return new Decision(Move.PIN_FLEEING, true, false);
            }
        }

        if (inCombo && !in.jumpedForCrit()) {
            return new Decision(Move.COMBO, clear, false);
        } else if (in.jumpedForCrit()) {
            return new Decision(Move.CRIT_PHASE2, clear, false);
        } else if (sword && in.onGround() && in.nearbyEnemies() >= profile.integer(TuningKey.MELEE_SWEEP_MIN_ENEMIES)) {
            return new Decision(Move.SWEEP, clear, true);
        } else if (sword && in.onGround() && charge >= strongCharge && in.targetFinishable()) {
            return new Decision(Move.PIN_FLEEING, clear, true);
        } else if (axe && in.targetBlocking()) {
            return new Decision(Move.HOLD, clear, true);
        } else {
            return new Decision(in.canCrit() ? Move.CRIT_JUMP : Move.FALLBACK, clear, true);
        }
    }

    boolean comboOpener() {
        if (!targetCornered() && !targetAtLedge()) {
            boolean veryLaggy = brain.targetPingMs > ctx.tuning().integer(TuningKey.MELEE_LAGGY_PING_MS);
            if ((Ent.dist(bot, brain.target) >= ctx.tuning().number(TuningKey.MELEE_OPENER_MIN_DIST).doubleValue() || veryLaggy)
                    && (!brain.targetHighPing || !(Ent.dist(bot, brain.target) > brain.attackRange - ctx.tuning().number(TuningKey.MELEE_HIGH_PING_REACH_MARGIN).doubleValue()))) {
                ctx.controls().setMovement(1.0F, 0.0F);
                ctx.controls().setSprinting(true);
                orientAndSwing();
                if (!veryLaggy) {
                    brain.sprintHitHold = ctx.tuning().integer(TuningKey.MELEE_SPRINT_HIT_HOLD_TICKS);
                    brain.releaseAfterHit = ctx.tuning().integer(TuningKey.MELEE_RELEASE_AFTER_HIT_TICKS);
                    brain.inCombo = true;
                    brain.comboHitsLeft = ctx.tuning().integer(TuningKey.MELEE_COMBO_HITS_MIN) + ctx.random().nextInt(ctx.tuning().integer(TuningKey.MELEE_COMBO_HITS_SPAN));
                }

                return true;
            } else {
                return false;
            }
        } else {
            ctx.controls().setMovement(1.0F, 0.0F);
            ctx.controls().setSprinting(true);
            orientAndSwing();
            brain.inCombo = true;
            brain.comboHitsLeft = ctx.tuning().integer(TuningKey.MELEE_COMBO_HITS_MIN) + ctx.random().nextInt(ctx.tuning().integer(TuningKey.MELEE_COMBO_HITS_SPAN));
            return true;
        }
    }

    boolean comboShieldLock() {
        return brain.inCombo && brain.fightState.ticksSinceOurHit() <= ctx.tuning().integer(TuningKey.MELEE_COMBO_SHIELD_LOCK_TICKS);
    }

    boolean maybeStartCombo() {
        if (brain.targetPingMs <= ctx.tuning().integer(TuningKey.MELEE_LAGGY_PING_MS) && shouldStartCombo()) {
            brain.inCombo = true;
            brain.comboHitsLeft = ctx.tuning().integer(TuningKey.MELEE_COMBO_HITS_MIN) + ctx.random().nextInt(ctx.tuning().integer(TuningKey.MELEE_COMBO_START_HITS_SPAN));
            return true;
        } else {
            return false;
        }
    }

    void doAxeFlick(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        int flickAxe = flickAxeSlot();
        if (flickAxe >= 0) {
            if (brain.target.getNoDamageTicks() <= 10) {
                int prevSlot = InventoryUtils.selected(ctx);
                InventoryUtils.select(ctx, flickAxe);
                ctx.controls().setSprinting(true);
                ctx.hands().attack(brain.target);
                ctx.hands().swing(EquipmentSlot.HAND);
                bot.resetCooldown();
                InventoryUtils.select(ctx, prevSlot);
                brain.axeController.onDisableSwung();
            }
        }
    }

    /**
     * Hard and Expert bots hit through a shield they just disabled, while the target's shield is still on cooldown.
     * Waits for the sword charge, because the axe flick resets it and an early hit wastes the follow-up.
     */
    private boolean stunStrikeReady(float charge) {
        if (!brain.axeController.punishActive()) {
            stunStruck = false;
        }

        return ctx.settings().difficulty().level() >= ctx.tuning().integer(TuningKey.AXE_SHIELD_STUN_MIN_LEVEL)
                && !stunStruck
                && brain.axeController.punishActive()
                && brain.target instanceof HumanEntity human
                && human.hasCooldown(Material.SHIELD)
                && stunSwordSlot() >= 0
                && charge >= ctx.tuning().number(TuningKey.AXE_SHIELD_STUN_CHARGE).floatValue();
    }

    private int stunSwordSlot() {
        for (int i = 0; i < 9; i++) {
            if (WeaponSelector.getType(bot.getInventory().getItem(i)) == WeaponSelector.WeaponType.SWORD) {
                return i;
            }
        }

        return -1;
    }

    void doStunStrike(float charge) {
        stunStruck = true;
        SwapEngine.syncedAttack(ctx, stunSwordSlot(), brain.target);
        ctx.log().decision("MeleeCombat", STUN_STRIKE,
                "hits through the disabled shield, charge " + String.format(java.util.Locale.ROOT, "%.2f", charge));
    }

    void doAxeDisable(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        ctx.controls().setSprinting(true);
        orientAndSwing();
        brain.axeController.onDisableSwung();
    }

    void doAutoCrit(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        brain.autoCritWindow = 0;
        ctx.controls().jumpOnce();
        brain.jumpedForCrit = true;
        brain.critJumpTick = brain.tickCount;
    }

    void doMirrorPunish(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        brain.mirrorPunishWindow = 0;
        brain.shieldController.reset();
        ctx.controls().setMovement(1.0F, 0.0F);
        ctx.controls().setSprinting(true);
        orientAndSwing();
    }

    void doAnswerSwing(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        brain.answerSwingWindow = 0;
        brain.hitSelectHold = 0;
        ctx.controls().setMovement(1.0F, 0.0F);
        ctx.controls().setSprinting(true);
        orientAndSwing();
        brain.sprintHitHold = ctx.tuning().integer(TuningKey.MELEE_SPRINT_HIT_HOLD_TICKS);
        brain.releaseAfterHit = ctx.tuning().integer(TuningKey.MELEE_RELEASE_AFTER_HIT_TICKS);
        brain.inCombo = true;
        brain.comboHitsLeft = ctx.tuning().integer(TuningKey.MELEE_COMBO_HITS_MIN) + ctx.random().nextInt(ctx.tuning().integer(TuningKey.MELEE_COMBO_HITS_SPAN));
    }

    void doCombo(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        if (charge >= comboFireAt()) {
            if (targetDescending() && brain.swingHold < ctx.tuning().integer(TuningKey.MELEE_DESCEND_HOLD_MAX)) {
                brain.swingHold++;
                return;
            }

            brain.swingHold = 0;
            ctx.controls().setMovement(1.0F, 0.0F);
            ctx.controls().setSprinting(true);
            orientAndSwing();
            brain.sprintHitHold = ctx.tuning().integer(TuningKey.MELEE_SPRINT_HIT_HOLD_TICKS);
            brain.releaseAfterHit = ctx.tuning().integer(TuningKey.MELEE_RELEASE_AFTER_HIT_TICKS);
            brain.jumpedForCrit = false;
            brain.comboHitsLeft--;
            if (brain.comboHitsLeft <= 0) {
                brain.inCombo = false;
            }
        }
    }

    void doCritPhase2(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        double critVelThreshold = ctx.difficulty().critThreshold;
        boolean falling = Ent.velocity(bot).y() < critVelThreshold && bot.getFallDistance() > 0.0 && !bot.isOnGround();
        if (brain.tickCount - brain.critJumpTick > ctx.tuning().integer(TuningKey.MELEE_CRIT_JUMP_TIMEOUT)) {
            brain.jumpedForCrit = false;
        } else if (!falling || !(charge < ctx.tuning().number(TuningKey.MELEE_CRIT_MIN_CHARGE).floatValue())) {
            if (!falling || brain.target.getNoDamageTicks() <= ctx.tuning().integer(TuningKey.MELEE_CRIT_PHASE_MAX_HURT) && !brain.targetBlocking()) {
                if (falling) {
                    brain.critSwingHold = ctx.tuning().integer(TuningKey.MELEE_CRIT_SWING_HOLD_TICKS);
                    critSwingNow(brain.target);
                    if (!targetInWeb && ctx.settings().roll()) {
                        brain.noKbHoldW = ctx.tuning().integer(TuningKey.MELEE_NO_KB_HOLD_TICKS);
                    }

                    brain.jumpedForCrit = false;
                    brain.critFollowUpWindow = ctx.tuning().integer(TuningKey.MELEE_CRIT_FOLLOWUP_TICKS);
                    brain.critChainWindow = ctx.tuning().integer(TuningKey.MELEE_CRIT_CHAIN_TICKS);
                }
            }
        }
    }

    void doSweep(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        if (charge >= ctx.tuning().number(TuningKey.MELEE_SWEEP_CHARGE).floatValue()) {
            sprintOffForCrit();
            ctx.controls().setMovement(0.0F, 0.0F);
            brain.critSwingHold = ctx.tuning().integer(TuningKey.MELEE_CRIT_SWING_HOLD_TICKS);
            orientAndSwing();
            brain.jumpedForCrit = false;
        }
    }

    void doPinFleeing(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        sprintOffForCrit();
        ctx.controls().setMovement(1.0F, 0.0F);
        brain.critSwingHold = ctx.tuning().integer(TuningKey.MELEE_CRIT_SWING_HOLD_TICKS);
        orientAndSwing();
        brain.jumpedForCrit = false;
    }

    void doCritJump(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        LivingEntity target = brain.target;
        boolean doubleCrit = brain.critFollowUpWindow > 0 && !target.isOnGround() && Ent.dist(bot, target) <= ctx.tuning().number(TuningKey.MELEE_DOUBLE_CRIT_RANGE).doubleValue();
        if (!bot.isOnGround()) {
            if (charge >= ctx.tuning().number(TuningKey.MELEE_STRONG_CHARGE).floatValue() && bot.getFallDistance() > 0.0) {
                brain.critSwingHold = ctx.tuning().integer(TuningKey.MELEE_CRIT_SWING_HOLD_TICKS);
                critSwingNow(target);
                if (ctx.settings().roll()) {
                    brain.noKbHoldW = ctx.tuning().integer(TuningKey.MELEE_NO_KB_HOLD_TICKS);
                }
            }
        } else if (doubleCrit && charge >= ctx.tuning().number(TuningKey.MELEE_STRONG_CHARGE).floatValue()) {
            ctx.controls().jumpOnce();
            brain.jumpedForCrit = true;
            brain.critJumpTick = brain.tickCount;
            brain.critFollowUpWindow = 0;
        } else {
            if (!targetInWeb) {
                boolean critNow = (brain.critChainWindow > 0
                                || brain.targetLandWindow > 0
                                || brain.healPunishWindow > 0
                                || brain.armorPunishWindow > 0
                                || brain.overextendWindow > 0
                                || brain.axeController.punishActive())
                        && Ent.dist(bot, target) <= ctx.tuning().number(TuningKey.MELEE_CRIT_CHAIN_RANGE).doubleValue();
                boolean earlyJump = !critNow
                        && brain.earlyCritThisCycle
                        && charge < ctx.tuning().number(TuningKey.MELEE_STRONG_CHARGE).floatValue()
                        && Ent.dist(bot, target) < ctx.tuning().number(TuningKey.MELEE_EARLY_JUMP_MAX_DIST).doubleValue()
                        && brain.targetPingMs <= ctx.tuning().integer(TuningKey.MELEE_LAGGY_PING_MS)
                        && !targetCornered()
                        && !targetAtLedge();
                if (charge < (!critNow && !earlyJump ? ctx.tuning().number(TuningKey.MELEE_STRONG_CHARGE).floatValue() : ctx.tuning().number(TuningKey.MELEE_CRIT_CHAIN_JUMP_CHARGE).floatValue())) {
                    return;
                }

                if (!critNow && targetDescending() && brain.swingHold < ctx.tuning().integer(TuningKey.MELEE_DESCEND_HOLD_MAX)) {
                    brain.swingHold++;
                    return;
                }

                if (!critNow && shouldHoldSwing()) {
                    brain.hitSelectHold++;
                    return;
                }

                if (critNow || readyToFire(charge, earlyJump ? ctx.tuning().number(TuningKey.MELEE_CRIT_CHAIN_JUMP_CHARGE).floatValue() : ctx.tuning().number(TuningKey.MELEE_STRONG_CHARGE).floatValue())) {
                    brain.hitSelectHold = 0;
                    if (target.getNoDamageTicks() > ctx.tuning().integer(TuningKey.MELEE_CRIT_JUMP_MAX_HURT)) {
                        if (!critNow) {
                            brain.reactionCountdown = 0;
                        }

                        return;
                    }

                    brain.swingHold = 0;
                    if (!critNow && !earlyJump && comboOpener()) {
                        return;
                    }

                    brain.targetLandWindow = 0;
                    ctx.controls().jumpOnce();
                    brain.jumpedForCrit = true;
                    brain.critJumpTick = brain.tickCount;
                }
            } else if (!botIsInWeb() && charge >= ctx.tuning().number(TuningKey.MELEE_STRONG_CHARGE).floatValue() && Ent.dist(bot, target) <= brain.attackRange) {
                ctx.controls().stopMoving();
                sprintOffForCrit();
                if (Ent.velocity(bot).horizontalDistanceSqr() < ctx.tuning().number(TuningKey.MELEE_CRIT_STILL_SPEED_SQR).doubleValue()) {
                    ctx.controls().jumpOnce();
                    brain.jumpedForCrit = true;
                    brain.critJumpTick = brain.tickCount;
                }
            }
        }
    }

    void doFallback(WeaponSelector.WeaponType weapon, float charge, boolean targetInWeb) {
        brain.jumpedForCrit = false;
        if ((!targetInWeb || botIsInWeb()) && charge >= ctx.tuning().number(TuningKey.MELEE_STRONG_CHARGE).floatValue()) {
            if (targetDescending() && brain.swingHold < ctx.tuning().integer(TuningKey.MELEE_DESCEND_HOLD_MAX)) {
                brain.swingHold++;
                return;
            }

            if (readyToFire(charge, ctx.tuning().number(TuningKey.MELEE_STRONG_CHARGE).floatValue())) {
                brain.swingHold = 0;
                ctx.controls().setSprinting(true);
                orientAndSwing();
            }
        }
    }

    void afterAttackBlockhit() {
        if (brain.offhandManager.hasShieldInOffhand()) {
            int window =
                    switch (ctx.settings().playStyle()) {
                        case SAFE, DEFENSIVE -> ctx.tuning().integer(TuningKey.MELEE_BLOCKHIT_DEFENSIVE_TICKS);
                        case AGGRESSIVE -> ctx.tuning().integer(TuningKey.MELEE_BLOCKHIT_AGGRESSIVE_TICKS);
                        default -> ctx.tuning().integer(TuningKey.MELEE_BLOCKHIT_DEFAULT_TICKS);
                    };
            if (brain.targetHighPing) {
                window += ctx.tuning().integer(TuningKey.MELEE_BLOCKHIT_HIGH_PING_BONUS);
            }

            brain.shieldController.requestBlockhit(window);
        }
    }

    boolean targetDescending() {
        return brain.target != null && !brain.target.isOnGround()
                ? ctx.match().velocityOf(brain.target).y() < -0.08
                : false;
    }

    boolean opponentAggressive() {
        if (!(brain.target instanceof Player)) {
            return false;
        } else {
            Vec3 tv = ctx.match().velocityOf(brain.target);
            double hs = Math.sqrt(tv.x() * tv.x() + tv.z() * tv.z());
            if (hs < ctx.tuning().number(TuningKey.MELEE_AGGRESSIVE_MIN_SPEED).doubleValue()) {
                return false;
            } else {
                Vec3 toBot = Ent.pos(bot).subtract(Ent.pos(brain.target));
                double len = Math.sqrt(toBot.x() * toBot.x() + toBot.z() * toBot.z());
                if (len < 0.001) {
                    return false;
                } else {
                    double dot = (tv.x() * toBot.x() + tv.z() * toBot.z()) / len;
                    return dot > ctx.tuning().number(TuningKey.MELEE_AGGRESSIVE_DOT).doubleValue();
                }
            }
        }
    }

    boolean shouldStartCombo() {
        if (brain.target == null) {
            return false;
        }

        boolean chainLive = brain.fightState.comboHits() >= 1 && brain.fightState.ticksSinceOurHit() <= ctx.tuning().integer(TuningKey.MELEE_COMBO_CHAIN_LIVE_TICKS);
        Vec3 vel = Ent.velocity(bot);
        return shouldStartCombo(
                ctx.tuning(),
                brain.comboThisCycle,
                brain.jumpedForCrit,
                brain.critChainWindow,
                chainLive,
                canAttemptCrit(),
                Ent.dist(bot, brain.target),
                brain.attackRange,
                bot.getHealth(),
                brain.targetBlocking(),
                Math.sqrt(vel.x() * vel.x() + vel.z() * vel.z()));
    }

    static boolean shouldStartCombo(
            BotTuning.Profile profile,
            boolean comboThisCycle,
            boolean jumpedForCrit,
            int critChainWindow,
            boolean chainLive,
            boolean canCrit,
            double dist,
            double attackRange,
            double health,
            boolean targetBlocking,
            double horizSpeed) {
        if (!comboThisCycle) {
            return false;
        } else if (jumpedForCrit) {
            return false;
        } else if (critChainWindow > 0) {
            return false;
        } else if (!chainLive && canCrit) {
            return false;
        } else if (dist > attackRange + profile.number(TuningKey.MELEE_COMBO_START_RANGE_MARGIN).doubleValue()) {
            return false;
        } else if (health <= profile.number(TuningKey.MELEE_COMBO_START_MIN_HP).doubleValue()) {
            return false;
        } else if (targetBlocking) {
            return false;
        } else {
            return !(horizSpeed > profile.number(TuningKey.MELEE_COMBO_START_MAX_SPEED).doubleValue());
        }
    }

    int countNearbyEnemies() {
        int n = 0;

        for (Player p : ctx.world().players()) {
            if (Ent.alive(p) && p.getGameMode() != GameMode.SPECTATOR && !(Ent.dist(bot, p) > brain.attackRange)) {
                if (p != brain.target) {
                    if (!ctx.match().isEnemy(p) || ctx.settings().ignoreCreative() && p.getGameMode() == GameMode.CREATIVE) {
                        continue;
                    }
                }

                n++;
            }
        }

        return n;
    }

    boolean canAttemptCrit() {
        Vec3 vel = Ent.velocity(bot);
        return canAttemptCrit(
                ctx.tuning(), bot.isInWater(), bot.isClimbing(), botIsInWeb(), Math.sqrt(vel.x() * vel.x() + vel.z() * vel.z()));
    }

    static boolean canAttemptCrit(BotTuning.Profile profile, boolean inWater, boolean climbing, boolean inWeb, double horizVel) {
        if (inWater) {
            return false;
        } else if (climbing) {
            return false;
        } else if (inWeb) {
            return false;
        } else {
            return horizVel <= profile.number(TuningKey.MELEE_CRIT_MAX_HORIZ_SPEED).doubleValue();
        }
    }

    boolean targetCornered() {
        if (brain.target == null) {
            return false;
        } else {
            double dx = brain.target.getLocation().getX() - bot.getLocation().getX();
            double dz = brain.target.getLocation().getZ() - bot.getLocation().getZ();
            double len = Math.hypot(dx, dz);
            if (len < 0.01) {
                return false;
            } else {
                BlockPos feet = BlockPos.containing(
                        brain.target.getLocation().getX() + dx / len,
                        brain.target.getLocation().getY(),
                        brain.target.getLocation().getZ() + dz / len);
                return ctx.world().blocksMotion(feet) && ctx.world().blocksMotion(feet.above());
            }
        }
    }

    boolean targetAtLedge() {
        if (brain.target != null && brain.target.isOnGround()) {
            double dx = brain.target.getLocation().getX() - bot.getLocation().getX();
            double dz = brain.target.getLocation().getZ() - bot.getLocation().getZ();
            double len = Math.hypot(dx, dz);
            if (len < 0.01) {
                return false;
            } else {
                BlockPos spot = BlockPos.containing(
                        brain.target.getLocation().getX() + dx / len * 1.5,
                        brain.target.getLocation().getY(),
                        brain.target.getLocation().getZ() + dz / len * 1.5);
                if (ctx.world().blocksMotion(spot)) {
                    return false;
                } else {
                    for (int down = 1; down <= 4; down++) {
                        if (ctx.world().blocksMotion(spot.below(down))) {
                            return false;
                        }
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    void tryJumpReset() {
        if (brain.jumpResetCooldown > 0) {
            brain.jumpResetCooldown--;
        } else if (brain.target != null && bot.isOnGround()) {
            if (!bot.isHandRaised()) {
                if (!(Ent.dist(bot, brain.target) > ctx.tuning().number(TuningKey.MELEE_JUMP_RESET_RANGE).doubleValue())) {
                    if (brain.target instanceof Player tp) {
                        if (!(charge(bot) >= ctx.tuning().number(TuningKey.MELEE_JUMP_RESET_MAX_CHARGE).floatValue())) {
                            if (!(charge(tp) < ctx.tuning().number(TuningKey.MELEE_JUMP_RESET_TARGET_CHARGE).floatValue())) {
                                Vec3 look = Ent.look(tp);
                                Vec3 toBot = Ent.pos(bot).subtract(Ent.pos(tp)).normalize();
                                double dot = look.x() * toBot.x() + look.z() * toBot.z();
                                if (!(dot < ctx.tuning().number(TuningKey.MELEE_JUMP_RESET_FACING_DOT).doubleValue())) {
                                    if (ctx.settings().rollJumpReset()) {
                                        ctx.controls().jumpOnce();
                                    }

                                    brain.jumpResetCooldown = ctx.tuning().integer(TuningKey.MELEE_JUMP_RESET_COOLDOWN);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    boolean isKnockedBack() {
        if (brain.target == null) {
            return false;
        } else {
            Vec3 vel = Ent.velocity(bot);
            double horizSpeed = Math.sqrt(vel.x() * vel.x() + vel.z() * vel.z());
            if (horizSpeed <= ctx.tuning().number(TuningKey.MELEE_KNOCKBACK_MIN_SPEED).doubleValue()) {
                return false;
            } else {
                Vec3 toTarget = Ent.pos(brain.target).subtract(Ent.pos(bot)).normalize();
                double dot = vel.x() * toTarget.x() + vel.z() * toTarget.z();
                return dot < 0.0;
            }
        }
    }

    void chooseHitReaction(boolean cpvpMode, boolean specialExecuting) {
        brain.hitReact = 0;
        if (brain.target != null
                && Ent.alive(brain.target)
                && ctx.match().lastAttackerOf(bot, ctx.tick()) == brain.target
                && !cpvpMode
                && !specialExecuting
                && !brain.healingController.isHealing()
                && !bot.isHandRaised()
                && WeaponSelector.getType(bot.getInventory().getItemInMainHand()) == WeaponSelector.WeaponType.SWORD) {
            if (!brain.jumpedForCrit && brain.critSwingHold <= 0) {
                int delay = rollReactionDelay();
                if (ticksUntilStrong() <= ctx.tuning().integer(TuningKey.MELEE_DEFLECT_LEAD_TICKS) + delay && ctx.settings().roll()) {
                    brain.hitReact = 1;
                    brain.hitReactTicks = ctx.tuning().integer(TuningKey.MELEE_DEFLECT_TICKS) + delay;
                    brain.hitReactDelay = delay;
                } else if (!isHardIncomingHit() && ticksUntilCharge(ctx.tuning().number(TuningKey.MELEE_CRIT_MIN_CHARGE).floatValue()) <= ctx.tuning().integer(TuningKey.MELEE_PUNISH_LEAD_TICKS) && ctx.settings().roll()) {
                    brain.hitReact = 2;
                    brain.hitReactTicks = ctx.tuning().integer(TuningKey.MELEE_PUNISH_TICKS);
                    brain.hitReactDelay = 0;
                }
            } else {
                if (ticksUntilCharge(ctx.tuning().number(TuningKey.MELEE_CRIT_MIN_CHARGE).floatValue()) <= ctx.tuning().integer(TuningKey.MELEE_PUNISH_LEAD_TICKS) && ctx.settings().roll()) {
                    brain.hitReact = 2;
                    brain.hitReactTicks = ctx.tuning().integer(TuningKey.MELEE_PUNISH_TICKS);
                    brain.hitReactDelay = 0;
                }
            }
        }
    }

    boolean runHitReaction() {
        if (brain.hitReactTicks > 0
                && brain.target != null
                && Ent.alive(brain.target)
                && WeaponSelector.getType(bot.getInventory().getItemInMainHand()) == WeaponSelector.WeaponType.SWORD) {
            if (brain.hitReact != 1) {
                brain.knockbackTicks++;
                brain.inCombo = false;
                aimAtTarget();
                ctx.controls().setMovement(1.0F, 0.0F);
                boolean falling = !bot.isOnGround() && bot.getFallDistance() > 0.0 && Ent.velocity(bot).y() < 0.0;
                if (falling) {
                    sprintOffForCrit();
                    if (charge(bot) >= ctx.tuning().number(TuningKey.MELEE_CRIT_MIN_CHARGE).floatValue() && canSwingAt(brain.target) && brain.target.getNoDamageTicks() <= 10) {
                        brain.critSwingHold = ctx.tuning().integer(TuningKey.MELEE_CRIT_SWING_HOLD_TICKS);
                        critSwingNow(brain.target);
                        brain.jumpedForCrit = false;
                        brain.hitReact = 0;
                        brain.hitReactTicks = 0;
                    }

                    return true;
                } else if (bot.isOnGround() && brain.hitReactTicks < ctx.tuning().integer(TuningKey.MELEE_PUNISH_GROUND_EXIT_TICKS)) {
                    brain.hitReact = 0;
                    brain.hitReactTicks = 0;
                    return false;
                } else {
                    ctx.controls().setSprinting(true);
                    return true;
                }
            } else if (brain.hitReactDelay > 0) {
                brain.hitReactDelay--;
                return false;
            } else if (strongReady(bot.getAttackCooldown())
                    && canSwingAt(brain.target)
                    && brain.target.getNoDamageTicks() <= 10) {
                brain.knockbackTicks++;
                brain.inCombo = false;
                aimAtTarget();
                ctx.controls().setMovement(1.0F, 0.0F);
                ctx.controls().setSprinting(true);
                orientAndSwing();
                brain.sprintHitHold = ctx.tuning().integer(TuningKey.MELEE_SPRINT_HIT_HOLD_TICKS);
                brain.reactionCountdown = -1;
                brain.hitReact = 0;
                brain.hitReactTicks = 0;
                return true;
            } else {
                return false;
            }
        } else {
            brain.hitReact = 0;
            return false;
        }
    }

    boolean canSwingAt(LivingEntity t) {
        return brain.specials.isFacingTarget()
                && bot.hasLineOfSight(t)
                && Ent.dist(bot, t) <= brain.swingReach.get(brain.attackRange);
    }

    /** {@code attackCooldown} is {@code bot.getAttackCooldown()} (getAttackStrengthScale(0.5F)). */
    boolean strongReady(float attackCooldown) {
        return attackCooldown > 0.9F;
    }

    int ticksUntilStrong() {
        float delay = Ent.attackDelay(bot);
        int ticker = Math.round(charge(bot) * delay);
        int firstStrong = (int) Math.floor(0.9F * delay - 0.5F) + 1;
        return Math.max(0, firstStrong - ticker);
    }

    int ticksUntilCharge(float charge) {
        float now = charge(bot);
        return now >= charge ? 0 : (int) Math.ceil((charge - now) * Ent.attackDelay(bot));
    }

    boolean isHardIncomingHit() {
        if (!isKnockedBack()) {
            return false;
        } else {
            Vec3 vel = Ent.velocity(bot);
            double horizSpeed = Math.sqrt(vel.x() * vel.x() + vel.z() * vel.z());
            return horizSpeed > ctx.tuning().number(TuningKey.MELEE_HARD_HIT_SPEED).doubleValue();
        }
    }

    boolean incomingCritDive() {
        if (brain.target instanceof Player p) {
            if (p.isOnGround()) {
                return false;
            } else if (ctx.match().velocityOf(p).y() > ctx.tuning().number(TuningKey.MELEE_CRIT_DIVE_FALL_SPEED).doubleValue()) {
                return false;
            } else {
                return p.getLocation().getY() < bot.getLocation().getY() - ctx.tuning().number(TuningKey.MELEE_CRIT_DIVE_HEIGHT_MARGIN).doubleValue() ? false : charge(p) >= ctx.tuning().number(TuningKey.MELEE_CRIT_DIVE_CHARGE).floatValue();
            }
        } else {
            return false;
        }
    }

    boolean isIncomingFallAttack(LivingEntity target) {
        if (target == null) {
            return false;
        } else if (target.getLocation().getY() - bot.getLocation().getY() < ctx.tuning().number(TuningKey.MELEE_MACE_DIVE_HEIGHT_MIN).doubleValue()) {
            return false;
        } else {
            double dx = target.getLocation().getX() - bot.getLocation().getX();
            double dz = target.getLocation().getZ() - bot.getLocation().getZ();
            if (dx * dx + dz * dz > ctx.tuning().number(TuningKey.MELEE_MACE_DIVE_HORIZ_SQR).doubleValue()) {
                return false;
            } else {
                return fallVelY(target) >= ctx.tuning().number(TuningKey.MELEE_MACE_DIVE_FALL_VEL).doubleValue() ? false : carriesSmashWeapon(target);
            }
        }
    }

    boolean carriesSmashWeapon(LivingEntity target) {
        if (target instanceof Player p) {
            return StunSlamDummy.carriesMaceOrSpear(p);
        }

        EntityEquipment eq = target.getEquipment();
        return eq != null
                && (StunSlamDummy.isMaceOrSpear(eq.getItemInMainHand())
                        || StunSlamDummy.isMaceOrSpear(eq.getItemInOffHand()));
    }

    boolean shouldHoldFallShield(LivingEntity target) {
        if (target == null) {
            return false;
        } else {
            double dx = target.getLocation().getX() - bot.getLocation().getX();
            double dz = target.getLocation().getZ() - bot.getLocation().getZ();
            if (dx * dx + dz * dz > ctx.tuning().number(TuningKey.MELEE_FALL_SHIELD_HOLD_HORIZ_SQR).doubleValue()) {
                return false;
            } else if (!carriesSmashWeapon(target)) {
                return false;
            } else {
                boolean atGround = target.isOnGround() || target.getLocation().getY() - bot.getLocation().getY() <= ctx.tuning().number(TuningKey.MELEE_FALL_SHIELD_LAND_HEIGHT).doubleValue();
                return !atGround;
            }
        }
    }

    void faceEntity(LivingEntity e) {
        Vec3 eye = Ent.eyePos(bot);
        Vec3 p = Ent.pos(e);
        float[] a = BotMath.getAngles(eye.x(), eye.y(), eye.z(), p.x(), p.y() + e.getHeight() * 0.5, p.z());
        ctx.controls().look(a[0], a[1]);
    }

    void startMaceDodge(LivingEntity target) {
        double dx = bot.getLocation().getX() - target.getLocation().getX();
        double dz = bot.getLocation().getZ() - target.getLocation().getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            dx = 1.0;
            dz = 0.0;
        } else {
            dx /= len;
            dz /= len;
        }

        brain.dodgeYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        brain.dodgeTicksLeft = ctx.tuning().integer(TuningKey.MELEE_MACE_DODGE_TICKS);
    }

    void tickMaceDodgeMovement() {
        if (brain.target != null) {
            lookAtTarget();
        }

        double rad = Math.toRadians(bot.getLocation().getYaw());
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double rx = Math.cos(rad);
        double rz = Math.sin(rad);
        double aRad = Math.toRadians(brain.dodgeYaw);
        double ax = -Math.sin(aRad);
        double az = Math.cos(aRad);
        float forward = (float) (ax * fx + az * fz);
        float strafe = (float) (ax * rx + az * rz);
        meleeMove(forward, strafe);
        ctx.controls().setSprinting(false);
    }

    void startFallAttackShield() {
        if (brain.offhandManager.hasShieldInOffhand()) {
            stopMainHandShield();
            brain.fallShieldUsesOffhand = true;
            if (!bot.isHandRaised()) {
                ctx.hands().useItem(EquipmentSlot.OFF_HAND);
            }
        } else {
            brain.fallShieldUsesOffhand = false;
            if (brain.mainHandShieldSlot < 0) {
                startMainHandShield();
            } else {
                InventoryUtils.select(ctx, brain.mainHandShieldSlot);
                if (!bot.isHandRaised()) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }
            }
        }
    }

    void tickFallAttackShield() {
        if (brain.fallShieldUsesOffhand) {
            if (!brain.offhandManager.hasShieldInOffhand()) {
                brain.fallShieldUsesOffhand = false;
                startMainHandShield();
            } else {
                if (!bot.isHandRaised() || bot.getActiveItemHand() != EquipmentSlot.OFF_HAND) {
                    ctx.hands().useItem(EquipmentSlot.OFF_HAND);
                }
            }
        } else {
            if (brain.mainHandShieldSlot >= 0) {
                InventoryUtils.select(ctx, brain.mainHandShieldSlot);
                if (!bot.isHandRaised()) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }
            } else {
                startFallAttackShield();
            }
        }
    }

    void stopFallAttackShield() {
        stopMainHandShield();
        if (brain.fallShieldUsesOffhand
                && bot.isHandRaised()
                && bot.getActiveItemHand() == EquipmentSlot.OFF_HAND
                && ItemKinds.is(bot.getActiveItem(), Material.SHIELD)) {
            ctx.hands().releaseUseItem();
        }

        brain.fallShieldUsesOffhand = false;
    }

    void startMainHandShield() {
        PlayerInventory inv = bot.getInventory();
        int srcSlot = -1;

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.SHIELD)) {
                srcSlot = i;
                break;
            }
        }

        if (srcSlot >= 0) {
            int hotbarSlot;
            if (srcSlot < 9) {
                hotbarSlot = srcSlot;
                brain.mainHandShieldSrc = -1;
            } else {
                hotbarSlot = InventoryUtils.moveToHotbarSafe(ctx, srcSlot);
                brain.mainHandShieldSrc = srcSlot;
            }

            brain.mainHandShieldSlot = hotbarSlot;
            InventoryUtils.select(ctx, hotbarSlot);
            ctx.hands().useItem(EquipmentSlot.HAND);
        }
    }

    void stopMainHandShield() {
        if (brain.mainHandShieldSlot >= 0) {
            if (bot.isHandRaised()
                    && bot.getActiveItemHand() == EquipmentSlot.HAND
                    && ItemKinds.is(bot.getActiveItem(), Material.SHIELD)) {
                ctx.hands().releaseUseItem();
            }

            if (brain.mainHandShieldSrc >= 0 && brain.mainHandShieldSrc != brain.mainHandShieldSlot) {
                InventoryUtils.swap(ctx, brain.mainHandShieldSrc, brain.mainHandShieldSlot);
            }

            brain.mainHandShieldSlot = -1;
            brain.mainHandShieldSrc = -1;
        }
    }

    void enforceConsumingMovementLimits() {
        if (brain.bot.isHandRaised() && !ItemKinds.isSpear(brain.bot.getInventory().getItemInMainHand())) {
            ctx.controls().setSprinting(false);
        }
    }

    void meleeMove(float forward, float strafe) {
        float[] m = MeleeWallGuard.deflect(ctx, forward, strafe);
        if (brain.shieldBrokenNow && m[0] > 0.0F) {
            m[0] *= ctx.tuning().number(TuningKey.MELEE_SHIELD_BROKEN_ADVANCE).floatValue();
        }

        ctx.controls().setMovement(m[0], m[1]);
    }

    Vec3 targetVelocity() {
        if (brain.fleeLastTick == brain.tickCount && brain.fleeLastTarget == brain.target) {
            return brain.fleeVel;
        } else {
            Vec3 now = Ent.pos(brain.target);
            if (brain.fleeLastPos != null && brain.fleeLastTarget == brain.target && brain.tickCount > brain.fleeLastTick) {
                Vec3 step = now.subtract(brain.fleeLastPos).scale(1.0 / (brain.tickCount - brain.fleeLastTick));
                if (step.horizontalDistanceSqr() > 4.0) {
                    step = Vec3.ZERO;
                }

                brain.fleeVel = brain.fleeVel.scale(0.5).add(step.scale(0.5));
            } else {
                brain.fleeVel = Vec3.ZERO;
            }

            brain.fleeLastPos = now;
            brain.fleeLastTarget = brain.target;
            brain.fleeLastTick = brain.tickCount;
            return brain.fleeVel;
        }
    }

    double fallVelY(LivingEntity e) {
        return e == brain.target ? targetVelocity().y() : ctx.match().velocityOf(e).y();
    }

    void sprintOffForCrit() {
        ctx.controls().setSprinting(false);
    }
}
