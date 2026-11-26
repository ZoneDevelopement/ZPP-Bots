package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.mc.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MaceDiveForecastTest {

    private static final Vec3 TARGET = new Vec3(0.0, 0.9, 0.0);

    @Test
    void diverFallingOntoTargetStrikesWithSmashFall() {
        MaceDiveForecast.Strike s = MaceDiveForecast.strike(new Vec3(0.0, 8.0, 0.0), Vec3.ZERO, 0.0, 1.62, false, true, TARGET, 0.0);
        assertNotNull(s);
        assertTrue(s.ticks() > 0);
        assertTrue(s.fall() >= 1.5);
    }

    @Test
    void diverLandingFarAwayNeverReaches() {
        assertNull(MaceDiveForecast.strike(new Vec3(30.0, 8.0, 0.0), Vec3.ZERO, 0.0, 1.62, false, true, TARGET, 0.0));
    }

    @Test
    void diverAlreadyBelowTargetIsIgnored() {
        assertNull(MaceDiveForecast.strike(new Vec3(0.0, -5.0, 0.0), Vec3.ZERO, 0.0, 1.62, false, true, TARGET, 0.0));
    }

    @Test
    void damageGrowsWithFallAndShrinksWithArmorAndResistance() {
        float low = MaceDiveForecast.estimateDamage(2.0, true, 0, 0, -1);
        float high = MaceDiveForecast.estimateDamage(10.0, true, 0, 0, -1);
        assertTrue(high > low);
        assertTrue(MaceDiveForecast.estimateDamage(10.0, true, 20, 8, -1) < high);
        assertTrue(MaceDiveForecast.estimateDamage(10.0, true, 0, 0, 1) < high);
        assertEquals(10.0f, MaceDiveForecast.estimateDamage(0.0, false, 0, 0, -1), 1.0e-4f);
    }
}
