package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;

public final class AxeController {

    private final BotContext ctx;
    private int shieldReactionCountdown = -1;
    private int shieldPunishWindow = 0;

    public AxeController(BotContext ctx) {
        this.ctx = ctx;
    }

    public boolean tickSeesShield(LivingEntity target) {
        boolean targetBlocking = target instanceof HumanEntity human && human.isBlocking();
        if (!targetBlocking) {
            shieldReactionCountdown = -1;
        } else if (shieldReactionCountdown < 0) {
            shieldReactionCountdown = rollShieldReactionDelay();
        } else if (shieldReactionCountdown > 0) {
            shieldReactionCountdown--;
        }

        return targetBlocking && shieldReactionCountdown == 0;
    }

    public void tickWindows() {
        if (shieldPunishWindow > 0) {
            shieldPunishWindow--;
        }
    }

    public boolean shouldDisableNow(float charge) {
        return charge >= (float) ctx.difficulty().shieldDisableCharge;
    }

    public boolean seesShieldNow() {
        return shieldReactionCountdown == 0;
    }

    public void onDisableSwung() {
        shieldPunishWindow = ctx.tuning().integer(TuningKey.AXE_PUNISH_TICKS);
    }

    public boolean punishActive() {
        return shieldPunishWindow > 0;
    }

    public void reset() {
        shieldReactionCountdown = -1;
        shieldPunishWindow = 0;
    }

    private int rollShieldReactionDelay() {
        return ctx.difficulty().rollShieldReactionTicks(ctx.random());
    }
}
