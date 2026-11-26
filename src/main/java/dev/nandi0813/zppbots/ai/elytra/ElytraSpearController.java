package dev.nandi0813.zppbots.ai.elytra;

import dev.nandi0813.zppbots.ai.gear.ArmorEvaluator;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ElytraFlight;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ElytraSpearController {

    private static final String COLLISION_ABORT = "COLLISION_ABORT";
    private static final int MIN_ROCKETS = 2;
    private static final int APPROACH_ROCKET_INTERVAL = 60;
    private static final int RETREAT_ROCKET_INTERVAL = 12;
    private static final int EQUIP_SETTLE_TICKS = 3;
    private static final int FLY_MAX_TICKS = 1200;
    private static final int MAX_PHASE_TICKS = 60;
    private static final float CLIMB_PITCH = -38.0F;
    private static final float MAX_DIVE_PITCH = 35.0F;
    private static final double ALT_FLOOR = 5.0;
    private static final float MAX_YAW_STEP = 13.0F;
    private static final float MAX_PITCH_STEP = 9.0F;
    private static final int GLIDE_DOWN_MAX_TICKS = 400;
    private static final float GLIDE_DOWN_PITCH = 20.0F;

    private final BotContext ctx;
    private final Player bot;
    private final ElytraObstacleGuard.CrashDetector crashGuard;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldown = 0;
    private int lastRocketTick = -100;
    private boolean chargeBoostDone = false;
    private FlyMode flyMode = FlyMode.RETREAT;
    private int modeTick = 0;
    private double minApproach = Double.MAX_VALUE;
    private boolean headingFrozen = false;
    private float frozenYaw = 0.0F;
    private float frozenPitch = 0.0F;
    private int spearSlot = -1;
    private int chestSaveSlot = -1;
    private int elytraFromSlot = -1;
    private int prevSelected = 0;
    private boolean hadOriginalChestplate = false;

    public ElytraSpearController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.crashGuard = new ElytraObstacleGuard.CrashDetector(ctx);
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldown > 0) {
            cooldown--;
        }
        return switch (phase) {
            case IDLE -> tickIdle(target, dist);
            case EQUIPPING -> tickEquipping();
            case LAUNCHING -> tickLaunching();
            case FLYING -> tickFlying(target);
            case GLIDE_DOWN -> tickGlideDown();
        };
    }

    public void reset() {
        if (phase != Phase.IDLE) {
            abort();
        }
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public boolean hasGearForCombo() {
        return findSpearAnywhere() >= 0 && findElytraInInventory() >= 0 && countItem(Material.FIREWORK_ROCKET) >= MIN_ROCKETS;
    }

    private boolean tickIdle(LivingEntity target, double dist) {
        ensureChestplateOnGround();
        if (target == null || !Ent.alive(target)) {
            return false;
        } else if (cooldown > 0) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        }
        boolean targetAir = isTargetAirborne(target);
        if (targetAir) {
            if (dist > ctx.tuning().number(TuningKey.ELYTRA_SPEAR_AIR_TRIGGER_RANGE).doubleValue()) {
                return false;
            }
        } else if (dist > ctx.tuning().number(TuningKey.ELYTRA_SPEAR_TRIGGER_RANGE).doubleValue()) {
            return false;
        }
        if (countItem(Material.FIREWORK_ROCKET) < MIN_ROCKETS) {
            return false;
        } else if (findSpearAnywhere() < 0) {
            return false;
        }
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (ItemKinds.is(chest, Material.ELYTRA)) {
            return false;
        }
        elytraFromSlot = findElytraInInventory();
        if (elytraFromSlot < 0) {
            return false;
        }
        hadOriginalChestplate = !ItemKinds.isEmpty(chest);
        prevSelected = InventoryUtils.selected(ctx);
        enterPhase(Phase.EQUIPPING);
        return true;
    }

    private boolean tickEquipping() {
        phaseTick++;
        if (phaseTick > MAX_PHASE_TICKS) {
            abort();
            return false;
        } else if (elytraFromSlot == -1) {
            enterPhase(Phase.LAUNCHING);
            return true;
        }
        PlayerInventory inv = bot.getInventory();
        if (phaseTick == 1) {
            ItemStack currentChest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            ItemStack elytra = inv.getItem(elytraFromSlot).clone();
            inv.setItem(elytraFromSlot, null);
            bot.getEquipment().setItem(EquipmentSlot.CHEST, elytra);
            if (!ItemKinds.isEmpty(currentChest)) {
                chestSaveSlot = findFreeHotbarSlot();
                if (chestSaveSlot < 0) {
                    chestSaveSlot = elytraFromSlot;
                }
                inv.setItem(chestSaveSlot, currentChest.clone());
            } else {
                chestSaveSlot = -1;
            }
        }
        if (phaseTick < EQUIP_SETTLE_TICKS) {
            return true;
        }
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (!ItemKinds.is(chest, Material.ELYTRA)) {
            abort();
            return false;
        }
        enterPhase(Phase.LAUNCHING);
        return true;
    }

    private boolean tickLaunching() {
        phaseTick++;
        if (phaseTick > 30) {
            abort();
            return false;
        }
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        if (bot.isOnGround()) {
            ctx.controls().jumpOnce();
            return true;
        } else if (!bot.isGliding()) {
            ctx.controls().jumpOnce();
            return true;
        }
        spearSlot = ensureSpearInHotbar();
        if (spearSlot < 0) {
            abort();
            return false;
        }
        flyMode = FlyMode.RETREAT;
        modeTick = 0;
        minApproach = Double.MAX_VALUE;
        headingFrozen = false;
        chargeBoostDone = false;
        lastRocketTick = -100;
        enterPhase(Phase.FLYING);
        return true;
    }

    private boolean tickFlying(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > FLY_MAX_TICKS) {
            abort();
            return false;
        }
        spearSlot = ensureSpearInHotbar();
        if (spearSlot < 0) {
            abort();
            return false;
        }
        InventoryUtils.select(ctx, spearSlot);
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        if (phaseTick == 1) {
            ctx.controls().look(yawAwayFrom(target), CLIMB_PITCH);
        }
        int rockets = countItem(Material.FIREWORK_ROCKET);
        double dist = Ent.dist(bot, target);
        double speed = Ent.velocity(bot).length();
        double clearance = groundClearance();
        engageFallFlying();
        if (bot.isOnGround()) {
            ctx.controls().jumpOnce();
        }
        boolean gliding = bot.isGliding() && !bot.isOnGround();
        modeTick++;
        boolean climbing = clearance < ALT_FLOOR;
        float desiredYaw;
        float desiredPitch;
        if (flyMode == FlyMode.RETREAT) {
            releaseIfUsing();
            chargeBoostDone = false;
            headingFrozen = false;
            desiredYaw = yawAwayFrom(target);
            desiredPitch = CLIMB_PITCH;
            if (gliding && rockets > 0 && phaseTick - lastRocketTick >= RETREAT_ROCKET_INTERVAL) {
                fireRocket();
                lastRocketTick = phaseTick;
            }
            double horiz = horizontalDist(target);
            boolean ready = modeTick >= ctx.tuning().integer(TuningKey.ELYTRA_SPEAR_MIN_MODE_TICKS)
                    && (horiz >= ctx.tuning().number(TuningKey.ELYTRA_SPEAR_RUN_UP_DIST).doubleValue()
                    && clearance >= ctx.tuning().number(TuningKey.ELYTRA_SPEAR_RUN_UP_ALT).doubleValue()
                    || modeTick > ctx.tuning().integer(TuningKey.ELYTRA_SPEAR_RETREAT_MAX_TICKS));
            if (ready && !climbing) {
                flyMode = FlyMode.APPROACH;
                modeTick = 0;
                minApproach = Double.MAX_VALUE;
                headingFrozen = false;
            }
        } else {
            minApproach = Math.min(minApproach, dist);
            double closing = closingSpeed(Ent.velocity(bot), ctx.match().velocityOf(target), Ent.pos(target).subtract(Ent.pos(bot)));
            double etaTicks = closing > ctx.tuning().number(TuningKey.ELYTRA_SPEAR_MIN_CLOSING_SPEED).doubleValue()
                    ? dist / closing
                    : Double.MAX_VALUE;
            boolean chargeNow = etaTicks <= ctx.tuning().integer(TuningKey.ELYTRA_SPEAR_CHARGE_LEAD_TICKS)
                    || dist <= ctx.tuning().number(TuningKey.ELYTRA_SPEAR_CLOSE_RANGE).doubleValue();
            if (!headingFrozen && dist <= ctx.tuning().number(TuningKey.ELYTRA_SPEAR_CLOSE_RANGE).doubleValue()) {
                headingFrozen = true;
                frozenYaw = yawToward(target);
                frozenPitch = clampPitch(pitchToward(target), MAX_DIVE_PITCH);
            }
            if (headingFrozen) {
                desiredYaw = frozenYaw;
                desiredPitch = frozenPitch;
            } else {
                desiredYaw = yawToward(target);
                desiredPitch = clampPitch(pitchToward(target), MAX_DIVE_PITCH);
            }
            if (chargeNow) {
                if (!chargeBoostDone) {
                    releaseIfUsing();
                    if (gliding && rockets > 0) {
                        fireRocket();
                        lastRocketTick = phaseTick;
                    }
                    chargeBoostDone = true;
                } else if (!bot.isHandRaised()) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }
            } else {
                releaseIfUsing();
                chargeBoostDone = false;
                if (gliding && rockets > 0 && phaseTick - lastRocketTick >= APPROACH_ROCKET_INTERVAL) {
                    fireRocket();
                    lastRocketTick = phaseTick;
                }
            }
            boolean passed = modeTick >= ctx.tuning().integer(TuningKey.ELYTRA_SPEAR_MIN_MODE_TICKS)
                    && (dist <= ctx.tuning().number(TuningKey.ELYTRA_SPEAR_PASS_REACH).doubleValue()
                    || minApproach < ctx.tuning().number(TuningKey.ELYTRA_SPEAR_PASS_NEAR_APPROACH).doubleValue()
                    && dist > minApproach + ctx.tuning().number(TuningKey.ELYTRA_SPEAR_PASS_RECEDE).doubleValue());
            if (passed) {
                releaseIfUsing();
                flyMode = FlyMode.RETREAT;
                modeTick = 0;
                headingFrozen = false;
            }
        }
        if (climbing) {
            desiredPitch = CLIMB_PITCH;
        }
        float[] safe = ElytraObstacleGuard.avoid(ctx, desiredYaw, desiredPitch);
        steer(safe[0], safe[1]);
        if (crashGuard.crashed()) {
            ctx.log().decision("ElytraSpearController", COLLISION_ABORT, "flew into something, breaking off the elytra run");
            abort();
            return false;
        } else if (rockets == 0 && speed < 0.6) {
            abort();
            return false;
        }
        return true;
    }

    private void steer(float desiredYaw, float desiredPitch) {
        float[] next = steerStep(bot.getLocation().getYaw(), bot.getLocation().getPitch(), desiredYaw, desiredPitch);
        ctx.controls().look(next[0], next[1]);
    }

    static float[] steerStep(float curYaw, float curPitch, float desiredYaw, float desiredPitch) {
        float yawStep = Mth.clamp(Mth.degreesDifference(curYaw, desiredYaw), -MAX_YAW_STEP, MAX_YAW_STEP);
        float newYaw = Mth.wrapDegrees(curYaw + yawStep);
        float pitchStep = Mth.clamp(desiredPitch - curPitch, -MAX_PITCH_STEP, MAX_PITCH_STEP);
        float newPitch = Mth.clamp(curPitch + pitchStep, -90.0F, 90.0F);
        return new float[] {newYaw, newPitch};
    }

    static double closingSpeed(Vec3 botVel, Vec3 targetVel, Vec3 toTarget) {
        Vec3 rel = botVel.subtract(targetVel);
        double len = toTarget.length();
        return len > 1.0E-4 ? rel.dot(toTarget.scale(1.0 / len)) : 0.0;
    }

    private float yawToward(LivingEntity target) {
        Vec3 d = Ent.pos(target).subtract(Ent.pos(bot));
        return d.x() * d.x() + d.z() * d.z() < 1.0E-4
                ? bot.getLocation().getYaw()
                : (float) Math.toDegrees(Math.atan2(-d.x(), d.z()));
    }

    private float yawAwayFrom(LivingEntity target) {
        Vec3 d = Ent.pos(bot).subtract(Ent.pos(target));
        return d.x() * d.x() + d.z() * d.z() < 1.0E-4
                ? bot.getLocation().getYaw()
                : (float) Math.toDegrees(Math.atan2(-d.x(), d.z()));
    }

    private float pitchToward(LivingEntity target) {
        Vec3 eye = Ent.eyePos(bot);
        double dx = Ent.pos(target).x() - eye.x();
        double dy = Ent.eyePos(target).y() - eye.y();
        double dz = Ent.pos(target).z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
    }

    private static float clampPitch(float pitch, float maxDown) {
        return Mth.clamp(pitch, -90.0F, maxDown);
    }

    private double groundClearance() {
        BlockPos base = Ent.blockPos(bot);
        for (int i = 0; i < 24; i++) {
            BlockPos p = base.below(i);
            if (!ctx.world().isAir(p)) {
                return Ent.pos(bot).y() - (p.getY() + 1);
            }
        }
        return 24.0;
    }

    private void releaseIfUsing() {
        if (bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }
    }

    private void abort() {
        crashGuard.reset();
        releaseIfUsing();
        if (!bot.isOnGround()) {
            enterPhase(Phase.GLIDE_DOWN);
        } else {
            restoreChest();
            InventoryUtils.select(ctx, prevSelected);
            cooldown = ctx.tuning().integer(TuningKey.ELYTRA_SPEAR_COOLDOWN_TICKS);
            enterPhase(Phase.IDLE);
        }
    }

    private boolean tickGlideDown() {
        phaseTick++;
        releaseIfUsing();
        if (phaseTick > GLIDE_DOWN_MAX_TICKS) {
            forceGroundReset();
            return false;
        } else if (bot.isOnGround()) {
            forceGroundReset();
            return false;
        }
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (!ItemKinds.is(chest, Material.ELYTRA)) {
            int elytraSlot = findElytraInInventory();
            if (elytraSlot >= 0) {
                PlayerInventory inv = bot.getInventory();
                ItemStack worn = ItemKinds.isEmpty(chest) ? null : chest.clone();
                ItemStack elytra = inv.getItem(elytraSlot).clone();
                inv.setItem(elytraSlot, worn);
                bot.getEquipment().setItem(EquipmentSlot.CHEST, elytra);
            }
        }
        engageFallFlying();
        ctx.controls().look(bot.getLocation().getYaw(), GLIDE_DOWN_PITCH);
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        return true;
    }

    private void forceGroundReset() {
        releaseIfUsing();
        if (bot.isGliding()) {
            bot.setGliding(false);
        }
        if (bot.isOnGround()) {
            ensureChestplateOnGround();
        } else {
            restoreChest();
        }
        InventoryUtils.select(ctx, prevSelected);
        cooldown = ctx.tuning().integer(TuningKey.ELYTRA_SPEAR_COOLDOWN_TICKS);
        enterPhase(Phase.IDLE);
    }

    private void enterPhase(Phase p) {
        phase = p;
        phaseTick = 0;
    }

    private void engageFallFlying() {
        if (!bot.isGliding()) {
            ElytraFlight.beginGlide(ctx);
        }
    }

    private double horizontalDist(LivingEntity target) {
        Vec3 d = Ent.pos(target).subtract(Ent.pos(bot));
        return Math.sqrt(d.x() * d.x() + d.z() * d.z());
    }

    private void fireRocket() {
        PlayerInventory inv = bot.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.FIREWORK_ROCKET)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            for (int i = 9; i < 36; i++) {
                if (ItemKinds.is(inv.getItem(i), Material.FIREWORK_ROCKET)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                return;
            }
            slot = InventoryUtils.moveToHotbarSafe(ctx, slot);
        }
        int prev = InventoryUtils.selected(ctx);
        InventoryUtils.select(ctx, slot);
        ctx.hands().useItem(EquipmentSlot.HAND);
        InventoryUtils.select(ctx, prev);
    }

    private void restoreChest() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (!ItemKinds.is(chest, Material.ELYTRA)) {
            return;
        }
        PlayerInventory inv = bot.getInventory();
        ItemStack elytra = chest.clone();
        if (hadOriginalChestplate && chestSaveSlot >= 0) {
            ItemStack toWear = inv.getItem(chestSaveSlot);
            if (ArmorEvaluator.getSlot(toWear) == EquipmentSlot.CHEST && !ItemKinds.is(toWear, Material.ELYTRA)) {
                bot.getEquipment().setItem(EquipmentSlot.CHEST, toWear.clone());
                inv.setItem(chestSaveSlot, elytra);
                return;
            }
        }
        int plateSlot = findBestChestplateInInventory();
        if (plateSlot >= 0) {
            bot.getEquipment().setItem(EquipmentSlot.CHEST, inv.getItem(plateSlot).clone());
            inv.setItem(plateSlot, elytra);
        } else {
            int elytraStore = findElytraStoreSlot();
            if (elytraStore < 0) {
                return;
            }
            bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
            inv.setItem(elytraStore, elytra);
        }
    }

    private void ensureChestplateOnGround() {
        if (!bot.isOnGround()) {
            return;
        }
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (!ItemKinds.is(chest, Material.ELYTRA)) {
            return;
        }
        if (bot.isGliding()) {
            bot.setGliding(false);
        }
        PlayerInventory inv = bot.getInventory();
        int plateSlot = findBestChestplateInInventory();
        ItemStack elytra = chest.clone();
        if (plateSlot >= 0) {
            bot.getEquipment().setItem(EquipmentSlot.CHEST, inv.getItem(plateSlot).clone());
            inv.setItem(plateSlot, elytra);
        } else {
            int store = findElytraStoreSlot();
            if (store >= 0) {
                bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
                inv.setItem(store, elytra);
            }
        }
    }

    private int ensureSpearInHotbar() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.isSpear(inv.getItem(i))) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (ItemKinds.isSpear(inv.getItem(i))) {
                return InventoryUtils.moveToHotbarSafe(ctx, i);
            }
        }
        return -1;
    }

    private int findSpearAnywhere() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isSpear(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findBestChestplateInInventory() {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        double bestScore = -1.0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (ArmorEvaluator.getSlot(s) == EquipmentSlot.CHEST) {
                double score = ArmorEvaluator.score(s);
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
        }
        return best;
    }

    private int findElytraInInventory() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ElytraFlight.isUsable(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findFreeHotbarSlot() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.isEmpty(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findElytraStoreSlot() {
        int slot = findFreeHotbarSlot();
        if (slot >= 0) {
            return slot;
        }
        PlayerInventory inv = bot.getInventory();
        for (int i = 9; i < 36; i++) {
            if (ItemKinds.isEmpty(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isTargetAirborne(LivingEntity target) {
        if (target == null || target.isOnGround()) {
            return false;
        } else if (target.isGliding()) {
            return true;
        }
        return target.isRiptiding() || Ent.pos(target).y() - Ent.pos(bot).y() > 4.0;
    }

    private int countItem(Material material) {
        int total = 0;
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, material)) {
                total += s.getAmount();
            }
        }
        return total;
    }

    private enum FlyMode {
        RETREAT,
        APPROACH
    }

    private enum Phase {
        IDLE,
        EQUIPPING,
        LAUNCHING,
        FLYING,
        GLIDE_DOWN
    }
}
