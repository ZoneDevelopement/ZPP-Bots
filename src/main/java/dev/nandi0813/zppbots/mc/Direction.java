package dev.nandi0813.zppbots.mc;

import org.bukkit.block.BlockFace;

import java.util.List;

/** Mojang {@code core.Direction} with the same declaration order and rotation rules. */
public enum Direction {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    public static final List<Direction> HORIZONTAL = List.of(NORTH, EAST, SOUTH, WEST);

    private final int stepX;
    private final int stepY;
    private final int stepZ;

    Direction(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    public int getStepX() {
        return stepX;
    }

    public int getStepY() {
        return stepY;
    }

    public int getStepZ() {
        return stepZ;
    }

    public Direction getOpposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public Axis getAxis() {
        return switch (this) {
            case DOWN, UP -> Axis.Y;
            case NORTH, SOUTH -> Axis.Z;
            case WEST, EAST -> Axis.X;
        };
    }

    public boolean isHorizontal() {
        return getAxis().isHorizontal();
    }

    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> throw new IllegalStateException("No clockwise direction for " + this);
        };
    }

    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("No counter-clockwise direction for " + this);
        };
    }

    public Vec3 getUnitVec3() {
        return new Vec3(stepX, stepY, stepZ);
    }

    /** Mojang: SOUTH 0, WEST 90, NORTH 180, EAST 270; vertical directions 0. */
    public float toYRot() {
        return switch (this) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
    }

    public static Direction fromYRot(double yRot) {
        Direction[] byData = {SOUTH, WEST, NORTH, EAST};
        return byData[Mth.floor(yRot / 90.0 + 0.5) & 3];
    }

    public static Direction getNearest(double x, double y, double z) {
        Direction nearest = NORTH;
        double best = 0.0;
        for (Direction direction : values()) {
            double dot = x * direction.stepX + y * direction.stepY + z * direction.stepZ;
            if (dot > best) {
                best = dot;
                nearest = direction;
            }
        }
        return nearest;
    }

    public BlockFace toBlockFace() {
        return switch (this) {
            case DOWN -> BlockFace.DOWN;
            case UP -> BlockFace.UP;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
        };
    }

    public static Direction fromBlockFace(BlockFace face) {
        return switch (face) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> throw new IllegalArgumentException("Not a cardinal face: " + face);
        };
    }

    public enum Axis {
        X, Y, Z;

        public boolean isHorizontal() {
            return this != Y;
        }
    }
}
