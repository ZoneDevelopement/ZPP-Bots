package dev.nandi0813.zppbots.ai.move;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class LocalPlannerTest {
    @Test
    void difficultyRanksHazardsAboveOpenGround() {
        var profile = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        assertEquals(10, LocalPlanner.bodyDifficulty(profile, Material.COBWEB));
        assertEquals(6, LocalPlanner.bodyDifficulty(profile, Material.WATER));
        assertEquals(1, LocalPlanner.bodyDifficulty(profile, Material.AIR));
        assertEquals(7, LocalPlanner.floorDifficulty(profile, Material.HONEY_BLOCK));
        assertEquals(4, LocalPlanner.floorDifficulty(profile, Material.BLUE_ICE));
        assertEquals(1, LocalPlanner.floorDifficulty(profile, Material.STONE));
    }

    @Test
    void eachMaterialCostCanBeChangedByTuning() {
        assertBodyCostChanges(TuningKey.PLANNER_COBWEB_COST, Material.COBWEB, 10);
        assertBodyCostChanges(TuningKey.PLANNER_POWDER_SNOW_COST, Material.POWDER_SNOW, 9);
        assertBodyCostChanges(TuningKey.PLANNER_BERRY_COST, Material.SWEET_BERRY_BUSH, 8);
        assertBodyCostChanges(TuningKey.PLANNER_WATER_COST, Material.WATER, 6);
        assertBodyCostChanges(TuningKey.PLANNER_BAMBOO_COST, Material.BAMBOO, 4);
        assertBodyCostChanges(TuningKey.PLANNER_SCAFFOLDING_COST, Material.SCAFFOLDING, 3);
        assertBodyCostChanges(TuningKey.PLANNER_SNOW_COST, Material.SNOW, 2);
        assertFloorCostChanges(TuningKey.PLANNER_HONEY_COST, Material.HONEY_BLOCK, 7);
        assertFloorCostChanges(TuningKey.PLANNER_SOUL_SAND_COST, Material.SOUL_SAND, 6);
        assertFloorCostChanges(TuningKey.PLANNER_MUD_COST, Material.MUD, 5);
        assertFloorCostChanges(TuningKey.PLANNER_ICE_COST, Material.BLUE_ICE, 4);
        assertFloorCostChanges(TuningKey.PLANNER_SOUL_SOIL_COST, Material.SOUL_SOIL, 2);

        var originalBase = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        var changedBase = profileWith(TuningKey.PLANNER_BASE_COST, 2);
        assertEquals(1, LocalPlanner.bodyDifficulty(originalBase, Material.AIR));
        assertEquals(1, LocalPlanner.floorDifficulty(originalBase, Material.STONE));
        assertEquals(2, LocalPlanner.bodyDifficulty(changedBase, Material.AIR));
        assertEquals(2, LocalPlanner.floorDifficulty(changedBase, Material.STONE));
    }

    @Test
    void weightedScorePreservesOriginalFormulaAndUsesEachTunedWeight() {
        var original = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        double score = LocalPlanner.weightedScore(original, 2.0, 3.0, 0.5, 1.0, 2.0, 4.0);
        assertEquals(-3.3, score, 1.0E-9);

        TuningKey[] weights = {
            TuningKey.PLANNER_W_PROGRESS,
            TuningKey.PLANNER_W_MOVE,
            TuningKey.PLANNER_W_TURN,
            TuningKey.PLANNER_W_VERT,
            TuningKey.PLANNER_W_OPEN,
            TuningKey.PLANNER_W_DIFFICULTY
        };
        for (TuningKey weight : weights) {
            var changed = profileWith(weight, original.number(weight).doubleValue() + 1.0);
            assertNotEquals(
                    score,
                    LocalPlanner.weightedScore(changed, 2.0, 3.0, 0.5, 1.0, 2.0, 4.0));
        }
    }

    @Test
    void everyDifficultyProfileValidatesAndInvalidBoundsAreRejected() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(root, difficulty.level());
        }

        JSONObject zeroWindow = PresetTuning.root(1);
        zeroWindow.getJSONObject("defaults").put(TuningKey.PLANNER_WINDOW_LEN.key(), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresetTuning.profile(zeroWindow, Difficulty.MEDIUM.level()));

        JSONObject wideBeam = PresetTuning.root(1);
        wideBeam.getJSONObject("defaults").put(TuningKey.PLANNER_BEAM_WIDTH.key(), 9);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresetTuning.profile(wideBeam, Difficulty.MEDIUM.level()));

        JSONObject zeroBaseCost = PresetTuning.root(1);
        zeroBaseCost.getJSONObject("defaults").put(TuningKey.PLANNER_BASE_COST.key(), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresetTuning.profile(zeroBaseCost, Difficulty.MEDIUM.level()));
    }

    @Test
    void jumpHeightGrowsWithJumpBoost() {
        assertEquals(1, LocalPlanner.maxJump(0));
        assertEquals(2, LocalPlanner.maxJump(2));
    }

    @Test
    void stepDirectionIsHorizontalUnitVector() {
        double[] d = LocalPlanner.dir(new BlockPos(0, 0, 0), new BlockPos(3, 1, 4));
        assertEquals(0.6, d[0], 1e-9);
        assertEquals(0.8, d[1], 1e-9);
        assertArrayEquals(
                new double[] {0.0, 0.0},
                LocalPlanner.dir(new BlockPos(1, 0, 1), new BlockPos(1, 5, 1)),
                1e-9);
    }

    private static void assertBodyCostChanges(TuningKey key, Material material, int originalCost) {
        var original = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        var changed = profileWith(key, originalCost + 1);
        assertEquals(originalCost, LocalPlanner.bodyDifficulty(original, material));
        assertEquals(originalCost + 1, LocalPlanner.bodyDifficulty(changed, material));
    }

    private static void assertFloorCostChanges(TuningKey key, Material material, int originalCost) {
        var original = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        var changed = profileWith(key, originalCost + 1);
        assertEquals(originalCost, LocalPlanner.floorDifficulty(original, material));
        assertEquals(originalCost + 1, LocalPlanner.floorDifficulty(changed, material));
    }

    private static BotTuning.Profile profileWith(TuningKey key, Object value) {
        JSONObject root = PresetTuning.root(1);
        root.getJSONObject("defaults").put(key.key(), value);
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }
}
