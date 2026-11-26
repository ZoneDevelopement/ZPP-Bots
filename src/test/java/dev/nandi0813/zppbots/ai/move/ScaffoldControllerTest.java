package dev.nandi0813.zppbots.ai.move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class ScaffoldControllerTest {

    @Test
    void stepDirectionFollowsDominantAxis() {
        assertEquals(Direction.EAST, ScaffoldController.stepDirection(3.0, 1.0));
        assertEquals(Direction.WEST, ScaffoldController.stepDirection(-3.0, 1.0));
        assertEquals(Direction.SOUTH, ScaffoldController.stepDirection(1.0, 3.0));
        assertEquals(Direction.NORTH, ScaffoldController.stepDirection(1.0, -3.0));
    }

    @Test
    void stepDirectionTieGoesToXAxis() {
        assertEquals(Direction.WEST, ScaffoldController.stepDirection(-2.0, 2.0));
        assertEquals(Direction.WEST, ScaffoldController.stepDirection(0.0, 0.0));
    }

    @Test
    void placementPolicyPreservesDefaultsAndRespondsToTuning() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        assertEquals(0.45, ScaffoldController.placeFraction(original, BotSettings.BridgeMode.NINJA));
        assertEquals(0.55, ScaffoldController.placeFraction(original, BotSettings.BridgeMode.GOD));
        assertEquals(3, ScaffoldController.placeDelay(original, BotSettings.BridgeMode.NINJA));
        assertEquals(2, ScaffoldController.placeDelay(original, BotSettings.BridgeMode.MOONWALK));
        assertEquals(0, ScaffoldController.resetEvery(original, BotSettings.BridgeMode.NINJA));
        assertEquals(8, ScaffoldController.resetEvery(original, BotSettings.BridgeMode.GOD));
        assertEquals(10, ScaffoldController.resetEvery(original, BotSettings.BridgeMode.MOONWALK));

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.SCAFFOLD_EDGE_PLACE_FRAC, 0.2);
        set(changedRoot, TuningKey.SCAFFOLD_EARLY_PLACE_FRAC, 0.8);
        set(changedRoot, TuningKey.SCAFFOLD_NINJA_PLACE_DELAY, 5);
        set(changedRoot, TuningKey.SCAFFOLD_EARLY_PLACE_DELAY, 7);
        set(changedRoot, TuningKey.SCAFFOLD_GOD_RESET_EVERY, 4);
        set(changedRoot, TuningKey.SCAFFOLD_MOONWALK_RESET_EVERY, 6);
        BotTuning.Profile changed = profile(changedRoot);
        assertEquals(0.2, ScaffoldController.placeFraction(changed, BotSettings.BridgeMode.NINJA));
        assertEquals(0.8, ScaffoldController.placeFraction(changed, BotSettings.BridgeMode.MOONWALK));
        assertEquals(5, ScaffoldController.placeDelay(changed, BotSettings.BridgeMode.NINJA));
        assertEquals(7, ScaffoldController.placeDelay(changed, BotSettings.BridgeMode.GOD));
        assertEquals(4, ScaffoldController.resetEvery(changed, BotSettings.BridgeMode.GOD));
        assertEquals(6, ScaffoldController.resetEvery(changed, BotSettings.BridgeMode.MOONWALK));
    }

    @Test
    void tuningProfilesValidateDefaultsAndFirePickupDelay() {
        for (Difficulty difficulty : Difficulty.values()) {
            BotTuning.Profile profile = PresetTuning.profile(PresetTuning.root(1), difficulty.level());
            assertEquals(10, profile.integer(TuningKey.FIRE_ESCAPE_PICKUP_DELAY));
        }

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.FIRE_ESCAPE_PICKUP_DELAY, 20);
        assertEquals(20, profile(changedRoot).integer(TuningKey.FIRE_ESCAPE_PICKUP_DELAY));
    }

    @Test
    void invalidScaffoldAndFireValuesAreRejected() {
        JSONObject godForward = PresetTuning.root(2);
        set(godForward, TuningKey.SCAFFOLD_GOD_FORWARD, 1.01);
        assertThrows(IllegalArgumentException.class, () -> profile(godForward));

        JSONObject edgeFraction = PresetTuning.root(3);
        set(edgeFraction, TuningKey.SCAFFOLD_EDGE_PLACE_FRAC, -0.01);
        assertThrows(IllegalArgumentException.class, () -> profile(edgeFraction));

        JSONObject pickupDelay = PresetTuning.root(4);
        set(pickupDelay, TuningKey.FIRE_ESCAPE_PICKUP_DELAY, -1);
        assertThrows(IllegalArgumentException.class, () -> profile(pickupDelay));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
