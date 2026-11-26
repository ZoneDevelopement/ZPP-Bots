package dev.nandi0813.zppbots.ai.move;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class JumpTunnelTuningTest {
    @Test
    void jumpPlacementHysteresisUsesOriginalBoundariesAndRetainsState() {
        var profile = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());

        assertFalse(JumpPlaceController.activeFor(profile, false, 20.0));
        assertTrue(JumpPlaceController.activeFor(profile, false, 20.0001));
        assertTrue(JumpPlaceController.activeFor(profile, true, 7.0001));
        assertFalse(JumpPlaceController.activeFor(profile, true, 7.0));
        assertTrue(JumpPlaceController.activeFor(profile, true, Double.NaN));
        assertFalse(JumpPlaceController.activeFor(profile, false, Double.NaN));
    }

    @Test
    void jumpPlacementHysteresisRespondsToChangedThresholds() {
        JSONObject root = PresetTuning.root(1);
        JSONObject defaults = root.getJSONObject("defaults");
        defaults.put(TuningKey.JUMP_PLACE_START_DIST.key(), 15.0);
        defaults.put(TuningKey.JUMP_PLACE_STOP_DIST.key(), 4.0);
        var profile = PresetTuning.profile(root, Difficulty.MEDIUM.level());

        assertTrue(JumpPlaceController.activeFor(profile, false, 15.0001));
        assertFalse(JumpPlaceController.activeFor(profile, true, 4.0));
    }

    @Test
    void jumpPlacementMaterialAvoidanceIsTunablePerMaterial() {
        var original = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        assertFalse(JumpPlaceController.allowedMaterial(original, Material.OBSIDIAN));
        assertFalse(JumpPlaceController.allowedMaterial(original, Material.CRYING_OBSIDIAN));
        assertFalse(JumpPlaceController.allowedMaterial(original, Material.RESPAWN_ANCHOR));
        assertFalse(JumpPlaceController.allowedMaterial(original, Material.GLOWSTONE));
        assertTrue(JumpPlaceController.allowedMaterial(original, Material.STONE));

        assertMaterialAllowedWhenAvoidanceDisabled(
                TuningKey.JUMP_PLACE_AVOID_OBSIDIAN, Material.OBSIDIAN);
        assertMaterialAllowedWhenAvoidanceDisabled(
                TuningKey.JUMP_PLACE_AVOID_CRYING_OBSIDIAN, Material.CRYING_OBSIDIAN);
        assertMaterialAllowedWhenAvoidanceDisabled(
                TuningKey.JUMP_PLACE_AVOID_ANCHOR, Material.RESPAWN_ANCHOR);
        assertMaterialAllowedWhenAvoidanceDisabled(
                TuningKey.JUMP_PLACE_AVOID_GLOWSTONE, Material.GLOWSTONE);
    }

    @Test
    void tunnelProgressHelpersUseOriginalAndChangedMargins() {
        var original = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        assertFalse(TunnelController.triggerBlocked(original, 9.75, 10.0));
        assertTrue(TunnelController.triggerBlocked(original, 9.7501, 10.0));
        assertFalse(TunnelController.madeProgress(original, 9.7, 10.0));
        assertTrue(TunnelController.madeProgress(original, 9.6999, 10.0));

        JSONObject root = PresetTuning.root(1);
        JSONObject defaults = root.getJSONObject("defaults");
        defaults.put(TuningKey.TUNNEL_TRIGGER_PROGRESS.key(), 0.5);
        defaults.put(TuningKey.TUNNEL_PROGRESS.key(), 0.6);
        var changed = PresetTuning.profile(root, Difficulty.MEDIUM.level());
        assertFalse(TunnelController.triggerBlocked(changed, 9.5, 10.0));
        assertTrue(TunnelController.triggerBlocked(changed, 9.5001, 10.0));
        assertFalse(TunnelController.madeProgress(changed, 9.4, 10.0));
        assertTrue(TunnelController.madeProgress(changed, 9.3999, 10.0));
    }

    @Test
    void everyDifficultyProfileValidatesAndInvalidBoundsAreRejected() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(root, difficulty.level());
        }

        JSONObject zeroPredictionSteps = PresetTuning.root(1);
        zeroPredictionSteps.getJSONObject("defaults")
                .put(TuningKey.JUMP_PLACE_PREDICTION_STEPS.key(), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresetTuning.profile(zeroPredictionSteps, Difficulty.MEDIUM.level()));

        JSONObject zeroBlockedTriggerTicks = PresetTuning.root(1);
        zeroBlockedTriggerTicks.getJSONObject("defaults")
                .put(TuningKey.TUNNEL_BLOCKED_TRIGGER_TICKS.key(), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresetTuning.profile(zeroBlockedTriggerTicks, Difficulty.MEDIUM.level()));
    }

    private static void assertMaterialAllowedWhenAvoidanceDisabled(
            TuningKey avoidanceKey, Material material) {
        JSONObject root = PresetTuning.root(1);
        root.getJSONObject("defaults").put(avoidanceKey.key(), false);
        var profile = PresetTuning.profile(root, Difficulty.MEDIUM.level());

        assertTrue(JumpPlaceController.allowedMaterial(profile, material));
        Material[] avoidedMaterials = {
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN,
            Material.RESPAWN_ANCHOR,
            Material.GLOWSTONE
        };
        for (Material avoidedMaterial : avoidedMaterials) {
            if (avoidedMaterial != material) {
                assertFalse(JumpPlaceController.allowedMaterial(profile, avoidedMaterial));
            }
        }
    }
}
