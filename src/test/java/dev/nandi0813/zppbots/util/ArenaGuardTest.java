package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaGuardTest {

    private static final AABB BOX = new AABB(-24, 99, -24, 25, 110, 25);

    @Test
    void emptyBoxNeverBlocks() {
        assertFalse(ArenaGuard.outsideXZ(new AABB(0, 0, 0, 0, 0, 0), 1000, 1000, 0.75));
    }

    @Test
    void walkInset() {
        assertFalse(ArenaGuard.outsideXZ(BOX, 0, 0, 0.75));
        assertTrue(ArenaGuard.outsideXZ(BOX, 24.5, 0, 0.75));
        assertTrue(ArenaGuard.outsideXZ(BOX, -23.5, 0, 0.75));
        assertFalse(ArenaGuard.outsideXZ(BOX, 0, 24.0, 0.75));
    }

    @Test
    void pearlInset() {
        assertTrue(ArenaGuard.outsideXZ(BOX, 23.5, 0, 2.0));
    }

    @Test
    void outsideCoversSidesAndCeiling() {
        AABB arena = new AABB(0, 0, 0, 20, 10, 20);
        assertFalse(ArenaGuard.outside(arena, new Vec3(10, 5, 10), 1.5));
        assertTrue(ArenaGuard.outside(arena, new Vec3(19, 5, 10), 1.5));
        assertTrue(ArenaGuard.outside(arena, new Vec3(10, 9, 10), 1.5));
        assertFalse(ArenaGuard.outside(new AABB(0, 0, 0, 0, 0, 0), new Vec3(10, 5, 10), 1.5));
    }

    @Test
    void outsideDepthInsideIsZero() {
        assertEquals(0.0, ArenaGuard.outsideDepth(BOX, 0, 0, 0.75));
        assertEquals(0.0, ArenaGuard.outsideDepth(new AABB(0, 0, 0, 0, 0, 0), 1000, 1000, 0.75));
    }

    @Test
    void outsideDepthGrowsOutward() {
        assertEquals(25.08 - 24.25, ArenaGuard.outsideDepth(BOX, 25.08, 8, 0.75), 1e-9);
        assertTrue(ArenaGuard.outsideDepth(BOX, 25.08, 9, 0.75) >= ArenaGuard.outsideDepth(BOX, 25.08, 8, 0.75));
        assertTrue(ArenaGuard.outsideDepth(BOX, 24.08, 8, 0.75) < ArenaGuard.outsideDepth(BOX, 25.08, 8, 0.75));
    }
}
