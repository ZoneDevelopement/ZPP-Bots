package dev.nandi0813.zppbots.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BotMathTest {

    @Test
    void anglesLookAlongAxes() {
        assertArrayEquals(new float[] {0F, 0F}, BotMath.getAngles(0, 0, 0, 0, 0, 5), 1e-4F);
        assertArrayEquals(new float[] {-90F, 0F}, BotMath.getAngles(0, 0, 0, 5, 0, 0), 1e-4F);
        assertArrayEquals(new float[] {90F, 0F}, BotMath.getAngles(0, 0, 0, -5, 0, 0), 1e-4F);
        assertArrayEquals(new float[] {0F, -45F}, BotMath.getAngles(0, 0, 0, 0, 3, 3), 1e-4F);
        assertArrayEquals(new float[] {0F, 90F}, BotMath.getAngles(0, 1, 0, 0, -4, 0), 1e-4F);
    }

    @Test
    void wrapDegrees() {
        assertEquals(-170F, BotMath.wrapDegrees(190F), 1e-4F);
        assertEquals(-180F, BotMath.wrapDegrees(180F), 1e-4F);
        assertEquals(170F, BotMath.wrapDegrees(-190F), 1e-4F);
        assertEquals(10F, BotMath.wrapDegrees(370F), 1e-4F);
        assertEquals(30F, BotMath.wrapDegrees(30F), 1e-4F);
    }

    @Test
    void smoothBodyYawClampsToFifteen() {
        assertEquals(15F, BotMath.smoothBodyYaw(0F, 90F), 1e-4F);
        assertEquals(-15F, BotMath.smoothBodyYaw(0F, -90F), 1e-4F);
        assertEquals(5F, BotMath.smoothBodyYaw(0F, 5F), 1e-4F);
        assertEquals(190F, BotMath.smoothBodyYaw(175F, -170F), 1e-4F);
    }

    @Test
    void smoothYawTowardClampsToStep() {
        assertEquals(40F, BotMath.smoothYawToward(30F, 100F, 10F), 1e-4F);
        assertEquals(20F, BotMath.smoothYawToward(30F, -100F, 10F), 1e-4F);
        assertEquals(32F, BotMath.smoothYawToward(30F, 32F, 10F), 1e-4F);
    }
}
