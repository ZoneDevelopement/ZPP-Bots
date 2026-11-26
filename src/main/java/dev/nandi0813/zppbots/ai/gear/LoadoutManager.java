package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.function.Predicate;

public final class LoadoutManager {

    private static final List<Predicate<ItemStack>> ENSURED = List.of(
            s -> ItemKinds.is(s, Material.MACE),
            s -> ItemKinds.is(s, Material.BOW),
            s -> ItemKinds.is(s, Material.CROSSBOW),
            s -> ItemKinds.is(s, Material.WIND_CHARGE),
            ItemKinds::isSword,
            ItemKinds::isAxe,
            s -> EnchantmentUtils.hasEnchantAtLeast(s, "lunge", 3),
            s -> ItemKinds.is(s, Material.TRIDENT) && EnchantmentUtils.hasEnchantAtLeast(s, "riptide", 3));
    private static final int ALL_KINDS = (1 << ENSURED.size()) - 1;

    private final BotContext ctx;
    private final int[] firstBagSlot;

    public LoadoutManager(BotContext ctx) {
        this.firstBagSlot = new int[ENSURED.size()];
        this.ctx = ctx;
    }

    public void tick() {
        PlayerInventory inv = ctx.player().getInventory();
        int emptyMask = matches(null, ALL_KINDS);
        int inHotbar = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            inHotbar |= ItemKinds.isEmpty(s) ? emptyMask : matches(s, ~inHotbar & ALL_KINDS);
        }

        int pending = ~inHotbar & ALL_KINDS;
        int inBag = 0;

        for (int i = 9; i < 36 && pending != 0; i++) {
            ItemStack s = inv.getItem(i);
            int m = ItemKinds.isEmpty(s) ? emptyMask & pending : matches(s, pending);
            if (m != 0) {
                for (int k = 0; k < ENSURED.size(); k++) {
                    if ((m & 1 << k) != 0) {
                        firstBagSlot[k] = i;
                    }
                }

                inBag |= m;
                pending &= ~m;
            }
        }

        for (int kx = 0; kx < ENSURED.size(); kx++) {
            if ((inBag & 1 << kx) != 0) {
                pull(inv, firstBagSlot[kx]);
                ensureFrom(inv, kx + 1);
                return;
            }
        }
    }

    private void ensureFrom(PlayerInventory inv, int from) {
        for (int kind = Math.max(from, 0); kind < ENSURED.size(); kind++) {
            ensureKind(inv, ENSURED.get(kind));
        }
    }

    private static int matches(ItemStack s, int mask) {
        int m = 0;

        for (int k = 0; k < ENSURED.size(); k++) {
            if ((mask & 1 << k) != 0 && ENSURED.get(k).test(s)) {
                m |= 1 << k;
            }
        }

        return m;
    }

    private void pull(PlayerInventory inv, int from) {
        boolean room = false;

        for (int i = 0; i < 9 && !room; i++) {
            ItemStack s = inv.getItem(i);
            room = ItemKinds.isEmpty(s) || !InventoryUtils.isProtected(s);
        }

        if (room || !isSoleEnsured(inv, 8)) {
            InventoryUtils.moveToHotbarSafe(ctx, from);
        }
    }

    private static boolean isSoleEnsured(PlayerInventory inv, int slot) {
        ItemStack held = inv.getItem(slot);
        if (ItemKinds.isEmpty(held)) {
            return false;
        } else {
            for (Predicate<ItemStack> kind : ENSURED) {
                if (kind.test(held)) {
                    boolean elsewhere = false;

                    for (int i = 0; i < 9 && !elsewhere; i++) {
                        if (i != slot && kind.test(inv.getItem(i))) {
                            elsewhere = true;
                        }
                    }

                    if (!elsewhere) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    /** ensureExactItem/ensureTagged/ensureLungeSpear/ensureRiptideTrident, which differed only in the predicate. */
    private void ensureKind(PlayerInventory inv, Predicate<ItemStack> kind) {
        for (int i = 0; i < 9; i++) {
            if (kind.test(inv.getItem(i))) {
                return;
            }
        }

        for (int ix = 9; ix < 36; ix++) {
            if (kind.test(inv.getItem(ix))) {
                pull(inv, ix);
                return;
            }
        }
    }
}
