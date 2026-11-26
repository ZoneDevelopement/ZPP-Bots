package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.ai.gear.ItemEvaluator;
import dev.nandi0813.zppbots.ai.gear.OffhandManager;
import dev.nandi0813.zppbots.ai.mace.MaceSmashGates;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ElytraFlight;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.StunSlam;
import dev.nandi0813.zppbots.util.SwapEngine;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class PearlCatchController {

    private static final String LAUNCH_TIMEOUT = "LAUNCH_TIMEOUT";
    private static final String WIND_CHARGE_NOT_FIRED = "WIND_CHARGE_NOT_FIRED";
    private static final int TICK_FIRE_PEARL = 1;
    private static final int TICK_FIRE_WC = 3;
    private static final int TICK_AIRBORNE = 5;
    private static final double APEX_Y_VELOCITY = 0.08;
    private static final float ANGLE_PEARL_PITCH = -55.0F;
    private static final float ANGLE_DRAG_DOWN = 20.0F;
    private static final int INSTANT_PARK_TICKS = 20;
    private static final int WALL_PROBE_DIST = 3;
    private static final int WALL_MIN_HEIGHT = 4;
    private static final int PRELAUNCH_TICK = 1;
    private static final int WC_COOLDOWN_TICKS = 11;
    private static final float PRELAUNCH_PITCH = 80.0F;
    private static final double MIN_FALL_DIST = 1.5;
    private static final int MAX_AIRBORNE_TICKS = 100;
    private static final int MAX_LAUNCH_TICKS = 40;
    private static final int WC_RETRY_TICKS = 14;
    private static final int MAX_SMASH_TICKS = 40;
    private static final int GLIDE_MAX_TICKS = 200;
    private static final int SETTLE_MAX = 30;

    private final BotContext ctx;
    private final Player bot;
    private final OffhandManager offhand;
    private final StunSlam stunSlam = new StunSlam();
    private float vPitch = -90.0F;
    private int vPearlTick = TICK_FIRE_PEARL;
    private int vWcTick = TICK_FIRE_WC;
    private int vAirborne = TICK_AIRBORNE;
    private int vSettleMax = SETTLE_MAX;
    private boolean vOffhandWc = false;
    private boolean vNoWc = false;
    private float vWallYaw = Float.NaN;
    private float vWcPitch = Float.NaN;
    private boolean vFaceTarget = false;
    private boolean vWaitForApex = false;
    private boolean apexSeen = false;
    private boolean preLaunchFired = false;
    private boolean vPreLaunch = false;
    private LivingEntity catchTarget = null;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldownLeft = 0;
    private boolean settled = false;
    private boolean wcFired = false;
    private boolean pearlFired = false;
    private int settleTicks = 0;
    private int prevSlot = 0;
    private int chestStashSlot = -1;
    private int maceSlot = -1;
    private int windChargeSlot = -1;
    private int pearlSlot = -1;

    public PearlCatchController(BotContext ctx, OffhandManager offhand) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.offhand = offhand;
    }

    private double attackRange() {
        return ctx.settings().attackRange();
    }

    private boolean throwsDone() {
        return pearlFired && (wcFired || vNoWc);
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldownLeft > 0) {
            cooldownLeft--;
        }
        return switch (phase) {
            case IDLE -> tickIdle(target, dist);
            case LAUNCHING -> tickLaunching();
            case AIRBORNE -> tickAirborne(target);
            case GLIDING -> tickGliding(target);
            case SMASHING -> tickSmashing(target);
        };
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public void reset() {
        if (vOffhandWc) {
            offhand.unpark();
            vOffhandWc = false;
        }
        if (phase != Phase.IDLE) {
            InventoryUtils.select(ctx, prevSlot);
        }
        unequipElytra();
        phase = Phase.IDLE;
        phaseTick = 0;
        catchTarget = null;
    }

    private boolean tickIdle(LivingEntity target, double dist) {
        if (target == null || !Ent.alive(target)) {
            return false;
        } else if (cooldownLeft > 0) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        } else if (dist > ctx.tuning().number(TuningKey.PEARL_CATCH_TRIGGER_RANGE).doubleValue()) {
            return false;
        } else if (!ctx.settings().usePearlCatch()) {
            return false;
        } else if (bot.hasCooldown(new ItemStack(Material.WIND_CHARGE))) {
            return false;
        } else if (bot.hasCooldown(new ItemStack(Material.ENDER_PEARL))) {
            return false;
        }
        maceSlot = findMaceSlot();
        windChargeSlot = findWindChargeSlot();
        pearlSlot = findPearlSlot();
        if (maceSlot < 0 || windChargeSlot < 0 || pearlSlot < 0) {
            return false;
        }
        if (pearlSlot >= 9) {
            pearlSlot = InventoryUtils.moveToHotbarSafe(ctx, pearlSlot);
            windChargeSlot = findWindChargeSlot();
            if (windChargeSlot < 0 || windChargeSlot == pearlSlot) {
                return false;
            }
        }
        prevSlot = InventoryUtils.selected(ctx);
        settled = false;
        settleTicks = 0;
        wcFired = false;
        pearlFired = false;
        preLaunchFired = false;
        catchTarget = target;
        resolveVariant(target);
        enterPhase(Phase.LAUNCHING);
        return true;
    }

    private boolean hasSpearDashKit() {
        PlayerInventory inv = bot.getInventory();
        boolean spear = false;
        boolean wbMace = false;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!ItemKinds.isEmpty(st)) {
                if (!spear && ItemKinds.isSpear(st) && EnchantmentUtils.getEnchantLevel(st, "lunge") >= 1) {
                    spear = true;
                }
                if (!wbMace && ItemKinds.is(st, Material.MACE) && EnchantmentUtils.getEnchantLevel(st, "wind_burst") >= 1) {
                    wbMace = true;
                }
            }
        }
        return spear && wbMace;
    }

    static TechniqueConfig.PearlCatchVariant resolveType(
            BotTuning.Profile profile, TechniqueConfig.PearlCatchVariant setting, double horiz, boolean onGround) {
        if (setting != TechniqueConfig.PearlCatchVariant.SMART) {
            return setting;
        } else if (horiz <= profile.number(TuningKey.PEARL_CATCH_STANDARD_MAX_HORIZ).doubleValue() && onGround) {
            return TechniqueConfig.PearlCatchVariant.STANDARD;
        } else if (horiz >= profile.number(TuningKey.PEARL_CATCH_ANGLE_MIN_HORIZ).doubleValue()) {
            return TechniqueConfig.PearlCatchVariant.ANGLE;
        }
        return TechniqueConfig.PearlCatchVariant.HIGHER;
    }

    private void resolveVariant(LivingEntity target) {
        vPitch = -90.0F;
        vPearlTick = TICK_FIRE_PEARL;
        vWcTick = TICK_FIRE_WC;
        vAirborne = TICK_AIRBORNE;
        vSettleMax = SETTLE_MAX;
        vOffhandWc = false;
        vNoWc = false;
        vPreLaunch = false;
        vWallYaw = Float.NaN;
        vWcPitch = Float.NaN;
        vFaceTarget = false;
        vWaitForApex = false;
        apexSeen = false;
        TechniqueConfig.PearlCatchVariant setting = ctx.settings().pearlCatchType();
        Vec3 pos = Ent.pos(bot);
        Vec3 tpos = Ent.pos(target);
        TechniqueConfig.PearlCatchVariant type = resolveType(
                ctx.tuning(), setting, Math.hypot(tpos.x() - pos.x(), tpos.z() - pos.z()), target.isOnGround());
        float wallYaw = probeWallYaw();
        if (Float.isNaN(wallYaw)
                || setting != TechniqueConfig.PearlCatchVariant.STANDARD && setting != TechniqueConfig.PearlCatchVariant.SMART) {
            switch (type) {
                case HIGHER -> {
                    vPreLaunch = true;
                    vWaitForApex = true;
                    vPearlTick = 5;
                    vWcTick = Math.max(vPearlTick + 4, 12);
                    vAirborne = vWcTick + 3;
                }
                case INSTANT -> {
                    vOffhandWc = offhand.park(windChargeSlot, INSTANT_PARK_TICKS);
                    vPearlTick = 1;
                    vWcTick = 2;
                    vAirborne = 4;
                    vSettleMax = 2;
                }
                case ANGLE -> {
                    vPitch = ANGLE_PEARL_PITCH;
                    vWcPitch = -35.0F;
                    vFaceTarget = true;
                    vPearlTick = 1;
                    vWcTick = 4;
                    vAirborne = vWcTick + 3;
                }
                default -> {
                }
            }
        } else {
            vWallYaw = wallYaw;
            vPitch = -45.0F;
            vNoWc = true;
            vAirborne = 14;
        }
    }

    private void holdForSequence() {
        int want;
        if (vPreLaunch && !preLaunchFired) {
            want = windChargeSlot;
        } else if (!pearlFired) {
            want = pearlSlot;
        } else if (!wcFired && !vNoWc && !vOffhandWc) {
            want = windChargeSlot;
        } else {
            want = maceSlot;
        }
        if (want >= 0 && want <= 8 && InventoryUtils.selected(ctx) != want) {
            InventoryUtils.select(ctx, want);
        }
    }

    private float yawTo(Entity e) {
        Vec3 pos = Ent.pos(bot);
        Vec3 t = Ent.pos(e);
        return (float) Math.toDegrees(Math.atan2(-(t.x() - pos.x()), t.z() - pos.z()));
    }

    static int[] probeOffset(int octant, int d) {
        double ang = octant * (Math.PI / 4);
        return new int[] {(int) Math.round(-Math.sin(ang) * d), (int) Math.round(Math.cos(ang) * d)};
    }

    private float probeWallYaw() {
        BlockPos feet = Ent.blockPos(bot);
        float best = Float.NaN;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double ang = i * (Math.PI / 4);
            for (int d = 1; d <= WALL_PROBE_DIST; d++) {
                int[] off = probeOffset(i, d);
                BlockPos at = feet.offset(off[0], 1, off[1]);
                if (isWallBlock(at)) {
                    boolean tall = true;
                    for (int h = 1; h < WALL_MIN_HEIGHT; h++) {
                        if (!isWallBlock(at.above(h))) {
                            tall = false;
                            break;
                        }
                    }
                    if (tall && d < bestDist) {
                        bestDist = d;
                        best = (float) Math.toDegrees(ang);
                    }
                    break;
                }
            }
        }
        return best;
    }

    private boolean isWallBlock(BlockPos pos) {
        return ctx.world().isFullBlock(pos);
    }

    private boolean tickLaunching() {
        if (phaseTick > MAX_LAUNCH_TICKS) {
            ctx.log().decision("PearlCatchController", LAUNCH_TIMEOUT, "[PearlCatch] " + ctx.name() + " launch timed out - back to fighting");
            abort();
            return false;
        }
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        ctx.controls().stopLookInterpolation();
        float pitchNow = vPitch;
        if (vPreLaunch && settled && phaseTick <= 1) {
            pitchNow = PRELAUNCH_PITCH;
        } else if (!Float.isNaN(vWcPitch) && settled && phaseTick >= vWcTick) {
            pitchNow = vWcPitch;
        }
        float yaw = bot.getLocation().getYaw();
        if (!Float.isNaN(vWallYaw)) {
            yaw = vWallYaw;
        }
        if (vFaceTarget && catchTarget != null) {
            yaw = yawTo(catchTarget);
        }
        ctx.controls().look(yaw, pitchNow);
        holdForSequence();
        if (!settled) {
            // The settle wait used the speed after it was zeroed (always 0); the real speed is read instead.
            if (bot.isOnGround() && Math.hypot(Ent.velocity(bot).x(), Ent.velocity(bot).z()) < 0.03 || ++settleTicks >= vSettleMax) {
                settled = true;
                phaseTick = 0;
            }
            return true;
        }
        phaseTick++;
        if (vPreLaunch && phaseTick == PRELAUNCH_TICK) {
            SwapEngine.sameTickJumpAndWc(ctx, windChargeSlot);
            preLaunchFired = true;
            return true;
        }
        if (vWaitForApex && !apexSeen && phaseTick > 1) {
            boolean rising = Ent.velocity(bot).y() > APEX_Y_VELOCITY;
            if (rising) {
                return true;
            }
            apexSeen = true;
            vPearlTick = phaseTick;
            vWcTick = Math.max(phaseTick + 1, 12);
            vAirborne = vWcTick + 3;
        }
        // Direct setDeltaMovement hold-still before the throws is not used; stopMoving above is the only hold.
        if (phaseTick == vPearlTick && !pearlFired) {
            InventoryUtils.select(ctx, pearlSlot);
            if (ItemKinds.is(bot.getInventory().getItemInMainHand(), Material.ENDER_PEARL)) {
                ctx.hands().useItem(EquipmentSlot.HAND);
                pearlFired = true;
            }
        }
        if (phaseTick >= vWcTick && !vNoWc && !wcFired) {
            float wcPitch = Float.isNaN(vWcPitch) ? vPitch : vWcPitch;
            if (vOffhandWc) {
                if (ItemKinds.is(bot.getInventory().getItemInOffHand(), Material.WIND_CHARGE)) {
                    ctx.hands().useItem(EquipmentSlot.OFF_HAND);
                    wcFired = true;
                }
            } else if (SwapEngine.fireWindChargeSelf(ctx, windChargeSlot, wcPitch)) {
                wcFired = true;
            }
            if (!wcFired && phaseTick < vWcTick + WC_RETRY_TICKS) {
                vAirborne = Math.max(vAirborne, phaseTick + 3);
            } else if (!wcFired) {
                ctx.log().decision("PearlCatchController", WIND_CHARGE_NOT_FIRED, "[PearlCatch] " + ctx.name() + " charge never fired - riding the pearl alone");
                vAirborne = phaseTick;
            }
        }
        if (phaseTick >= vAirborne && throwsDone()) {
            InventoryUtils.select(ctx, maceSlot);
            if (vOffhandWc) {
                offhand.unpark();
                vOffhandWc = false;
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
        }
        InventoryUtils.select(ctx, maceSlot);
        faceTarget(target);
        driftToward(target);
        boolean falling = Ent.velocity(bot).y() < -0.05 && phaseTick > 10;
        if (falling) {
            double hd = horizDist(target);
            if (hd > ctx.tuning().number(TuningKey.PEARL_CATCH_GLIDE_ENTER_HORIZ).doubleValue()
                    && ctx.settings().elytraGlide()
                    && equipElytra()) {
                ElytraFlight.beginGlide(ctx);
                enterPhase(Phase.GLIDING);
            } else {
                if (hd > ctx.tuning().number(TuningKey.PEARL_CATCH_ANGLE_MIN_HORIZ).doubleValue()
                        && !bot.isGliding()
                        && ctx.settings().techniques().spearMacing
                        && hasSpearDashKit()) {
                    InventoryUtils.select(ctx, maceSlot);
                    cooldownLeft = ctx.tuning().integer(TuningKey.PEARL_CATCH_COOLDOWN_TICKS);
                    enterPhase(Phase.IDLE);
                    return false;
                }
                enterPhase(Phase.SMASHING);
            }
        }
        return true;
    }

    private boolean tickGliding(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            unequipElytra();
            abort();
            return false;
        } else if (phaseTick > GLIDE_MAX_TICKS) {
            unequipElytra();
            abort();
            return false;
        } else if (bot.isOnGround() && phaseTick > 5) {
            unequipElytra();
            abort();
            return false;
        }
        ElytraFlight.beginGlide(ctx);
        InventoryUtils.select(ctx, maceSlot);
        faceTarget(target);
        // Direct setDeltaMovement pull toward the target is not used: the glide is steered by look only.
        if (horizDist(target) <= ctx.tuning().number(TuningKey.PEARL_CATCH_GLIDE_EXIT_HORIZ).doubleValue()
                && Ent.pos(bot).y() > Ent.pos(target).y()
                        + ctx.tuning().number(TuningKey.PEARL_CATCH_SMASH_HEIGHT).doubleValue()) {
            unequipElytra();
            enterPhase(Phase.SMASHING);
        }
        return true;
    }

    private boolean tickSmashing(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > MAX_SMASH_TICKS) {
            abort();
            return false;
        }
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
                }
                if (ssRes != StunSlam.Result.SMASHED) {
                    InventoryUtils.select(ctx, maceSlot);
                    ctx.hands().attack(target);
                    ctx.hands().swing(EquipmentSlot.HAND);
                }
                InventoryUtils.select(ctx, prevSlot);
                cooldownLeft = ctx.tuning().integer(TuningKey.PEARL_CATCH_COOLDOWN_TICKS);
                enterPhase(Phase.IDLE);
                return false;
            } else if (landed) {
                abort();
                return false;
            }
            return true;
        } else if (landed) {
            abort();
            return false;
        }
        return true;
    }

    private void enterPhase(Phase p) {
        phase = p;
        phaseTick = 0;
    }

    private void abort() {
        unequipElytra();
        InventoryUtils.select(ctx, prevSlot);
        cooldownLeft = ctx.tuning().integer(TuningKey.PEARL_CATCH_ABORT_COOLDOWN_TICKS);
        enterPhase(Phase.IDLE);
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

    private int findWindChargeSlot() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.WIND_CHARGE)) {
                return i;
            }
        }
        return -1;
    }

    private int findPearlSlot() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.ENDER_PEARL)) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.ENDER_PEARL)) {
                return i;
            }
        }
        return -1;
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

    private double horizDist(LivingEntity target) {
        Vec3 pos = Ent.pos(bot);
        Vec3 t = Ent.pos(target);
        double dx = t.x() - pos.x();
        double dz = t.z() - pos.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean equipElytra() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (ElytraFlight.isUsable(chest)) {
            return true;
        }
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (ElytraFlight.isUsable(s)) {
                ItemStack elytra = s.clone();
                inv.setItem(i, ItemKinds.isEmpty(chest) ? null : chest.clone());
                bot.getEquipment().setItem(EquipmentSlot.CHEST, elytra);
                chestStashSlot = i;
                return true;
            }
        }
        return false;
    }

    private void unequipElytra() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (!ItemKinds.is(chest, Material.ELYTRA)) {
            return;
        }
        PlayerInventory inv = bot.getInventory();
        if (chestStashSlot >= 0 && chestStashSlot < 36) {
            ItemStack stashed = inv.getItem(chestStashSlot);
            inv.setItem(chestStashSlot, chest.clone());
            bot.getEquipment().setItem(EquipmentSlot.CHEST, ItemKinds.isEmpty(stashed) ? null : stashed.clone());
        } else {
            for (int i = 0; i < 36; i++) {
                if (ItemKinds.isEmpty(inv.getItem(i))) {
                    inv.setItem(i, chest.clone());
                    bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
                    break;
                }
            }
        }
        chestStashSlot = -1;
    }

    private void faceTarget(LivingEntity target) {
        Vec3 eye = Ent.eyePos(bot);
        Vec3 t = Ent.pos(target);
        double dx = t.x() - eye.x();
        double dy = t.y() - eye.y();
        double dz = t.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        ctx.controls().look(yaw, pitch);
    }

    private void driftToward(LivingEntity target) {
        double dist = Ent.dist(bot, target);
        if (dist > ctx.tuning().number(TuningKey.PEARL_CATCH_DRIFT_STOP_DIST).doubleValue()) {
            ctx.controls().moveForward();
            ctx.controls().setSprinting(dist > ctx.tuning().number(TuningKey.PEARL_CATCH_DRIFT_SPRINT_DIST).doubleValue());
        } else {
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
        }
    }

    private enum Phase {
        IDLE,
        LAUNCHING,
        AIRBORNE,
        GLIDING,
        SMASHING
    }
}
