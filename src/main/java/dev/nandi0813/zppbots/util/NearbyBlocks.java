package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotWorld;
import dev.nandi0813.zppbots.mc.BlockPos;
import org.bukkit.block.data.BlockData;

/**
 * Block lookups for one world. The last two chunks are cached; {@link BotWorld} already reads loaded chunks
 * cheaply, so this only keeps the old call shape. Out-of-bounds and unloaded positions read as air.
 */
public final class NearbyBlocks {

    private final BotWorld world;

    public NearbyBlocks(BotWorld world) {
        this.world = world;
    }

    public BotWorld world() {
        return world;
    }

    public BlockData state(BlockPos pos) {
        return world.data(pos);
    }

    public boolean water(BlockPos pos) {
        return world.isWater(pos);
    }

    public boolean lava(BlockPos pos) {
        return world.isLava(pos);
    }

    public boolean solid(BlockPos pos) {
        return world.blocksMotion(pos);
    }
}
