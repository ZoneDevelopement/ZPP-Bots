package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TargetingTest {

    @Test
    void plainTargetRanksByDistance() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(5.0, Targeting.targetPriority(profile, 5.0, false, 20.0));
    }

    @Test
    void consumingTargetIsPreferredBy20() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(-5.0, Targeting.targetPriority(profile, 15.0, true, 20.0));
    }

    @Test
    void lowHealthTargetIsPreferredBy10() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(0.0, Targeting.targetPriority(profile, 10.0, false, 6.0));
        assertEquals(10.0, Targeting.targetPriority(profile, 10.0, false, 6.5));
    }

    @Test
    void bonusesStack() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(-20.0, Targeting.targetPriority(profile, 10.0, true, 2.0));
    }

    @Test
    void changedConsumingBonusControlsPriority() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.TARGET_CONSUMING_BONUS, 5);
        assertEquals(10.0, Targeting.targetPriority(profile(root), 15.0, true, 20.0));
    }

    @Test
    void changedLowHealthThresholdControlsPriority() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.TARGET_LOW_HP, 7);
        assertEquals(0.0, Targeting.targetPriority(profile(root), 10.0, false, 6.5));
    }

    @Test
    void targetPriorityBoundsAreEnforced() {
        JSONObject root = PresetTuning.root(4);
        set(root, TuningKey.TARGET_CLOSE_RANGE, 65);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
