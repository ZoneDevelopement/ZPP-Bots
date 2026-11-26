package dev.nandi0813.zppbots.mc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class BlockPosTest {

    @Test
    void containingFloorsEachCoordinate() {
        assertEquals(new BlockPos(-1, 64, 3), BlockPos.containing(-0.5, 64.9, 3.2));
    }

    @Test
    void packedLongRoundTrips() {
        BlockPos extreme = new BlockPos(-30000000, -64, 29999999);
        assertEquals(extreme, BlockPos.of(extreme.asLong()));
        BlockPos high = new BlockPos(5, 319, -5);
        assertEquals(high, BlockPos.of(high.asLong()));
        assertEquals(high.asLong(), BlockPos.asLong(5, 319, -5));
    }

    @Test
    void relativeAndCardinalOffsets() {
        BlockPos origin = new BlockPos(0, 0, 0);
        assertEquals(new BlockPos(0, 0, -1), origin.north());
        assertEquals(new BlockPos(0, 0, 1), origin.south());
        assertEquals(new BlockPos(1, 0, 0), origin.east());
        assertEquals(new BlockPos(-2, 0, 0), origin.west(2));
        assertEquals(new BlockPos(0, 1, 0), origin.relative(Direction.UP));
        assertEquals(origin.east(), origin.relative(Direction.EAST));
    }

    @Test
    void betweenClosedYieldsEveryCorner() {
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1))) {
            positions.add(pos);
        }
        assertEquals(8, positions.size());
    }
}
