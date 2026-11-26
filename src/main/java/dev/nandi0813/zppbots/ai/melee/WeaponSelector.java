package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.ai.gear.ItemEvaluator;
import dev.nandi0813.zppbots.ai.ranged.RangedController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class WeaponSelector {

    private final BotContext ctx;
    private final Player bot;
    private int bowLockUntil = 0;

    public WeaponSelector(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    /** Skips the bow and crossbow until the tick, so the sword chases after a volley. */
    public void lockBow(int untilTick) {
        bowLockUntil = untilTick;
    }

    public boolean bowLocked(double dist) {
        return RangedController.bowLockActive(ctx.tick(), bowLockUntil, dist,
                ctx.tuning().number(TuningKey.SPECIALS_RANGED_MAX_DIST).doubleValue());
    }

    public WeaponType selectWeapon(LivingEntity target, double dist, boolean effectivelyBlocking) {
        PlayerInventory inv = bot.getInventory();
        if (dist > ctx.tuning().number(TuningKey.WEAPON_RANGED_SWITCH_DIST).doubleValue() && ctx.settings().useBows()
                && bot.hasLineOfSight(target) && !bowLocked(dist)) {
            int xbowSlot = bestInHotbar(WeaponType.CROSSBOW);
            if (xbowSlot >= 0 && ItemKinds.isCrossbowCharged(inv.getItem(xbowSlot))
                    && ctx.tuning().flag(TuningKey.RANGED_PREFER_CHARGED_CROSSBOW)) {
                InventoryUtils.select(ctx, xbowSlot);
                return WeaponType.CROSSBOW;
            }

            boolean arrows = hasArrows();
            if (arrows) {
                int bowSlot = bestInHotbar(WeaponType.BOW);
                if (bowSlot >= 0) {
                    InventoryUtils.select(ctx, bowSlot);
                    return WeaponType.BOW;
                }
            }

            if (xbowSlot >= 0 && (arrows || ItemKinds.isCrossbowCharged(inv.getItem(xbowSlot)))) {
                InventoryUtils.select(ctx, xbowSlot);
                return WeaponType.CROSSBOW;
            }
        }

        if (effectivelyBlocking) {
            int axeSlotB = bestInHotbar(WeaponType.AXE);
            int swordSlotB = bestInHotbar(WeaponType.SWORD);
            if (axeSlotB >= 0 && swordSlotB < 0) {
                InventoryUtils.select(ctx, axeSlotB);
                return WeaponType.AXE;
            }
        }

        int swordSlot = bestInHotbar(WeaponType.SWORD);
        int axeSlot = bestInHotbar(WeaponType.AXE);
        if (swordSlot >= 0) {
            InventoryUtils.select(ctx, swordSlot);
            return WeaponType.SWORD;
        } else if (axeSlot >= 0) {
            InventoryUtils.select(ctx, axeSlot);
            return WeaponType.AXE;
        } else {
            int pulled = pullBestMeleeFromInventory();
            if (pulled >= 0) {
                InventoryUtils.select(ctx, pulled);
                return getType(inv.getItem(pulled));
            } else {
                int spearSlot = ctx.tuning().flag(TuningKey.WEAPON_SPEAR_OVER_MACE) ? bestInHotbar(WeaponType.SPEAR) : -1;
                if (spearSlot >= 0) {
                    InventoryUtils.select(ctx, spearSlot);
                    return WeaponType.SPEAR;
                }

                int maceSlot = bestInHotbar(WeaponType.MACE);
                if (maceSlot >= 0) {
                    InventoryUtils.select(ctx, maceSlot);
                    return WeaponType.MACE;
                } else {
                    for (int slot = 0; slot < 9; slot++) {
                        if (ItemKinds.isEmpty(inv.getItem(slot))) {
                            InventoryUtils.select(ctx, slot);
                            return WeaponType.EMPTY;
                        }
                    }

                    return WeaponType.OTHER;
                }
            }
        }
    }

    public boolean selectCrossbowIfAvailable() {
        int slot = bestInHotbar(WeaponType.CROSSBOW);
        if (slot >= 0) {
            InventoryUtils.select(ctx, slot);
            return true;
        } else {
            return false;
        }
    }

    public WeaponType currentType() {
        return getType(bot.getInventory().getItemInMainHand());
    }

    public static WeaponType getType(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return WeaponType.EMPTY;
        } else if (stack.getType() == Material.BOW) {
            return WeaponType.BOW;
        } else if (stack.getType() == Material.CROSSBOW) {
            return WeaponType.CROSSBOW;
        } else if (stack.getType() == Material.MACE) {
            return WeaponType.MACE;
        } else if (ItemKinds.isSpear(stack)) {
            return WeaponType.SPEAR;
        } else if (ItemKinds.isSword(stack)) {
            return WeaponType.SWORD;
        } else {
            return ItemKinds.isAxe(stack) ? WeaponType.AXE : WeaponType.OTHER;
        }
    }

    private boolean hasArrows() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isArrow(inv.getItem(i))) {
                return true;
            }
        }

        return ItemKinds.isArrow(inv.getItem(40));
    }

    private int pullBestMeleeFromInventory() {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        double bestScore = 0.0;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            WeaponType type = getType(stack);
            if (type == WeaponType.SWORD || type == WeaponType.AXE) {
                double score = ItemEvaluator.scoreWeapon(stack);
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
        }

        return best < 0 ? -1 : InventoryUtils.moveToHotbarSafe(ctx, best);
    }

    private int bestInHotbar(WeaponType wanted) {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        double bestScore = -1.0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (getType(stack) == wanted) {
                double score;
                if (wanted == WeaponType.CROSSBOW) {
                    score = ItemKinds.isCrossbowCharged(stack) ? 2.0 : 1.0;
                } else if (wanted == WeaponType.BOW) {
                    score = 1.0;
                } else if (wanted == WeaponType.MACE) {
                    score = ItemEvaluator.scoreMace(stack);
                } else {
                    score = ItemEvaluator.scoreWeapon(stack);
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
        }

        return best;
    }

    public enum WeaponType {
        SWORD,
        AXE,
        BOW,
        CROSSBOW,
        MACE,
        SPEAR,
        OTHER,
        EMPTY
    }
}
