package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.ai.gear.OffhandManager;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.StunSlam;
import dev.nandi0813.zppbots.util.SwapEngine;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class SpearMaceController {

    private static final double FULL_DASH_DIST = 14.0;
    private static final double SHORT_DASH_DIST = 5.0;
    private static final float DASH_PITCH_FAR = 0.0F;
    private static final float DASH_PITCH_NEAR = 40.0F;
    private static final int MOMENTUM_LEAD_TICKS = 8;
    private static final double SPEAR_REACH = 4.5;
    private static final int DASH_INTERVAL_TICKS = 4;
    private static final double MIN_FALL_DIST = 1.5;
    private static final double WINDBURST_LAUNCH_VEL = 0.4;
    private static final int MAX_RISING_TICKS = 60;
    private static final int MAX_DASH_TICKS = 90;
    private static final int MAX_SMASH_TICKS = 50;
    private static final int MAX_RECOVERY_TICKS = 30;
    private static final double AIRBORNE_START_VEL = 0.4;

    private final BotContext ctx;
    private final Player bot;
    private final OffhandManager offhand;
    private final StunSlam stunSlam = new StunSlam();
    private int dashesThisCombo = 0;
    private double lastDashGap = Double.MAX_VALUE;
    private boolean flyPastHit = false;
    private int lastDashTick = -100;
    private int tickCounter = 0;
    private boolean spearPopActive = false;
    private boolean spearPopOffhand = false;
    private int spearStunStep = 0;
    private boolean wcCancelUsed = false;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldown = 0;
    private int spearSlot = -1;
    private int maceSlot = -1;
    private int windChargeSlot = -1;
    private int prevSlot = 0;

    public SpearMaceController(BotContext ctx, OffhandManager offhand) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.offhand = offhand;
    }

    private double smashRange() {
        return ctx.settings().attackRange();
    }

    private void endSpearPop() {
        if (bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        if (spearPopOffhand) {
            offhand.unpark();
            spearPopOffhand = false;
        }

        spearPopActive = false;
        InventoryUtils.select(ctx, maceSlot);
    }

    public boolean tick(LivingEntity target, double dist) {
        tickCounter++;
        if (cooldown > 0) {
            cooldown--;
        }
        return switch (phase) {
            case IDLE -> tickIdle(target, dist);
            case LAUNCHING -> tickLaunching();
            case RISING -> tickRising(target);
            case DASHING -> tickDashing(target);
            case SMASHING -> tickSmashing(target);
            case RECOVERY -> tickRecovery(target);
        };
    }

    public boolean canCloseGap(double dist) {
        if (cooldown > 0) {
            return false;
        } else if (!(dist < ctx.tuning().number(TuningKey.SPEAR_MACE_CLOSE_GAP_MIN).doubleValue())
                && !(dist > ctx.tuning().number(TuningKey.SPEAR_MACE_TRIGGER_RANGE).doubleValue())) {
            TechniqueConfig cfg = ctx.settings().techniques();
            return cfg.spearMacing && comboItemsPresent();
        } else {
            return false;
        }
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public void reset() {
        if (phase != Phase.IDLE && bot.isOnGround()) {
            abort();
        }
    }

    private boolean tickIdle(LivingEntity target, double dist) {
        if (!ctx.settings().techniques().spearMacing) {
            return false;
        } else {
            if (spearSlot >= 0) {
                recoverSpearToHotbar();
            }

            if (target == null || !Ent.alive(target)) {
                return false;
            } else if (cooldown > 0) {
                return false;
            } else if (dist > ctx.tuning().number(TuningKey.SPEAR_MACE_TRIGGER_RANGE).doubleValue()) {
                return false;
            } else {
                boolean onGroundStart = bot.isOnGround();
                Vec3 pos = Ent.pos(bot);
                Vec3 tpos = Ent.pos(target);
                double dxIdle = pos.x() - tpos.x();
                double dzIdle = pos.z() - tpos.z();
                double horizIdle = Math.sqrt(dxIdle * dxIdle + dzIdle * dzIdle);
                double minHeight = ctx.tuning().number(TuningKey.SPEAR_MACE_AIRBORNE_DASH_MIN_HEIGHT).doubleValue();
                double closeGapMin = ctx.tuning().number(TuningKey.SPEAR_MACE_CLOSE_GAP_MIN).doubleValue();
                boolean airborneAbove = !onGroundStart && target.isOnGround() && pos.y() - tpos.y() >= minHeight
                        && horizIdle >= closeGapMin;
                boolean airborneRising = !onGroundStart && !airborneAbove && Ent.velocity(bot).y() >= AIRBORNE_START_VEL;
                if (!onGroundStart && !airborneAbove && !airborneRising) {
                    return false;
                } else if (onGroundStart && bot.hasCooldown(new ItemStack(Material.WIND_CHARGE))) {
                    return false;
                } else {
                    spearSlot = findLungeSpearSlot();
                    maceSlot = findWindBurstMaceSlot();
                    windChargeSlot = findWindChargeSlot();
                    if (spearSlot < 0 || maceSlot < 0) {
                        return false;
                    } else if (onGroundStart && windChargeSlot < 0) {
                        return false;
                    } else if (!ctx.settings().roll()) {
                        return false;
                    } else {
                        prevSlot = InventoryUtils.selected(ctx);
                        if (onGroundStart) {
                            enterPhase(Phase.LAUNCHING);
                        } else if (airborneAbove) {
                            enterPhase(Phase.DASHING);
                        } else {
                            enterPhase(Phase.RISING);
                        }

                        return true;
                    }
                }
            }
        }
    }

    private boolean tickLaunching() {
        phaseTick++;
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        InventoryUtils.select(ctx, windChargeSlot);
        ctx.controls().look(bot.getLocation().getYaw(), 80.0F);
        if (phaseTick >= 2) {
            ctx.controls().jumpOnce();
            fireWindCharge();
            enterPhase(Phase.RISING);
        }

        return true;
    }

    private boolean tickRising(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (!comboItemsPresent()) {
            abort();
            return false;
        } else if (phaseTick > MAX_RISING_TICKS) {
            abort();
            return false;
        } else {
            recoverSpearToHotbar();
            InventoryUtils.select(ctx, holdSlotForCharge());
            faceTarget(target);
            driftToward(target);
            double yVel = Ent.velocity(bot).y();
            double peakThreshold = -0.05 - ctx.random().nextDouble() * 0.03;
            if (yVel >= peakThreshold) {
                return true;
            } else {
                Vec3 pos = Ent.pos(bot);
                Vec3 tpos = Ent.pos(target);
                double dx = pos.x() - tpos.x();
                double dz = pos.z() - tpos.z();
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                double triggerBase = ctx.tuning().number(TuningKey.SPEAR_MACE_DASH_TRIGGER_BASE).doubleValue();
                double triggerFuzzRange = ctx.tuning().number(TuningKey.SPEAR_MACE_DASH_TRIGGER_FUZZ).doubleValue();
                double triggerFuzz = triggerBase + (ctx.random().nextDouble() - 0.5) * triggerFuzzRange;
                boolean dashAllowed = ctx.settings().useSpearDash();
                if (!(horizDist <= triggerFuzz) && dashAllowed) {
                    enterPhase(Phase.DASHING);
                } else {
                    InventoryUtils.select(ctx, maceSlot);
                    enterPhase(Phase.SMASHING);
                }

                return true;
            }
        }
    }

    private boolean tickDashing(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (!comboItemsPresent()) {
            abort();
            return false;
        } else if (phaseTick <= MAX_DASH_TICKS && !bot.isOnGround()) {
            recoverSpearToHotbar();
            Vec3 pos = Ent.pos(bot);
            Vec3 tpos = Ent.pos(target);
            double gapPast = Math.hypot(pos.x() - tpos.x(), pos.z() - tpos.z());
            boolean leaving = gapPast > lastDashGap
                    + ctx.tuning().number(TuningKey.SPEAR_MACE_OVERSHOOT_SPEED).doubleValue();
            lastDashGap = gapPast;
            float jabCharge = ctx.tuning().number(TuningKey.SPEAR_MACE_JAB_CHARGE).floatValue();
            if (leaving && spearSlot >= 0 && Ent.dist(bot, target) <= SPEAR_REACH
                    && Ent.attackStrength(bot, 0.0F) > jabCharge) {
                faceTarget(target);
                performFlyPastJab(target);
                flyPastHit = true;
                return true;
            } else {
                faceForDash(target);
                double horizSpeed = Ent.velocity(bot).horizontalDistance();
                TechniqueConfig smCfg = ctx.settings().techniques();
                boolean chainOk = smCfg.dashChain || dashesThisCombo == 0;
                double gapNow = Math.hypot(pos.x() - tpos.x(), pos.z() - tpos.z());
                if (smCfg.dashChain
                        && gapNow > FULL_DASH_DIST
                        && Ent.velocity(bot).y() < -0.35
                        && horizSpeed < 0.35
                        && windChargeSlot >= 0
                        && windChargeSlot <= 8
                        && SwapEngine.fireWindChargeDown(ctx, windChargeSlot)) {
                    return true;
                } else {
                    double correctingGap = ctx.tuning().number(TuningKey.SPEAR_MACE_CORRECTING_GAP).doubleValue();
                    boolean correcting = flyPastHit || gapNow > correctingGap && leaving;
                    double correctingSpeed = ctx.tuning().number(TuningKey.SPEAR_MACE_CORRECTING_SPEED).doubleValue();
                    double dashChainSpeed = ctx.tuning().number(TuningKey.SPEAR_MACE_DASH_CHAIN_SPEED).doubleValue();
                    double speedGate = correcting ? correctingSpeed : dashChainSpeed;
                    boolean cadenceOk = tickCounter - lastDashTick >= DASH_INTERVAL_TICKS;
                    if (cadenceOk && horizSpeed < speedGate && chainOk) {
                        flyPastHit = false;
                        dashesThisCombo++;
                        performSpearDash(target);
                    } else {
                        InventoryUtils.select(ctx, holdSlotForCharge());
                    }

                    double dx = pos.x() - tpos.x();
                    double dz = pos.z() - tpos.z();
                    double horizDist = Math.sqrt(dx * dx + dz * dz);
                    double aboveFuzz = ctx.tuning().number(TuningKey.SPEAR_MACE_ABOVE_FUZZ).doubleValue();
                    double aboveThreshFuzz = ctx.tuning().number(TuningKey.SPEAR_MACE_ABOVE_HORIZ).doubleValue()
                            + ctx.random().nextDouble() * aboveFuzz;
                    if (horizDist <= aboveThreshFuzz) {
                        InventoryUtils.select(ctx, maceSlot);
                        enterPhase(Phase.SMASHING);
                    }

                    return true;
                }
            }
        } else {
            recoverSpearToHotbar();
            InventoryUtils.select(ctx, maceSlot);
            enterPhase(Phase.SMASHING);
            return true;
        }
    }

    private boolean tickSmashing(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (!comboItemsPresent()) {
            abort();
            return false;
        } else if (phaseTick > MAX_SMASH_TICKS) {
            abort();
            return false;
        } else {
            recoverSpearToHotbar();
            InventoryUtils.select(ctx, maceSlot);
            faceTarget(target);
            driftToward(target);
            double dist = Ent.dist(bot, target);
            boolean inRange = dist <= smashRange();
            boolean hasFall = bot.getFallDistance() > MIN_FALL_DIST && MaceSmashGates.botMayUseSmashFallBonus(ctx);
            boolean landed = bot.isOnGround() && phaseTick > 3;
            TechniqueConfig smTech = ctx.settings().techniques();
            boolean fastFall = Ent.velocity(bot).y() <= -0.3;
            boolean blocking = target instanceof HumanEntity human && human.isBlocking();
            double popMinDist = ctx.tuning().number(TuningKey.SPEAR_MACE_POP_MIN_DIST).doubleValue();
            double popMaxDist = ctx.tuning().number(TuningKey.SPEAR_MACE_POP_MAX_DIST).doubleValue();
            double stunMaxDist = ctx.tuning().number(TuningKey.SPEAR_MACE_STUN_MAX_DIST).doubleValue();
            double wcFallSpeed = ctx.tuning().number(TuningKey.SPEAR_MACE_WC_CANCEL_FALL_SPEED).doubleValue();
            double wcHeight = ctx.tuning().number(TuningKey.SPEAR_MACE_WC_CANCEL_HEIGHT).doubleValue();
            if (smTech.spearDoublePop && !blocking && fastFall && dist >= popMinDist && dist <= popMaxDist
                    && spearSlot >= 0) {
                if (!spearPopActive) {
                    spearPopActive = true;
                    if (smTech.spearDoublePopOffhand) {
                        spearPopOffhand = offhand.park(spearSlot, 30);
                    }

                    if (!spearPopOffhand) {
                        InventoryUtils.select(ctx, spearSlot);
                    }

                    SwapEngine.useDown(ctx, spearPopOffhand ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
                }

                faceTarget(target);
                if (landed || phaseTick > 46) {
                    endSpearPop();
                }

                return true;
            } else {
                if (spearPopActive) {
                    endSpearPop();
                }

                if (spearStunStep == 1 && fastFall && dist >= popMinDist && dist <= stunMaxDist && spearSlot >= 0) {
                    InventoryUtils.select(ctx, spearSlot);
                    SwapEngine.useDown(ctx, EquipmentSlot.HAND);
                    spearPopActive = true;
                    spearStunStep = 0;
                    faceTarget(target);
                    return true;
                } else {
                    if (smTech.spearStunSlam && blocking && fastFall && dist >= popMinDist && dist <= stunMaxDist
                            && spearSlot >= 0 && hasFall) {
                        int axe = findAxeSlot();
                        if (axe >= 0 && spearStunStep == 0 && target.getNoDamageTicks() <= 10) {
                            SwapEngine.syncedAttack(ctx, axe, target);
                            spearStunStep = 1;
                            return true;
                        }
                    }

                    var below = Ent.blockPos(bot);
                    if (smTech.wcCancel
                            && !wcCancelUsed
                            && !inRange
                            && !landed
                            && Ent.velocity(bot).y() < wcFallSpeed
                            // Bukkit's highest-block Y is one below the heightmap position
                            && Ent.pos(bot).y() - (ctx.world().surfaceY(below.getX(), below.getZ()) + 1) < wcHeight
                            && windChargeSlot >= 0
                            && windChargeSlot <= 8
                            && SwapEngine.fireWindChargeDown(ctx, windChargeSlot)) {
                        wcCancelUsed = true;
                        enterPhase(Phase.RISING);
                        return true;
                    } else if (inRange && hasFall) {
                        if (bot.hasLineOfSight(target)) {
                            ctx.controls().setSprinting(true);
                            TechniqueConfig ssCfg = ctx.settings().techniques();
                            StunSlam.Result ssRes = stunSlam.tick(ctx, target, findAxeSlot(), maceSlot, ssCfg.stunSlamVariant, ssCfg.stunSlam);
                            if (ssRes == StunSlam.Result.HOLD) {
                                return true;
                            } else {
                                if (ssRes != StunSlam.Result.SMASHED) {
                                    InventoryUtils.select(ctx, maceSlot);
                                    ctx.hands().attack(target);
                                    ctx.hands().swing(EquipmentSlot.HAND);
                                }

                                InventoryUtils.select(ctx, holdSlotForCharge());
                                enterPhase(Phase.RECOVERY);
                                return true;
                            }
                        } else if (landed) {
                            abort();
                            return false;
                        } else {
                            return true;
                        }
                    } else if (landed) {
                        abort();
                        return false;
                    } else {
                        return true;
                    }
                }
            }
        }
    }

    private boolean tickRecovery(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (!comboItemsPresent()) {
            abort();
            return false;
        } else if (phaseTick > MAX_RECOVERY_TICKS) {
            abort();
            return false;
        } else {
            recoverSpearToHotbar();
            PlayerInventory inv = bot.getInventory();
            InventoryUtils.select(ctx, holdSlotForCharge());
            if (Ent.velocity(bot).y() >= WINDBURST_LAUNCH_VEL) {
                enterPhase(Phase.RISING);
                return true;
            } else if (ctx.settings().techniques().wcSelfReset
                    && !bot.isOnGround()
                    && phaseTick <= 3
                    && windChargeSlot >= 0
                    && ItemKinds.is(inv.getItem(windChargeSlot), Material.WIND_CHARGE)
                    && SwapEngine.fireWindChargeDown(ctx, windChargeSlot)) {
                enterPhase(Phase.RISING);
                return true;
            } else if (bot.isOnGround() && phaseTick > 5) {
                abort();
                return false;
            } else {
                return true;
            }
        }
    }

    private void enterPhase(Phase p) {
        if (p == Phase.IDLE) {
            dashesThisCombo = 0;
            wcCancelUsed = false;
        }

        if (p != Phase.SMASHING) {
            spearStunStep = 0;
        }

        if (p == Phase.DASHING) {
            lastDashGap = Double.MAX_VALUE;
            flyPastHit = false;
        }

        if (spearPopActive && p != Phase.SMASHING) {
            endSpearPop();
        }

        phase = p;
        phaseTick = 0;
    }

    private void abort() {
        if (spearPopActive) {
            endSpearPop();
        }

        recoverSpearToHotbar();
        InventoryUtils.select(ctx, prevSlot);
        cooldown = ctx.tuning().integer(TuningKey.SPEAR_MACE_ABORT_COOLDOWN);
        enterPhase(Phase.IDLE);
    }

    private void performSpearDash(LivingEntity target) {
        if (bot.hasLineOfSight(target)) {
            PlayerInventory inv = bot.getInventory();
            ItemStack spear = inv.getItem(spearSlot);
            if (getEnchantLevel(spear, "lunge") >= 3 && ItemKinds.isSpear(spear)) {
                if (!(Ent.dist(bot, target) <= SHORT_DASH_DIST)) {
                    Vec3 pos = Ent.pos(bot);
                    Vec3 tpos = Ent.pos(target);
                    double gap = Math.hypot(pos.x() - tpos.x(), pos.z() - tpos.z());
                    double lead = Ent.velocity(bot).horizontalDistance() * MOMENTUM_LEAD_TICKS;
                    if (!(gap - lead <= ctx.tuning().number(TuningKey.SPEAR_MACE_ABOVE_HORIZ).doubleValue())) {
                        InventoryUtils.select(ctx, spearSlot);
                        ctx.hands().stab();
                        lastDashTick = tickCounter;
                        InventoryUtils.select(ctx, holdSlotForCharge());
                    }
                }
            }
        }
    }

    private void performFlyPastJab(LivingEntity target) {
        ItemStack spear = bot.getInventory().getItem(spearSlot);
        if (ItemKinds.isSpear(spear)) {
            InventoryUtils.select(ctx, spearSlot);
            ctx.hands().stab();
            bot.resetCooldown();
            InventoryUtils.select(ctx, holdSlotForCharge());
        }
    }

    private void faceForDash(LivingEntity target) {
        Vec3 pos = Ent.pos(bot);
        Vec3 tpos = Ent.pos(target);
        double dx = tpos.x() - pos.x();
        double dz = tpos.z() - pos.z();
        double d = Math.sqrt(dx * dx + dz * dz);
        d = Math.max(0.0, d - Ent.velocity(bot).horizontalDistance() * MOMENTUM_LEAD_TICKS);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch;
        if (d >= FULL_DASH_DIST) {
            pitch = DASH_PITCH_FAR;
        } else {
            double t = Math.max(0.0, Math.min(1.0, (d - SHORT_DASH_DIST) / (FULL_DASH_DIST - SHORT_DASH_DIST)));
            pitch = (float) (DASH_PITCH_NEAR - DASH_PITCH_NEAR * t);
        }

        ctx.controls().look(yaw, pitch);
    }

    private int holdSlotForCharge() {
        return windChargeSlot >= 0 && ItemKinds.is(bot.getInventory().getItem(windChargeSlot), Material.WIND_CHARGE)
                ? windChargeSlot
                : maceSlot;
    }

    private void fireWindCharge() {
        SwapEngine.fireWindCharge(ctx, windChargeSlot, 80.0F);
    }

    private void faceTarget(LivingEntity target) {
        Vec3 eye = Ent.eyePos(bot);
        Vec3 tpos = Ent.pos(target);
        double dx = tpos.x() - eye.x();
        double dy = tpos.y() - eye.y();
        double dz = tpos.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        ctx.controls().look(yaw, pitch);
    }

    private void driftToward(LivingEntity target) {
        double dist = Ent.dist(bot, target);
        if (dist > ctx.tuning().number(TuningKey.SPEAR_MACE_DRIFT_STOP_DIST).doubleValue()) {
            ctx.controls().moveForward();
            ctx.controls().setSprinting(
                    dist > ctx.tuning().number(TuningKey.SPEAR_MACE_DRIFT_SPRINT_DIST).doubleValue());
        } else {
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
        }
    }

    private void recoverSpearToHotbar() {
        PlayerInventory inv = bot.getInventory();
        if (spearSlot < 0 || spearSlot >= 9 || getEnchantLevel(inv.getItem(spearSlot), "lunge") < 3) {
            int spearAt = -1;

            for (int i = 0; i < 36; i++) {
                if (getEnchantLevel(inv.getItem(i), "lunge") >= 3) {
                    spearAt = i;
                    break;
                }
            }

            if (spearAt >= 0) {
                if (spearAt < 9) {
                    spearSlot = spearAt;
                } else {
                    spearSlot = InventoryUtils.moveToHotbarSafe(ctx, spearAt);
                }
            }
        }
    }

    private boolean comboItemsPresent() {
        PlayerInventory inv = bot.getInventory();
        boolean spearAnywhere = false;
        boolean maceAnywhere = false;

        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!spearAnywhere && getEnchantLevel(s, "lunge") >= 3) {
                spearAnywhere = true;
            }

            if (!maceAnywhere && ItemKinds.is(s, Material.MACE) && getEnchantLevel(s, "wind_burst") >= 1) {
                maceAnywhere = true;
            }

            if (spearAnywhere && maceAnywhere) {
                return true;
            }
        }

        if (!spearAnywhere && spearPopOffhand && getEnchantLevel(inv.getItemInOffHand(), "lunge") >= 3) {
            spearAnywhere = true;
        }

        return spearAnywhere && maceAnywhere;
    }

    private int findAxeSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.isAxe(inv.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private int findLungeSpearSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (getEnchantLevel(inv.getItem(i), "lunge") >= 3) {
                return i;
            }
        }

        return -1;
    }

    private int findWindBurstMaceSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, Material.MACE) && getEnchantLevel(s, "wind_burst") >= 1) {
                return i;
            }
        }

        return -1;
    }

    private int findWindChargeSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.WIND_CHARGE)) {
                return i;
            }
        }

        return -1;
    }

    private static int getEnchantLevel(ItemStack stack, String enchantPath) {
        return EnchantmentUtils.getEnchantLevel(stack, enchantPath);
    }

    private enum Phase {
        IDLE,
        LAUNCHING,
        RISING,
        DASHING,
        SMASHING,
        RECOVERY
    }
}
