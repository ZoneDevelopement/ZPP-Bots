package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.ai.explosive.CartDoctrine;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.team.perf.BotAiTier;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.BotMath;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.BooleanSupplier;

final class SpecialsPhase {

    enum CpvpPick {
        CRYSTAL,
        ANCHOR,
        CHAIN
    }

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;
    private boolean atStandoff = false;

    SpecialsPhase(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    boolean tickSpecials() {
        brain.armorManager.resetNavigation();
        brain.lastCombatTarget = brain.target;
        brain.melee.updateAdaptiveStyle(brain.target);
        Vec3 standoff = ctx.squad().spreadOut().standoffFor(ctx, brain.target);
        if (standoff != null) {
            brain.melee.aimAtTarget();
            double sdx = standoff.x() - bot.getLocation().getX();
            double sdz = standoff.z() - bot.getLocation().getZ();
            double sd = Math.sqrt(sdx * sdx + sdz * sdz);
            if (sd < (atStandoff ? ctx.tuning().number(TuningKey.SPECIALS_STANDOFF_LEAVE_DIST).doubleValue() : ctx.tuning().number(TuningKey.SPECIALS_STANDOFF_ARRIVE_DIST).doubleValue())) {
                brain.pathFollower.reset();
                atStandoff = true;
                ctx.controls().setSprinting(false);
            } else {
                atStandoff = false;
                brain.pathFollower.chase(standoff, Ent.pos(brain.target));
            }

            return true;
        } else {
            atStandoff = false;
            boolean diveIncoming = brain.maceDiveIncoming(brain.target);
            brain.buildFight.tick(brain.target, brain.dist, brain.healing);
            if (!brain.healing && !diveIncoming && ctx.settings().useScaffolding() && brain.scaffoldController.tick(brain.target)) {
                return true;
            } else if (!brain.healing && brain.boatTravelController.tick(brain.target, brain.dist)) {
                return true;
            } else if (!brain.healing
                    && ctx.settings().useEnderPearl()
                    && ctx.tuning().flag(TuningKey.PEARL_DROP_IN_ON_BELOW)
                    && brain.enderPearlController.tickDropIn(brain.target)) {
                // A target below the bot: pearl straight down onto it instead of waiting until the bot is stuck.
                return true;
            } else if (!brain.healing
                    && ctx.settings().useEnderPearl()
                    && ctx.settings().pearlChasing()
                    && ctx.tuning().flag(TuningKey.PEARL_CLIMB_ON_ABOVE)
                    && !brain.crystalPvpController.isExecuting()
                    && brain.enderPearlController.tickClimb(brain.target)) {
                // A target above the bot that it cannot reach on foot: pearl up instead of pacing underneath.
                return true;
            } else if (brain.healing
                    || !ctx.settings().techniques().pearlGrapple
                    || !ctx.settings().pearlChasing()
                    || !brain.tunnelController.stuck()
                    || !brain.enderPearlController.tryThrowToward(brain.target) && !brain.enderPearlController.tickDropIn(brain.target)) {
                if (!brain.healing && brain.tunnelController.tick(brain.target, brain.dist)) {
                    return true;
                } else if (!brain.healing && ctx.settings().useOrbitalStrike() && brain.orbitalStrikeController.tick(brain.target, brain.dist)) {
                    return true;
                } else if (brain.aiTier == BotAiTier.FULL
                        && !brain.healing
                        && !brain.specialExecuting
                        && !brain.cpvpMode
                        && brain.target != null
                        && brain.swapTechniques.maybeSwapAttack(brain.target, brain.dist)) {
                    if (brain.swapTechniques.consumeLaunchFlag()) {
                        brain.windBurstElytraController.armAfterSmash();
                    }

                    return true;
                } else {
                    if (CartDoctrine.isGuideDoctrine(ctx)) {
                        float floor = ctx.settings().playStyle() == BotSettings.PlayStyle.AGGRESSIVE ? ctx.tuning().number(TuningKey.SPECIALS_CART_FLOOR_AGGRESSIVE).floatValue() : ctx.tuning().number(TuningKey.SPECIALS_CART_FLOOR_DEFAULT).floatValue();
                        brain.cartHealthOk = bot.getHealth() + bot.getAbsorptionAmount() >= floor || CartDoctrine.isFinishWindow(ctx, brain.target);
                    } else {
                        brain.cartHealthOk = ctx.settings().playStyle() != BotSettings.PlayStyle.SAFE || bot.getHealth() >= ctx.tuning().number(TuningKey.SPECIALS_CART_SAFE_MIN_HP).floatValue();
                    }

                    boolean playerTarget = brain.target instanceof Player;
                    if (brain.aiTier == BotAiTier.FULL
                            && !brain.healing
                            && brain.cartHealthOk
                            && playerTarget
                            && brain.maySpecial(brain.cartController.isExecuting())
                            && brain.cartController.tick(brain.target, brain.dist)) {
                        return true;
                    } else {
                        if (brain.anchorPvpController.takeChainEnded()) {
                            brain.crystalAfterChainUntil = brain.tickCount + ctx.tuning().integer(TuningKey.SPECIALS_CRYSTAL_AFTER_CHAIN_TICKS);
                        }

                        if (brain.aiTier == BotAiTier.FULL && !brain.healing && playerTarget) {
                            watchOpponentTool(brain.target);
                            if (brain.crystalPvpController.isExecuting()) {
                                boolean busy = brain.crystalPvpController.tick(brain.target, brain.dist);
                                if (!brain.crystalPvpController.isExecuting()) {
                                    brain.anchorAfterCrystal = true;
                                }

                                if (busy) {
                                    if (brain.crystalPvpController.isExecuting() && !brain.crystalPvpController.isRepositioning()) {
                                        cpvpFootwork(brain.target, brain.dist);
                                    }

                                    return true;
                                }
                            } else if (brain.anchorPvpController.isExecuting()) {
                                brain.lastAnchorActiveTick = brain.tickCount;
                                if (brain.anchorPvpController.tick(brain.target, brain.dist, false)) {
                                    return true;
                                }
                            } else {
                                CpvpPick pick = pickCpvp(brain.target);
                                if (pick == CpvpPick.CRYSTAL) {
                                    if (brain.crystalPvpController.tick(brain.target, brain.dist)) {
                                        brain.crystalAfterChainUntil = 0;
                                        return true;
                                    }

                                    if (brain.anchorPvpController.tick(brain.target, brain.dist, false)) {
                                        brain.lastAnchorActiveTick = brain.tickCount;
                                        brain.anchorAfterCrystal = false;
                                        return true;
                                    }
                                } else {
                                    if (brain.anchorPvpController.tick(brain.target, brain.dist, pick == CpvpPick.CHAIN)) {
                                        brain.lastAnchorActiveTick = brain.tickCount;
                                        brain.anchorAfterCrystal = false;
                                        return true;
                                    }

                                    if (!brain.anchorPvpController.isCoolingDown() && brain.crystalPvpController.tick(brain.target, brain.dist)) {
                                        return true;
                                    }
                                }
                            }
                        }

                        if (brain.cpvpMode && !brain.targetBlocking()) {
                            Vec3 lowGround = brain.target.getLocation().getY() > bot.getLocation().getY() + ctx.tuning().number(TuningKey.SPECIALS_HIGH_GROUND_MARGIN).doubleValue() ? findLowGroundNearTarget(brain.target) : null;
                            if (lowGround != null && Ent.pos(bot).distanceTo(lowGround) > ctx.tuning().number(TuningKey.SPECIALS_LOW_GROUND_ARRIVE_DIST).doubleValue()) {
                                brain.pathFollower.chase(lowGround, Ent.pos(brain.target));
                                brain.melee.lookAtTarget();
                                ctx.controls().setSprinting(true);
                            } else if (lowGround == null && brain.dist > brain.attackRange) {
                                if (ctx.settings().techniques().pearlGrapple
                                        && ctx.settings().pearlChasing()
                                        && !brain.spearMaceController.canCloseGap(brain.dist)
                                        && brain.dist > ctx.tuning().number(TuningKey.MELEE_GRAPPLE_MIN_DIST).doubleValue()
                                        && brain.dist < ctx.tuning().number(TuningKey.MELEE_GRAPPLE_MAX_DIST).doubleValue()
                                        && bot.hasLineOfSight(brain.target)
                                        && !brain.cobwebTrapController.placedWebRecently()
                                        && brain.enderPearlController.tryThrowToward(brain.target)) {
                                    brain.melee.lookAtTarget();
                                    return true;
                                }

                                if (ctx.settings().useEnderPearl()
                                        && brain.crystalPvpController.targetInHole(brain.target)
                                        && bot.getLocation().getY() > brain.target.getLocation().getY() + ctx.tuning().number(TuningKey.SPECIALS_HIGH_GROUND_MARGIN).doubleValue()
                                        && brain.dist < ctx.tuning().number(TuningKey.SPECIALS_HOLE_PEARL_MAX_DIST).doubleValue()
                                        && bot.hasLineOfSight(brain.target)
                                        && brain.enderPearlController.tryThrowToward(brain.target)) {
                                    brain.melee.lookAtTarget();
                                    return true;
                                }

                                brain.pathFollower.chase(brain.target, Ent.pos(brain.target));
                                brain.melee.lookAtTarget();
                                ctx.controls().setSprinting(true);
                            } else {
                                brain.pathFollower.reset();
                                brain.melee.aimAtTarget();
                                if (!brain.healing
                                        && brain.dist <= brain.attackRange
                                        && bot.hasLineOfSight(brain.target)
                                        && Ent.attackStrength(bot, 0.0F) >= ctx.tuning().number(TuningKey.SPECIALS_CPVP_SWING_CHARGE).floatValue()) {
                                    brain.weaponSelector.selectWeapon(brain.target, Math.min(brain.dist, ctx.tuning().number(TuningKey.WEAPON_RANGED_SWITCH_DIST).doubleValue()), brain.targetBlocking());
                                    ctx.controls().setSprinting(true);
                                    ctx.hands().attack(brain.target);
                                    ctx.hands().swing(EquipmentSlot.HAND);
                                }

                                ctx.controls().stopMoving();
                                ctx.controls().setSprinting(false);
                            }

                            return true;
                        } else {
                            return false;
                        }
                    }
                }
            } else {
                return true;
            }
        }
    }

    void manageRangedPosition(double dist) {
        if (dist < ctx.tuning().number(TuningKey.SPECIALS_RANGED_MIN_DIST).doubleValue()) {
            brain.pathFollower.reset();
            ctx.controls().moveBackward();
            ctx.controls().setSprinting(false);
        } else if (dist > ctx.tuning().number(TuningKey.SPECIALS_RANGED_MAX_DIST).doubleValue()) {
            brain.pathFollower.chase(brain.target, Ent.pos(brain.target));
            ctx.controls().setSprinting(true);
            brain.bunnyHopController.tick(true);
        } else {
            // drawing slows the walk, so the bot closes in on foot instead of standing still
            brain.pathFollower.chase(brain.target, Ent.pos(brain.target));
            ctx.controls().setSprinting(false);
        }
    }

    CpvpPick pickCpvp(LivingEntity t) {
        boolean chainHp = brain.fightState.isOpening() || t.getHealth() >= Ent.maxHealth(t) * ctx.tuning().number(TuningKey.SPECIALS_CHAIN_HP_FRACTION).floatValue();
        boolean targetHigh = t.isOnGround() && t.getLocation().getY() - bot.getLocation().getY() >= ctx.tuning().number(TuningKey.SPECIALS_CPVP_HIGH_TARGET).doubleValue();
        return pickCpvp(
                ctx.tuning(),
                chainHp,
                targetHigh,
                brain.tickCount,
                brain.crystalAfterChainUntil,
                brain.anchorAfterCrystal,
                brain.tickCount - brain.oppAnchorTick,
                brain.tickCount - brain.oppCrystalTick,
                () -> preferAnchorOverCrystal(t));
    }

    static CpvpPick pickCpvp(
            BotTuning.Profile profile,
            boolean chainHp,
            boolean targetHigh,
            int tickCount,
            int crystalAfterChainUntil,
            boolean anchorAfterCrystal,
            int sinceAnchor,
            int sinceCrystal,
            BooleanSupplier preferAnchor) {
        if (targetHigh) {
            return CpvpPick.CRYSTAL;
        } else if (tickCount < crystalAfterChainUntil) {
            return CpvpPick.CRYSTAL;
        } else if (anchorAfterCrystal) {
            return chainHp ? CpvpPick.CHAIN : CpvpPick.ANCHOR;
        } else if (chainHp) {
            return CpvpPick.CHAIN;
        } else if (sinceAnchor <= profile.integer(TuningKey.SPECIALS_CPVP_RECENT_TOOL_TICKS) && sinceAnchor <= sinceCrystal) {
            return CpvpPick.ANCHOR;
        } else if (sinceCrystal <= profile.integer(TuningKey.SPECIALS_CPVP_RECENT_TOOL_TICKS)) {
            return CpvpPick.CRYSTAL;
        } else {
            return preferAnchor.getAsBoolean() ? CpvpPick.ANCHOR : CpvpPick.CRYSTAL;
        }
    }

    void watchOpponentTool(LivingEntity t) {
        EntityEquipment equipment = t.getEquipment();
        ItemStack main = equipment == null ? null : equipment.getItemInMainHand();
        if (ItemKinds.is(main, Material.RESPAWN_ANCHOR) || ItemKinds.is(main, Material.GLOWSTONE)) {
            brain.oppAnchorTick = brain.tickCount;
        }
    }

    void noteBlastFrom(LivingEntity t) {
        if (t != null) {
            EntityDamageEvent cause = bot.getLastDamageCause();
            if (cause != null) {
                DamageSource src = cause.getDamageSource();
                if (src.getDirectEntity() instanceof EnderCrystal && src.getCausingEntity() == t) {
                    brain.oppCrystalTick = brain.tickCount;
                } else if (src.getDamageType() == DamageType.BAD_RESPAWN_POINT
                        && ctx.tick() - brain.anchorPvpController.lastDetonateTick() > 2) {
                    brain.oppAnchorTick = brain.tickCount;
                }
            }
        }
    }

    void cpvpFootwork(LivingEntity target, double dist) {
        if (dist > ctx.tuning().number(TuningKey.SPECIALS_CPVP_FOLLOW_DIST).doubleValue()) {
            brain.pathFollower.chase(target, Ent.pos(target));
            ctx.controls().setSprinting(true);
        } else {
            brain.pathFollower.reset();
            if (--brain.cpvpStrafeTicks <= 0) {
                brain.cpvpStrafeDir = ctx.random().nextBoolean() ? 1 : -1;
                brain.cpvpStrafeTicks = ctx.tuning().integer(TuningKey.SPECIALS_CPVP_STRAFE_TICKS_MIN) + ctx.random().nextInt(ctx.tuning().integer(TuningKey.SPECIALS_CPVP_STRAFE_TICKS_SPAN));
            }

            Vec3 eye = Ent.eyePos(bot);
            float[] angles = BotMath.getAngles(eye.x(), eye.y(), eye.z(), target.getLocation().getX(), Ent.eyePos(target).y(), target.getLocation().getZ());
            float yaw = BotMath.wrapDegrees(BotMath.smoothYawToward(bot.getLocation().getYaw(), angles[0], ctx.tuning().number(TuningKey.MELEE_AIM_HEAD_MAX_ROT).floatValue()));
            ctx.controls().look(yaw, bot.getLocation().getPitch());
            ctx.controls().setSprinting(false);
            brain.melee.meleeMove(dist < ctx.tuning().number(TuningKey.SPECIALS_CPVP_CROWD_DIST).doubleValue() ? -ctx.tuning().number(TuningKey.SPECIALS_CPVP_BACKOFF).floatValue() : 0.0F, brain.cpvpStrafeDir);
        }
    }

    boolean preferAnchorOverCrystal(LivingEntity t) {
        if (t == null) {
            return false;
        } else if (ctx.world().bukkit().getEnvironment() == World.Environment.NETHER) {
            return false;
        } else {
            boolean cpvp = inCrystalPvpMode(t);
            if (brain.fightState.isOpening()) {
                return true;
            } else if (cpvp && brain.tickCount - brain.lastAnchorActiveTick < ctx.tuning().integer(TuningKey.SPECIALS_ANCHOR_ACTIVE_GAP_TICKS)) {
                return false;
            } else {
                double speedSq = ctx.match().velocityOf(t).horizontalDistanceSqr();
                boolean higher = cpvp && t.getLocation().getY() - bot.getLocation().getY() >= ctx.tuning().number(TuningKey.SPECIALS_ANCHOR_HIGHER_DIST).doubleValue();
                return ctx.random().nextDouble() < anchorScore(ctx.tuning(), t.isOnGround(), (float) t.getHealth(), speedSq, higher);
            }
        }
    }

    static double anchorScore(BotTuning.Profile profile, boolean onGround, float targetHealth, double speedSq, boolean cpvpAndHigher) {
        double score = profile.number(TuningKey.SPECIALS_ANCHOR_BASE).doubleValue();
        if (onGround) {
            score += profile.number(TuningKey.SPECIALS_ANCHOR_GROUND_BONUS).doubleValue();
        }

        if (targetHealth > profile.number(TuningKey.SPECIALS_ANCHOR_HEALTHY_HP).floatValue()) {
            score += profile.number(TuningKey.SPECIALS_ANCHOR_HEALTHY_BONUS).doubleValue();
        }

        if (speedSq < profile.number(TuningKey.SPECIALS_ANCHOR_STILL_SPEED_SQR).doubleValue()) {
            score += profile.number(TuningKey.SPECIALS_ANCHOR_STILL_BONUS).doubleValue();
        }

        if (cpvpAndHigher) {
            score -= profile.number(TuningKey.SPECIALS_ANCHOR_HIGHER_PENALTY).doubleValue();
        }

        return score;
    }

    boolean inCrystalPvpMode(LivingEntity t) {
        if (t instanceof Player && Ent.alive(t)) {
            if (Ent.dist(bot, t) > ctx.tuning().number(TuningKey.SPECIALS_CPVP_MODE_RANGE).doubleValue()) {
                return false;
            } else if (!hasItemInInventory(Material.TOTEM_OF_UNDYING)) {
                return false;
            } else {
                boolean crystalReady = hasItemInInventory(Material.END_CRYSTAL) && hasItemInInventory(Material.OBSIDIAN);
                boolean anchorReady = hasItemInInventory(Material.RESPAWN_ANCHOR)
                        && hasItemInInventory(Material.GLOWSTONE)
                        && ctx.world().bukkit().getEnvironment() != World.Environment.NETHER;
                return crystalReady || anchorReady;
            }
        } else {
            return false;
        }
    }

    boolean hasItemInInventory(Material item) {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i <= 40; i++) {
            if (ItemKinds.is(inv.getItem(i), item)) {
                return true;
            }
        }

        return false;
    }

