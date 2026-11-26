package dev.nandi0813.zppbots.path;

import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Flat stone floor at y=63 (feet at y=64) over a 40x40 area, plus extra solid blocks. */
class PathFinderTest {

    private final Set<Long> solid = new HashSet<>();

    private final PathTerrain terrain = new PathTerrain() {
        @Override
        public boolean loaded(int x, int y, int z) {
            return x >= -20 && x < 20 && z >= -20 && z < 20;
        }

        @Override
        public Material type(int x, int y, int z) {
            return solid(x, y, z) ? Material.STONE : Material.AIR;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return y <= 63 || solid.contains(BlockPos.asLong(x, y, z));
        }

        @Override
        public boolean water(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean dragDown(int x, int y, int z) {
            return false;
        }
    };

    private List<BlockPos> find(BlockPos start, Vec3 target) {
        try (BlockProbe probe = BlockProbe.open(terrain, new PathSettings(
                PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level())))) {
            return PathFinder.findPath(probe, start, target, 1, 3, 50000);
        }
    }

    @Test
    void straightPath() {
        List<BlockPos> path = find(new BlockPos(0, 64, 0), new Vec3(8.5, 64, 0.5));
        assertNotNull(path);
        assertEquals(new BlockPos(0, 64, 0), path.getFirst());
        assertTrue(path.getLast().getX() >= 7);
        assertEquals(64, path.getLast().getY());
    }

    @Test
    void pathAroundWall() {
        for (int z = -5; z <= 5; z++) {
            for (int y = 64; y <= 66; y++) solid.add(BlockPos.asLong(4, y, z));
        }
        List<BlockPos> path = find(new BlockPos(0, 64, 0), new Vec3(8.5, 64, 0.5));
        assertNotNull(path);
        assertTrue(path.getLast().getX() >= 7);
        assertTrue(path.stream().anyMatch(p -> Math.abs(p.getZ()) > 5), "must detour past the wall end");
        assertTrue(path.stream().noneMatch(p -> p.getX() == 4 && p.getY() < 67 && Math.abs(p.getZ()) <= 5));
    }

    @Test
    void pathAroundThreeByThreeWallAtRuntimeBudget() {
        for (int z = -1; z <= 1; z++) for (int y = 64; y <= 66; y++) solid.add(BlockPos.asLong(0, y, z));
        try (BlockProbe probe = BlockProbe.open(terrain, new PathSettings(
                PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level())))) {
            List<BlockPos> path = PathFinder.findPath(probe, new BlockPos(-5, 64, 0), new Vec3(4.5, 64, 0.5), 1, 3, 1500);
            assertNotNull(path);
            assertTrue(path.getLast().getX() >= 3);
            assertTrue(path.stream().anyMatch(p -> Math.abs(p.getZ()) >= 2));
            assertTrue(path.stream().noneMatch(p -> p.getX() == 0 && p.getY() < 67 && Math.abs(p.getZ()) <= 1));
        }
    }

    @Test
    void unloadedGoalHasNoPath() {
        assertNull(find(new BlockPos(0, 64, 0), new Vec3(100.5, 64, 0.5)));
    }

    @Test
    void closestHDistIsZeroInsideBlock() {
        assertEquals(0.0, PathFinder.closestHDistToBlock(new BlockPos(2, 64, 2), new Vec3(2.5, 64, 2.5)), 1e-9);
        assertEquals(1.0, PathFinder.closestHDistToBlock(new BlockPos(2, 64, 2), new Vec3(4.0, 64, 2.5)), 1e-9);
    }
}
