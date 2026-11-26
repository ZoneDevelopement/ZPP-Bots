package dev.nandi0813.zppbots.ai.web;

import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CobwebTrapControllerTest {

    @Test
    void cooldownScalePerPlayStyle() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(1.6F, CobwebTrapController.cooldownScale(profile, BotSettings.PlayStyle.AGGRESSIVE));
        assertEquals(0.55F, CobwebTrapController.cooldownScale(profile, BotSettings.PlayStyle.DEFENSIVE));
        assertEquals(0.7F, CobwebTrapController.cooldownScale(profile, BotSettings.PlayStyle.SAFE));
        assertEquals(0.85F, CobwebTrapController.cooldownScale(profile, BotSettings.PlayStyle.BALANCED));
        assertEquals(1.0F, CobwebTrapController.cooldownScale(profile, BotSettings.PlayStyle.DEFAULT));
    }

    @Test
    void defensiveStyleRollsShorterCooldownThanAggressive() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertTrue(CobwebTrapController.cooldownScale(profile, BotSettings.PlayStyle.DEFENSIVE)
                < CobwebTrapController.cooldownScale(profile, BotSettings.PlayStyle.AGGRESSIVE));
    }

    @Test
    void changedSafeScaleControlsStyle() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.WEB_TRAP_SCALE_SAFE, 2.0);
        assertEquals(2.0F, CobwebTrapController.cooldownScale(profile(root), BotSettings.PlayStyle.SAFE));
    }

    @Test
    void cooldownSpanBoundIsEnforced() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.WEB_TRAP_COOLDOWN_SPAN, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
