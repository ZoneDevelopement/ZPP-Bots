package dev.nandi0813.zppbots.ai.move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class MovementTuningTest {
    @Test
    void headStepPreservesOriginalPositiveAndNegativeYawAndPitchMagnitudes() {
        BotTuning.Profile profile = PresetTuning.profile(PresetTuning.root(1), 3);

        assertEquals(3.5F, IdleHumanizer.headStep(profile, 10.0F, true), 1.0E-6F);
        assertEquals(3.5F, IdleHumanizer.headStep(profile, -10.0F, true), 1.0E-6F);
        assertEquals(3.5F, IdleHumanizer.headStep(profile, 10.0F, false), 1.0E-6F);
        assertEquals(3.5F, IdleHumanizer.headStep(profile, -10.0F, false), 1.0E-6F);
        assertEquals(0.0F, IdleHumanizer.headStep(profile, 0.39F, true), 1.0E-6F);
        assertEquals(0.0F, IdleHumanizer.headStep(profile, -0.29F, false), 1.0E-6F);
        assertEquals(14.0F, IdleHumanizer.headStep(profile, 100.0F, true), 1.0E-6F);
        assertEquals(9.0F, IdleHumanizer.headStep(profile, -100.0F, false), 1.0E-6F);
    }

    @Test
    void spreadWidthUsesConfiguredMinimumAndAspectForSmallAndLargeSquads() {
        JSONObject root = PresetTuning.root(1);
        BotTuning.Profile profile = PresetTuning.profile(root, 3);

        assertEquals(12, SpreadOut.widthFor(4, profile));
        assertEquals(55, SpreadOut.widthFor(1000, profile));

        JSONObject changedRoot = PresetTuning.root(2);
        changedRoot.getJSONObject("defaults").put(TuningKey.SPREAD_MIN_WIDTH.key(), 20);
        changedRoot.getJSONObject("defaults").put(TuningKey.SPREAD_ASPECT.key(), 4.0);
        BotTuning.Profile changed = PresetTuning.profile(changedRoot, 3);
        assertNotEquals(SpreadOut.widthFor(1000, profile), SpreadOut.widthFor(1000, changed));
        assertEquals(20, SpreadOut.widthFor(4, changed));
    }

    @Test
    void profilePerturbationsChangeTheProductionHeadStep() {
        JSONObject root = PresetTuning.root(1);
        BotTuning.Profile original = PresetTuning.profile(root, 3);
        float originalStep = IdleHumanizer.headStep(original, 10.0F, true);

        JSONObject changedRoot = PresetTuning.root(2);
        JSONObject defaults = changedRoot.getJSONObject("defaults");
        defaults.put(TuningKey.IDLE_EASE.key(), 0.5);
        defaults.put(TuningKey.IDLE_MAX_YAW_STEP.key(), 3.0);
        defaults.put(TuningKey.IDLE_YAW_DEADZONE.key(), 2.0);
        BotTuning.Profile changed = PresetTuning.profile(changedRoot, 3);

        assertNotEquals(originalStep, IdleHumanizer.headStep(changed, 10.0F, true));
        assertEquals(0.0F, IdleHumanizer.headStep(changed, 1.9F, true), 1.0E-6F);
        assertEquals(3.0F, IdleHumanizer.headStep(changed, 10.0F, true), 1.0E-6F);
    }

    @Test
    void allFivePresetProfilesContainTheMovementRegistry() {
        JSONObject root = PresetTuning.root(1);

        for (Difficulty level : Difficulty.values()) {
            BotTuning.Profile profile = PresetTuning.profile(root, level.level());
            assertEquals(16.0, profile.number(TuningKey.IDLE_ATTEND_RANGE).doubleValue());
            assertEquals(100, profile.integer(TuningKey.SPREAD_MIN_TEAM));
        }
    }

    @Test
    void missingMovementKeyAndNonpositiveRandomSpanAreRejected() {
        JSONObject missing = PresetTuning.root(1);
        missing.getJSONObject("defaults").remove(TuningKey.IDLE_ATTEND_RANGE.key());
        assertThrows(IllegalArgumentException.class, () -> PresetTuning.profile(missing, 3));

        JSONObject invalidSpan = PresetTuning.root(2);
        invalidSpan.getJSONObject("defaults").put(TuningKey.IDLE_IDLE_HOLD_SPAN.key(), 0);
        assertThrows(IllegalArgumentException.class, () -> PresetTuning.profile(invalidSpan, 3));
    }
}
