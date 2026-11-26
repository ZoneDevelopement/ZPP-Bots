package dev.nandi0813.zppbots.ai.elytra;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraSkillTest {

    @Test
    void perfectLevelHasNoDiveError() {
        assertArrayEquals(new double[]{0.0, 0.0}, ElytraSkill.diveOffset(new Random(7), 0.0));
    }

    @Test
    void diveOffsetStaysWithinMaxRadiusAndAboveMinimum() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            double[] o = ElytraSkill.diveOffset(random, 1.9);
            double r = Math.hypot(o[0], o[1]);
            assertTrue(r <= 1.9 + 1e-9 && r >= 1.9 * 0.4 - 1e-9);
        }
    }

    @Test
    void levelClamps() {
        assertEquals(1, ElytraSkill.clampLevel(0));
        assertEquals(5, ElytraSkill.clampLevel(7));
    }
}
