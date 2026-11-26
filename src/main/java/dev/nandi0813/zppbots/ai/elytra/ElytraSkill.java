package dev.nandi0813.zppbots.ai.elytra;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.tuning.TuningKey;

import java.util.Random;

final class ElytraSkill {

    private static final TuningKey[] SKIP_LAUNCH = {
            TuningKey.ELYTRA_SKILL_SKIP_LAUNCH_L1,
            TuningKey.ELYTRA_SKILL_SKIP_LAUNCH_L2,
            TuningKey.ELYTRA_SKILL_SKIP_LAUNCH_L3,
            TuningKey.ELYTRA_SKILL_SKIP_LAUNCH_L4,
            TuningKey.ELYTRA_SKILL_SKIP_LAUNCH_L5
    };
    private static final TuningKey[] HESITATE_TICKS = {
            TuningKey.ELYTRA_SKILL_HESITATE_TICKS_L1,
            TuningKey.ELYTRA_SKILL_HESITATE_TICKS_L2,
            TuningKey.ELYTRA_SKILL_HESITATE_TICKS_L3,
            TuningKey.ELYTRA_SKILL_HESITATE_TICKS_L4,
            TuningKey.ELYTRA_SKILL_HESITATE_TICKS_L5
    };
    private static final TuningKey[] STEER = {
            TuningKey.ELYTRA_SKILL_STEER_L1,
            TuningKey.ELYTRA_SKILL_STEER_L2,
            TuningKey.ELYTRA_SKILL_STEER_L3,
            TuningKey.ELYTRA_SKILL_STEER_L4,
            TuningKey.ELYTRA_SKILL_STEER_L5
    };
    private static final TuningKey[] DIVE_ERROR = {
            TuningKey.ELYTRA_SKILL_DIVE_ERROR_L1,
            TuningKey.ELYTRA_SKILL_DIVE_ERROR_L2,
            TuningKey.ELYTRA_SKILL_DIVE_ERROR_L3,
            TuningKey.ELYTRA_SKILL_DIVE_ERROR_L4,
            TuningKey.ELYTRA_SKILL_DIVE_ERROR_L5
    };
    private static final TuningKey[] WHIFF = {
            TuningKey.ELYTRA_SKILL_WHIFF_L1,
            TuningKey.ELYTRA_SKILL_WHIFF_L2,
            TuningKey.ELYTRA_SKILL_WHIFF_L3,
            TuningKey.ELYTRA_SKILL_WHIFF_L4,
            TuningKey.ELYTRA_SKILL_WHIFF_L5
    };
    private static final TuningKey[] CHAIN_CONTINUE = {
            TuningKey.ELYTRA_SKILL_CHAIN_CONTINUE_L1,
            TuningKey.ELYTRA_SKILL_CHAIN_CONTINUE_L2,
            TuningKey.ELYTRA_SKILL_CHAIN_CONTINUE_L3,
            TuningKey.ELYTRA_SKILL_CHAIN_CONTINUE_L4,
            TuningKey.ELYTRA_SKILL_CHAIN_CONTINUE_L5
    };

    private ElytraSkill() {
    }

    static int level(BotContext ctx) {
        return clampLevel(ctx.difficulty().elytraMaceLevel);
    }

    static int clampLevel(int lvl) {
        return Math.max(1, Math.min(5, lvl));
    }

    static boolean skipLaunch(BotContext ctx) {
        return ctx.random().nextDouble() < ctx.tuning().number(SKIP_LAUNCH[level(ctx) - 1]).doubleValue();
    }

    static int hesitateTicks(BotContext ctx) {
        return ctx.tuning().integer(HESITATE_TICKS[level(ctx) - 1]);
    }

    static double steer(BotContext ctx) {
        return ctx.tuning().number(STEER[level(ctx) - 1]).doubleValue();
    }

    static double[] diveOffset(BotContext ctx) {
        return diveOffset(ctx.random(), ctx.tuning().number(DIVE_ERROR[level(ctx) - 1]).doubleValue());
    }

    static double[] diveOffset(Random random, double max) {
        if (max <= 0.0) {
            return new double[]{0.0, 0.0};
        } else {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = max * (0.4 + 0.6 * random.nextDouble());
            return new double[]{Math.cos(a) * r, Math.sin(a) * r};
        }
    }

    static boolean whiff(BotContext ctx) {
        return ctx.random().nextDouble() < ctx.tuning().number(WHIFF[level(ctx) - 1]).doubleValue();
    }

    static boolean continueChain(BotContext ctx) {
        return ctx.random().nextDouble() < ctx.tuning().number(CHAIN_CONTINUE[level(ctx) - 1]).doubleValue();
    }
}
