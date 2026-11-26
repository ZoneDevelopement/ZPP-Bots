package dev.nandi0813.zppbots.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class BotCombatScoringTest {

    @Test
    void nullPlayerHasNoScore() {
        assertEquals(0.0, BotCombatScoring.score(null, null));
    }

    @Test
    void inventoryWeightsUseOriginalAndChangedProfiles() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        assertEquals(6.0, BotCombatScoring.itemWeight(original, Material.TOTEM_OF_UNDYING));
        assertEquals(4.0, BotCombatScoring.itemWeight(original, Material.ENCHANTED_GOLDEN_APPLE));
        assertEquals(1.0, BotCombatScoring.itemWeight(original, Material.GOLDEN_APPLE));
        assertEquals(12.0, BotCombatScoring.itemWeight(original, Material.MACE));
        assertEquals(5.0, BotCombatScoring.itemWeight(original, Material.ELYTRA));
        assertEquals(0.0, BotCombatScoring.itemWeight(original, Material.STONE));

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.COMBAT_SCORE_TOTEM, 7);
        set(changedRoot, TuningKey.COMBAT_SCORE_ENCHANTED_APPLE, 8);
        set(changedRoot, TuningKey.COMBAT_SCORE_APPLE, 9);
        set(changedRoot, TuningKey.COMBAT_SCORE_MACE, 10);
        set(changedRoot, TuningKey.COMBAT_SCORE_ELYTRA, 11);
        BotTuning.Profile changed = profile(changedRoot);
        assertEquals(7.0, BotCombatScoring.itemWeight(changed, Material.TOTEM_OF_UNDYING));
        assertEquals(8.0, BotCombatScoring.itemWeight(changed, Material.ENCHANTED_GOLDEN_APPLE));
        assertEquals(9.0, BotCombatScoring.itemWeight(changed, Material.GOLDEN_APPLE));
        assertEquals(10.0, BotCombatScoring.itemWeight(changed, Material.MACE));
        assertEquals(11.0, BotCombatScoring.itemWeight(changed, Material.ELYTRA));
        assertEquals(0.0, BotCombatScoring.itemWeight(changed, Material.STONE));
    }

    @Test
    void profilesValidateAndInventoryWeightBoundsAreEnforced() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(root, difficulty.level());
        }

        JSONObject totem = PresetTuning.root(2);
        set(totem, TuningKey.COMBAT_SCORE_TOTEM, -1);
        assertThrows(IllegalArgumentException.class, () -> profile(totem));

        JSONObject mace = PresetTuning.root(3);
        set(mace, TuningKey.COMBAT_SCORE_MACE, 1025);
        assertThrows(IllegalArgumentException.class, () -> profile(mace));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
