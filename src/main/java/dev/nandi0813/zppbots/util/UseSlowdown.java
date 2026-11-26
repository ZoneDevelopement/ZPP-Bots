package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.ItemKinds;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

public final class UseSlowdown {

    private UseSlowdown() {
    }

    public static void tick(BotContext ctx) {
        Player bot = ctx.player();
        if (bot.isHandRaised() && bot.getActiveItemHand() == EquipmentSlot.HAND
                && !ItemKinds.isSpear(bot.getActiveItem())) {
            ctx.controls().setSprinting(false);
        }
    }

    public static void clear(BotContext ctx) {
    }
}
