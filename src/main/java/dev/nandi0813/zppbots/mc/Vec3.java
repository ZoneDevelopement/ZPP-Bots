package dev.nandi0813.zppbots.mc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/** Mojang {@code world.phys.Vec3}: immutable double vector with the same method names. */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    public static Vec3 of(Location location) {
        return new Vec3(location.getX(), location.getY(), location.getZ());
    }

    public static Vec3 of(Vector vector) {
        return new Vec3(vector.getX(), vector.getY(), vector.getZ());
    }

    public static Vec3 atCenterOf(BlockPos pos) {
        return new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }

    public static Vec3 atBottomCenterOf(BlockPos pos) {
        return new Vec3(pos.x() + 0.5, pos.y(), pos.z() + 0.5);
    }

    public static Vec3 atLowerCornerOf(BlockPos pos) {
        return new Vec3(pos.x(), pos.y(), pos.z());
    }

    public static Vec3 directionFromRotation(float pitch, float yaw) {
        float f = Mth.cos(-yaw * Mth.DEG_TO_RAD - Mth.PI);
        float g = Mth.sin(-yaw * Mth.DEG_TO_RAD - Mth.PI);
        float h = -Mth.cos(-pitch * Mth.DEG_TO_RAD);
        float i = Mth.sin(-pitch * Mth.DEG_TO_RAD);
        return new Vec3(g * h, i, f * h);
    }

    public Vec3 add(Vec3 other) {
        return add(other.x, other.y, other.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 subtract(Vec3 other) {
        return subtract(other.x, other.y, other.z);
    }

    public Vec3 subtract(double dx, double dy, double dz) {
        return new Vec3(x - dx, y - dy, z - dz);
    }

    public Vec3 scale(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public Vec3 multiply(Vec3 other) {
        return multiply(other.x, other.y, other.z);
    }

    public Vec3 multiply(double mx, double my, double mz) {
        return new Vec3(x * mx, y * my, z * mz);
    }

    public Vec3 reverse() {
        return new Vec3(-x, -y, -z);
    }

    public Vec3 normalize() {
        double length = length();
        if (length < 1.0E-5) return ZERO;
        return scale(1.0 / length);
    }

    public double length() {
        return Math.sqrt(lengthSqr());
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    public double horizontalDistance() {
        return Math.sqrt(horizontalDistanceSqr());
    }

    public double horizontalDistanceSqr() {
        return x * x + z * z;
    }

    public double distanceTo(Vec3 other) {
        return Math.sqrt(distanceToSqr(other));
    }

    public double distanceToSqr(Vec3 other) {
        return distanceToSqr(other.x, other.y, other.z);
    }

    public double distanceToSqr(double ox, double oy, double oz) {
        double dx = ox - x;
        double dy = oy - y;
        double dz = oz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public Vec3 yRot(float radians) {
        float cos = Mth.cos(radians);
        float sin = Mth.sin(radians);
        return new Vec3(x * cos + z * sin, y, z * cos - x * sin);
    }

    public Vec3 xRot(float radians) {
        float cos = Mth.cos(radians);
        float sin = Mth.sin(radians);
        return new Vec3(x, y * cos + z * sin, z * cos - y * sin);
    }

    public Vec3 horizontal() {
        return new Vec3(x, 0.0, z);
    }

    public Vec3 with(Direction.Axis axis, double value) {
        return switch (axis) {
            case X -> new Vec3(value, y, z);
            case Y -> new Vec3(x, value, z);
            case Z -> new Vec3(x, y, value);
        };
    }

    public double get(Direction.Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public Vec3 relative(Direction direction, double amount) {
        return new Vec3(
                x + direction.getStepX() * amount,
                y + direction.getStepY() * amount,
                z + direction.getStepZ() * amount
        );
    }

    public Vec3 vectorTo(Vec3 other) {
        return other.subtract(this);
    }

    public boolean closerThan(Vec3 other, double distance) {
        return distanceToSqr(other) < distance * distance;
    }

    public Vec3 lerp(Vec3 to, double delta) {
        return new Vec3(Mth.lerp(delta, x, to.x), Mth.lerp(delta, y, to.y), Mth.lerp(delta, z, to.z));
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    public Location toLocation(World world, float yaw, float pitch) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
