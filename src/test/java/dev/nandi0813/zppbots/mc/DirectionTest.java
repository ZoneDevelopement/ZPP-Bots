package dev.nandi0813.zppbots.mc;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DirectionTest {

    @Test
    void oppositeIsAnInvolution() {
        for (Direction direction : Direction.values()) {
            assertEquals(direction, direction.getOpposite().getOpposite());
        }
        assertEquals(Direction.SOUTH, Direction.NORTH.getOpposite());
        assertEquals(Direction.EAST, Direction.WEST.getOpposite());
    }

    @Test
    void clockwiseCyclesBackToStart() {
        Direction direction = Direction.NORTH;
        for (Direction expected : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH}) {
            direction = direction.getClockWise();
            assertEquals(expected, direction);
        }
        for (Direction d : Direction.HORIZONTAL) {
            assertEquals(d, d.getClockWise().getCounterClockWise());
        }
    }

    @Test
    void verticalDirectionsHaveNoRotation() {
        assertThrows(IllegalStateException.class, () -> Direction.UP.getClockWise());
        assertThrows(IllegalStateException.class, () -> Direction.DOWN.getCounterClockWise());
    }

    @Test
    void fromYRotUsesMojangRounding() {
        assertEquals(Direction.SOUTH, Direction.fromYRot(0));
        assertEquals(Direction.WEST, Direction.fromYRot(90));
        assertEquals(Direction.NORTH, Direction.fromYRot(180));
        assertEquals(Direction.EAST, Direction.fromYRot(-90));
        assertEquals(Direction.SOUTH, Direction.fromYRot(44));
        assertEquals(Direction.WEST, Direction.fromYRot(46));
    }

    @Test
    void toYRotRoundTripsForHorizontals() {
        for (Direction direction : Direction.HORIZONTAL) {
            assertEquals(direction, Direction.fromYRot(direction.toYRot()));
        }
    }

    @Test
    void getNearestPicksLargestComponent() {
        assertEquals(Direction.UP, Direction.getNearest(0.2, 0.9, 0.1));
        assertEquals(Direction.WEST, Direction.getNearest(-1.0, 0.0, 0.0));
        assertEquals(Direction.NORTH, Direction.getNearest(0.0, 0.0, 0.0));
    }

    @Test
    void blockFaceRoundTripsForCardinalFaces() {
        for (Direction direction : Direction.values()) {
            assertEquals(direction, Direction.fromBlockFace(direction.toBlockFace()));
        }
        assertThrows(IllegalArgumentException.class, () -> Direction.fromBlockFace(BlockFace.NORTH_EAST));
    }
}
