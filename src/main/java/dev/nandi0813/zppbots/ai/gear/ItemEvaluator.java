package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class ItemEvaluator {

    private ItemEvaluator() {
    }

    public static boolean isWeapon(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return false;
        }
        if (stack.getType() == Material.MACE) {
            return false;
        }
        if (ItemKinds.isPickaxe(stack) || ItemKinds.isShovel(stack) || ItemKinds.isHoe(stack)) {
            return false;
        }
        return ItemKinds.attackDamage(stack) > 0.0;
    }

    public static double scoreWeapon(ItemStack stack) {
        if (!isWeapon(stack)) {
            return 0.0;
        }
        double score = ItemKinds.attackDamage(stack);
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            score += weaponEnchantValue(entry.getKey().getKey().getKey(), entry.getValue());
        }
        return score;
    }

    public static boolean isMace(ItemStack stack) {
        return !ItemKinds.isEmpty(stack) && stack.getType() == Material.MACE;
    }

    public static double scoreMace(ItemStack stack) {
        if (!isMace(stack)) {
            return 0.0;
        }
        double score = 6.0;
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            score += maceEnchantValue(entry.getKey().getKey().getKey(), entry.getValue());
        }
        return score;
    }

    public static boolean isShield(ItemStack stack) {
        return !ItemKinds.isEmpty(stack) && stack.getType() == Material.SHIELD;
    }

    public static double scoreShield(ItemStack stack) {
        if (!isShield(stack)) {
            return 0.0;
        }
        double score = 2.0;
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            score += shieldEnchantValue(entry.getKey().getKey().getKey(), entry.getValue());
        }
        return score;
    }

    static double maceEnchantValue(String id, int level) {
        return switch (id) {
            case "density" -> 10.0 * level;
            case "breach" -> 4.0 * level;
            case "wind_burst" -> 6.0 * level;
            case "smite" -> 1.0 * level;
            case "fire_aspect" -> 1.5 * level;
            case "mending" -> 5.0;
            case "unbreaking" -> 0.5 * level;
            default -> 0.3 * level;
        };
    }

    static double weaponEnchantValue(String id, int level) {
        return switch (id) {
            case "sharpness" -> 2.5 * level;
            case "smite" -> 1.0 * level;
            case "bane_of_arthropods" -> 0.8 * level;
            case "fire_aspect" -> 1.5 * level;
            case "looting" -> 1.0 * level;
            case "knockback" -> 1.0 * level;
            case "sweeping_edge" -> 1.0 * level;
            case "mending" -> 5.0;
            case "unbreaking" -> 0.5 * level;
            default -> 0.3 * level;
        };
    }

    static double shieldEnchantValue(String id, int level) {
        return switch (id) {
            case "mending" -> 5.0;
            case "unbreaking" -> 0.5 * level;
            default -> 0.1 * level;
        };
    }
}
