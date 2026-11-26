package dev.nandi0813.zppbots.ai.gear;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ArmorManagerTest {

    @Test
    void comboCriticalItemsStayInTheHotbar() {
        assertTrue(ArmorManager.alwaysCritical(Material.MACE));
        assertTrue(ArmorManager.alwaysCritical(Material.ELYTRA));
        assertFalse(ArmorManager.alwaysCritical(Material.DIAMOND_SWORD));
        assertFalse(ArmorManager.isComboCritical(null));
    }
}
