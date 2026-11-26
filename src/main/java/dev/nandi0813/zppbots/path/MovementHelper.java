package dev.nandi0813.zppbots.path;

import dev.nandi0813.zppbots.brain.BotWorld;
import dev.nandi0813.zppbots.mc.BlockPos;
import org.bukkit.Material;
import org.bukkit.block.data.type.BubbleColumn;

public final class MovementHelper {

    private MovementHelper() {
    }

    public static boolean isPassable(BotWorld world, int x, int y, int z, PathSettings settings) {
        BlockPos pos = new BlockPos(x, y, z);
        return world.isLoaded(pos) && !world.blocksMotion(pos) && settings.isNotAvoided(world.type(pos));
    }

    public static boolean canWalkOn(BotWorld world, int x, int y, int z, PathSettings settings) {
        BlockPos pos = new BlockPos(x, y, z);
        return world.isLoaded(pos) && world.blocksMotion(pos) && settings.isNotAvoided(world.type(pos));
    }

    public static boolean isWalkable(BotWorld world, int x, int y, int z, PathSettings settings) {
        return isPassable(world, x, y, z, settings) && isPassable(world, x, y + 1, z, settings)
                && canWalkOn(world, x, y - 1, z, settings);
    }

    public static boolean isWater(BotWorld world, int x, int y, int z) {
        return world.isWater(new BlockPos(x, y, z));
    }

    public static boolean isBubbleColumn(BotWorld world, int x, int y, int z) {
        return world.type(new BlockPos(x, y, z)) == Material.BUBBLE_COLUMN;
    }

    public static boolean isBubbleColumnUp(BotWorld world, int x, int y, int z) {
        return isBubbleColumn(world, x, y, z) && !isDrag(world, x, y, z);
    }

    public static boolean isBubbleColumnDown(BotWorld world, int x, int y, int z) {
        return isBubbleColumn(world, x, y, z) && isDrag(world, x, y, z);
    }

    public static boolean canSwimThrough(BotWorld world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return world.isLoaded(pos) && !world.blocksMotion(pos) && world.isWater(pos);
    }

    public static int getBubbleColumnHeight(BotWorld world, int x, int y, int z, boolean goingUp) {
        int count = 0;
        int dy = goingUp ? 1 : -1;
        for (int cy = y; isBubbleColumn(world, x, cy, z); cy += dy) {
            count++;
        }
        return count;
    }

    private static boolean isDrag(BotWorld world, int x, int y, int z) {
        return world.data(new BlockPos(x, y, z)) instanceof BubbleColumn column && column.isDrag();
    }
}
