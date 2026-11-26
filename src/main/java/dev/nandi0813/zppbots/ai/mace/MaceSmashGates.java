package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.brain.BotContext;
import org.bukkit.potion.PotionEffectType;

public final class MaceSmashGates {

    private MaceSmashGates() {
    }

    public static boolean botMayUseSmashFallBonus(BotContext ctx) {
        return !ctx.player().hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }
}
