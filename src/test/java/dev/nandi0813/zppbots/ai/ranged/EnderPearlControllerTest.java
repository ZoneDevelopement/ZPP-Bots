package dev.nandi0813.zppbots.ai.ranged;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class EnderPearlControllerTest {

    @Test
    void awayYawPointsAwayFromTheThreat() {
        assertEquals(0.0F, EnderPearlController.awayYaw(0.0, 5.0), 1.0E-3F);
        assertEquals(-90.0F, EnderPearlController.awayYaw(5.0, 0.0), 1.0E-3F);
    }

    @Test
    void awayYawFromOpponentOnPlusXPointsToMinusX() {
        assertEquals(90.0F, EnderPearlController.awayYaw(-5.0, 0.0), 1.0E-3F);
    }

    @Test
    void awayYawOnTopOfThreatFallsBackToPlusX() {
        assertEquals(-90.0F, EnderPearlController.awayYaw(0.0, 0.0), 1.0E-3F);
    }
}
