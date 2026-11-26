package dev.nandi0813.zppbots.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BuiltInTuningTest {
    @Test
    void everyKeyResolvesAndPassesItsCheckForEveryDifficulty() {
        for (int level = 1; level <= 5; level++) {
            for (TuningKey key : TuningKey.values()) key.checked(BuiltInTuning.raw(key, level));
            BotTuning.Profile profile = BotTuning.profile(level);
            assertTrue(profile.integer(TuningKey.REACTION_MIN_TICKS) <= profile.integer(TuningKey.REACTION_MAX_TICKS));
            assertTrue(profile.integer(TuningKey.WEB_TRAP_HIT_COOLDOWN_MIN)
                    <= profile.integer(TuningKey.WEB_TRAP_HIT_COOLDOWN_MAX));
            assertTrue(profile.number(TuningKey.STYLE_RETREAT_MIN)
                            .compareTo(profile.number(TuningKey.STYLE_RETREAT_MAX))
                    <= 0);
        }
    }

    @Test
    void spotChecksMatchTheShippedValues() {
        assertEquals(6, BotTuning.profile(1).integer(TuningKey.REACTION_MIN_TICKS));
        assertEquals(0, new BigDecimal("1").compareTo(BotTuning.profile(5).number(TuningKey.ACCURACY)));
        assertEquals(0, new BigDecimal("0.1").compareTo(BotTuning.profile(3).number(TuningKey.MISS_CHANCE)));
        assertTrue(BotTuning.profile(3).flag(TuningKey.BEHAVIOR_USE_BOWS));
    }

    @Test
    void profilesAreCachedAndDifficultyIsChecked() {
        assertEquals(BotTuning.profile(2), BotTuning.profile(2));
        assertThrows(IllegalArgumentException.class, () -> BotTuning.profile(0));
        assertThrows(IllegalArgumentException.class, () -> BotTuning.profile(6));
    }
}
