package dev.nandi0813.zppbots.team;

import dev.nandi0813.zppbots.ai.gear.ArmorEvaluator;
import dev.nandi0813.zppbots.ai.gear.ItemEvaluator;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class BotCombatScoring {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private BotCombatScoring() {
    }

    public static double score(BotTuning.Profile profile, Player bot) {
        if (bot == null) {
            return 0.0;
        }
        double total = 0.0;
        for (EquipmentSlot slot : ARMOR) {
            total += ArmorEvaluator.score(bot.getEquipment().getItem(slot));
        }

        PlayerInventory inv = bot.getInventory();
        total += ItemEvaluator.scoreWeapon(inv.getItemInMainHand());
        ItemStack off = inv.getItemInOffHand();
        if (ItemKinds.is(off, Material.SHIELD)) {
            total += ItemEvaluator.scoreShield(off);
        } else {
            total += ItemEvaluator.scoreWeapon(off);
        }

        for (int i = 0; i < InventoryUtils.SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (ItemKinds.isEmpty(stack)) {
                continue;
            }
            total += itemWeight(profile, stack.getType());
        }
        return total;
    }

    static double itemWeight(BotTuning.Profile profile, Material material) {
        return switch (material) {
            case TOTEM_OF_UNDYING -> profile.number(TuningKey.COMBAT_SCORE_TOTEM).doubleValue();
            case ENCHANTED_GOLDEN_APPLE ->
                profile.number(TuningKey.COMBAT_SCORE_ENCHANTED_APPLE).doubleValue();
            case GOLDEN_APPLE -> profile.number(TuningKey.COMBAT_SCORE_APPLE).doubleValue();
            case MACE -> profile.number(TuningKey.COMBAT_SCORE_MACE).doubleValue();
            case ELYTRA -> profile.number(TuningKey.COMBAT_SCORE_ELYTRA).doubleValue();
            default -> 0.0;
        };
    }
}
