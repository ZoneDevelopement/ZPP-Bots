package dev.nandi0813.zppbots.ai.ranged;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PearlAimTest {

    @Test
    void levelFlatShotLandsPastAimWhenAimedHigh() {
        double dist = 20.0;
        double high = PearlAim.missAt(10.0, dist, 0.0, 0.0, 0.0, 0.0);
        double low = PearlAim.missAt(-30.0, dist, 0.0, 0.0, 0.0, 0.0);
        assertTrue(high < low, "a lower pitch must arrive higher");
    }

    @Test
    void refineFindsPitchWhereTheShotMeetsTheAim() {
        double dist = 18.0;
        double pitch = PearlAim.refine(-30.0, 30.0, dist, 0.0, 0.0, 0.0, 0.0);
        assertEquals(0.0, PearlAim.missAt(pitch, dist, 0.0, 0.0, 0.0, 0.0), 0.01);
    }

    @Test
    void impossibleSidewaysMotionHasNoSolution() {
        assertTrue(Double.isNaN(PearlAim.missAt(80.0, 10.0, 0.0, 5.0, 0.0, 0.0)));
    }
}
