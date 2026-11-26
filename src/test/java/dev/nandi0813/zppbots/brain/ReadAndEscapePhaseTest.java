package dev.nandi0813.zppbots.brain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class ReadAndEscapePhaseTest {

    @Test
    void shieldDropsAtOrBelowLowHp() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertFalse(ReadAndEscapePhase.nextShieldMode(profile, true, 8.0F, 8.0F));
        assertTrue(ReadAndEscapePhase.nextShieldMode(profile, true, 8.5F, 8.0F));
    }

    @Test
    void shieldReturnsOnlyFourHpAboveLow() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertFalse(ReadAndEscapePhase.nextShieldMode(profile, false, 11.9F, 8.0F));
        assertTrue(ReadAndEscapePhase.nextShieldMode(profile, false, 12.0F, 8.0F));
    }

    @Test
    void changedShieldReturnMarginControlsHysteresis() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.READ_SHIELD_RETURN_HP, 2.0);
        BotTuning.Profile profile = profile(root);
        assertTrue(ReadAndEscapePhase.nextShieldMode(profile, false, 10.0F, 8.0F));
        assertFalse(ReadAndEscapePhase.nextShieldMode(profile, false, 9.9F, 8.0F));
    }

    @Test
    void shieldReturnMarginBoundIsEnforced() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.READ_SHIELD_RETURN_HP, -1);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
