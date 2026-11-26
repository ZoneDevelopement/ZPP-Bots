package dev.nandi0813.zppbots.ai.explosive;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.tuning.TuningKey;

import java.util.Random;

final class CrystalSkill {

    private static final TuningKey[] SKIP_SESSION = {
            TuningKey.CRYSTAL_SKILL_SKIP_SESSION_L1,
            TuningKey.CRYSTAL_SKILL_SKIP_SESSION_L2,
            TuningKey.CRYSTAL_SKILL_SKIP_SESSION_L3,
            TuningKey.CRYSTAL_SKILL_SKIP_SESSION_L4,
            TuningKey.CRYSTAL_SKILL_SKIP_SESSION_L5
    };
    private static final TuningKey[] HESITATE_TICKS = {
            TuningKey.CRYSTAL_SKILL_HESITATE_TICKS_L1,
            TuningKey.CRYSTAL_SKILL_HESITATE_TICKS_L2,
            TuningKey.CRYSTAL_SKILL_HESITATE_TICKS_L3,
            TuningKey.CRYSTAL_SKILL_HESITATE_TICKS_L4,
            TuningKey.CRYSTAL_SKILL_HESITATE_TICKS_L5
    };
    private static final TuningKey[] BAD_SEAT = {
            TuningKey.CRYSTAL_SKILL_BAD_SEAT_L1,
            TuningKey.CRYSTAL_SKILL_BAD_SEAT_L2,
            TuningKey.CRYSTAL_SKILL_BAD_SEAT_L3,
            TuningKey.CRYSTAL_SKILL_BAD_SEAT_L4,
            TuningKey.CRYSTAL_SKILL_BAD_SEAT_L5
    };
    private static final TuningKey[] SELF_CARE = {
            TuningKey.CRYSTAL_SKILL_SELF_CARE_L1,
            TuningKey.CRYSTAL_SKILL_SELF_CARE_L2,
            TuningKey.CRYSTAL_SKILL_SELF_CARE_L3,
            TuningKey.CRYSTAL_SKILL_SELF_CARE_L4,
            TuningKey.CRYSTAL_SKILL_SELF_CARE_L5
    };
    private static final TuningKey[] NO_INSTANT_HIT = {
            TuningKey.CRYSTAL_SKILL_NO_INSTANT_HIT_L1,
            TuningKey.CRYSTAL_SKILL_NO_INSTANT_HIT_L2,
            TuningKey.CRYSTAL_SKILL_NO_INSTANT_HIT_L3,
            TuningKey.CRYSTAL_SKILL_NO_INSTANT_HIT_L4,
            TuningKey.CRYSTAL_SKILL_NO_INSTANT_HIT_L5
    };
    private static final TuningKey[] FUMBLE = {
            TuningKey.CRYSTAL_SKILL_FUMBLE_L1,
            TuningKey.CRYSTAL_SKILL_FUMBLE_L2,
            TuningKey.CRYSTAL_SKILL_FUMBLE_L3,
            TuningKey.CRYSTAL_SKILL_FUMBLE_L4,
            TuningKey.CRYSTAL_SKILL_FUMBLE_L5
    };
    private static final TuningKey[] SESSION_GAP = {
            TuningKey.CRYSTAL_SKILL_SESSION_GAP_L1,
            TuningKey.CRYSTAL_SKILL_SESSION_GAP_L2,
            TuningKey.CRYSTAL_SKILL_SESSION_GAP_L3,
            TuningKey.CRYSTAL_SKILL_SESSION_GAP_L4,
            TuningKey.CRYSTAL_SKILL_SESSION_GAP_L5
    };
    private static final TuningKey[] ANCHOR_DUD = {
            TuningKey.CRYSTAL_SKILL_ANCHOR_DUD_L1,
            TuningKey.CRYSTAL_SKILL_ANCHOR_DUD_L2,
            TuningKey.CRYSTAL_SKILL_ANCHOR_DUD_L3,
            TuningKey.CRYSTAL_SKILL_ANCHOR_DUD_L4,
            TuningKey.CRYSTAL_SKILL_ANCHOR_DUD_L5
    };
    private static final TuningKey[] MAX_ROUNDS = {
            TuningKey.CRYSTAL_SKILL_MAX_ROUNDS_L1,
            TuningKey.CRYSTAL_SKILL_MAX_ROUNDS_L2,
            TuningKey.CRYSTAL_SKILL_MAX_ROUNDS_L3,
            TuningKey.CRYSTAL_SKILL_MAX_ROUNDS_L4,
            TuningKey.CRYSTAL_SKILL_MAX_ROUNDS_L5
    };

    private CrystalSkill() {
    }

    static int level(BotContext ctx) {
        return clampLevel(ctx.difficulty().crystalPvpLevel);
    }

    static int clampLevel(int lvl) {
        return Math.max(1, Math.min(5, lvl));
    }

    static boolean roll(Random random, double chance) {
        return chance > 0.0 && random.nextDouble() < chance;
    }

    static boolean skipSession(BotContext ctx) {
        return roll(ctx.random(), ctx.tuning().number(SKIP_SESSION[level(ctx) - 1]).doubleValue());
    }

    static int hesitateTicks(BotContext ctx) {
        return ctx.tuning().integer(HESITATE_TICKS[level(ctx) - 1]);
    }

    static boolean badSeat(BotContext ctx) {
        return roll(ctx.random(), ctx.tuning().number(BAD_SEAT[level(ctx) - 1]).doubleValue());
    }

    static double selfCare(BotContext ctx) {
        return ctx.tuning().number(SELF_CARE[level(ctx) - 1]).doubleValue();
    }

    static boolean noInstantHit(BotContext ctx) {
        return roll(ctx.random(), ctx.tuning().number(NO_INSTANT_HIT[level(ctx) - 1]).doubleValue());
    }

    static boolean fumble(BotContext ctx) {
        return roll(ctx.random(), ctx.tuning().number(FUMBLE[level(ctx) - 1]).doubleValue());
    }

    static int sessionGap(BotContext ctx) {
        return ctx.tuning().integer(SESSION_GAP[level(ctx) - 1]);
    }

    static boolean anchorDud(BotContext ctx) {
        return roll(ctx.random(), ctx.tuning().number(ANCHOR_DUD[level(ctx) - 1]).doubleValue());
    }

    static int maxRounds(BotContext ctx) {
        return ctx.tuning().integer(MAX_ROUNDS[level(ctx) - 1]);
    }
}
