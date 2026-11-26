package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class OffhandManager {

    private static final int OFFHAND_SLOT = 40;

    private final BotContext ctx;
    private final Player bot;
    /** {sourceSlot, expiryGameTime} while an item is parked in the offhand, else null. */
    private long[] park;
    private boolean hadTotemLastTick = false;
    private int totemRegrabDelay = 0;
    private long lastTotemTick = Long.MIN_VALUE;

    public OffhandManager(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean park(int sourceSlot, int ticks) {
        if (bot.getHealth() <= ctx.tuning().number(TuningKey.OFFHAND_TOTEM_HP).doubleValue()) {
            return false;
        } else if (sourceSlot >= 0 && sourceSlot <= 35) {
            if (park != null) {
                return false;
            } else {
                PlayerInventory inv = bot.getInventory();
                if (ItemKinds.isEmpty(inv.getItem(sourceSlot))) {
                    return false;
                } else {
                    swap(inv, sourceSlot, OFFHAND_SLOT);
                    park = new long[]{sourceSlot, ctx.world().gameTime() + ticks};
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    public void unpark() {
        long[] parked = park;
        park = null;
        if (parked != null) {
            swap(bot.getInventory(), (int) parked[0], OFFHAND_SLOT);
        }
    }

    public boolean isParked() {
        return park != null;
    }

    private boolean parkActive() {
        if (park == null) {
            return false;
        } else if (ctx.world().gameTime() > park[1]) {
            unpark();
            return false;
        } else {
            return true;
        }
    }

    public boolean tickTotem() {
        if (parkActive()) {
            return true;
        } else {
            long now = ctx.world().gameTime();
            if (now == lastTotemTick) {
                return hasTotemInOffhand();
            } else {
                lastTotemTick = now;
                PlayerInventory inv = bot.getInventory();
                ItemStack offhand = inv.getItem(OFFHAND_SLOT);
                boolean hasTotem = ItemKinds.is(offhand, Material.TOTEM_OF_UNDYING);
                if (hadTotemLastTick && !hasTotem) {
                    totemRegrabDelay = totemRegrabDelay(ctx.tuning().integer(TuningKey.TOTEM_REGRAB_BASE_TICKS) + ctx.difficulty().totemRegrabExtraTicks, reactionTicks());
                }

                hadTotemLastTick = hasTotem;
                if (hasTotem) {
                    return true;
                } else if (totemRegrabDelay > 0) {
                    totemRegrabDelay--;
                    return false;
                } else {
                    int totemIdx = findInMainInventory(Material.TOTEM_OF_UNDYING);
                    if (totemIdx != -1) {
                        swap(inv, totemIdx, OFFHAND_SLOT);
                        hadTotemLastTick = true;
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }
    }

    /** Inventory-open time from tuning plus the reaction roll before a popped totem is replaced. */
    public static int totemRegrabDelay(int baseTicks, int reactionTicks) {
        return Math.max(0, baseTicks) + Math.max(0, reactionTicks);
    }

    private int reactionTicks() {
        int lo = Math.max(0, ctx.difficulty().reactionMinTicks);
        int hi = Math.max(lo, ctx.difficulty().reactionMaxTicks);
        return lo == hi ? lo : lo + ctx.random().nextInt(hi - lo + 1);
    }

    public void tick() {
        if (!parkActive()) {
            tick(false);
        }
    }

    public void tick(boolean preferShield) {
        if (!parkActive()) {
            PlayerInventory inv = bot.getInventory();
            ItemStack offhand = inv.getItem(OFFHAND_SLOT);
            if (!preferShield || !ensureShieldOffhand()) {
                if (!tickTotem()) {
                    if (!preferShield) {
                        offhand = inv.getItem(OFFHAND_SLOT);
                        int bestShieldIdx = findBestShieldInMainInventory();
                        if (bestShieldIdx != -1
                                && (!ItemKinds.is(offhand, Material.SHIELD)
                                        || ItemEvaluator.scoreShield(inv.getItem(bestShieldIdx)) > ItemEvaluator.scoreShield(offhand))) {
                            swap(inv, bestShieldIdx, OFFHAND_SLOT);
                        }
                    }
                }
            }
        }
    }

    public boolean ensureShieldOffhand() {
        PlayerInventory inv = bot.getInventory();
        ItemStack offhand = inv.getItem(OFFHAND_SLOT);
        int bestShieldIdx = findBestShieldInMainInventory();
        if (ItemKinds.is(offhand, Material.SHIELD)) {
            if (bestShieldIdx != -1 && ItemEvaluator.scoreShield(inv.getItem(bestShieldIdx)) > ItemEvaluator.scoreShield(offhand)) {
                swap(inv, bestShieldIdx, OFFHAND_SLOT);
            }

            hadTotemLastTick = false;
            return true;
        } else if (bestShieldIdx != -1) {
            swap(inv, bestShieldIdx, OFFHAND_SLOT);
            hadTotemLastTick = false;
            return true;
        } else {
            return false;
        }
    }

    public boolean hasShieldAvailable() {
        return hasShieldInOffhand() || findBestShieldInMainInventory() != -1;
    }

    public boolean hasShieldInOffhand() {
        return ItemKinds.is(bot.getInventory().getItem(OFFHAND_SLOT), Material.SHIELD);
    }

    public boolean hasTotemInOffhand() {
        return ItemKinds.is(bot.getInventory().getItem(OFFHAND_SLOT), Material.TOTEM_OF_UNDYING);
    }

    private int findInMainInventory(Material item) {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), item)) {
                return i;
            }
        }

        return -1;
    }

    private int findBestShieldInMainInventory() {
        PlayerInventory inv = bot.getInventory();
        int bestIdx = -1;
        double bestScore = -1.0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (ItemEvaluator.isShield(stack)) {
                double s = ItemEvaluator.scoreShield(stack);
                if (s > bestScore) {
                    bestScore = s;
                    bestIdx = i;
                }
            }
        }

        return bestIdx;
    }

    private static void swap(PlayerInventory inv, int slotA, int slotB) {
        ItemStack a = inv.getItem(slotA) == null ? null : inv.getItem(slotA).clone();
        ItemStack b = inv.getItem(slotB) == null ? null : inv.getItem(slotB).clone();
        inv.setItem(slotA, b);
        inv.setItem(slotB, a);
    }
}
