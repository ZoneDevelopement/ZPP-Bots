package dev.nandi0813.zppbots.mc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Null-safe item checks for Mojang item stacks. */
public final class ItemKinds {

    private ItemKinds() {
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    public static boolean is(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material;
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && isSword(stack.getType());
    }

    public static boolean isSword(Material material) {
        return Tag.ITEMS_SWORDS.isTagged(material);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack != null && isAxe(stack.getType());
    }

    public static boolean isAxe(Material material) {
        return Tag.ITEMS_AXES.isTagged(material);
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && isPickaxe(stack.getType());
    }

    public static boolean isPickaxe(Material material) {
        return Tag.ITEMS_PICKAXES.isTagged(material);
    }

    public static boolean isShovel(ItemStack stack) {
        return stack != null && isShovel(stack.getType());
    }

    public static boolean isShovel(Material material) {
        return Tag.ITEMS_SHOVELS.isTagged(material);
    }

    public static boolean isHoe(ItemStack stack) {
        return stack != null && isHoe(stack.getType());
    }

    public static boolean isHoe(Material material) {
        return Tag.ITEMS_HOES.isTagged(material);
    }

    public static boolean isArrow(ItemStack stack) {
        return stack != null && isArrow(stack.getType());
    }

    public static boolean isArrow(Material material) {
        return Tag.ITEMS_ARROWS.isTagged(material);
    }

    public static boolean isPlanks(ItemStack stack) {
        return stack != null && isPlanks(stack.getType());
    }

    public static boolean isPlanks(Material material) {
        return Tag.PLANKS.isTagged(material);
    }

    public static boolean isLog(ItemStack stack) {
        return stack != null && isLog(stack.getType());
    }

    public static boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material);
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return stack != null && isShulkerBox(stack.getType());
    }

    public static boolean isShulkerBox(Material material) {
        return Tag.SHULKER_BOXES.isTagged(material);
    }

    public static boolean isSpear(ItemStack stack) {
        return stack != null && isSpear(stack.getType());
    }

    public static boolean isSpear(Material material) {
        return material.name().endsWith("_SPEAR");
    }

    public static boolean isFood(ItemStack stack) {
        return stack != null && stack.getType().isEdible();
    }

    public static boolean isCrossbowCharged(ItemStack stack) {
        return stack != null && stack.getType() == Material.CROSSBOW
                && stack.getItemMeta() instanceof CrossbowMeta meta && meta.hasChargedProjectiles();
    }

    public static int maxDamage(ItemStack stack) {
        if (isEmpty(stack)) return 0;
        Integer max = stack.getData(DataComponentTypes.MAX_DAMAGE);
        return max != null ? max : stack.getType().getMaxDurability();
    }

    public static int damage(ItemStack stack) {
        if (isEmpty(stack)) return 0;
        Integer damage = stack.getData(DataComponentTypes.DAMAGE);
        return damage != null ? damage : 0;
    }

    public static boolean damageable(ItemStack stack) {
        return maxDamage(stack) > 0 && !stack.hasData(DataComponentTypes.UNBREAKABLE);
    }

    public static List<ItemStack> containerContents(ItemStack stack) {
        if (isEmpty(stack)) return List.of();
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) return List.of();
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulker)) return List.of();
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : shulker.getInventory().getContents()) {
            if (!isEmpty(item)) contents.add(item.clone());
        }
        return contents;
    }

    public static double attackDamage(ItemStack stack) {
        if (isEmpty(stack)) return 0.0;
        ItemMeta meta = stack.getItemMeta();
        Collection<AttributeModifier> modifiers = meta != null && meta.hasAttributeModifiers()
                ? meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE)
                : stack.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND).get(Attribute.ATTACK_DAMAGE);
        if (modifiers == null) return 0.0;
        double total = 0.0;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER
                    && modifier.getSlotGroup().test(EquipmentSlot.HAND)) {
                total += modifier.getAmount();
            }
        }
        return total;
    }

    public static EquipmentSlot equipSlot(ItemStack stack) {
        return isEmpty(stack) ? EquipmentSlot.HAND : stack.getType().getEquipmentSlot();
    }

    public static float destroySpeed(ItemStack stack, BlockData data) {
        return isEmpty(stack) ? data.getDestroySpeed(new ItemStack(Material.AIR)) : data.getDestroySpeed(stack);
    }
}
