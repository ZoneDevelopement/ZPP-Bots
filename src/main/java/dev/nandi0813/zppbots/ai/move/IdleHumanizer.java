package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.platform.BotGuards;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.BotMath;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

public final class IdleHumanizer {

    private final BotContext ctx;
    private final Player bot;
    private float goalYaw;
    private float goalPitch;
    private boolean hasGoal = false;
    private int holdTicks = 0;
    private boolean glancing = false;
    private UUID attending = null;

    public IdleHumanizer(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public void tick(int tickCount) {
        // hurtTime <= 0: the 10-tick hurt animation is the first half of the invulnerability window
        if (bot.getNoDamageTicks() <= bot.getMaximumNoDamageTicks() / 2) {
            pickGaze(nearestPerson());
            easeHead();
        }
    }

    private Player nearestPerson() {
        Player best = null;
        double bestScore = Double.MAX_VALUE;

        for (Player p : ctx.world().players()) {
            if (p == bot) {
                continue;
            }
            double d = Ent.dist(bot, p);
            if (d > ctx.tuning().number(TuningKey.IDLE_ATTEND_RANGE).doubleValue()
                    || !Ent.alive(p)
                    || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double score = BotGuards.isBot(p.getUniqueId())
                    ? d + ctx.tuning().number(TuningKey.IDLE_BOT_PENALTY).doubleValue()
                    : d;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private void pickGaze(Player near) {
        Random r = ctx.random();
        if (holdTicks > 0) {
            holdTicks--;
        }

        if (near != null) {
            if (!near.getUniqueId().equals(attending)) {
                attending = near.getUniqueId();
                glancing = false;
                holdTicks = ctx.tuning().integer(TuningKey.IDLE_INITIAL_HOLD_BASE)
                        + r.nextInt(ctx.tuning().integer(TuningKey.IDLE_INITIAL_HOLD_SPAN));
            }

            if (holdTicks <= 0) {
                if (glancing) {
                    glancing = false;
                    holdTicks = ctx.tuning().integer(TuningKey.IDLE_ATTEND_HOLD_BASE)
                            + r.nextInt(ctx.tuning().integer(TuningKey.IDLE_ATTEND_HOLD_SPAN));
                } else {
                    glancing = true;
                    holdTicks = ctx.tuning().integer(TuningKey.IDLE_GLANCE_HOLD_BASE)
                            + r.nextInt(ctx.tuning().integer(TuningKey.IDLE_GLANCE_HOLD_SPAN));
                    goalYaw = bot.getLocation().getYaw()
                            + (r.nextBoolean() ? 1 : -1)
                                    * (float) range(
                                            r,
                                            ctx.tuning().number(TuningKey.IDLE_GLANCE_YAW_MIN).doubleValue(),
                                            ctx.tuning().number(TuningKey.IDLE_GLANCE_YAW_SPAN).doubleValue());
                    goalPitch = (float) range(
                            r,
                            ctx.tuning().number(TuningKey.IDLE_GLANCE_PITCH_MIN).doubleValue(),
                            ctx.tuning().number(TuningKey.IDLE_GLANCE_PITCH_SPAN).doubleValue());
                    hasGoal = true;
                }
            }

            if (!glancing) {
                setGoalAt(Ent.eyePos(near));
            }
        } else {
            attending = null;
            glancing = false;
            if (holdTicks <= 0 || !hasGoal) {
                goalYaw = bot.getLocation().getYaw()
                        + (float) range(
                                r,
                                ctx.tuning().number(TuningKey.IDLE_IDLE_YAW_MIN).doubleValue(),
                                ctx.tuning().number(TuningKey.IDLE_IDLE_YAW_SPAN).doubleValue());
                goalPitch = (float) range(
                        r,
                        ctx.tuning().number(TuningKey.IDLE_IDLE_PITCH_MIN).doubleValue(),
                        ctx.tuning().number(TuningKey.IDLE_IDLE_PITCH_SPAN).doubleValue());
                hasGoal = true;
                holdTicks = ctx.tuning().integer(TuningKey.IDLE_IDLE_HOLD_BASE)
                        + r.nextInt(ctx.tuning().integer(TuningKey.IDLE_IDLE_HOLD_SPAN));
            }
        }
    }

    private static double range(Random r, double min, double span) {
        return min + r.nextDouble() * span;
    }

    private void setGoalAt(Vec3 p) {
        Vec3 eye = Ent.eyePos(bot);
        float[] a = BotMath.getAngles(eye.x(), eye.y(), eye.z(), p.x(), p.y(), p.z());
        goalYaw = a[0];
        goalPitch = a[1];
        hasGoal = true;
    }

    private void easeHead() {
        if (!hasGoal) {
            return;
        }
        Random r = ctx.random();
        float yaw = bot.getLocation().getYaw();
        float pitch = bot.getLocation().getPitch();
        float dy = BotMath.wrapDegrees(goalYaw - yaw);
        float dp = goalPitch - pitch;
        float stepY = headStep(ctx.tuning(), dy, true);
        float stepP = headStep(ctx.tuning(), dp, false);
        yaw += Math.signum(dy) * stepY;
        pitch += Math.signum(dp) * stepP;
        if (r.nextInt(ctx.tuning().integer(TuningKey.IDLE_JITTER_INTERVAL)) == 0) {
            double yawAmplitude = ctx.tuning().number(TuningKey.IDLE_JITTER_YAW_AMPLITUDE).doubleValue();
            double pitchAmplitude = ctx.tuning().number(TuningKey.IDLE_JITTER_PITCH_AMPLITUDE).doubleValue();
            yaw += (float) range(r, -yawAmplitude, 2.0 * yawAmplitude);
            pitch += (float) range(r, -pitchAmplitude, 2.0 * pitchAmplitude);
        }

        yaw = BotMath.wrapDegrees(yaw);
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch));
        ctx.controls().look(yaw, pitch);
        float off = BotMath.wrapDegrees(yaw - bot.getBodyYaw());
        float bodyYawLimit = ctx.tuning().number(TuningKey.IDLE_BODY_YAW_LIMIT).floatValue();
        if (off > bodyYawLimit) {
            bot.setBodyYaw(yaw - bodyYawLimit);
        }
        if (off < -bodyYawLimit) {
            bot.setBodyYaw(yaw + bodyYawLimit);
        }
    }

    static float headStep(BotTuning.Profile profile, float delta, boolean yaw) {
        float deadzone = profile.number(yaw
                ? TuningKey.IDLE_YAW_DEADZONE
                : TuningKey.IDLE_PITCH_DEADZONE).floatValue();
        float maxStep = profile.number(yaw
                ? TuningKey.IDLE_MAX_YAW_STEP
                : TuningKey.IDLE_MAX_PITCH_STEP).floatValue();
        if (Math.abs(delta) < deadzone) {
            return 0.0F;
        }
        float ease = profile.number(TuningKey.IDLE_EASE).floatValue();
        return Math.min(maxStep, Math.max(deadzone, Math.abs(delta) * ease));
    }
}