    Vec3 findLowGroundNearTarget(LivingEntity target) {
        if (target == null) {
            return null;
        } else {
            double targetX = target.getLocation().getX();
            double targetZ = target.getLocation().getZ();
            int targetFeetY = (int) Math.floor(target.getLocation().getY());
            Vec3 best = null;
            double bestHorizSq = Double.MAX_VALUE;
            int radius = ctx.tuning().integer(TuningKey.SPECIALS_LOW_GROUND_RADIUS);
            int minDrop = ctx.tuning().integer(TuningKey.SPECIALS_LOW_GROUND_MIN_DROP);
            int maxDrop = ctx.tuning().integer(TuningKey.SPECIALS_LOW_GROUND_MAX_DROP);

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = (int) Math.floor(targetX) + dx;
                    int z = (int) Math.floor(targetZ) + dz;

                    for (int dy = minDrop; dy <= maxDrop; dy++) {
                        BlockPos floor = new BlockPos(x, targetFeetY - dy, z);
                        if (ctx.world().isFullBlock(floor)) {
                            BlockPos stand = floor.above();
                            if (ctx.world().isAir(stand) && ctx.world().isAir(stand.above())) {
                                Vec3 standVec = Vec3.atCenterOf(stand);
                                double hdx = standVec.x() - targetX;
                                double hdz = standVec.z() - targetZ;
                                double horizSq = hdx * hdx + hdz * hdz;
                                if (!(horizSq > ctx.tuning().number(TuningKey.SPECIALS_LOW_GROUND_MAX_HORIZ_SQR).doubleValue()) && horizSq < bestHorizSq) {
                                    bestHorizSq = horizSq;
                                    best = standVec;
                                }
                                break;
                            }
                        }
                    }
                }
            }

            return best;
        }
    }

    boolean isFacingTarget() {
        if (brain.target == null) {
            return false;
        } else {
            double dx = brain.target.getLocation().getX() - bot.getLocation().getX();
            double dz = brain.target.getLocation().getZ() - bot.getLocation().getZ();
            float angleToTarget = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float diff = Math.abs(BotMath.wrapDegrees(bot.getLocation().getYaw() - angleToTarget));
            return diff < ctx.tuning().number(TuningKey.SPECIALS_FACING_MAX_ANGLE).floatValue();
        }
    }
}
