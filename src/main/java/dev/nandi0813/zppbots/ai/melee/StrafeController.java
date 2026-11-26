package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.tuning.TuningKey;

public final class StrafeController {

    private final BotContext ctx;
    private int direction = 1;
    private int remaining;
    private boolean inBurst = false;
    private int burstTicks = 0;

    public StrafeController(BotContext ctx) {
        this.ctx = ctx;
        this.remaining = ctx.tuning().integer(TuningKey.STRAFE_FLIP_MIN)
                + ctx.random().nextInt(ctx.tuning().integer(TuningKey.STRAFE_FLIP_SPAN));
    }

    public float getYawOffset(int tickCount, float baseAngle) {
        if (--remaining <= 0) {
            direction = -direction;
            remaining = ctx.tuning().integer(TuningKey.STRAFE_FLIP_MIN)
                    + ctx.random().nextInt(ctx.tuning().integer(TuningKey.STRAFE_FLIP_SPAN));
        }

        if (inBurst) {
            burstTicks--;
            if (burstTicks <= 0) {
                inBurst = false;
            }
            float jitter = (ctx.random().nextFloat() - 0.5F)
                    * ctx.tuning().number(TuningKey.STRAFE_JITTER).floatValue();
            return direction * ctx.tuning().number(TuningKey.STRAFE_BURST_ANGLE).floatValue() + jitter;
        }
        float jitter = (ctx.random().nextFloat() - 0.5F)
                * ctx.tuning().number(TuningKey.STRAFE_JITTER).floatValue()
                * ctx.tuning().number(TuningKey.STRAFE_NORMAL_JITTER_SCALE).floatValue();
        return direction * baseAngle + jitter;
    }

    public float attackTickStrafe() {
        return direction
                * (ctx.tuning().number(TuningKey.STRAFE_ATTACK_BASE).floatValue()
                        + ctx.random().nextFloat()
                                * ctx.tuning().number(TuningKey.STRAFE_ATTACK_SPAN).floatValue());
    }

    public void onAttack() {
        direction = -direction;
        remaining = ctx.tuning().integer(TuningKey.STRAFE_FLIP_MIN)
                    + ctx.random().nextInt(ctx.tuning().integer(TuningKey.STRAFE_FLIP_SPAN));
    }

    public void triggerBurst() {
        if (!inBurst) {
            inBurst = true;
            burstTicks = ctx.tuning().integer(TuningKey.STRAFE_BURST_MIN)
                    + ctx.random().nextInt(ctx.tuning().integer(TuningKey.STRAFE_BURST_SPAN));
            if (ctx.random().nextBoolean()) {
                direction = -direction;
            }
        }
    }

    public boolean isBursting() {
        return inBurst;
    }
}
