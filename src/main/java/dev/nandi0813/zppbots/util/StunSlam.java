package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class StunSlam {

    private static final int STALE_TICKS = 2;

    private int step = 0;
    private long pendingTick = -1L;
    private int targetId = -1;

    public Result tick(BotContext ctx, LivingEntity target, int axeSlot, int maceSlot,
            TechniqueConfig.StunSlamVariant variant, boolean enabled) {
        if (target != null && Ent.alive(target) && maceSlot >= 0) {
            long now = ctx.world().gameTime();
            if (step > 0 && (target.getEntityId() != targetId || now > pendingTick + STALE_TICKS)) {
                reset();
            }
            if (step > 0) {
                return now < pendingTick ? Result.HOLD : advance(ctx, target, axeSlot, maceSlot, variant);
            } else if (!enabled || axeSlot < 0) {
                return Result.PLAIN;
            } else if (!(target instanceof HumanEntity human && human.isBlocking())) {
                return Result.PLAIN;
            } else if (target.getNoDamageTicks() > 10) {
                return Result.HOLD;
            } else if (!ctx.settings().rollStunSlamChanceOnly()) {
                return Result.PLAIN;
            } else if (variant == TechniqueConfig.StunSlamVariant.LEGACY_1TICK) {
                SwapEngine.syncedAttack(ctx, axeSlot, target);
                SwapEngine.syncedAttack(ctx, maceSlot, target);
                reset();
                return Result.SMASHED;
            } else {
                SwapEngine.syncedAttack(ctx, axeSlot, target);
                targetId = target.getEntityId();
                if (variant == TechniqueConfig.StunSlamVariant.OPTIMAL) {
                    InventoryUtils.select(ctx, maceSlot);
                    step = 1;
                    pendingTick = now + 1L;
                } else {
                    step = 1;
                    pendingTick = now + 2L;
                }
                return Result.HOLD;
            }
        } else {
            reset();
            return Result.PLAIN;
        }
    }

    private Result advance(BotContext ctx, LivingEntity target, int axeSlot, int maceSlot,
            TechniqueConfig.StunSlamVariant variant) {
        long now = ctx.world().gameTime();
        if (variant == TechniqueConfig.StunSlamVariant.OPTIMAL) {
            return smash(ctx, target, maceSlot);
        }
        if (step == 1) {
            InventoryUtils.select(ctx, maceSlot);
            step = 2;
            pendingTick = now + 1L;
            return Result.HOLD;
        }
        return smash(ctx, target, maceSlot);
    }

    private Result smash(BotContext ctx, LivingEntity target, int maceSlot) {
        reset();
        Player bot = ctx.player();
        if (!bot.isOnGround() && !(bot.getFallDistance() <= 1.5)) {
            SwapEngine.syncedAttack(ctx, maceSlot, target);
            return Result.SMASHED;
        }
        return Result.PLAIN;
    }

    public boolean isRunning() {
        return step > 0;
    }

    public void reset() {
        step = 0;
        pendingTick = -1L;
        targetId = -1;
    }

    public enum Result {
        HOLD,
        SMASHED,
        PLAIN;
    }
}
