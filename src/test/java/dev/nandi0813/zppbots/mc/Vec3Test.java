package dev.nandi0813.zppbots.mc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class Vec3Test {

    private static final double EPSILON = 1e-6;

    @Test
    void normalizeOfTinyVectorIsZero() {
        assertEquals(Vec3.ZERO, new Vec3(1e-6, 0.0, 0.0).normalize());
    }

    @Test
    void normalizeScalesToUnitLength() {
        Vec3 normalized = new Vec3(3.0, 0.0, 4.0).normalize();
        assertEquals(1.0, normalized.length(), EPSILON);
        assertEquals(0.6, normalized.x(), EPSILON);
    }

    @Test
    void crossAndDot() {
        assertEquals(new Vec3(0.0, 0.0, 1.0), new Vec3(1.0, 0.0, 0.0).cross(new Vec3(0.0, 1.0, 0.0)));
        assertEquals(32.0, new Vec3(1.0, 2.0, 3.0).dot(new Vec3(4.0, 5.0, 6.0)), EPSILON);
    }

    @Test
    void directionFromRotationMatchesMojang() {
        assertVec(new Vec3(0.0, 0.0, 1.0), Vec3.directionFromRotation(0.0f, 0.0f));
        assertVec(new Vec3(-1.0, 0.0, 0.0), Vec3.directionFromRotation(0.0f, 90.0f));
        assertVec(new Vec3(0.0, -1.0, 0.0), Vec3.directionFromRotation(90.0f, 0.0f));
    }

    @Test
    void horizontalDistanceIgnoresY() {
        assertEquals(5.0, new Vec3(3.0, 100.0, 4.0).horizontalDistance(), EPSILON);
        assertEquals(25.0, new Vec3(3.0, 100.0, 4.0).horizontalDistanceSqr(), EPSILON);
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }
}
