package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.ai.gear.ItemEvaluator;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.StunSlam;
import dev.nandi0813.zppbots.util.SwapEngine;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class MaceController {

    private static final int MAX_AIRBORNE_TICKS = 80;
    private static final int MAX_SMASH_TICKS = 40;
    private static final double MIN_FALL_DIST = 1.5;

    private final BotContext ctx;
    private final Player bot;
    private final StunSlam stunSlam = new StunSlam();
    private final DiveLead lead;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldownLeft = 0;
    private double launchY = 0.0;
    private double targetHeight = 0.0;
    private int prevSlot = 0;
    private int maceSlot = -1;
    private int windChargeSlot = -1;
    private boolean crouchBoostActive = false;

    public MaceController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.lead = new DiveLead(ctx);
    }

    private double attackRange() {
        return ctx.settings().attackRange();
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldownLeft > 0) {
            cooldownLeft--;
        }
        return switch (phase) {
            case IDLE -> tickIdle(target, dist);
            case LAUNCHING -> tickLaunching();
            case AIRBORNE -> tickAirborne(target);
            case SMASHING -> tickSmashing(target);
        };
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public boolean hasGearForCombo() {
        return findMaceSlot() >= 0 && findWindChargeSlot() >= 0;
    }

    public void reset() {
        if (phase != Phase.IDLE) {
            InventoryUtils.select(ctx, prevSlot);
        }

        phase = Phase.IDLE;
        phaseTick = 0;
    }

    private boolean tickIdle(LivingEntity target, double dist) {
        if (!ctx.settings().techniques().windchargeMace) {
            return false;
        } else if (target == null || !Ent.alive(target)) {
            return false;
        } else if (cooldownLeft > 0) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        } else if (dist > ctx.tuning().number(TuningKey.MACE_TRIGGER_RANGE).doubleValue()) {
            return false;
        } else if (ctx.settings().techniques().wcFullCooldownDiscipline && bot.hasCooldown(new ItemStack(Material.WIND_CHARGE))) {
            return false;
        } else {
            maceSlot = findMaceSlot();
            windChargeSlot = findWindChargeSlot();
            if (maceSlot < 0 || windChargeSlot < 0) {
                return false;
            } else if (!ctx.settings().roll()) {
                cooldownLeft = ctx.tuning().integer(TuningKey.MACE_RETRY_COOLDOWN);
                return false;
            } else {
                prevSlot = bot.getInventory().getHeldItemSlot();
                targetHeight = rollTargetHeight();
                enterPhase(Phase.LAUNCHING);
                return true;
            }
        }
    }

    private boolean tickLaunching() {
        phaseTick++;
        InventoryUtils.select(ctx, windChargeSlot);
        ctx.controls().look(bot.getLocation().getYaw(), 80.0F);
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        if (phaseTick >= 2) {
            launchY = Ent.pos(bot).y();
            boolean crouch = ctx.settings().techniques().wcCrouchBoost;
            if (crouch) {
                ctx.controls().setSneaking(true);
            }

            ctx.controls().jumpOnce();
            fireWindCharge();
            if (crouch) {
                crouchBoostActive = true;
            }

            enterPhase(Phase.AIRBORNE);
        }

        return true;
    }

    private boolean tickAirborne(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > MAX_AIRBORNE_TICKS) {
            abort();
            return false;
        } else {
            InventoryUtils.select(ctx, maceSlot);
            double heightGained = Ent.pos(bot).y() - launchY;
            double yVel = Ent.velocity(bot).y();
            faceTarget(target);
            driftToward(target);
            boolean reachedGoal = yVel < -0.05 && heightGained >= targetHeight;
            boolean alreadyFalling = yVel < -0.05 && phaseTick > 20;
            if (reachedGoal || alreadyFalling) {
                enterPhase(Phase.SMASHING);
            }

            return true;
        }
    }

    private boolean tickSmashing(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > MAX_SMASH_TICKS) {
            abort();
            return false;
        } else {
            InventoryUtils.select(ctx, maceSlot);
            faceTarget(target);
            driftToward(target);
            double dist = Ent.dist(bot, target);
            boolean inRange = dist <= attackRange();
            boolean hasFall = bot.getFallDistance() > MIN_FALL_DIST && MaceSmashGates.botMayUseSmashFallBonus(ctx);
            boolean landed = bot.isOnGround() && phaseTick > 3;
            if (inRange && hasFall) {
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

                        InventoryUtils.select(ctx, prevSlot);
                        cooldownLeft = ctx.tuning().integer(TuningKey.MACE_COOLDOWN_TICKS);
                        enterPhase(Phase.IDLE);
                        return false;
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

    private void enterPhase(Phase p) {
        phase = p;
        phaseTick = 0;
    }

    private void abort() {
        InventoryUtils.select(ctx, prevSlot);
        cooldownLeft = ctx.tuning().integer(TuningKey.MACE_RETRY_COOLDOWN);
        enterPhase(Phase.IDLE);
    }

    private void fireWindCharge() {
        SwapEngine.fireWindCharge(ctx, windChargeSlot, 80.0F);
    }

    private int findMaceSlot() {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        double bestS = -1.0;

        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, Material.MACE)) {
                double score = ItemEvaluator.scoreMace(s);
                if (score > bestS) {
                    bestS = score;
                    best = i;
                }
            }
        }

        return best;
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

    private int findWindChargeSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.WIND_CHARGE)) {
                return i;
            }
        }

        return -1;
    }

    private double rollTargetHeight() {
        double min = ctx.tuning().number(TuningKey.MACE_LAUNCH_HEIGHT_MIN).doubleValue();
        double span = ctx.tuning().number(TuningKey.MACE_LAUNCH_HEIGHT_SPAN).doubleValue();
        return min + ctx.random().nextDouble() * span;
    }

    private void faceTarget(LivingEntity target) {
        if (crouchBoostActive) {
            ctx.controls().setSneaking(false);
            crouchBoostActive = false;
        }

        TechniqueConfig.HitPreference hitPref = ctx.settings().techniques().wcHitPreference;
        double aimY = Ent.pos(target).y();
        if (hitPref == TechniqueConfig.HitPreference.HORIZONTAL && Ent.attackStrength(bot, 0.0F) > ctx.tuning().number(TuningKey.MACE_HORIZONTAL_HIT_CHARGE).floatValue()) {
            aimY = Ent.eyePos(target).y() - 0.4;
        }

        Vec3 aimPt = lead.lead(target, DiveLead.ticksToDrop(Math.max(0.0, Ent.pos(bot).y() - Ent.eyePos(target).y()),
                Math.min(0.0, Ent.velocity(bot).y())));
        Vec3 eye = Ent.eyePos(bot);
        double dx = aimPt.x() - eye.x();
        double dy = aimY - eye.y();
        double dz = aimPt.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        ctx.controls().look(yaw, pitch);
    }

    private void driftToward(LivingEntity target) {
        Vec3 pos = Ent.pos(bot);
        Vec3 aimPt = lead.lead(target, DiveLead.ticksToDrop(Math.max(0.0, pos.y() - Ent.eyePos(target).y()),
                Math.min(0.0, Ent.velocity(bot).y())));
        double dist = Math.sqrt(Math.pow(aimPt.x() - pos.x(), 2.0) + Math.pow(aimPt.z() - pos.z(), 2.0)
                + Math.pow(Ent.pos(target).y() - pos.y(), 2.0));
        if (dist > ctx.tuning().number(TuningKey.MACE_DRIFT_STOP_DIST).doubleValue()) {
            ctx.controls().moveForward();
            ctx.controls().setSprinting(dist > ctx.tuning().number(TuningKey.MACE_DRIFT_SPRINT_DIST).doubleValue());
        } else {
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
        }
    }

    private enum Phase {
        IDLE,
        LAUNCHING,
        AIRBORNE,
        SMASHING
    }
}
