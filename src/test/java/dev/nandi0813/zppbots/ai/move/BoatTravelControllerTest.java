package dev.nandi0813.zppbots.ai.move;

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

final class BoatTravelControllerTest {
    @Test
    void continueDrivingPreservesArrivalAndLeadBoundaries() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        assertFalse(BoatTravelController.continueDriving(original, 3.5, 10.0));
        assertFalse(BoatTravelController.continueDriving(original, 8.0, 2.0));
        assertTrue(BoatTravelController.continueDriving(original, 8.0, 2.001));
        assertTrue(BoatTravelController.continueDriving(original, 8.001, 2.0));
        assertTrue(BoatTravelController.continueDriving(original, Double.NaN, 1.0));
        assertTrue(BoatTravelController.continueDriving(original, 9.0, Double.NaN));

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.BOAT_ARRIVE_DIST, 5.0);
        set(changedRoot, TuningKey.BOAT_LEAD_ARRIVE, 3.0);
        set(changedRoot, TuningKey.BOAT_LEAD_CLOSE_DIST, 10.0);
        BotTuning.Profile changed = profile(changedRoot);
        assertFalse(BoatTravelController.continueDriving(changed, 5.0, 20.0));
        assertFalse(BoatTravelController.continueDriving(changed, 9.0, 3.0));
        assertTrue(BoatTravelController.continueDriving(changed, 11.0, 3.0));
    }

    @Test
    void steeringPreservesStrictDeadzoneAndRespondsToTuning() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        assertEquals(0.0F, BoatTravelController.steering(original, -5.0F));
        assertEquals(0.0F, BoatTravelController.steering(original, 5.0F));
        assertEquals(1.0F, BoatTravelController.steering(original, -5.01F));
        assertEquals(-1.0F, BoatTravelController.steering(original, 5.01F));

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.BOAT_STEER_DEADZONE, 10.0);
        BotTuning.Profile changed = profile(changedRoot);
        assertEquals(0.0F, BoatTravelController.steering(changed, 10.0F));
        assertEquals(-1.0F, BoatTravelController.steering(changed, 10.01F));
    }

    @Test
    void leadPointPreservesOriginalProjectionAndRespondsToTuning() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        Vec3 position = new Vec3(0.0, 2.0, 0.0);
        Vec3 velocity = new Vec3(0.1, 0.0, 0.0);
        assertVector(new Vec3(3.5, 2.0, 0.0), BoatTravelController.leadPoint(original, position, velocity, 7.0));
        assertVector(new Vec3(5.5, 2.0, 0.0), BoatTravelController.leadPoint(original, position, velocity, 70.0));
        assertVector(position, BoatTravelController.leadPoint(original, position, new Vec3(0.02, 0.0, 0.0), 7.0));

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.BOAT_SPEED, 0.7);
        set(changedRoot, TuningKey.BOAT_MAX_LEAD_TICKS, 10);
        set(changedRoot, TuningKey.BOAT_LEAD_EXTRA, 2.0);
        BotTuning.Profile changed = profile(changedRoot);
        assertVector(new Vec3(3.0, 2.0, 0.0), BoatTravelController.leadPoint(changed, position, velocity, 7.0));
    }

    @Test
    void filteredVelocityPreservesCutoffAndRespondsToWeights() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        assertVector(new Vec3(1.3, 0.0, 0.0),
                BoatTravelController.filteredVelocity(original, new Vec3(1.0, 0.0, 0.0), new Vec3(2.0, 0.0, 0.0)));
        assertVector(new Vec3(0.7, 0.0, 0.0),
                BoatTravelController.filteredVelocity(original, new Vec3(1.0, 0.0, 0.0), new Vec3(2.001, 0.0, 0.0)));

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.BOAT_TARGET_JUMP_SQR, 1.0);
        set(changedRoot, TuningKey.BOAT_TARGET_OLD_WEIGHT, 0.5);
        set(changedRoot, TuningKey.BOAT_TARGET_NEW_WEIGHT, 0.5);
        BotTuning.Profile changed = profile(changedRoot);
        assertVector(new Vec3(1.5, 0.0, 0.0),
                BoatTravelController.filteredVelocity(changed, new Vec3(2.0, 0.0, 0.0), new Vec3(1.0, 0.0, 0.0)));
        assertVector(new Vec3(1.0, 0.0, 0.0),
                BoatTravelController.filteredVelocity(changed, new Vec3(2.0, 0.0, 0.0), new Vec3(2.0, 0.0, 0.0)));
    }

    @Test
    void fiveProfilesValidateAndInvalidBoatValuesAreRejected() {
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(PresetTuning.root(1), difficulty.level());
        }

        JSONObject zeroSpeed = PresetTuning.root(2);
        set(zeroSpeed, TuningKey.BOAT_SPEED, 0.0);
        assertThrows(IllegalArgumentException.class, () -> profile(zeroSpeed));

        JSONObject zeroAttackPeriod = PresetTuning.root(3);
        set(zeroAttackPeriod, TuningKey.BOAT_ATTACK_PERIOD, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(zeroAttackPeriod));

        JSONObject zeroWaterSamples = PresetTuning.root(4);
        set(zeroWaterSamples, TuningKey.BOAT_WATER_SAMPLES, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(zeroWaterSamples));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9);
        assertEquals(expected.y(), actual.y(), 1.0E-9);
        assertEquals(expected.z(), actual.z(), 1.0E-9);
    }
}
