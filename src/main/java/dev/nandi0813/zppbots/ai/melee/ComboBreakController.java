package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.ai.ranged.EnderPearlController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.LivingEntity;

public final class ComboBreakController {

    private final BotContext ctx;
    private int jumpCooldown = 0;
    private int pearlCooldown = 0;
    private int jumpRolledChain = -1;
    private int pearlRolledChain = -1;

    public ComboBreakController(BotContext ctx) {
        this.ctx = ctx;
    }

    public void reset() {
        jumpCooldown = pearlCooldown = 0;
        jumpRolledChain = pearlRolledChain = -1;
    }

    public boolean tick(LivingEntity target, FightState fightState, EnderPearlController pearls) {
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }

        if (pearlCooldown > 0) {
            pearlCooldown--;
        }

        if (!fightState.beingCombod()) {
            jumpRolledChain = pearlRolledChain = -1;
        }

        if (target == null || !Ent.alive(target)) {
            return false;
        } else if (!fightState.beingCombod()) {
            return false;
        } else if (fightState.ticksSinceHitTaken() > ctx.tuning().integer(TuningKey.COMBO_BREAK_CHAIN_FRESH_TICKS)) {
            return false;
        } else {
            int chain = fightState.incomingComboHits();
            double accuracy = ctx.difficulty().accuracy;
            if (chain >= ctx.tuning().integer(TuningKey.COMBO_BREAK_PEARL_AT) && pearlCooldown == 0 && ctx.settings().useEnderPearl() && chain != pearlRolledChain) {
                pearlRolledChain = chain;
                if (ctx.random().nextDouble() < accuracy) {
                    pearlCooldown = ctx.tuning().integer(TuningKey.COMBO_BREAK_PEARL_COOLDOWN);
                    if (pearls.tryThrow(target)) {
                        return true;
                    }
                }
            }

            if (chain >= ctx.tuning().integer(TuningKey.COMBO_BREAK_JUMP_AT) && jumpCooldown == 0 && ctx.player().isOnGround() && chain != jumpRolledChain) {
                jumpRolledChain = chain;
                if (ctx.random().nextDouble() < accuracy) {
                    jumpCooldown = ctx.tuning().integer(TuningKey.COMBO_BREAK_JUMP_COOLDOWN);
                    ctx.controls().jumpOnce();
                }
            }

            return false;
        }
    }
}
