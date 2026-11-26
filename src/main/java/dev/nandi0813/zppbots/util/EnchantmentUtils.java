package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public final class EnchantmentUtils {

    private EnchantmentUtils() {
    }

    /** Level of the enchantment with this vanilla key path (e.g. "riptide"); 0 when absent or unknown on this server. */
    public static int getEnchantLevel(ItemStack stack, String enchantPath) {
        if (ItemKinds.isEmpty(stack) || enchantPath == null || enchantPath.isBlank()) {
            return 0;
        }
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantPath));
        return enchantment == null ? 0 : stack.getEnchantmentLevel(enchantment);
    }

    public static boolean hasEnchantAtLeast(ItemStack stack, String enchantPath, int minLevel) {
        return getEnchantLevel(stack, enchantPath) >= minLevel;
    }
}
