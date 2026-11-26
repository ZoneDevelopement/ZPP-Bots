package dev.nandi0813.zppbots.ai.move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.Random;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class WanderHopTuningTest {
    @Test
    void forwardAndPitchHelpersUseOriginalValues() {
        var profile = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());

        assertEquals(0.4F, WanderController.forward(profile, 1.0), 0.0001F);
        assertEquals(0.5F, WanderController.forward(profile, 1.5), 0.0001F);
        assertEquals(1.0F, WanderController.forward(profile, 3.0), 0.0001F);
        assertEquals(1.0F, WanderController.forward(profile, 10.0), 0.0001F);
        assertEquals(2.4F, WanderController.pitchStep(profile, 2.0F, 10.0F), 0.0001F);
    }

    @Test
    void forwardAndPitchHelpersRespondToTuningChanges() {
        JSONObject root = PresetTuning.root(1);
        JSONObject defaults = root.getJSONObject("defaults");
        defaults.put(TuningKey.WANDER_MIN_FORWARD.key(), 0.2);
        defaults.put(TuningKey.WANDER_SLOW_DISTANCE.key(), 5.0);
        defaults.put(TuningKey.WANDER_PITCH_EASE.key(), 0.5);
        var profile = PresetTuning.profile(root, Difficulty.MEDIUM.level());

        assertEquals(0.2F, WanderController.forward(profile, 1.0), 0.0001F);
        assertEquals(0.4F, WanderController.forward(profile, 2.0), 0.0001F);
        assertEquals(6.0F, WanderController.pitchStep(profile, 2.0F, 10.0F), 0.0001F);
    }

    @Test
    void hopHelpersPreserveSeededDrawOrderAndUseTunedBounds() {
        var profile = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        Random actual = new Random(7319L);
        int actualDelay = BunnyHopController.hopDelay(profile, actual);
        boolean actualSkip = BunnyHopController.skipHop(profile, actual);

        Random expected = new Random(7319L);
        int expectedDelay = expected.nextInt(5);
        boolean expectedSkip = expected.nextDouble() < 0.18;
        assertEquals(expectedDelay, actualDelay);
        assertEquals(expectedSkip, actualSkip);
        assertEquals(expected.nextLong(), actual.nextLong());

        JSONObject zeroDelayRoot = PresetTuning.root(1);
        zeroDelayRoot.getJSONObject("defaults").put(TuningKey.BUNNY_MAX_DELAY.key(), 0);
        var zeroDelayProfile = PresetTuning.profile(zeroDelayRoot, Difficulty.MEDIUM.level());
        assertEquals(0, BunnyHopController.hopDelay(zeroDelayProfile, new Random(1L)));

        JSONObject neverSkipRoot = PresetTuning.root(1);
        neverSkipRoot.getJSONObject("defaults").put(TuningKey.BUNNY_SKIP_CHANCE.key(), 0.0);
        var neverSkip = PresetTuning.profile(neverSkipRoot, Difficulty.MEDIUM.level());
        assertFalse(BunnyHopController.skipHop(neverSkip, new Random(1L)));

        JSONObject alwaysSkipRoot = PresetTuning.root(1);
        alwaysSkipRoot.getJSONObject("defaults").put(TuningKey.BUNNY_SKIP_CHANCE.key(), 1.0);
        var alwaysSkip = PresetTuning.profile(alwaysSkipRoot, Difficulty.MEDIUM.level());
        assertTrue(BunnyHopController.skipHop(alwaysSkip, new Random(1L)));
    }

    @Test
    void everyDifficultyProfileValidatesAndInvalidBoundsAreRejected() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(root, difficulty.level());
        }

        JSONObject zeroDriftSpan = PresetTuning.root(1);
        zeroDriftSpan.getJSONObject("defaults").put(TuningKey.WANDER_DRIFT_SPAN.key(), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresetTuning.profile(zeroDriftSpan, Difficulty.MEDIUM.level()));

        JSONObject zeroSlowDistance = PresetTuning.root(1);
        zeroSlowDistance.getJSONObject("defaults").put(TuningKey.WANDER_SLOW_DISTANCE.key(), 0.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresetTuning.profile(zeroSlowDistance, Difficulty.MEDIUM.level()));
    }
}
