package dev.nandi0813.zppbots.mc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Mojang {@code core.BlockPos}: immutable block coordinates with Mojang's long packing. */
public record BlockPos(int x, int y, int z) {

    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    private static final int PACKED_X_LENGTH = 26;
    private static final int PACKED_Z_LENGTH = 26;
    private static final int PACKED_Y_LENGTH = 12;
    private static final long PACKED_X_MASK = (1L << PACKED_X_LENGTH) - 1L;
    private static final long PACKED_Z_MASK = (1L << PACKED_Z_LENGTH) - 1L;
    private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
    private static final int Z_OFFSET = PACKED_Y_LENGTH;
    private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    public static BlockPos containing(Vec3 vec) {
        return containing(vec.x(), vec.y(), vec.z());
    }

    public static BlockPos of(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPos of(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos of(long packed) {
        int x = (int) (packed >> X_OFFSET);
        int y = (int) ((packed << (64 - Z_OFFSET)) >> (64 - Z_OFFSET));
        int z = (int) ((packed << (64 - X_OFFSET)) >> (64 - PACKED_Z_LENGTH));
        return new BlockPos(x, y, z);
    }

    public static long asLong(int x, int y, int z) {
        return ((long) x & PACKED_X_MASK) << X_OFFSET
                | ((long) z & PACKED_Z_MASK) << Z_OFFSET
                | ((long) y & PACKED_Y_MASK);
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos a, BlockPos b) {
        int minX = Math.min(a.x, b.x), maxX = Math.max(a.x, b.x);
        int minY = Math.min(a.y, b.y), maxY = Math.max(a.y, b.y);
        int minZ = Math.min(a.z, b.z), maxZ = Math.max(a.z, b.z);
        List<BlockPos> positions = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockPos above() {
        return above(1);
    }

    public BlockPos above(int n) {
        return offset(0, n, 0);
    }

    public BlockPos below() {
        return below(1);
    }

    public BlockPos below(int n) {
        return offset(0, -n, 0);
    }

    public BlockPos north() {
        return north(1);
    }

    public BlockPos north(int n) {
        return offset(0, 0, -n);
    }

    public BlockPos south() {
        return south(1);
    }

    public BlockPos south(int n) {
        return offset(0, 0, n);
    }

    public BlockPos east() {
        return east(1);
    }

    public BlockPos east(int n) {
        return offset(n, 0, 0);
    }

    public BlockPos west() {
        return west(1);
    }

    public BlockPos west(int n) {
        return offset(-n, 0, 0);
    }

    public BlockPos relative(Direction direction) {
        return relative(direction, 1);
    }

    public BlockPos relative(Direction direction, int n) {
        return offset(direction.getStepX() * n, direction.getStepY() * n, direction.getStepZ() * n);
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos offset(BlockPos other) {
        return offset(other.x, other.y, other.z);
    }

    public BlockPos subtract(BlockPos other) {
        return new BlockPos(x - other.x, y - other.y, z - other.z);
    }

    public BlockPos multiply(int factor) {
        return new BlockPos(x * factor, y * factor, z * factor);
    }

    public BlockPos atY(int newY) {
        return new BlockPos(x, newY, z);
    }

    public BlockPos immutable() {
        return this;
    }

    public long asLong() {
        return asLong(x, y, z);
    }

    public double distSqr(BlockPos other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distToCenterSqr(Vec3 vec) {
        return distToCenterSqr(vec.x(), vec.y(), vec.z());
    }

    public double distToCenterSqr(double ox, double oy, double oz) {
        double dx = x + 0.5 - ox;
        double dy = y + 0.5 - oy;
        double dz = z + 0.5 - oz;
        return dx * dx + dy * dy + dz * dz;
    }

    public int distManhattan(BlockPos other) {
        return Math.abs(other.x - x) + Math.abs(other.y - y) + Math.abs(other.z - z);
    }

    public boolean closerThan(BlockPos other, double distance) {
        return distSqr(other) < distance * distance;
    }

    public Vec3 getCenter() {
        return Vec3.atCenterOf(this);
    }

    public Vec3 getBottomCenter() {
        return Vec3.atBottomCenterOf(this);
    }

    public Block block(World world) {
        return world.getBlockAt(x, y, z);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }
}
