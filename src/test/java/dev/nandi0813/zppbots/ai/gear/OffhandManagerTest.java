package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.DifficultyParams;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class OffhandManagerTest {

    @Test
    void totemRegrabDelayAddsHotkeyTime() {
        assertEquals(8, OffhandManager.totemRegrabDelay(8, 0));
        assertEquals(22, OffhandManager.totemRegrabDelay(8, 14));
        assertEquals(8, OffhandManager.totemRegrabDelay(8, -2));
        assertEquals(3, OffhandManager.totemRegrabDelay(-1, 3));
    }

    @Test
    void presetReactionGivesHumanTotemSwap() {
        for (Difficulty difficulty : Difficulty.values()) {
            BotTuning.Profile profile = PresetTuning.profile(PresetTuning.root(1), difficulty.level());
            DifficultyParams params = DifficultyParams.fromProfile(profile);
            int base = profile.integer(TuningKey.TOTEM_REGRAB_BASE_TICKS);
            assertTrue(OffhandManager.totemRegrabDelay(base, params.reactionMinTicks) >= 8, difficulty.name());
            if (difficulty == Difficulty.EASY) {
                assertTrue(OffhandManager.totemRegrabDelay(base, params.reactionMinTicks) >= 14);
            }
        }
    }
}
