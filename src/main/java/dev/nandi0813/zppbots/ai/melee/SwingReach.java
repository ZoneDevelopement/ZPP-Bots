package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.tuning.TuningKey;

public final class SwingReach {

    private final BotContext ctx;
    private double current = -1.0;
    private float lastCharge = 0.0F;

    public SwingReach(BotContext ctx) {
        this.ctx = ctx;
    }

    public void tick(float charge, double maxReach, int flips) {
        if (current < 0.0 || charge < lastCharge) {
            resample(maxReach, flips);
        }
        lastCharge = charge;
    }

    private void resample(double maxReach, int flips) {
        if (flips <= 0) {
            current = maxReach;
            return;
        }
        int hits = 0;
        for (int i = 0; i < flips; i++) {
            if (ctx.random().nextBoolean()) {
                hits++;
            }
        }
        double min = Math.max(ctx.tuning().number(TuningKey.SWING_REACH_MIN).doubleValue(), maxReach - flips * ctx.tuning().number(TuningKey.SWING_REACH_STEP).doubleValue());
        current = Math.min(maxReach, min + hits * ctx.tuning().number(TuningKey.SWING_REACH_STEP).doubleValue());
    }

    public double get(double maxReach) {
        return current < 0.0 ? maxReach : Math.min(current, maxReach);
    }
}
