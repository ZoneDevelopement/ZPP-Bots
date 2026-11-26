package dev.nandi0813.zppbots.path;

import dev.nandi0813.zppbots.brain.BotWorld;
import dev.nandi0813.zppbots.mc.BlockPos;
import org.bukkit.Material;
import org.bukkit.block.data.type.BubbleColumn;

/** The raw block facts {@link BlockProbe} derives its answers from; lets tests feed a fake world. */
interface PathTerrain {

    boolean loaded(int x, int y, int z);

    Material type(int x, int y, int z);

    /** Has a non-empty collision shape. */
    boolean solid(int x, int y, int z);

    /** Water fluid or bubble column. */
    boolean water(int x, int y, int z);

    boolean dragDown(int x, int y, int z);

    static PathTerrain of(BotWorld world) {
        return new PathTerrain() {
            @Override
            public boolean loaded(int x, int y, int z) {
                return world.isLoaded(new BlockPos(x, y, z));
            }

            @Override
            public Material type(int x, int y, int z) {
                return world.type(new BlockPos(x, y, z));
            }

            @Override
            public boolean solid(int x, int y, int z) {
                return world.blocksMotion(new BlockPos(x, y, z));
            }

            @Override
            public boolean water(int x, int y, int z) {
                return world.isWater(new BlockPos(x, y, z));
            }

            @Override
            public boolean dragDown(int x, int y, int z) {
                return world.data(new BlockPos(x, y, z)) instanceof BubbleColumn column && column.isDrag();
            }
        };
    }
}
