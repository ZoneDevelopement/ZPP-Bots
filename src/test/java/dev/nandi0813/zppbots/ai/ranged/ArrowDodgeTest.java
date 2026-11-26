package dev.nandi0813.zppbots.ai.ranged;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ArrowDodgeTest {

    @Test
    void dodgesOnlyAfterTheDrawAndFromAFarEnoughDistance() {
        assertFalse(ArrowDodge.shouldDodge(7, 10.0, 8, 5.0));
        assertFalse(ArrowDodge.shouldDodge(8, 4.9, 8, 5.0));
        assertTrue(ArrowDodge.shouldDodge(8, 5.0, 8, 5.0));
    }
}
