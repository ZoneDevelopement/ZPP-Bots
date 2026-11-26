package dev.nandi0813.zppbots.mc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class EntTest {

    @Test
    void fullChargeIsExactlyOne() {
        assertEquals(1.0f, Ent.strengthFrom(1.0f, 0f, 12.5f), 0.0f);
    }

    @Test
    void partialChargeUsesHalfTickAdjust() {
        assertEquals(0.46f, Ent.strengthFrom(0.5f, 0f, 12.5f), 1.0E-6f);
    }

    @Test
    void chargeIsClampedToZero() {
        assertEquals(0f, Ent.strengthFrom(0f, 0f, 12.5f), 0.0f);
    }
}
