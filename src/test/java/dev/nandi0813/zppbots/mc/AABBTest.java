package dev.nandi0813.zppbots.mc;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class AABBTest {

    private static final double EPSILON = 1e-9;
    private final AABB unit = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    @Test
    void constructorOrdersMinAndMax() {
        AABB box = new AABB(new Vec3(2.0, 2.0, 2.0), new Vec3(0.0, 0.0, 0.0));
        assertEquals(new AABB(0.0, 0.0, 0.0, 2.0, 2.0, 2.0), box);
        AABB reversed = new AABB(2.0, 2.0, 2.0, 0.0, 0.0, 0.0);
        assertEquals(0.0, reversed.minX());
        assertEquals(2.0, reversed.maxX());
    }

    @Test
    void containsIsMinInclusiveMaxExclusive() {
        assertTrue(unit.contains(0.0, 0.0, 0.0));
        assertTrue(unit.contains(0.5, 0.5, 0.5));
        assertFalse(unit.contains(1.0, 0.5, 0.5));
        assertFalse(unit.contains(new Vec3(0.5, 1.0, 0.5)));
    }

    @Test
    void clipFromOutsideHitsNearFace() {
        Optional<Vec3> hit = unit.clip(new Vec3(-1.0, 0.5, 0.5), new Vec3(2.0, 0.5, 0.5));
        assertTrue(hit.isPresent());
        assertEquals(0.0, hit.get().x(), EPSILON);
        assertEquals(0.5, hit.get().y(), EPSILON);
        assertEquals(0.5, hit.get().z(), EPSILON);
    }

    @Test
    void clipThatMissesIsEmpty() {
        assertTrue(unit.clip(new Vec3(-1.0, 5.0, 0.5), new Vec3(2.0, 5.0, 0.5)).isEmpty());
    }

    @Test
    void clipFromInsideReturnsOrigin() {
        Vec3 inside = new Vec3(0.5, 0.5, 0.5);
        assertEquals(Optional.of(inside), unit.clip(inside, new Vec3(3.0, 0.5, 0.5)));
    }

    @Test
    void distanceToSqrIsZeroInsideAndSquaredOutside() {
        assertEquals(0.0, unit.distanceToSqr(new Vec3(0.5, 0.5, 0.5)), EPSILON);
        assertEquals(1.0, unit.distanceToSqr(new Vec3(2.0, 0.5, 0.5)), EPSILON);
        assertEquals(2.0, unit.distanceToSqr(new Vec3(2.0, 2.0, 0.5)), EPSILON);
    }

    @Test
    void intersectsSegmentSpanBox() {
        assertTrue(unit.intersects(new Vec3(-1.0, 0.5, 0.5), new Vec3(2.0, 0.5, 0.5)));
        assertFalse(unit.intersects(new Vec3(2.0, 2.0, 2.0), new Vec3(3.0, 3.0, 3.0)));
    }
}
