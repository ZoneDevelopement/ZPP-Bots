package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.BotMath;
import dev.nandi0813.zppbots.util.LedgeGuard;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Random;

public final class WanderController {

    private final BotContext ctx;
    private final Player bot;
    private final Random rng;
    private Vec3 spawnPos;
    private World spawnLevel;
    private Vec3 wanderTarget = null;
    private int pauseTicksLeft = 0;
    private float idleYawTarget = 0.0F;
    private float lookPitch = 0.0F;
    private float lookPitchTarget = 0.0F;
    private int driftReroll = 0;
    private int jumpCd = 0;

    public WanderController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.rng = ctx.random();
        this.spawnPos = Ent.pos(bot);
        this.spawnLevel = bot.getWorld();
    }

    public boolean tick() {
        if (!ctx.settings().wanderWhenIdle()) {
            wanderTarget = null;
            pauseTicksLeft = 0;
            return false;
        } else {
            if (jumpCd > 0) {
                jumpCd--;
            }

            Vec3 pos = Ent.pos(bot);
            double recentreDistance =
                    ctx.tuning().number(TuningKey.WANDER_RECENTRE_DIST).doubleValue();
            if (bot.getWorld() != spawnLevel
                    || pos.distanceToSqr(spawnPos) > recentreDistance * recentreDistance) {
                spawnPos = pos;
                spawnLevel = bot.getWorld();
                wanderTarget = null;
            }

            if (pauseTicksLeft > 0) {
                pauseTicksLeft--;
                ctx.controls().stopMoving();
                ctx.controls().setSprinting(false);
                idleLookDrift();
                return true;
            } else if (wanderTarget != null) {
                double dx = wanderTarget.x() - pos.x();
                double dz = wanderTarget.z() - pos.z();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < ctx.tuning().number(TuningKey.WANDER_ARRIVE_DIST).doubleValue()) {
                    ctx.controls().stopMoving();
                    ctx.controls().setSprinting(false);
                    wanderTarget = null;
                    pauseTicksLeft = ctx.tuning().integer(TuningKey.WANDER_ARRIVE_PAUSE_BASE)
                            + rng.nextInt(ctx.tuning().integer(TuningKey.WANDER_ARRIVE_PAUSE_SPAN));
                    return true;
                } else if (LedgeGuard.wouldFallDangerously(ctx, dx, dz, ctx.tuning().integer(TuningKey.WANDER_SAFE_DROP))) {
                    ctx.controls().stopMoving();
                    ctx.controls().setSprinting(false);
                    wanderTarget = null;
                    pauseTicksLeft = ctx.tuning().integer(TuningKey.WANDER_LEDGE_PAUSE_BASE)
                            + rng.nextInt(ctx.tuning().integer(TuningKey.WANDER_LEDGE_PAUSE_SPAN));
                    return true;
                } else {
                    double nlen = dist > 1.0E-6 ? dist : 1.0;
                    double fx = dx / nlen;
                    double fz = dz / nlen;
                    double lookAhead =
                            ctx.tuning().number(TuningKey.WANDER_LOOK_AHEAD).doubleValue();
                    BlockPos feetAhead = BlockPos.containing(
                            pos.x() + fx * lookAhead,
                            pos.y(),
                            pos.z() + fz * lookAhead);
                    boolean blockFeet = solidAt(feetAhead);
                    boolean blockHead = solidAt(feetAhead.above());
                    boolean lavaAhead = ctx.world().isLava(feetAhead) || ctx.world().isLava(feetAhead.below());
                    if ((!blockFeet || !blockHead) && !lavaAhead) {
                        if (blockFeet && !solidAt(feetAhead.above(2)) && bot.isOnGround() && jumpCd <= 0) {
                            ctx.controls().jumpOnce();
                            jumpCd = ctx.tuning().integer(TuningKey.WANDER_JUMP_COOLDOWN);
                        }

                        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
                        float yaw = BotMath.smoothYawToward(
                                bot.getLocation().getYaw(),
                                bearing,
                                ctx.tuning()
                                        .number(TuningKey.WANDER_MOVING_YAW_STEP)
                                        .floatValue());
                        advancePitchDrift();
                        ctx.controls().look(yaw, lookPitch);
                        float fwd = forward(ctx.tuning(), dist);
                        ctx.controls().setMovement(fwd, 0.0F);
                        ctx.controls().setSprinting(false);
                        return true;
                    } else {
                        ctx.controls().stopMoving();
                        ctx.controls().setSprinting(false);
                        wanderTarget = null;
                        pauseTicksLeft = ctx.tuning().integer(TuningKey.WANDER_BLOCKED_PAUSE_BASE)
                                + rng.nextInt(ctx.tuning().integer(TuningKey.WANDER_BLOCKED_PAUSE_SPAN));
                        return true;
                    }
                }
            } else {
                wanderTarget = pickWanderTarget();
                if (wanderTarget == null) {
                    pauseTicksLeft = ctx.tuning().integer(TuningKey.WANDER_NO_TARGET_PAUSE);
                }

                return true;
            }
        }
    }

    public void reset() {
        wanderTarget = null;
        pauseTicksLeft = 0;
        driftReroll = 0;
        jumpCd = 0;
        lookPitch = 0.0F;
        lookPitchTarget = 0.0F;
    }

    private void idleLookDrift() {
        if (driftReroll <= 0) {
            driftReroll = ctx.tuning().integer(TuningKey.WANDER_DRIFT_BASE)
                    + rng.nextInt(ctx.tuning().integer(TuningKey.WANDER_DRIFT_SPAN));
            idleYawTarget = bot.getLocation().getYaw()
                    + (float) ((rng.nextDouble() * 2.0 - 1.0)
                            * ctx.tuning().number(TuningKey.WANDER_IDLE_YAW_BAND).doubleValue());
            lookPitchTarget = (float) ((rng.nextDouble() * 2.0 - 1.0)
                    * ctx.tuning().number(TuningKey.WANDER_IDLE_PITCH_BAND).doubleValue());
        }

        driftReroll--;
        float yaw = BotMath.smoothYawToward(
                bot.getLocation().getYaw(),
                idleYawTarget,
                ctx.tuning().number(TuningKey.WANDER_IDLE_YAW_STEP).floatValue());
        lookPitch = pitchStep(ctx.tuning(), lookPitch, lookPitchTarget);
        ctx.controls().look(yaw, lookPitch);
    }

    private void advancePitchDrift() {
        if (driftReroll <= 0) {
            driftReroll = ctx.tuning().integer(TuningKey.WANDER_DRIFT_BASE)
                    + rng.nextInt(ctx.tuning().integer(TuningKey.WANDER_DRIFT_SPAN));
            lookPitchTarget = (float) ((rng.nextDouble() * 2.0 - 1.0)
                    * ctx.tuning().number(TuningKey.WANDER_MOVING_PITCH_BAND).doubleValue());
        }

        driftReroll--;
        lookPitch = pitchStep(ctx.tuning(), lookPitch, lookPitchTarget);
    }

    private boolean solidAt(BlockPos pos) {
        return ctx.world().blocksMotion(pos);
    }

    private Vec3 pickWanderTarget() {
        for (int attempt = 0; attempt < 20; attempt++) {
            double radius = ctx.tuning().number(TuningKey.WANDER_RADIUS).doubleValue();
            double dx = (rng.nextDouble() * 2.0 - 1.0) * radius;
            double dz = (rng.nextDouble() * 2.0 - 1.0) * radius;
            int bx = (int) Math.floor(spawnPos.x() + dx);
            int bz = (int) Math.floor(spawnPos.z() + dz);
            int startY = (int) spawnPos.y();
            int scanUp = ctx.tuning().integer(TuningKey.WANDER_SCAN_UP);
            int scanDown = ctx.tuning().integer(TuningKey.WANDER_SCAN_DOWN);

            for (int y = startY + scanUp; y >= startY - scanDown; y--) {
                BlockPos feet = new BlockPos(bx, y, bz);
                BlockPos head = feet.above();
                if (!ctx.world().isAir(feet)
                        && ctx.world().isAir(head)
                        && ctx.world().isAir(head.above())
                        && !ctx.world().isLava(feet)
                        && !ctx.world().isLava(head)) {
                    double jitterSpan =
                            ctx.tuning().number(TuningKey.WANDER_JITTER_SPAN).doubleValue();
                    double ox = (rng.nextDouble() - 0.5) * jitterSpan;
                    double oz = (rng.nextDouble() - 0.5) * jitterSpan;
                    return new Vec3(bx + 0.5 + ox, y + 1.0, bz + 0.5 + oz);
                }
            }
        }

        return null;
    }

    static float forward(BotTuning.Profile profile, double dist) {
        double minimum = profile.number(TuningKey.WANDER_MIN_FORWARD).doubleValue();
        double slowDistance = profile.number(TuningKey.WANDER_SLOW_DISTANCE).doubleValue();
        return (float) Math.max(minimum, Math.min(1.0, dist / slowDistance));
    }

    static float pitchStep(BotTuning.Profile profile, float current, float target) {
        float ease = profile.number(TuningKey.WANDER_PITCH_EASE).floatValue();
        return current + (target - current) * ease;
    }

}
