package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;

public final class WaterRules {

    private WaterRules() {
    }

    public static boolean evaporates(BotContext ctx, BlockPos pos) {
        return ctx.world().bukkit().isUltraWarm();
    }
}
