package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.settings.TechniqueConfig.PearlCatchVariant;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PearlCatchControllerTest {

    @Test
    void smartVariantPicksByDistanceAndGround() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(PearlCatchVariant.STANDARD, PearlCatchController.resolveType(profile, PearlCatchVariant.SMART, 1.5, true));
        assertEquals(PearlCatchVariant.HIGHER, PearlCatchController.resolveType(profile, PearlCatchVariant.SMART, 1.5, false));
        assertEquals(PearlCatchVariant.HIGHER, PearlCatchController.resolveType(profile, PearlCatchVariant.SMART, 4.0, true));
        assertEquals(PearlCatchVariant.ANGLE, PearlCatchController.resolveType(profile, PearlCatchVariant.SMART, 6.0, true));
        assertEquals(PearlCatchVariant.INSTANT, PearlCatchController.resolveType(profile, PearlCatchVariant.INSTANT, 9.0, true));
    }

    @Test
    void changedSmartHorizontalsControlVariant() {
        JSONObject angle = PresetTuning.root(2);
        set(angle, TuningKey.PEARL_CATCH_ANGLE_MIN_HORIZ, 4.0);
        assertEquals(PearlCatchVariant.ANGLE, PearlCatchController.resolveType(profile(angle), PearlCatchVariant.SMART, 4.0, true));

        JSONObject standard = PresetTuning.root(3);
        set(standard, TuningKey.PEARL_CATCH_STANDARD_MAX_HORIZ, 1.0);
        assertEquals(PearlCatchVariant.HIGHER, PearlCatchController.resolveType(profile(standard), PearlCatchVariant.SMART, 1.5, true));
    }

    @Test
    void leadTrustBoundsAreEnforced() {
        JSONObject root = PresetTuning.root(4);
        set(root, TuningKey.PEARL_LEAD_TRUST, 5);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    @Test
    void wallProbeOffsetsFollowTheOctants() {
        assertArrayEquals(new int[] {0, 2}, PearlCatchController.probeOffset(0, 2));
        assertArrayEquals(new int[] {-3, 0}, PearlCatchController.probeOffset(2, 3));
        assertArrayEquals(new int[] {0, -1}, PearlCatchController.probeOffset(4, 1));
        assertArrayEquals(new int[] {3, 0}, PearlCatchController.probeOffset(6, 3));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
