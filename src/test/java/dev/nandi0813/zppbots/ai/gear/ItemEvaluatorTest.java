package dev.nandi0813.zppbots.ai.gear;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ItemStack types need a running server (Material registry), so only null handling and the weights are unit-tested. */
final class ItemEvaluatorTest {

    @Test
    void nullStacksAreNothing() {
        assertFalse(ItemEvaluator.isMace(null));
        assertFalse(ItemEvaluator.isShield(null));
        assertFalse(ItemEvaluator.isWeapon(null));
        assertEquals(0.0, ItemEvaluator.scoreWeapon(null), 1e-9);
        assertEquals(0.0, ItemEvaluator.scoreMace(null), 1e-9);
        assertEquals(0.0, ItemEvaluator.scoreShield(null), 1e-9);
    }

    @Test
    void enchantValuesFollowWeightTable() {
        assertEquals(7.5, ItemEvaluator.weaponEnchantValue("sharpness", 3), 1e-9);
        assertEquals(5.0, ItemEvaluator.weaponEnchantValue("mending", 1), 1e-9);
        assertEquals(0.6, ItemEvaluator.weaponEnchantValue("unknown", 2), 1e-9);
        assertEquals(30.0, ItemEvaluator.maceEnchantValue("density", 3), 1e-9);
        assertEquals(24.0, ItemEvaluator.maceEnchantValue("wind_burst", 4), 1e-9);
        assertEquals(1.0, ItemEvaluator.shieldEnchantValue("unbreaking", 2), 1e-9);
        assertEquals(0.3, ItemEvaluator.shieldEnchantValue("other", 3), 1e-9);
    }
}
