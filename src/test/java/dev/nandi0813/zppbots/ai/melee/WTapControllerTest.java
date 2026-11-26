package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class WTapControllerTest {

    @Test
    void sprintStopsAtChargeAndResetsOnLowCharge() {
        WTapController w = new WTapController(profile(PresetTuning.root(1)));
        assertFalse(w.shouldStopSprint(0.5f));
        assertTrue(w.shouldStopSprint(0.9f));
        assertTrue(w.shouldStopSprint(0.5f));
        assertFalse(w.shouldStopSprint(0.1f));
    }

    @Test
    void attackAndResetRules() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        WTapController w = new WTapController(profile);
        assertTrue(w.shouldStopSprint(0.9f));
        w.onAttack();
        assertFalse(w.shouldStopSprint(0.5f));
        assertTrue(WTapController.canAttack(profile, 0.95f));
        assertFalse(WTapController.canAttack(profile, 0.94f));
    }

    @Test
    void changedProfileControlsStopResetAndAttack() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.WTAP_STOP_CHARGE, 0.7);
        set(root, TuningKey.WTAP_RESET_CHARGE, 0.3);
        set(root, TuningKey.WTAP_ATTACK_CHARGE, 0.8);
        BotTuning.Profile profile = profile(root);

        WTapController w = new WTapController(profile);
        assertFalse(w.shouldStopSprint(0.69f));
        assertTrue(w.shouldStopSprint(0.7f));
        assertTrue(w.shouldStopSprint(0.3f));
        assertFalse(w.shouldStopSprint(0.29f));
        assertTrue(WTapController.canAttack(profile, 0.8f));
        assertFalse(WTapController.canAttack(profile, 0.79f));
    }

    @Test
    void allProfilesValidateAndWtapBoundsAreEnforced() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(root, difficulty.level());
        }

        JSONObject stopCharge = PresetTuning.root(2);
        set(stopCharge, TuningKey.WTAP_STOP_CHARGE, 1.01);
        assertThrows(IllegalArgumentException.class, () -> profile(stopCharge));

        JSONObject resetCharge = PresetTuning.root(3);
        set(resetCharge, TuningKey.WTAP_RESET_CHARGE, -0.01);
        assertThrows(IllegalArgumentException.class, () -> profile(resetCharge));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
