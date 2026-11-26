package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Only the static item helpers of the training dummy; its tick is not ported. */
public final class StunSlamDummy {

    private StunSlamDummy() {
    }

    public static boolean carriesMaceOrSpear(Player p) {
        for (int i = 0; i < 36; i++) {
            if (isMaceOrSpear(p.getInventory().getItem(i))) {
                return true;
            }
        }
        return isMaceOrSpear(p.getInventory().getItemInOffHand()) || isMaceOrSpear(p.getInventory().getItemInMainHand());
    }

    public static boolean isMaceOrSpear(ItemStack s) {
        return ItemKinds.is(s, Material.MACE) || ItemKinds.isSpear(s);
    }
}
