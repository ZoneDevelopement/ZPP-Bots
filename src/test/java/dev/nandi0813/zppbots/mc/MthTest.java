package dev.nandi0813.zppbots.mc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MthTest {

    @Test
    void wrapDegreesStaysInHalfOpenRange() {
        assertEquals(-170, Mth.wrapDegrees(190));
        assertEquals(-180, Mth.wrapDegrees(-180));
        assertEquals(-180.0, Mth.wrapDegrees(540.0), 0.0);
        assertEquals(-180.0f, Mth.wrapDegrees(540.0f), 0.0f);
    }

    @Test
    void floorAndCeilRoundTowardInfinity() {
        assertEquals(-1, Mth.floor(-0.5));
        assertEquals(-1, Mth.floor(-0.5f));
        assertEquals(0, Mth.ceil(-0.5));
        assertEquals(2, Mth.ceil(1.2f));
        assertEquals(-1L, Mth.lfloor(-0.5));
    }

    @Test
    void clampLimitsBothEnds() {
        assertEquals(3, Mth.clamp(5, 0, 3));
        assertEquals(0, Mth.clamp(-1, 0, 3));
        assertEquals(0L, Mth.clamp(-1L, 0L, 10L));
        assertEquals(1.0f, Mth.clamp(2.5f, 0.0f, 1.0f));
        assertEquals(1.0, Mth.clamp(2.0, 0.0, 1.0));
        assertEquals(0.5, Mth.clamp(0.5, 0.0, 1.0));
    }

    @Test
    void degreesDifferenceTakesShortestWay() {
        assertEquals(20.0f, Mth.degreesDifference(170.0f, -170.0f));
        assertEquals(20.0f, Mth.degreesDifferenceAbs(170.0f, -170.0f));
    }

    @Test
    void approachDegreesCrossesTheSeam() {
        assertEquals(175.0f, Mth.approachDegrees(170.0f, -170.0f, 5.0f));
        assertEquals(-175.0f, Mth.approachDegrees(-170.0f, 170.0f, 5.0f));
        assertEquals(3.0f, Mth.approach(0.0f, 10.0f, -3.0f));
    }
}
