package dev.nandi0813.zppbots.ai.heal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HealingRunFirstTest {

    @Test
    void runsOnlyWhileThreatIsCloseAndCapped() {
        assertTrue(HealingController.runFirst(true, 3, 6, 0, 60));
        assertFalse(HealingController.runFirst(true, 7, 6, 0, 60));
        assertFalse(HealingController.runFirst(true, 3, 6, 60, 60));
        assertFalse(HealingController.runFirst(false, 3, 6, 0, 60));
    }
}
