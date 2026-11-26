package dev.nandi0813.zppbots.ai.heal;

import dev.nandi0813.zppbots.mc.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HealingFleeingTest {

    private static final Vec3 BOT = new Vec3(0, 0, 0);
    private static final Vec3 TARGET = new Vec3(5, 0, 0);

    @Test
    void targetRunningAwayIsFleeing() {
        assertTrue(HealingController.isFleeing(new Vec3(0.2, 0, 0), BOT, TARGET, 0.1, 0.5));
    }

    @Test
    void targetRunningTowardIsNotFleeing() {
        assertFalse(HealingController.isFleeing(new Vec3(-0.2, 0, 0), BOT, TARGET, 0.1, 0.5));
    }

    @Test
    void standingTargetIsNotFleeing() {
        assertFalse(HealingController.isFleeing(new Vec3(0, 0, 0), BOT, TARGET, 0.1, 0.5));
    }
}
