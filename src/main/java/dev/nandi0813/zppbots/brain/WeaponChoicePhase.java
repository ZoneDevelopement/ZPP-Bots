package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.ai.melee.WeaponSelector;
import dev.nandi0813.zppbots.ai.ranged.RangedController;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import dev.nandi0813.zppbots.team.perf.BotAiTier;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.SwapEngine;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

final class WeaponChoicePhase {

    final BotBrain brain;
    private final BotContext ctx;
    private final Player bot;

    WeaponChoicePhase(BotBrain brain) {
        this.brain = brain;
        this.ctx = brain.ctx;
        this.bot = brain.bot;
    }

    boolean tickWeaponChoice() {
        if (brain.aiTier == BotAiTier.FULL && !brain.healing && ctx.settings().useCarting() && brain.crossbowChargeController.tick()) {
            return true;
        } else {
            boolean seesShield = brain.axeController.tickSeesShield(brain.target);
            if (ctx.settings().techniques().hotbarShieldTracking && !brain.healing && brain.target != null) {
                if (brain.shieldReraiseCd > 0) {
                    brain.shieldReraiseCd--;
                }

                if (brain.tickCount % 20 == 0 && brain.findHotbarTag(Tag.ITEMS_SWORDS) >= 0) {
                    PlayerInventory inv = bot.getInventory();
                    boolean inHotbar = false;

                    for (int i = 0; i < 9; i++) {
                        if (ItemKinds.is(inv.getItem(i), Material.SHIELD)) {
                            inHotbar = true;
                            break;
                        }
                    }

                    if (!inHotbar) {
                        for (int i = 9; i < 36; i++) {
                            if (ItemKinds.is(inv.getItem(i), Material.SHIELD)) {
                                InventoryUtils.moveToHotbarSafe(ctx, i);
                                break;
                            }
                        }
                    }
                }

                int shieldSlot = -1;
                PlayerInventory inv2 = bot.getInventory();

                for (int i = 0; i < 9; i++) {
                    if (ItemKinds.is(inv2.getItem(i), Material.SHIELD)) {
                        shieldSlot = i;
                        break;
                    }
                }

                boolean charging = brain.dist <= ctx.tuning().number(TuningKey.WEAPON_SHIELD_CHARGE_RANGE).doubleValue()
                        && brain.target instanceof Player tp && tp.isSprinting();
                if (shieldSlot >= 0 && charging && !bot.isHandRaised() && brain.shieldHoldTicks <= 0 && brain.shieldReraiseCd <= 0) {
                    InventoryUtils.select(ctx, shieldSlot);
                    ctx.hands().useItem(EquipmentSlot.HAND);
                    brain.shieldHoldTicks = ctx.tuning().integer(TuningKey.WEAPON_SHIELD_HOLD_TICKS);
                    brain.melee.lookAtTarget();
                    return true;
                }

                if (brain.shieldHoldTicks > 0) {
                    brain.shieldHoldTicks--;
                    if (brain.shieldHoldTicks == 0) {
                        brain.shieldReraiseCd = ctx.tuning().integer(TuningKey.WEAPON_SHIELD_RERAISE_COOLDOWN);
                    }

                    if (brain.shieldHoldTicks == 0 && bot.isHandRaised()) {
                        ctx.hands().releaseUseItem();
                    } else if (charging) {
                        brain.melee.lookAtTarget();
                        return true;
                    }
                }
            } else {
                brain.melee.releaseHotbarShield();
            }

            if (brain.target != null && brain.targetBlocking()) {
                brain.blockingTicks++;
                TechniqueConfig asCfg = ctx.settings().techniques();
                if (asCfg.groundBackstab
                        && !asCfg.stunSlamEnabled()
                        && bot.isOnGround()
                        && brain.dist <= ctx.tuning().number(TuningKey.WEAPON_BACKSTAB_RANGE).doubleValue()
                        && brain.blockingTicks >= ctx.tuning().integer(TuningKey.WEAPON_BACKSTAB_MIN_BLOCKING)
                        && brain.backstabTicks < ctx.tuning().integer(TuningKey.WEAPON_BACKSTAB_MAX_TICKS)) {
                    brain.backstabTicks++;
                    Vec3 look = Ent.look(brain.target).normalize();
                    Vec3 toBot = Ent.pos(bot).subtract(Ent.pos(brain.target)).normalize();
                    double dot = look.x() * toBot.x() + look.z() * toBot.z();
                    if (!(dot < ctx.tuning().number(TuningKey.WEAPON_BACKSTAB_FACING_DOT).doubleValue())) {
                        Vec3 cross = look.cross(new Vec3(0.0, 1.0, 0.0));
                        boolean left = cross.x() * toBot.x() + cross.z() * toBot.z() > 0.0;
                        ctx.controls().setMovement(
                                ctx.tuning().number(TuningKey.WEAPON_BACKSTAB_FORWARD).floatValue(), left ? 1.0F : -1.0F);
                        brain.melee.lookAtTarget();
                        return true;
                    }

                    int sw = brain.findHotbarTag(Tag.ITEMS_SWORDS);
                    if (sw >= 0
                            && Ent.attackStrength(bot, 0.0F) > ctx.tuning().number(TuningKey.WEAPON_BACKSTAB_CHARGE).floatValue()) {
                        SwapEngine.syncedAttack(ctx, sw, brain.target);
                        brain.backstabTicks = 0;
                        return true;
                    }
                }

                if (asCfg.shieldDrain
                        && bot.isOnGround()
                        && brain.dist <= ctx.tuning().number(TuningKey.WEAPON_SHIELD_DRAIN_RANGE).doubleValue()
                        && brain.blockingTicks >= ctx.tuning().integer(TuningKey.WEAPON_SHIELD_DRAIN_MIN_BLOCKING)
                        && brain.target.getNoDamageTicks() <= 10) {
                    int axe = brain.findHotbarTag(Tag.ITEMS_AXES);
                    if (axe >= 0) {
                        SwapEngine.syncedAttack(ctx, axe, brain.target);
                        return true;
                    }
                }
            } else {
                brain.blockingTicks = 0;
                brain.backstabTicks = 0;
            }

            tickBowVolley();
            brain.weapon = !brain.healing
                            && !brain.cartController.isExecuting()
                            && !brain.crystalPvpController.isExecuting()
                            && !brain.anchorPvpController.isExecuting()
                            && !brain.spearMaceController.isExecuting()
                            && !brain.tridentMaceController.isExecuting()
                            && !brain.cobwebTrapController.isPlacing()
                            && !brain.jumpedForCrit
                    ? brain.weaponSelector.selectWeapon(brain.target, brain.dist, seesShield)
                    : WeaponSelector.WeaponType.SWORD;

            brain.spearMaceOffered = false;
            if (brain.aiTier == BotAiTier.FULL
                    && !brain.healing
                    && !brain.cpvpMode
                    && brain.dist > ctx.tuning().number(TuningKey.WEAPON_SPEAR_MACE_MIN_DIST).doubleValue()
                    && brain.dist <= ctx.tuning().number(TuningKey.WEAPON_SPEAR_MACE_MAX_DIST).doubleValue()
                    && brain.maySpecial(brain.spearMaceController.isExecuting())) {
                brain.spearMaceOffered = true;
                if (brain.spearMaceController.tick(brain.target, brain.dist)) {
                    brain.lastMaceComboWasSpear = true;
                    return true;
                }
            }

            if (brain.aiTier == BotAiTier.FULL && brain.cartHealthOk && !brain.healing && brain.cartController.wantsToChaseForCart(brain.target, brain.dist)) {
                brain.rangedController.reset();
                brain.pathFollower.chase(brain.target, Ent.pos(brain.target));
                brain.melee.lookAtTarget();
                ctx.controls().setSprinting(true);
                brain.bunnyHopController.tick(true);
                return true;
            } else if (brain.healing
                    || brain.cpvpMode
                    || brain.weapon != WeaponSelector.WeaponType.BOW && brain.weapon != WeaponSelector.WeaponType.CROSSBOW) {
                return false;
            } else {
                brain.shieldController.reset();
                brain.rangedController.tick(brain.target, brain.weapon);
                brain.specials.manageRangedPosition(brain.dist);
                return true;
            }
        }
    }

    // Default: two arrows per volley, then the sword closes in for 80 ticks before the bow is allowed again.
    private void tickBowVolley() {
        if (!brain.weaponSelector.bowLocked(brain.dist)
                && RangedController.volleySpent(brain.rangedController.shotsFired() - brain.bowShotBase, ctx.tuning().integer(TuningKey.WEAPON_BOW_VOLLEY_SHOTS))) {
            brain.weaponSelector.lockBow(ctx.tick() + ctx.tuning().integer(TuningKey.WEAPON_BOW_CHASE_TICKS));
            brain.bowShotBase = brain.rangedController.shotsFired();
            brain.rangedController.reset();
        }
    }
}
