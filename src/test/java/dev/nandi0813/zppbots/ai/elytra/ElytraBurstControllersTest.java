package dev.nandi0813.zppbots.ai.elytra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElytraBurstControllersTest {

    @Test
    void approachAngleLimitsStepAndTakesShortWayAround() {
        assertEquals(12.0F, WindBurstElytraController.approachAngle(0.0F, 90.0F, 12.0F), 1.0E-4F);
        assertEquals(-12.0F, WindBurstElytraController.approachAngle(0.0F, -90.0F, 12.0F), 1.0E-4F);
        assertEquals(185.0F, WindBurstElytraController.approachAngle(170.0F, -170.0F, 15.0F), 1.0E-4F);
        assertEquals(5.0F, WindBurstElytraController.approachAngle(0.0F, 5.0F, 12.0F), 1.0E-4F);
    }

    @Test
    void rocketlessSmashForwardScalesWithDistance() {
        assertEquals(0.0F, RocketlessElytraController.smashForward(0.3));
        assertEquals(1.0F, RocketlessElytraController.smashForward(5.0), 1.0E-4F);
        assertEquals(0.5F * 0.6F / 0.42F, RocketlessElytraController.smashForward(0.6), 1.0E-4F);
    }
}
