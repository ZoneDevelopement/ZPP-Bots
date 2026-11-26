package dev.nandi0813.zppbots.ai.mace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class DiveLeadTest {

    @Test
    void noHeightNeedsNoTicks() {
        assertEquals(0, DiveLead.ticksToDrop(0.0, 0.0));
        assertEquals(0, DiveLead.ticksToDrop(-3.0, 0.0));
    }

    @Test
    void higherDropsTakeLongerAndAreCapped() {
        int low = DiveLead.ticksToDrop(5.0, 0.0);
        int high = DiveLead.ticksToDrop(20.0, 0.0);
        assertTrue(low > 0 && high > low);
        assertEquals(60, DiveLead.ticksToDrop(100000.0, 0.0));
    }

    @Test
    void fasterDownwardStartArrivesSooner() {
        assertTrue(DiveLead.ticksToDrop(20.0, -1.0) < DiveLead.ticksToDrop(20.0, 0.0));
    }

    @Test
    void projectedUsesOriginalAndChangedProfiles() {
        var original = profile(PresetTuning.root(1));
        assertVector(
                new Vec3(9.5, 2.0, -1.25),
                DiveLead.projected(original, new Vec3(1, 2, 3), new Vec3(0.5, 0.7, -0.25), 100));
        assertVector(
                new Vec3(1, 2, 3),
                DiveLead.projected(original, new Vec3(1, 2, 3), new Vec3(0.5, 0.7, -0.25), -1));

        JSONObject changed = PresetTuning.root(2);
        set(changed, TuningKey.DIVE_LEAD_MAX_TICKS, 4);
        set(changed, TuningKey.DIVE_LEAD_TRUST, 0.5);
        assertVector(
                new Vec3(2, 2, 2.5),
                DiveLead.projected(profile(changed), new Vec3(1, 2, 3), new Vec3(0.5, 0.7, -0.25), 100));
    }

    @Test
    void smoothUsesOriginalAndChangedProfiles() {
        var original = profile(PresetTuning.root(1));
        assertEquals(1.55, DiveLead.smooth(original, new Vec3(1, 0, 0), new Vec3(2, 0, 0)).x(), 1.0E-12);
        assertEquals(0.45, DiveLead.smooth(original, new Vec3(1, 0, 0), new Vec3(7, 0, 0)).x(), 1.0E-12);

        JSONObject changed = PresetTuning.root(2);
        set(changed, TuningKey.DIVE_LEAD_MAX_STEP_SQUARED, 1.0);
        set(changed, TuningKey.DIVE_LEAD_OLD_WEIGHT, 0.0);
        set(changed, TuningKey.DIVE_LEAD_STEP_WEIGHT, 1.0);
        var profile = profile(changed);
        assertEquals(
                0.0, DiveLead.smooth(profile, new Vec3(2, 0, 0), new Vec3(2, 0, 0)).x(), 1.0E-12);
        assertEquals(
                1.0, DiveLead.smooth(profile, new Vec3(2, 0, 0), new Vec3(1, 0, 0)).x(), 1.0E-12);
    }

    @Test
    void lethalUsesOriginalAndChangedProfiles() {
        var original = profile(PresetTuning.root(1));
        assertTrue(MaceTotemGuard.lethal(original, 10, 11.5));
        assertFalse(MaceTotemGuard.lethal(original, 10, 11.6));

        JSONObject changed = PresetTuning.root(2);
        set(changed, TuningKey.MACE_TOTEM_DAMAGE_MARGIN, 2.0);
        var profile = profile(changed);
        assertTrue(MaceTotemGuard.lethal(profile, 10, 20));
        assertFalse(MaceTotemGuard.lethal(profile, 10, 20.1));
    }

    @Test
    void allProfilesValidateAndDiveTotemBoundsAreEnforced() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(root, difficulty.level());
        }

        JSONObject maxTicks = PresetTuning.root(2);
        set(maxTicks, TuningKey.DIVE_LEAD_MAX_TICKS, -1);
        assertThrows(IllegalArgumentException.class, () -> profile(maxTicks));

        JSONObject margin = PresetTuning.root(3);
        set(margin, TuningKey.MACE_TOTEM_DAMAGE_MARGIN, 16.1);
        assertThrows(IllegalArgumentException.class, () -> profile(margin));
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-12);
        assertEquals(expected.y(), actual.y(), 1.0E-12);
        assertEquals(expected.z(), actual.z(), 1.0E-12);
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
