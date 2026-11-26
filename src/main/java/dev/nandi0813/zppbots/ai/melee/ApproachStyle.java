package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.LivingEntity;

public final class ApproachStyle {

    private final BotContext ctx;
    private double lateral;
    private double wantLateral;
    private int holdTicks;
    private int pauseTicks;
    private int pauseCooldown;
    private int targetId = -1;

    public ApproachStyle(BotContext ctx) {
        this.ctx = ctx;
    }

    private void retarget(LivingEntity target) {
        if (target.getEntityId() != targetId) {
            targetId = target.getEntityId();
            lateral = 0.0;
            wantLateral = 0.0;
            holdTicks = 0;
            pauseTicks = 0;
            pauseCooldown = ctx.tuning().integer(TuningKey.APPROACH_PAUSE_COOLDOWN);
        }
    }

    public void tick(double dist, double strafeAngle, boolean targetMoving) {
        double skill = Math.min(1.0, strafeAngle / ctx.tuning().number(TuningKey.APPROACH_SKILL_ANGLE).doubleValue());
        if (pauseCooldown > 0) {
            pauseCooldown--;
        }

        if (pauseTicks > 0) {
            pauseTicks--;
            if (!targetMoving) {
                pauseTicks = 0;
            }
        } else if (pauseCooldown == 0 && dist > ctx.tuning().number(TuningKey.APPROACH_PAUSE_DISTANCE).doubleValue()
                && targetMoving
                && ctx.random().nextInt(ctx.tuning().integer(TuningKey.APPROACH_PAUSE_CHANCE_IN)) == 0) {
            pauseTicks = (int) ((ctx.tuning().integer(TuningKey.APPROACH_PAUSE_MIN)
                    + ctx.random().nextInt(ctx.tuning().integer(TuningKey.APPROACH_PAUSE_SPAN)))
                    * (ctx.tuning().number(TuningKey.APPROACH_PAUSE_BASE).doubleValue()
                            + ctx.tuning().number(TuningKey.APPROACH_PAUSE_SKILL).doubleValue() * skill));
            pauseCooldown = ctx.tuning().integer(TuningKey.APPROACH_PAUSE_COOLDOWN);
        }

        if (--holdTicks <= 0) {
            holdTicks = ctx.tuning().integer(TuningKey.APPROACH_HOLD_MIN)
                    + ctx.random().nextInt(ctx.tuning().integer(TuningKey.APPROACH_HOLD_SPAN));
            double width = ctx.tuning().number(TuningKey.APPROACH_MAX_LATERAL).doubleValue() * skill;
            double mag = width
                    * (ctx.tuning().number(TuningKey.APPROACH_MAG_BASE).doubleValue()
                            + ctx.random().nextDouble()
                                    * ctx.tuning().number(TuningKey.APPROACH_MAG_SPAN).doubleValue());
            wantLateral = lateral >= 0.0 ? -mag : mag;
        }

        lateral = lateral + Math.signum(wantLateral - lateral) * Math.min(ctx.tuning().number(TuningKey.APPROACH_EASE).doubleValue(), Math.abs(wantLateral - lateral));
    }

    public boolean paused() {
        return pauseTicks > 0;
    }

    public Vec3 goal(LivingEntity target, Vec3 botPos, double dist) {
        retarget(target);
        Vec3 targetPos = Ent.pos(target);
        double taper = Math.max(0.0, Math.min(1.0, (dist - ctx.tuning().number(TuningKey.APPROACH_TAPER_START).doubleValue())
                / ctx.tuning().number(TuningKey.APPROACH_TAPER_WIDTH).doubleValue()));
        double offset = lateral * Math.max(ctx.tuning().number(TuningKey.APPROACH_OFFSET_FLOOR).doubleValue(), taper);
        if (Math.abs(offset) < ctx.tuning().number(TuningKey.APPROACH_OFFSET_DEADZONE).doubleValue()) {
            return targetPos;
        }
        double dx = targetPos.x() - botPos.x();
        double dz = targetPos.z() - botPos.z();
        double len = Math.sqrt(dx * dx + dz * dz);
        return len < 1.0E-4 ? targetPos : new Vec3(targetPos.x() + -dz / len * offset, targetPos.y(), targetPos.z() + dx / len * offset);
    }

    public boolean shouldSprint(double dist, double strafeAngle) {
        if (paused()) {
            return false;
        }
        double commitAt = ctx.tuning().number(TuningKey.APPROACH_SPRINT_BASE).doubleValue()
                - Math.min(1.0, strafeAngle / ctx.tuning().number(TuningKey.APPROACH_SKILL_ANGLE).doubleValue())
                        * ctx.tuning().number(TuningKey.APPROACH_SPRINT_SKILL).doubleValue();
        return dist <= commitAt;
    }

    public boolean shouldHop(double dist) {
        return !paused() && dist <= ctx.tuning().number(TuningKey.APPROACH_HOP_DISTANCE).doubleValue();
    }
}
