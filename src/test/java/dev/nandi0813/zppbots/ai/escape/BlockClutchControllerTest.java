package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class BlockClutchControllerTest {

    @Test
    void clutchFaceOnlyForSideBySideCellAtSupportHeight() {
        BlockPos support = new BlockPos(0, 64, 0);
        assertEquals(Direction.EAST, BlockClutchController.clutchFace(support, new BlockPos(1, 64, 0)));
        assertEquals(Direction.NORTH, BlockClutchController.clutchFace(support, new BlockPos(0, 64, -1)));
        assertNull(BlockClutchController.clutchFace(support, new BlockPos(1, 64, 1)));
        assertNull(BlockClutchController.clutchFace(support, new BlockPos(0, 63, 0)));
        assertNull(BlockClutchController.clutchFace(support, new BlockPos(0, 64, 0)));
    }
}
