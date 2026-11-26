package dev.nandi0813.zppbots.mc;

import org.bukkit.util.BoundingBox;

import java.util.Optional;

/** Mojang {@code world.phys.AABB}: axis-aligned box, min inclusive and max exclusive for {@link #contains}. */
public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public AABB {
        if (minX > maxX) {
            double t = minX;
            minX = maxX;
            maxX = t;
        }
        if (minY > maxY) {
            double t = minY;
            minY = maxY;
            maxY = t;
        }
        if (minZ > maxZ) {
            double t = minZ;
            minZ = maxZ;
            maxZ = t;
        }
    }

    public AABB(Vec3 a, Vec3 b) {
        this(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }

    public static AABB ofSize(Vec3 center, double xs, double ys, double zs) {
        return new AABB(
                center.x() - xs / 2.0, center.y() - ys / 2.0, center.z() - zs / 2.0,
                center.x() + xs / 2.0, center.y() + ys / 2.0, center.z() + zs / 2.0
        );
    }

    public static AABB of(BoundingBox box) {
        return new AABB(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    public static AABB unitCubeFromLowerCorner(Vec3 corner) {
        return new AABB(corner.x(), corner.y(), corner.z(), corner.x() + 1.0, corner.y() + 1.0, corner.z() + 1.0);
    }

    public static AABB ofBlock(BlockPos pos) {
        return unitCubeFromLowerCorner(Vec3.atLowerCornerOf(pos));
    }

    public BoundingBox toBoundingBox() {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public AABB inflate(double amount) {
        return inflate(amount, amount, amount);
    }

    public AABB inflate(double dx, double dy, double dz) {
        return new AABB(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public AABB deflate(double amount) {
        return inflate(-amount);
    }

    public AABB move(Vec3 offset) {
        return move(offset.x(), offset.y(), offset.z());
    }

    public AABB move(double dx, double dy, double dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public AABB expandTowards(Vec3 direction) {
        return expandTowards(direction.x(), direction.y(), direction.z());
    }

    public AABB expandTowards(double dx, double dy, double dz) {
        double x0 = minX, x1 = maxX, y0 = minY, y1 = maxY, z0 = minZ, z1 = maxZ;
        if (dx < 0.0) x0 += dx; else if (dx > 0.0) x1 += dx;
        if (dy < 0.0) y0 += dy; else if (dy > 0.0) y1 += dy;
        if (dz < 0.0) z0 += dz; else if (dz > 0.0) z1 += dz;
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    public boolean intersects(AABB other) {
        return intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean intersects(double ox0, double oy0, double oz0, double ox1, double oy1, double oz1) {
        return minX < ox1 && maxX > ox0
                && minY < oy1 && maxY > oy0
                && minZ < oz1 && maxZ > oz0;
    }

    public boolean intersects(Vec3 a, Vec3 b) {
        return intersects(
                Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())
        );
    }

    public boolean contains(Vec3 point) {
        return contains(point.x(), point.y(), point.z());
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX
                && y >= minY && y < maxY
                && z >= minZ && z < maxZ;
    }

    public Vec3 getCenter() {
        return new Vec3(minX + getXsize() / 2.0, minY + getYsize() / 2.0, minZ + getZsize() / 2.0);
    }

    public double getXsize() {
        return maxX - minX;
    }

    public double getYsize() {
        return maxY - minY;
    }

    public double getZsize() {
        return maxZ - minZ;
    }

    public double getSize() {
        return (getXsize() + getYsize() + getZsize()) / 3.0;
    }

    public double distanceToSqr(Vec3 point) {
        double dx = Math.max(Math.max(minX - point.x(), point.x() - maxX), 0.0);
        double dy = Math.max(Math.max(minY - point.y(), point.y() - maxY), 0.0);
        double dz = Math.max(Math.max(minZ - point.z(), point.z() - maxZ), 0.0);
        return dx * dx + dy * dy + dz * dz;
    }

    /** First point where the segment from {@code from} to {@code to} meets the box surface, or empty if it misses. */
    public Optional<Vec3> clip(Vec3 from, Vec3 to) {
        if (contains(from)) return Optional.of(from);
        double[] origin = {from.x(), from.y(), from.z()};
        double[] delta = {to.x() - from.x(), to.y() - from.y(), to.z() - from.z()};
        double[] low = {minX, minY, minZ};
        double[] high = {maxX, maxY, maxZ};
        double tEnter = 0.0;
        double tExit = 1.0;
        for (int i = 0; i < 3; i++) {
            if (delta[i] == 0.0) {
                if (origin[i] < low[i] || origin[i] > high[i]) return Optional.empty();
                continue;
            }
            double t1 = (low[i] - origin[i]) / delta[i];
            double t2 = (high[i] - origin[i]) / delta[i];
            tEnter = Math.max(tEnter, Math.min(t1, t2));
            tExit = Math.min(tExit, Math.max(t1, t2));
            if (tEnter > tExit) return Optional.empty();
        }
        return Optional.of(new Vec3(
                origin[0] + delta[0] * tEnter,
                origin[1] + delta[1] * tEnter,
                origin[2] + delta[2] * tEnter
        ));
    }

    public AABB minmax(AABB other) {
        return new AABB(
                Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ)
        );
    }

    /** Overlap of both boxes; disjoint boxes come back with their pairs reordered by the record constructor. */
    public AABB intersect(AABB other) {
        return new AABB(
                Math.max(minX, other.minX), Math.max(minY, other.minY), Math.max(minZ, other.minZ),
                Math.min(maxX, other.maxX), Math.min(maxY, other.maxY), Math.min(maxZ, other.maxZ)
        );
    }
}
