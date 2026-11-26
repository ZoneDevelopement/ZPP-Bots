package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WaterExitTest {

    @Test
    void aheadStepsAlongTheFacingAtSurfaceHeight() {
        BotTuning.Profile profile = mediumProfile(PresetTuning.root(1));
        assertEquals(new BlockPos(0, 63, 0), WaterExit.ahead(profile, new Vec3(0.5, 64.0, -0.5), 0.0F, 63));
        assertEquals(new BlockPos(0, 63, -2), WaterExit.ahead(profile, new Vec3(0.5, 64.0, -0.5), 180.0F, 63));
        assertEquals(new BlockPos(0, 63, 1), WaterExit.ahead(profile, new Vec3(0.5, 64.0, 0.10000001), 0.0F, 63));

        JSONObject changedRoot = PresetTuning.root(2);
        changedRoot.getJSONObject("defaults").put(TuningKey.WATER_EXIT_AHEAD_PROBE.key(), 2.0);
        BotTuning.Profile changed = mediumProfile(changedRoot);
        assertEquals(new BlockPos(0, 63, 1), WaterExit.ahead(changed, new Vec3(0.5, 64.0, -0.5), 0.0F, 63));
    }

    @Test
    void exitScoringAndDetourPreserveOriginalBoundariesAndRespondToWeights() {
        BotTuning.Profile original = mediumProfile(PresetTuning.root(1));
        assertEquals(6.0, WaterExit.exitScore(original, 2, 3.0));
        assertTrue(WaterExit.acceptsDetour(original, 18.0, 10.0));
        assertFalse(WaterExit.acceptsDetour(original, 18.001, 10.0));
        assertTrue(WaterExit.acceptsDetour(original, Double.NaN, 10.0));

        JSONObject changedRoot = PresetTuning.root(2);
        changedRoot.getJSONObject("defaults").put(TuningKey.WATER_EXIT_PATH_WEIGHT.key(), 2.0);
        changedRoot.getJSONObject("defaults").put(TuningKey.WATER_EXIT_GOAL_WEIGHT.key(), 0.5);
        changedRoot.getJSONObject("defaults").put(TuningKey.WATER_EXIT_MAX_DETOUR.key(), 2.0);
        BotTuning.Profile changed = mediumProfile(changedRoot);
        assertEquals(7.5, WaterExit.exitScore(changed, 2, 3.0));
        assertFalse(WaterExit.acceptsDetour(changed, 13.0, 10.0));
    }

    @Test
    void pearlHelpersPreserveOriginalValuesAndRespondToTuning() {
        BotTuning.Profile original = mediumProfile(PresetTuning.root(1));
        assertEquals(2.5, CobwebEscapeController.pearlScore(original, 4.0, 1.0));
        assertEquals(-24.0F, CobwebEscapeController.pearlPitch(original, 0.05, 1.0));
        assertEquals(-45.0F, CobwebEscapeController.pearlPitch(original, 1.0, 1.0));

        JSONObject changedRoot = PresetTuning.root(2);
        JSONObject defaults = changedRoot.getJSONObject("defaults");
        defaults.put(TuningKey.WEB_ESCAPE_PEARL_IDEAL_DIST.key(), 3.0);
        defaults.put(TuningKey.WEB_ESCAPE_PEARL_DISTANCE_SCALE.key(), 2.0);
        defaults.put(TuningKey.WEB_ESCAPE_PEARL_ALIGNMENT_WEIGHT.key(), 0.5);
        defaults.put(TuningKey.WEB_ESCAPE_AIM_MIN_HORIZ.key(), 2.0);
        defaults.put(TuningKey.WEB_ESCAPE_PEARL_PITCH.key(), -10.0);
        BotTuning.Profile changed = mediumProfile(changedRoot);
        assertEquals(1.0, CobwebEscapeController.pearlScore(changed, 4.0, 1.0));
        assertEquals(-10.0F, CobwebEscapeController.pearlPitch(changed, 1.0, 1.0));
        assertEquals(-45.0F, CobwebEscapeController.pearlPitch(changed, 3.0, 3.0));
    }

    @Test
    void allFiveProfilesValidateAndInvalidWaterOrPearlValuesAreRejected() {
        for (Difficulty level : Difficulty.values()) {
            PresetTuning.profile(PresetTuning.root(1), level.level());
        }

        JSONObject bankStep = PresetTuning.root(2);
        bankStep.getJSONObject("defaults").put(TuningKey.WATER_EXIT_BANK_STEP.key(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> mediumProfile(bankStep));

        JSONObject pearlMin = PresetTuning.root(3);
        pearlMin.getJSONObject("defaults").put(TuningKey.WEB_ESCAPE_PEARL_MIN_DIST.key(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> mediumProfile(pearlMin));

        JSONObject pearlScale = PresetTuning.root(4);
        pearlScale.getJSONObject("defaults").put(TuningKey.WEB_ESCAPE_PEARL_DISTANCE_SCALE.key(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> mediumProfile(pearlScale));
    }

    private static BotTuning.Profile mediumProfile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }
}
