package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestockControllerTest {

    @Test
    void needScoreIsZeroWithoutWantAndGrowsWithDeficit() {
        assertEquals(0.0, RestockController.needScore(0, 4, 10.0));
        assertEquals(10.0 * 0.75, RestockController.needScore(1, 4, 10.0));
        assertEquals(10.0 * 1.5, RestockController.needScore(8, 4, 10.0));
        assertEquals(1.5, RestockController.needScore(3, 0, 1.0));
    }

    @Test
    void fixedWeightRanksSupplies() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(7.0, RestockController.fixedWeight(profile, Material.ENDER_CHEST, false));
        assertEquals(5.0, RestockController.fixedWeight(profile, Material.ENDER_PEARL, false));
        assertEquals(8.0, RestockController.fixedWeight(profile, Material.SPLASH_POTION, true));
        assertEquals(3.0, RestockController.fixedWeight(profile, Material.SPLASH_POTION, false));
        assertEquals(-1.0, RestockController.fixedWeight(profile, Material.STICK, false));
    }

    @Test
    void fixedTargetsMatchExpectedTable() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(4, RestockController.fixedTarget(profile, Material.TOTEM_OF_UNDYING));
        assertEquals(32, RestockController.fixedTarget(profile, Material.ENDER_PEARL));
        assertEquals(64, RestockController.fixedTarget(profile, Material.EXPERIENCE_BOTTLE));
        assertEquals(16, RestockController.fixedTarget(profile, Material.POWERED_RAIL));
        assertEquals(2, RestockController.fixedTarget(profile, Material.CROSSBOW));
        assertEquals(-1, RestockController.fixedTarget(profile, Material.DIAMOND_SWORD));
    }

    @Test
    void changedWeightAndTargetControlPriority() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.RESTOCK_WEIGHT_POTION_HURT, 9);
        set(root, TuningKey.RESTOCK_TARGET_PEARL_GAP, 16);
        BotTuning.Profile profile = profile(root);
        assertEquals(9.0, RestockController.fixedWeight(profile, Material.SPLASH_POTION, true));
        assertEquals(16, RestockController.fixedTarget(profile, Material.ENDER_PEARL));
    }

    @Test
    void regearWearMustStayWithinBounds() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.RESTOCK_REGEAR_WEAR, 1.5);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    @Test
    void railsAndBuildingMaterials() {
        assertTrue(RestockController.isRailItem(Material.ACTIVATOR_RAIL));
        assertFalse(RestockController.isRailItem(Material.TNT_MINECART));
        assertTrue(RestockController.isBuildingMaterial(Material.NETHERRACK));
        assertFalse(RestockController.isBuildingMaterial(Material.OBSIDIAN));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
