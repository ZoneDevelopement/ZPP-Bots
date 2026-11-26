package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.SwapEngine;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class SwapTechniques {

    private final BotContext ctx;
    private final Player bot;
    private int cdMaceSwap;
    private int cdSpearMaceSwap;
    private int cdSwordWb;
    private int cdBreach;
    private int cdReach;
    private int cdDetapSpear;
    private int cdDetapBreach;
    private int detapStep = 0;
    private long detapDue = -1L;
    private LivingEntity detapTarget;
    private int detapPrevSlot = -1;
    private boolean launchedThisTick = false;

    public SwapTechniques(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean consumeLaunchFlag() {
        boolean f = launchedThisTick;
        launchedThisTick = false;
        return f;
    }

    public void tickCooldowns() {
        if (cdMaceSwap > 0) {
            cdMaceSwap--;
        }

        if (cdSpearMaceSwap > 0) {
            cdSpearMaceSwap--;
        }

        if (cdSwordWb > 0) {
            cdSwordWb--;
        }

        if (cdBreach > 0) {
            cdBreach--;
        }

        if (cdReach > 0) {
            cdReach--;
        }

        if (cdDetapSpear > 0) {
            cdDetapSpear--;
        }

        if (cdDetapBreach > 0) {
            cdDetapBreach--;
        }
    }

    public boolean isExecuting() {
        return detapStep > 0;
    }

    public void reset() {
        if (detapStep > 0 && bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        if (detapStep > 0 && detapPrevSlot >= 0) {
            InventoryUtils.select(ctx, detapPrevSlot);
        }

        detapStep = 0;
        detapDue = -1L;
        detapTarget = null;
        detapPrevSlot = -1;
    }

    public boolean maybeSwapAttack(LivingEntity target, double dist) {
        launchedThisTick = false;
        tickCooldowns();
        if (detapStep > 0) {
            return driveDetap();
        } else if (target != null && Ent.alive(target)) {
            TechniqueConfig cfg = ctx.settings().techniques();
            long now = ctx.world().gameTime();
            boolean falling = bot.getFallDistance() > 1.5 && !bot.isGliding();
            float charge = Ent.attackStrength(bot, 0.0F);
            if (cfg.detapSpearMace
                    && cdDetapSpear <= 0
                    && target.getHealth() <= ctx.tuning().number(TuningKey.SWAP_DETAP_HP).floatValue()
                    && dist >= ctx.tuning().number(TuningKey.SWAP_DETAP_MIN_DIST).doubleValue()
                    && dist <= ctx.tuning().number(TuningKey.SWAP_SPEAR_MAX_DIST).doubleValue()
                    && findSpear() >= 0
                    && findMace(false) >= 0) {
                detapStep = 1;
                detapDue = now;
                detapTarget = target;
                detapPrevSlot = InventoryUtils.selected(ctx);
                cdDetapSpear = ctx.tuning().integer(TuningKey.SWAP_DETAP_SPEAR_COOLDOWN);
                return driveDetap();
            } else if (cfg.detapBreach
                    && cdDetapBreach <= 0
                    && target.getHealth() <= ctx.tuning().number(TuningKey.SWAP_DETAP_HP).floatValue()
                    && dist <= 3.0
                    && target.getNoDamageTicks() <= 10
                    && findMace(true) >= 0) {
                SwapEngine.syncedAttack(ctx, findMace(true), target);
                restore();
                cdDetapBreach = ctx.tuning().integer(TuningKey.SWAP_DETAP_BREACH_COOLDOWN);
                return true;
            } else if (charge <= ctx.tuning().number(TuningKey.SWAP_MIN_CHARGE).floatValue()) {
                return false;
            } else {
                int swordSlot = findSword();
                if (cfg.breachSwap && cdBreach <= 0 && swordSlot >= 0 && dist <= 3.0 && armorValue(target) >= ctx.tuning().integer(TuningKey.SWAP_BREACH_ARMOR)) {
                    int m = findMace(true);
                    if (m >= 0 && heldIsSynced(swordSlot)) {
                        SwapEngine.staleSwapAttack(ctx, m, target);
                        restoreTo(swordSlot);
                        cdBreach = ctx.tuning().integer(TuningKey.SWAP_BREACH_COOLDOWN);
                        return true;
                    }
                }

                if (cfg.swordWbSwap && cdSwordWb <= 0 && swordSlot >= 0 && falling && dist <= 3.0) {
                    int m = findWindBurstMace();
                    if (m >= 0 && heldIsSynced(swordSlot)) {
                        SwapEngine.staleSwapAttack(ctx, m, target);
                        restoreTo(swordSlot);
                        cdSwordWb = ctx.tuning().integer(TuningKey.SWAP_SWORD_WB_COOLDOWN);
                        launchedThisTick = true;
                        return true;
                    }
                }

                if (cfg.spearMaceSwap && cdSpearMaceSwap <= 0 && falling && dist <= ctx.tuning().number(TuningKey.SWAP_SPEAR_MAX_DIST).doubleValue()) {
                    int sp = findSpear();
                    int m = findMace(false);
                    if (sp >= 0 && m >= 0 && heldIsSynced(sp)) {
                        SwapEngine.staleSwapAttack(ctx, m, target);
                        restoreTo(sp);
                        cdSpearMaceSwap = ctx.tuning().integer(TuningKey.SWAP_SPEAR_MACE_COOLDOWN);
                        return true;
                    }
                }

                if (cfg.maceSwap && cdMaceSwap <= 0 && dist <= 3.0) {
                    int m = findMace(false);
                    if (m >= 0 && (falling || armorValue(target) < ctx.tuning().integer(TuningKey.SWAP_BREACH_ARMOR))) {
                        int wc = findWindCharge();
                        int pre = wc >= 0 && heldIsSynced(wc) ? wc : (swordSlot >= 0 && heldIsSynced(swordSlot) ? swordSlot : -1);
                        if (pre >= 0) {
                            SwapEngine.staleSwapAttack(ctx, m, target);
                            restoreTo(pre);
                            cdMaceSwap = ctx.tuning().integer(TuningKey.SWAP_MACE_COOLDOWN);
                            return true;
                        }
                    }
                }

                if (cfg.spearReachSwap && cdReach <= 0 && swordSlot >= 0 && dist > 3.0 && dist <= ctx.tuning().number(TuningKey.SWAP_REACH_MAX_DIST).doubleValue() && target.getNoDamageTicks() <= 10) {
                    int sp = findSpear();
                    if (sp >= 0 && heldIsSynced(sp)) {
                        SwapEngine.syncedAttack(ctx, swordSlot, target);
                        restoreTo(sp);
                        cdReach = ctx.tuning().integer(TuningKey.SWAP_REACH_COOLDOWN);
                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private static int armorValue(LivingEntity entity) {
        return (int) Ent.attr(entity, Attribute.ARMOR);
    }

    private boolean driveDetap() {
        long now = ctx.world().gameTime();
        if (detapTarget == null || !Ent.alive(detapTarget)) {
            reset();
            return false;
        } else if (now > detapDue + 5L) {
            reset();
            return false;
        } else if (now < detapDue) {
            return true;
        } else {
            int spear = findSpear();
            int mace = findMace(false);
            boolean inReach = Ent.dist(bot, detapTarget) <= 3.0 && bot.hasLineOfSight(detapTarget);
            switch (detapStep) {
                case 1:
                    if (spear < 0) {
                        reset();
                        return false;
                    }

                    InventoryUtils.select(ctx, spear);
                    SwapEngine.useDown(ctx, EquipmentSlot.HAND);
                    detapStep = 2;
                    detapDue = now + 1L;
                    return true;
                case 2:
                    if (ItemKinds.isSpear(bot.getInventory().getItemInMainHand()) && bot.isHandRaised()) {
                        ctx.hands().stab();
                    }

                    detapStep = 3;
                    detapDue = now + 1L;
                    return true;
                case 3:
                    SwapEngine.useUp(ctx);
                    detapStep = bot.getFallDistance() > 1.5 && !bot.isGliding() ? 4 : 5;
                    detapDue = now + (detapStep == 4 ? 1 : 10);
                    return true;
                case 4:
                    if (mace >= 0 && inReach) {
                        SwapEngine.syncedAttack(ctx, mace, detapTarget);
                    }

                    restore();
                    reset();
                    return true;
                default:
                    int sword = findSword();
                    if (sword >= 0 && inReach) {
                        SwapEngine.syncedAttack(ctx, sword, detapTarget);
                    }

                    restore();
                    reset();
                    return true;
            }
        }
    }

    private void restore() {
        restoreTo(detapPrevSlot >= 0 ? detapPrevSlot : findSword());
    }

    private void restoreTo(int slot) {
        if (slot >= 0 && slot <= 8) {
            InventoryUtils.select(ctx, slot);
        }
    }

    private boolean heldIsSynced(int slot) {
        return InventoryUtils.selected(ctx) == slot;
    }

    private int findSword() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.isSword(inv.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private int findSpear() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!ItemKinds.isEmpty(s) && ItemKinds.isSpear(s)) {
                return i;
            }
        }

        return -1;
    }

    private int findMace(boolean preferBreach) {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        int bestLvl = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, Material.MACE)) {
                int lvl = EnchantmentUtils.getEnchantLevel(s, "breach");
                if (preferBreach) {
                    if (lvl > bestLvl) {
                        bestLvl = lvl;
                        best = i;
                    }
                } else if (best < 0) {
                    best = i;
                }
            }
        }

        return preferBreach && bestLvl < 1 ? -1 : best;
    }

    private int findWindCharge() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.WIND_CHARGE)) {
                return i;
            }
        }

        return -1;
    }

    private int findWindBurstMace() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, Material.MACE) && EnchantmentUtils.getEnchantLevel(s, "wind_burst") >= 1) {
                return i;
            }
        }

        return -1;
    }
}
