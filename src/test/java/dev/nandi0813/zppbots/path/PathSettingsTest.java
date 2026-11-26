package dev.nandi0813.zppbots.path;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.Map;
import org.bukkit.Material;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathSettingsTest {

    private static BotTuning.Profile profile() {
        return PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
    }

    private static JSONObject rootWith(Map<TuningKey, Object> changes) {
        JSONObject root = PresetTuning.root(1);
        JSONObject level = root.getJSONObject("difficulty")
                .getJSONObject(Integer.toString(Difficulty.MEDIUM.level()));
        changes.forEach((key, value) -> level.put(key.key(), value));
        return root;
    }

    private static PathSettings settings(Map<TuningKey, Object> changes) {
        return new PathSettings(PresetTuning.profile(rootWith(changes), Difficulty.MEDIUM.level()));
    }

    @Test
    void defaults() {
        PathSettings s = new PathSettings(profile());
        assertEquals(PathSettings.MoveType.SPRINT, s.getMoveType());
        assertEquals(1.0, s.getMaxHorizontalDistance());
        assertEquals(2.0, s.getMaxVerticalDistance());
        assertEquals(0.25, s.getNodeHorizontalDistance());
        assertEquals(0.5, s.getNodeVerticalDistance());
        assertTrue(s.isStopFollowing());
        assertEquals(1.0, s.getHorizontalMoveCost());
        assertEquals(1.5, s.getVerticalMoveCost());
        assertEquals(3.0, s.getSwimCostMultiplier());
        for (Material material : avoidedMaterials()) assertFalse(s.isNotAvoided(material));
        assertTrue(s.isNotAvoided(Material.STONE));
    }

    @Test
    void everyPresetProfileCreatesSettings() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertNotNull(new PathSettings(
                    PresetTuning.profile(PresetTuning.root(1), difficulty.level())));
        }
    }

    @Test
    void invalidValuesAreIgnored() {
        PathSettings s = new PathSettings(profile());
        s.setMaxHorizontalDistance(0.0);
        s.setSwimCostMultiplier(-1.0);
        assertEquals(1.0, s.getMaxHorizontalDistance());
        assertEquals(3.0, s.getSwimCostMultiplier());
    }

    @Test
    void withinTargetAndNode() {
        PathSettings s = new PathSettings(profile());
        assertTrue(s.isWithinTarget(1.0, 2.0));
        assertFalse(s.isWithinTarget(1.1, 0.0));
        assertFalse(s.isWithinTarget(0.0, 2.1));
        s.setMaxVerticalDistance(-1.0);
        assertTrue(s.isWithinTarget(0.5, 100.0));
        assertTrue(s.isWithinNode(0.25, 0.5));
        assertFalse(s.isWithinNode(0.26, 0.0));
    }

    @Test
    void swimCostScalesWithEfficiencyAndGrace() {
        PathSettings s = new PathSettings(profile());
        s.calculateSwimCost(0.0, false);
        assertEquals(3.0, s.getSwimCostMultiplier(), 1e-9);
        s.calculateSwimCost(1.0, false);
        assertEquals(0.3, s.getSwimCostMultiplier(), 1e-9);
        s.calculateSwimCost(1.0, true);
        assertEquals(0.1, s.getSwimCostMultiplier(), 1e-9);
    }

    @Test
    void copyFromCopiesAvoidedBlocks() {
        PathSettings a = new PathSettings(profile());
        a.clearAvoidedBlocks();
        a.addAvoidedBlock(Material.DIRT);
        PathSettings b = new PathSettings(profile());
        b.copyFrom(a);
        assertFalse(b.isNotAvoided(Material.DIRT));
        assertTrue(b.isNotAvoided(Material.LAVA));
    }

    @Test
    void moveResultResetsToInfinity() {
        MoveResult r = new MoveResult();
        assertEquals(MoveResult.COST_INF, r.cost);
        r.x = 3;
        r.cost = 1.0;
        r.reset();
        assertEquals(0, r.x);
        assertEquals(MoveResult.COST_INF, r.cost);
    }

    @Test
    void profileCanChangePathSettings() {
        PathSettings s = settings(Map.of(
                TuningKey.PATH_MAX_HORIZONTAL_DISTANCE, 2.0,
                TuningKey.PATH_MAX_VERTICAL_DISTANCE, 3.0,
                TuningKey.PATH_NODE_HORIZONTAL_DISTANCE, 0.75,
                TuningKey.PATH_NODE_VERTICAL_DISTANCE, 1.25,
                TuningKey.PATH_MOVE_TYPE, 0,
                TuningKey.PATH_STOP_FOLLOWING, false,
                TuningKey.PATH_HORIZONTAL_MOVE_COST, 2.0,
                TuningKey.PATH_VERTICAL_MOVE_COST, 4.0));
        assertEquals(2.0, s.getMaxHorizontalDistance());
        assertEquals(3.0, s.getMaxVerticalDistance());
        assertEquals(0.75, s.getNodeHorizontalDistance());
        assertEquals(1.25, s.getNodeVerticalDistance());
        assertEquals(PathSettings.MoveType.WALK, s.getMoveType());
        assertFalse(s.isStopFollowing());
        assertEquals(2.0, s.getHorizontalMoveCost());
        assertEquals(4.0, s.getVerticalMoveCost());
    }

    @Test
    void eachAvoidanceFlagIsIndependent() {
        Map<TuningKey, Material> materials = Map.ofEntries(
                Map.entry(TuningKey.PATH_AVOID_LAVA, Material.LAVA),
                Map.entry(TuningKey.PATH_AVOID_MAGMA, Material.MAGMA_BLOCK),
                Map.entry(TuningKey.PATH_AVOID_FIRE, Material.FIRE),
                Map.entry(TuningKey.PATH_AVOID_SOUL_FIRE, Material.SOUL_FIRE),
                Map.entry(TuningKey.PATH_AVOID_CACTUS, Material.CACTUS),
                Map.entry(TuningKey.PATH_AVOID_BERRY, Material.SWEET_BERRY_BUSH),
                Map.entry(TuningKey.PATH_AVOID_WITHER_ROSE, Material.WITHER_ROSE),
                Map.entry(TuningKey.PATH_AVOID_COBWEB, Material.COBWEB),
                Map.entry(TuningKey.PATH_AVOID_SCULK, Material.SCULK_SHRIEKER),
                Map.entry(TuningKey.PATH_AVOID_RAIL, Material.RAIL),
                Map.entry(TuningKey.PATH_AVOID_POWERED_RAIL, Material.POWERED_RAIL),
                Map.entry(TuningKey.PATH_AVOID_DETECTOR_RAIL, Material.DETECTOR_RAIL),
                Map.entry(TuningKey.PATH_AVOID_ACTIVATOR_RAIL, Material.ACTIVATOR_RAIL));
        for (Map.Entry<TuningKey, Material> entry : materials.entrySet()) {
            PathSettings s = settings(Map.of(entry.getKey(), false));
            assertTrue(s.isNotAvoided(entry.getValue()), entry.getKey().key());
            assertEquals(entry.getValue().equals(Material.LAVA), s.isNotAvoided(Material.LAVA));
            assertEquals(entry.getValue().equals(Material.COBWEB), s.isNotAvoided(Material.COBWEB));
        }
    }

    @Test
    void swimPolicyRecomputesAndCopies() {
        PathSettings original = new PathSettings(profile());
        PathSettings changed = settings(Map.of(
                TuningKey.PATH_SWIM_BASE, 4.0,
                TuningKey.PATH_SWIM_EFFICIENCY_FACTOR, 0.5,
                TuningKey.PATH_SWIM_GRACE_FACTOR, 0.25,
                TuningKey.PATH_SWIM_MINIMUM, 0.2));
        changed.calculateSwimCost(1.0, true);
        assertEquals(0.5, changed.getSwimCostMultiplier(), 1e-9);
        changed.calculateSwimCost(2.0, true);
        assertEquals(0.2, changed.getSwimCostMultiplier(), 1e-9);
        changed.calculateSwimCost(0.0, false);
        assertEquals(4.0, changed.getSwimCostMultiplier(), 1e-9);
        original.copyFrom(changed);
        original.calculateSwimCost(1.0, true);
        assertEquals(0.5, original.getSwimCostMultiplier(), 1e-9);
    }

    @Test
    void invalidProfilesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PresetTuning.profile(
                        rootWith(Map.of(TuningKey.PATH_MOVE_TYPE, 3)), Difficulty.MEDIUM.level()));
        assertThrows(IllegalArgumentException.class,
                () -> PresetTuning.profile(rootWith(Map.of(TuningKey.PATH_MAX_HORIZONTAL_DISTANCE, 0.0)),
                        Difficulty.MEDIUM.level()));
        assertThrows(IllegalArgumentException.class,
                () -> PresetTuning.profile(
                        rootWith(Map.of(TuningKey.PATH_SWIM_BASE, 0.0)), Difficulty.MEDIUM.level()));
    }

    private static Material[] avoidedMaterials() {
        return new Material[] {
            Material.LAVA, Material.MAGMA_BLOCK, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.COBWEB, Material.SCULK_SHRIEKER,
            Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL
        };
    }
}
