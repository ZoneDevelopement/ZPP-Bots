package dev.nandi0813.zppbots.ai.heal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HealingMendingTest {

    @Test
    void mendingThresholdUsesRemainingDurability() {
        assertTrue(MendingController.remainingAtOrBelow(100, 90, 0.15F));
        assertFalse(MendingController.remainingAtOrBelow(100, 50, 0.15F));
        assertTrue(MendingController.remainingAtOrBelow(100, 50, 0.9F));
    }

    @Test
    void regenHealsScaleWithAmplifier() {
        assertEquals(2.0F, HealingController.regenHeals(100, 0), 1.0E-4F);
        assertEquals(4.0F, HealingController.regenHeals(100, 1), 1.0E-4F);
    }
}
