package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.Map;

public final class ArmorEvaluator {

    private ArmorEvaluator() {
    }

    public static double score(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return 0.0;
        }
        EquipmentSlot slot = getSlot(stack);
        if (slot == null) {
            return 0.0;
        }
        double defense = modifierTotal(stack, Attribute.ARMOR, slot);
        double toughness = modifierTotal(stack, Attribute.ARMOR_TOUGHNESS, slot);
        double score = defense + toughness * 2.0;
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            score += enchantmentValue(entry.getKey().getKey().getKey(), entry.getValue());
        }
        return score;
    }

    public static EquipmentSlot getSlot(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return null;
        }
        EquipmentSlot slot = ItemKinds.equipSlot(stack);
        if (!isArmorSlot(slot)) {
            return null;
        }
        double protection = modifierTotal(stack, Attribute.ARMOR, slot) + modifierTotal(stack, Attribute.ARMOR_TOUGHNESS, slot);
        return protection > 0.0 ? slot : null;
    }

    public static boolean isArmor(ItemStack stack) {
        return getSlot(stack) != null;
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static double modifierTotal(ItemStack stack, Attribute attribute, EquipmentSlot slot) {
        ItemMeta meta = stack.getItemMeta();
        Collection<AttributeModifier> modifiers = meta != null && meta.hasAttributeModifiers()
                ? meta.getAttributeModifiers(attribute)
                : stack.getType().getDefaultAttributeModifiers(slot).get(attribute);
        if (modifiers == null) {
            return 0.0;
        }
        double total = 0.0;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER && modifier.getSlotGroup().test(slot)) {
                total += modifier.getAmount();
            }
        }
        return total;
    }

    static double enchantmentValue(String id, int level) {
        return switch (id) {
            case "protection" -> 3.0 * level;
            case "fire_protection" -> 1.5 * level;
            case "blast_protection" -> 1.5 * level;
            case "projectile_protection" -> 1.5 * level;
            case "mending" -> 5.0;
            case "thorns" -> 2.0 * level;
            case "unbreaking" -> 0.5 * level;
            case "feather_falling" -> 1.0 * level;
            case "depth_strider" -> 1.0 * level;
            case "soul_speed" -> 0.5 * level;
            default -> 0.3 * level;
        };
    }
}
