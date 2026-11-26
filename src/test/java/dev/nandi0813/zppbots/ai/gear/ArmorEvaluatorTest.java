package dev.nandi0813.zppbots.ai.gear;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ArmorEvaluatorTest {

    @Test
    void emptyStackIsNotArmor() {
        assertEquals(0.0, ArmorEvaluator.score(null), 1e-9);
        assertNull(ArmorEvaluator.getSlot(null));
        assertFalse(ArmorEvaluator.isArmor(null));
    }

    @Test
    void enchantValuesFollowWeightTable() {
        assertEquals(12.0, ArmorEvaluator.enchantmentValue("protection", 4), 1e-9);
        assertEquals(4.5, ArmorEvaluator.enchantmentValue("blast_protection", 3), 1e-9);
        assertEquals(5.0, ArmorEvaluator.enchantmentValue("mending", 1), 1e-9);
        assertEquals(6.0, ArmorEvaluator.enchantmentValue("thorns", 3), 1e-9);
        assertEquals(0.9, ArmorEvaluator.enchantmentValue("respiration", 3), 1e-9);
    }
}
