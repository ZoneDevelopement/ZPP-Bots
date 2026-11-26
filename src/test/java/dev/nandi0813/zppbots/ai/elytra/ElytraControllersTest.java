package dev.nandi0813.zppbots.ai.elytra;

import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElytraControllersTest {

    @Test
    void followPitchIsClampedAndYawPointsAtGoal() {
        assertEquals(-45.0F, ElytraFollowController.pitchTo(new Vec3(0.0, 100.0, 1.0), 1.0));
        assertEquals(45.0F, ElytraFollowController.pitchTo(new Vec3(0.0, -100.0, 1.0), 1.0));
        assertEquals(0.0F, ElytraFollowController.yawTo(new Vec3(0.0, 0.0, 5.0)), 1.0E-4F);
        assertEquals(-90.0F, ElytraFollowController.yawTo(new Vec3(5.0, 0.0, 0.0)), 1.0E-4F);
    }

    @Test
    void spearSteerLimitsTurnRate() {
        float[] next = ElytraSpearController.steerStep(0.0F, 0.0F, 90.0F, -80.0F);
        assertArrayEquals(new float[] {13.0F, -9.0F}, next, 1.0E-4F);
        assertArrayEquals(new float[] {5.0F, -3.0F}, ElytraSpearController.steerStep(0.0F, 0.0F, 5.0F, -3.0F), 1.0E-4F);
    }

    @Test
    void spearClosingSpeedIsRelativeVelocityAlongLineOfSight() {
        assertEquals(1.0, ElytraSpearController.closingSpeed(new Vec3(1.0, 0.0, 0.0), Vec3.ZERO, new Vec3(10.0, 0.0, 0.0)), 1.0E-9);
        assertEquals(0.0, ElytraSpearController.closingSpeed(new Vec3(1.0, 0.0, 0.0), Vec3.ZERO, Vec3.ZERO));
    }

    @Test
    void maceApexAndCooldownFollowFormulas() {
        BotTuning.Profile p = profile(PresetTuning.root(1));
        assertEquals(10.0 + 2.0 + 1.0, ElytraMaceController.apexY(p, 0.0, false, 2.0, 1.0), 1.0E-9);
        assertEquals(2.0 + 1.0 + 1.0, ElytraMaceController.apexY(p, 0.0, true, 2.0, 1.0), 1.0E-9);
        assertEquals(12, ElytraMaceController.smashCooldown(p, 5, false));
        assertEquals(10, ElytraMaceController.smashCooldown(p, 5, true));
        assertEquals(42, ElytraMaceController.smashCooldown(p, 1, false));
    }

    @Test
    void changedMaceTuningControlsApexAndCooldown() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.ELYTRA_MACE_TARGET_HEIGHT, 5.0);
        assertEquals(8.0, ElytraMaceController.apexY(profile(root), 0.0, false, 2.0, 1.0), 1.0E-9);

        JSONObject mult = PresetTuning.root(3);
        set(mult, TuningKey.ELYTRA_MACE_COOLDOWN_MULT_L1, 2.0);
        assertEquals(24, ElytraMaceController.smashCooldown(profile(mult), 1, false));
    }

    @Test
    void maceRechainChanceBoundIsEnforced() {
        JSONObject root = PresetTuning.root(4);
        set(root, TuningKey.ELYTRA_MACE_RECHAIN_CHANCE_L3, 1.5);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
