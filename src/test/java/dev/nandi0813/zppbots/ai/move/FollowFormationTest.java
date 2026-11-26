package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.mc.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FollowFormationTest {

    @Test
    void namesParseWithAliases() {
        assertEquals(FollowFormation.Type.WALL, FollowFormation.fromString("Phalanx"));
        assertEquals(FollowFormation.Type.CIRCLE, FollowFormation.fromString("ring"));
        assertEquals(FollowFormation.Type.NONE, FollowFormation.fromString("nonsense"));
        assertEquals(FollowFormation.Type.NONE, FollowFormation.fromString(null));
    }

    @Test
    void columnTrailsTheLeaderBySpacing() {
        double[] second = FollowFormation.localOffset(FollowFormation.Type.COLUMN, 1, 3, 3);
        assertArrayEquals(new double[] {4.0, 0.0}, second, 1e-9);
    }

    @Test
    void lineIsCentredAcrossTheLeader() {
        double[] first = FollowFormation.localOffset(FollowFormation.Type.LINE, 0, 3, 3);
        double[] last = FollowFormation.localOffset(FollowFormation.Type.LINE, 2, 3, 3);
        assertEquals(-last[1], first[1], 1e-9);
    }

    @Test
    void hexagonKeepsTheCentreFreeForTheLeaderShift() {
        assertTrue(FollowFormation.reservesCentre(FollowFormation.Type.HEXAGON));
        Vec3 leader = new Vec3(10, 64, 10);
        Vec3 shifted = FollowFormation.slotWorld(FollowFormation.Type.HEXAGON, 0, 1, 3, leader, 0f, true);
        assertNotEquals(leader, shifted);
        assertEquals(leader, FollowFormation.slotWorld(FollowFormation.Type.HEXAGON, 0, 1, 3, leader, 0f, false));
    }

    @Test
    void slotsAreBehindTheLeaderFacingYawZero() {
        Vec3 leader = new Vec3(0, 64, 0);
        Vec3 slot = FollowFormation.slotWorld(FollowFormation.Type.COLUMN, 0, 1, 3, leader, 0f);
        assertEquals(-2.0, slot.z(), 1e-9);
        assertEquals(0.0, slot.x(), 1e-9);
    }
}
