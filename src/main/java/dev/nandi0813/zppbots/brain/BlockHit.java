package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Vec3;

/** Result of a block ray; {@link #MISS} when nothing was hit. */
public record BlockHit(boolean hit, BlockPos pos, Direction face, Vec3 location) {

    public static final BlockHit MISS = new BlockHit(false, null, null, null);
}
