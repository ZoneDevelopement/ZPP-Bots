package dev.nandi0813.zppbots.ai.gear;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ShulkerRaidControllerTest {

    private final UUID self = UUID.randomUUID();

    @Test
    void unclaimedBoxIsRaidable() {
        assertTrue(ShulkerRaidController.isRaidable(null, self, false));
    }

    @Test
    void ownAndAlliedBoxesAreSkipped() {
        assertFalse(ShulkerRaidController.isRaidable(self, self, false));
        assertFalse(ShulkerRaidController.isRaidable(UUID.randomUUID(), self, true));
    }

    @Test
    void enemyBoxIsRaidable() {
        assertTrue(ShulkerRaidController.isRaidable(UUID.randomUUID(), self, false));
    }
}
