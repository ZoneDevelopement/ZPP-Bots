package dev.nandi0813.zppbots.ai.escape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class ChorusClutchTuningTest {

    @Test
    void boxedUsesOriginalAndChangedThresholds() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        assertFalse(ChorusEscapeController.boxed(original, 2));
        assertTrue(ChorusEscapeController.boxed(original, 3));
        assertTrue(ChorusEscapeController.boxed(original, 4));

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.CHORUS_ESCAPE_BOX_SIDES, 2);
        BotTuning.Profile changed = profile(changedRoot);
        assertTrue(ChorusEscapeController.boxed(changed, 2));
    }

    @Test
    void deployHeightPreservesOriginalBoundariesAndRespondsToTuning() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        assertEquals(2, WaterBucketClutchController.deployHeight(original, -0.1));
        assertEquals(2, WaterBucketClutchController.deployHeight(original, -1.0));
        assertEquals(3, WaterBucketClutchController.deployHeight(original, -1.1));
        assertEquals(3, WaterBucketClutchController.deployHeight(original, -10.0));
        assertEquals(2, WaterBucketClutchController.deployHeight(original, Double.NaN));

        JSONObject changedRoot = PresetTuning.root(3);
        set(changedRoot, TuningKey.WATER_CLUTCH_DEPLOY_HEIGHT, 1);
        set(changedRoot, TuningKey.WATER_CLUTCH_MAX_DEPLOY_HEIGHT, 5);
        set(changedRoot, TuningKey.WATER_CLUTCH_DEPLOY_EXTRA, 2);
        BotTuning.Profile changed = profile(changedRoot);
        assertEquals(4, WaterBucketClutchController.deployHeight(changed, -1.1));
        assertEquals(5, WaterBucketClutchController.deployHeight(changed, -10.0));
    }

    @Test
    void profilesValidateAndInvalidBoundsAreRejected() {
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(PresetTuning.root(1), difficulty.level());
        }

        JSONObject boxSides = PresetTuning.root(2);
        set(boxSides, TuningKey.CHORUS_ESCAPE_BOX_SIDES, 5);
        assertThrows(IllegalArgumentException.class, () -> profile(boxSides));

        JSONObject scanDepth = PresetTuning.root(3);
        set(scanDepth, TuningKey.WATER_CLUTCH_SCAN_DEPTH, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(scanDepth));

        JSONObject minFallDist = PresetTuning.root(4);
        set(minFallDist, TuningKey.WATER_CLUTCH_MIN_FALL_DIST, -1);
        assertThrows(IllegalArgumentException.class, () -> profile(minFallDist));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
