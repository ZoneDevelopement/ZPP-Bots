package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.team.perf.BotAiTier;
import org.bukkit.entity.Player;

final class NoTargetPhase {

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;

    NoTargetPhase(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    boolean tickNoTarget() {
        if (brain.aiTier == BotAiTier.SLEEP && brain.target == null) {
            if ((brain.tickCount & 3) != 0
                    && ReadAndEscapePhase.hurtAnimationOver(bot)
                    && brain.idleNavTick != brain.tickCount - 1
                    && bot.isOnGround()
                    && ctx.match().velocityOf(bot).horizontalDistanceSqr() < 1.0E-4) {
                return true;
            } else {
                resetFightState();
                brain.cobwebTrapController.reset();
                brain.predictionWebController.reset();
                brain.headWebController.reset();
                brain.chorusEscapeController.reset();
                brain.bubbleWebController.reset();
                brain.scaffoldController.reset();
                brain.boatTravelController.reset();
                brain.tunnelController.reset();
                brain.comboBreakController.reset();
                brain.orbitalStrikeController.reset();
                brain.shulkerRaidController.reset();
                if (brain.idleNavTick != brain.tickCount - 1) {
                    brain.pathFollower.reset();
                }

                if (brain.restockController.tick(null)) {
                    return true;
                } else if (idleNav(brain.loot.tickContainerPickup())) {
                    return true;
                } else if (brain.restockController.tryPackLoot()) {
                    return true;
                } else if (idleNav(ctx.squad().tickSurround(ctx, brain.pathFollower::chase))) {
                    return true;
                } else {
                    boolean fetchingArmor = brain.armorManager.tickNavigation();
                    if (!fetchingArmor) {
                        idle();
                    }

                    return true;
                }
            }
        } else if (brain.target == null) {
            resetFightState();
            brain.cobwebTrapController.reset();
            brain.predictionWebController.reset();
            brain.headWebController.reset();
            brain.chorusEscapeController.reset();
            brain.bubbleWebController.reset();
            brain.scaffoldController.reset();
            brain.boatTravelController.reset();
            brain.tunnelController.reset();
            brain.orbitalStrikeController.reset();
            brain.shulkerRaidController.reset();
            if (brain.idleNavTick != brain.tickCount - 1) {
                brain.pathFollower.reset();
            }

            if (brain.restockController.tick(null)) {
                return true;
            } else if (idleNav(brain.loot.tickContainerPickup())) {
                return true;
            } else if (brain.restockController.tryPackLoot()) {
                return true;
            } else {
                // no FFA loot mode: kill loot is never chased
                brain.lastCombatTarget = null;
                brain.killLootTicks = 0;
                brain.killLootPos = null;

                if (ctx.settings().useCarting() && brain.crossbowChargeController.tick()) {
                    return true;
                } else if (idleNav(ctx.squad().tickSurround(ctx, brain.pathFollower::chase))) {
                    return true;
                } else {
                    boolean fetchingArmor = brain.armorManager.tickNavigation();
                    if (!fetchingArmor) {
                        idle();
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private void resetFightState() {
        brain.inCombo = false;
        brain.comboHitsLeft = 0;
        brain.critChainWindow = 0;
        brain.axeController.reset();
        brain.jumpedForCrit = false;
        brain.critSwingHold = 0;
        brain.sprintHitHold = 0;
        brain.melee.releaseHotbarShield();
        brain.elytraMaceController.reset();
        brain.windBurstElytraController.reset();
        brain.rocketlessElytraController.reset();
        brain.elytraSpearController.reset();
        brain.tridentMaceController.reset();
        brain.maceController.reset();
        brain.spearMaceController.reset();
        brain.pearlCatchController.reset();
        brain.cartController.reset();
        brain.swapTechniques.reset();
        brain.crystalPvpController.reset();
        brain.anchorPvpController.reset();
        brain.shieldController.reset();
        brain.rangedController.reset();
    }

    void idle() {
        if (!brain.wanderController.tick()) {
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
            brain.idleHumanizer.tick(brain.tickCount);
        }

        if (brain.weaponSelector.selectCrossbowIfAvailable()) {
            brain.rangedController.tickPreload();
        }
    }

    boolean idleNav(boolean driving) {
        if (driving) {
            brain.idleNavTick = brain.tickCount;
        }

        return driving;
    }
}
