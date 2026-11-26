package dev.nandi0813.zppbots.path;

import dev.nandi0813.zppbots.brain.BotWorld;
import dev.nandi0813.zppbots.mc.BlockPos;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/** Per-search cache of the block facts the path moves ask for. Unloaded positions are neither walkable nor passable. */
public final class BlockProbe implements AutoCloseable {

    private static final int LOADED = 1;
    private static final int SOLID = 2;
    private static final int WATER = 4;
    private static final int BUBBLE = 8;
    private static final int DRAG_DOWN = 16;
    private static final int NOT_AVOIDED = 32;

    private final Map<Long, Integer> cache = new HashMap<>();
    private final PathTerrain terrain;
    private final PathSettings settings;

    private BlockProbe(PathTerrain terrain, PathSettings settings) {
        this.terrain = terrain;
        this.settings = settings;
    }

    public static BlockProbe open(BotWorld world, PathSettings settings) {
        return new BlockProbe(PathTerrain.of(world), settings);
    }

    static BlockProbe open(PathTerrain terrain, PathSettings settings) {
        return new BlockProbe(terrain, settings);
    }

    @Override
    public void close() {
        cache.clear();
    }

    public PathSettings settings() {
        return settings;
    }

    private int flags(int x, int y, int z) {
        return cache.computeIfAbsent(BlockPos.asLong(x, y, z), key -> compute(x, y, z));
    }

    private int compute(int x, int y, int z) {
        int f = 0;
        if (!terrain.loaded(x, y, z)) return f;
        f |= LOADED;
        Material type = terrain.type(x, y, z);
        if (terrain.solid(x, y, z)) f |= SOLID;
        if (settings.isNotAvoided(type)) f |= NOT_AVOIDED;
        if (terrain.water(x, y, z)) f |= WATER;
        if (type == Material.BUBBLE_COLUMN) {
            f |= BUBBLE;
            if (terrain.dragDown(x, y, z)) f |= DRAG_DOWN;
        }
        return f;
    }

    public Material type(int x, int y, int z) {
        return terrain.type(x, y, z);
    }

    public boolean isPassable(int x, int y, int z) {
        return (flags(x, y, z) & (LOADED | SOLID | NOT_AVOIDED)) == (LOADED | NOT_AVOIDED);
    }

    public boolean canWalkOn(int x, int y, int z) {
        return (flags(x, y, z) & (LOADED | SOLID | NOT_AVOIDED)) == (LOADED | SOLID | NOT_AVOIDED);
    }

    public boolean isWalkable(int x, int y, int z) {
        return isPassable(x, y, z) && isPassable(x, y + 1, z) && canWalkOn(x, y - 1, z);
    }

    public boolean isSolid(int x, int y, int z) {
        return (flags(x, y, z) & (LOADED | SOLID)) == (LOADED | SOLID);
    }

    public boolean isWater(int x, int y, int z) {
        return (flags(x, y, z) & WATER) != 0;
    }

    public boolean isBubbleColumn(int x, int y, int z) {
        return (flags(x, y, z) & BUBBLE) != 0;
    }

    public boolean isBubbleColumnUp(int x, int y, int z) {
        return (flags(x, y, z) & (BUBBLE | DRAG_DOWN)) == BUBBLE;
    }

    public boolean isBubbleColumnDown(int x, int y, int z) {
        return (flags(x, y, z) & (BUBBLE | DRAG_DOWN)) == (BUBBLE | DRAG_DOWN);
    }

    public boolean canSwimThrough(int x, int y, int z) {
        return (flags(x, y, z) & (LOADED | SOLID | WATER)) == (LOADED | WATER);
    }

    public int getBubbleColumnHeight(int x, int y, int z, boolean goingUp) {
        int count = 0;
        int dy = goingUp ? 1 : -1;
        for (int cy = y; isBubbleColumn(x, cy, z); cy += dy) {
            count++;
        }
        return count;
    }
}
