package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.ai.move.PathFollower;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ArmorManager {

    private static final String EQUIP_ARMOR = "EQUIP_ARMOR";
    private static final String EQUIP_TURTLE_SHELL = "EQUIP_TURTLE_SHELL";
    private static final String MOVE_BEST_WEAPON = "MOVE_BEST_WEAPON";

    private static final int DROP_SCAN_INTERVAL = 10;
    private static final int MAX_CHASE_TICKS = 200;
    private static final int MAX_IGNORED = 32;

    private final BotContext ctx;
    private final Player bot;
    private final InvisibleSuitUp suitUp;
    private final PathFollower path;
    private int dropScanTick = DROP_SCAN_INTERVAL;
    private Item targetItem = null;
    private int chaseTicks = 0;
    private final Set<UUID> ignoredDrops = new HashSet<>();

    public ArmorManager(BotContext ctx, InvisibleSuitUp suitUp) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.suitUp = suitUp;
        this.path = new PathFollower(ctx);
    }

    public void equipFromInventory() {
        if (ctx.settings().equipArmor()) {
            PlayerInventory inv = bot.getInventory();
            if (suitUp.holdsArmour()) {
                equipBestWeapon();
            } else {
                if (bot.isInWater()) {
                    equipTurtleShell(inv);
                }

                for (int i = 0; i < 36; i++) {
                    ItemStack stack = inv.getItem(i);
                    if (ArmorEvaluator.isArmor(stack)) {
                        EquipmentSlot slot = ArmorEvaluator.getSlot(stack);
                        if (slot != null) {
                            ItemStack current = bot.getEquipment().getItem(slot);
                            if ((slot != EquipmentSlot.HEAD || !bot.isInWater() || !ItemKinds.is(current, Material.TURTLE_HELMET))
                                    && ArmorEvaluator.score(stack) > ArmorEvaluator.score(current)) {
                                ItemStack old = current.clone();
                                bot.getEquipment().setItem(slot, stack.clone());
                                inv.setItem(i, old);
                                ctx.log().decision("ArmorManager", EQUIP_ARMOR, "equipped " + stack.getType() + " (score " + String.format("%.1f", ArmorEvaluator.score(stack))
                                        + ") over " + (ItemKinds.isEmpty(current) ? "nothing" : current.getType())
                                        + " (score " + String.format("%.1f", ArmorEvaluator.score(current)) + ")");
                            }
                        }
                    }
                }

                equipBestWeapon();
            }
        }
    }

    private void equipTurtleShell(PlayerInventory inv) {
        ItemStack currentHelmet = bot.getEquipment().getItem(EquipmentSlot.HEAD);
        if (!ItemKinds.is(currentHelmet, Material.TURTLE_HELMET)) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getItem(i);
                if (ItemKinds.is(stack, Material.TURTLE_HELMET)) {
                    bot.getEquipment().setItem(EquipmentSlot.HEAD, stack.clone());
                    inv.setItem(i, currentHelmet.clone());
                    ctx.log().decision("ArmorManager", EQUIP_TURTLE_SHELL, "equipped Turtle Shell for water breathing");
                    return;
                }
            }
        }
    }

    private void equipBestWeapon() {
        PlayerInventory inv = bot.getInventory();
        int bestSlot = -1;
        double bestScore = 0.0;

        for (int i = 0; i < 36; i++) {
            double s = ItemEvaluator.scoreWeapon(inv.getItem(i));
            if (s > bestScore) {
                bestScore = s;
                bestSlot = i;
            }
        }

        if (bestSlot >= 0) {
            if (bestSlot >= 9) {
                int destSlot = -1;
                double destScore = Double.MAX_VALUE;

                for (int ix = 0; ix < 9; ix++) {
                    ItemStack stack = inv.getItem(ix);
                    if (!isComboCritical(stack)) {
                        double s = ItemEvaluator.scoreWeapon(stack);
                        if (s < destScore) {
                            destScore = s;
                            destSlot = ix;
                        }
                    }
                }

                if (destSlot >= 0) {
                    if (!(bestScore <= destScore)) {
                        ItemStack candidate = inv.getItem(bestSlot).clone();
                        ItemStack displaced = ItemKinds.isEmpty(inv.getItem(destSlot)) ? null : inv.getItem(destSlot).clone();
                        inv.setItem(destSlot, candidate);
                        inv.setItem(bestSlot, displaced);
                        ctx.log().decision("ArmorManager", MOVE_BEST_WEAPON, "pulled best weapon " + candidate.getType() + " to the hotbar (score " + String.format("%.1f", bestScore) + ")");
                    }
                }
            }
        }
    }

    static boolean isComboCritical(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return false;
        } else {
            return alwaysCritical(stack.getType()) || EnchantmentUtils.hasEnchantAtLeast(stack, "lunge", 3);
        }
    }

    static boolean alwaysCritical(Material type) {
        return switch (type) {
            case MACE, BOW, CROSSBOW, ELYTRA, TRIDENT -> true;
            default -> false;
        };
    }

    public boolean tickNavigation() {
        if (!ctx.settings().pickupLoot()) {
            targetItem = null;
            path.reset();
            return false;
        } else {
            if (targetItem != null && !Ent.alive(targetItem)) {
                targetItem = null;
            }

            if (++dropScanTick >= DROP_SCAN_INTERVAL) {
                dropScanTick = 0;
                Item best = findBestDrop();
                if (best != targetItem) {
                    chaseTicks = 0;
                }

                targetItem = best;
            }

            if (targetItem == null) {
                chaseTicks = 0;
                path.reset();
                return false;
            } else if (++chaseTicks > MAX_CHASE_TICKS) {
                if (ignoredDrops.size() >= MAX_IGNORED) {
                    ignoredDrops.clear();
                }

                ignoredDrops.add(targetItem.getUniqueId());
                targetItem = null;
                chaseTicks = 0;
                path.reset();
                return false;
            } else {
                path.chase(targetItem);
                return true;
            }
        }
    }

    public void resetNavigation() {
        targetItem = null;
        chaseTicks = 0;
        path.reset();
    }

    private Item findBestDrop() {
        if (bot.getInventory().firstEmpty() < 0) {
            return null;
        } else {
            AABB box = Ent.box(bot).inflate(ctx.tuning().number(TuningKey.ARMOR_DROP_SCAN_RANGE).doubleValue());
            Item best = null;
            double bestGain = ctx.tuning().number(TuningKey.ARMOR_MIN_GAIN).doubleValue();

            for (Item ie : ctx.world().entities(Item.class, box, i -> true)) {
                if (ie.isValid() && !ignoredDrops.contains(ie.getUniqueId())) {
                    ItemStack stack = ie.getItemStack();
                    if (ArmorEvaluator.isArmor(stack)) {
                        EquipmentSlot slot = ArmorEvaluator.getSlot(stack);
                        if (slot != null) {
                            double gain = ArmorEvaluator.score(stack) - ArmorEvaluator.score(bot.getEquipment().getItem(slot));
                            if (gain > bestGain) {
                                bestGain = gain;
                                best = ie;
                            }
                        }
                    }
                }
            }

            return best;
        }
    }
}
