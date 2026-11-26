package dev.nandi0813.zppbots.ai.elytra;

import dev.nandi0813.zppbots.ai.gear.ArmorEvaluator;
import dev.nandi0813.zppbots.ai.gear.ItemEvaluator;
import dev.nandi0813.zppbots.ai.mace.DiveCover;
import dev.nandi0813.zppbots.ai.mace.DiveLead;
import dev.nandi0813.zppbots.ai.mace.MaceSmashGates;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ElytraFlight;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.StunSlam;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class WindBurstElytraController {

    private static final double LAUNCH_VEL_THRESHOLD = 0.35;
    private static final int CLIMB_TICK_CAP = 40;
    private static final double UNEQUIP_HEIGHT = 3.0;
    private static final double MIN_FALL_DIST = 1.5;
    private static final int MAX_PHASE_TICKS = 200;

    private final BotContext ctx;
    private final Player bot;
    private final DiveLead diveLead;
    private final DiveCover cover;
    private final StunSlam stunSlam = new StunSlam();
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldown = 0;
    private int armTicksLeft = 0;
    private int maceSlot = -1;
    private int chestSaveSlot = -1;
    private int elytraFromSlot = -1;
    private int prevSelected = 0;
    private boolean diveRocketFired = false;
    private int diveSide = 1;
    private double lastTgtX = Double.NaN;
    private double lastTgtZ = Double.NaN;
    private double diveOffX;
    private double diveOffZ;

    public WindBurstElytraController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.diveLead = new DiveLead(ctx);
        this.cover = new DiveCover(ctx);
    }

    private double smashHitRange() {
        return ctx.settings().attackRange();
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public void reset() {
        if (phase != Phase.IDLE) {
            abort();
        }
    }

    public void armAfterSmash() {
        if (phase == Phase.IDLE && cooldown == 0) {
            armTicksLeft = ctx.tuning().integer(TuningKey.WIND_BURST_ARM_WINDOW_TICKS);
        }
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (armTicksLeft > 0) {
            armTicksLeft--;
        }
        return switch (phase) {
            case IDLE -> tickIdle(target);
            case EQUIPPING -> tickEquipping();
            case GLIDING -> tickGliding(target);
            case APEX_UNEQUIP -> tickApexUnequip(target);
            case APEX_REEQUIP -> tickApexReequip(target);
            case DIVING -> tickDiving(target);
            case UNEQUIPPING -> tickUnequipping();
            case SMASHING -> tickSmashing(target);
        };
    }

    private boolean tickIdle(LivingEntity target) {
        if (!ctx.settings().techniques().diveBomb) {
            return false;
        } else if (target == null || !Ent.alive(target)) {
            return false;
        } else if (cooldown > 0) {
            return false;
        } else if (armTicksLeft <= 0) {
            return false;
        } else if (bot.isOnGround()) {
            return false;
        }
        boolean rising = Ent.velocity(bot).y() >= LAUNCH_VEL_THRESHOLD;
        boolean highEnough = Ent.pos(bot).y() - Ent.pos(target).y()
                >= ctx.tuning().number(TuningKey.WIND_BURST_DIVE_TARGET_HEIGHT).doubleValue();
        if (!rising && !highEnough) {
            return false;
        } else if (lostCover(target)) {
            return false;
        } else if (!ElytraSkill.continueChain(ctx)) {
            armTicksLeft = 0;
            return false;
        }
        maceSlot = findMaceInHotbar();
        if (maceSlot < 0) {
            return false;
        }
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (ElytraFlight.isUsable(chest)) {
            elytraFromSlot = -1;
            chestSaveSlot = -1;
        } else {
            elytraFromSlot = findElytraInInventory();
            if (elytraFromSlot < 0) {
                return false;
            }
        }
        prevSelected = InventoryUtils.selected(ctx);
        armTicksLeft = 0;
        enterPhase(Phase.EQUIPPING);
        return true;
    }

    private boolean tickEquipping() {
        phaseTick++;
        if (phaseTick > 30) {
            abort();
            return false;
        } else if (elytraFromSlot == -1) {
            engageFallFlying();
            enterPhase(Phase.GLIDING);
            return true;
        }
        if (phaseTick == 1) {
            PlayerInventory inv = bot.getInventory();
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
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (ItemKinds.is(chest, Material.ELYTRA)) {
            engageFallFlying();
            enterPhase(Phase.GLIDING);
        }
        return true;
    }

    private boolean tickGliding(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > MAX_PHASE_TICKS) {
            abort();
            return false;
        } else if (bot.isOnGround() && phaseTick > 5) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        engageFallFlying();
        InventoryUtils.select(ctx, maceSlot);
        if (phaseTick == 1) {
            ctx.controls().look(bot.getLocation().getYaw(), -90.0F);
        }
        double heightAboveTarget = Ent.pos(bot).y() - Ent.pos(target).y();
        boolean atApex = phaseTick >= 4 && Ent.velocity(bot).y() <= 0.0;
        if (!(heightAboveTarget >= ctx.tuning().number(TuningKey.WIND_BURST_DIVE_TARGET_HEIGHT).doubleValue())
                && !atApex && phaseTick <= CLIMB_TICK_CAP) {
            Vec3 eye = Ent.eyePos(bot);
            double yr = Math.toRadians(bot.getLocation().getYaw());
            smoothAimAtPoint(eye.x() - Math.sin(yr), eye.y() + 50.0, eye.z() + Math.cos(yr), 12.0);
            return true;
        }
        enterPhase(Phase.APEX_UNEQUIP);
        return true;
    }

    private boolean tickApexUnequip(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > 30) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        if (phaseTick == 1) {
            if (chestSaveSlot < 0) {
                chestSaveSlot = findFreeSlot();
                if (chestSaveSlot < 0) {
                    aimAt(target, true);
                    enterPhase(Phase.DIVING);
                    return true;
                }
            }
            swapChestEquipment();
        }
        aimAt(target, true);
        if (phaseTick > 3 && !bot.isGliding()) {
            enterPhase(Phase.APEX_REEQUIP);
        }
        return true;
    }

    private boolean tickApexReequip(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > 10) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        if (phaseTick == 1) {
            swapChestEquipment();
        }
        aimAt(target, true);
        if (phaseTick >= 2) {
            enterPhase(Phase.DIVING);
        }
        return true;
    }

    private boolean tickDiving(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > MAX_PHASE_TICKS) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        if (phaseTick == 1) {
            diveRocketFired = false;
            lastTgtX = Double.NaN;
            lastTgtZ = Double.NaN;
            double[] off = ElytraSkill.diveOffset(ctx);
            diveOffX = off[0];
            diveOffZ = off[1];
        }
        engageFallFlying();
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        Vec3 pos = Ent.pos(bot);
        Vec3 tpos = Ent.pos(target);
        boolean targetStationary = !diveLead.moving(target);
        double aimOffX = 0.0;
        double aimOffZ = 0.0;
        if (target instanceof HumanEntity human && human.isBlocking() && backstabAllowed() && Ent.dist(bot, target) > 4.0) {
            Vec3 look = Ent.look(target).normalize();
            aimOffX = -look.x() * 2.0;
            aimOffZ = -look.z() * 2.0;
        }
        aimOffX += diveOffX;
        aimOffZ += diveOffZ;
        double hx = tpos.x() + aimOffX - pos.x();
        double hz = tpos.z() + aimOffZ - pos.z();
        double hd = Math.sqrt(hx * hx + hz * hz);
        // Direct setDeltaMovement drift is not used: the glide is steered by look only.
        if (targetStationary) {
            if (hd <= 1.2) {
                ctx.controls().look(bot.getLocation().getYaw(), 90.0F);
            } else {
                smoothAimAtPoint(tpos.x(), tpos.y(), tpos.z(), 22.0);
                if (bot.getLocation().getPitch() < 60.0F) {
                    ctx.controls().look(bot.getLocation().getYaw(), 60.0F);
                }
            }
            lastTgtX = tpos.x();
            lastTgtZ = tpos.z();
        } else {
            lastTgtX = tpos.x();
            lastTgtZ = tpos.z();
            int eta = Math.max(4, DiveLead.ticksToDrop(Math.max(0.0, pos.y() - tpos.y() - 1.0), Ent.velocity(bot).y()));
            Vec3 aimPt = diveLead.lead(target, eta);
            double aimY = tpos.y() + target.getHeight() * 0.5;
            smoothAimAtPoint(aimPt.x() + aimOffX, aimY, aimPt.z() + aimOffZ, 22.0);
        }
        if (!diveRocketFired
                && bot.isGliding()
                && (bot.getLocation().getPitch() >= 45.0F || phaseTick >= 8)
                && ctx.settings().techniques().rocketMace) {
            fireRocket();
            diveRocketFired = true;
        }
        InventoryUtils.select(ctx, maceSlot);
        double heightAboveTarget = pos.y() - tpos.y();
        double dist = Ent.dist(bot, target);
        if (heightAboveTarget <= UNEQUIP_HEIGHT
                || dist <= ctx.tuning().number(TuningKey.WIND_BURST_SMASH_RANGE).doubleValue()) {
            enterPhase(Phase.UNEQUIPPING);
        }
        return true;
    }

    private boolean tickUnequipping() {
        phaseTick++;
        if (phaseTick == 1) {
            ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            if (ItemKinds.is(chest, Material.ELYTRA)) {
                PlayerInventory inv = bot.getInventory();
                ItemStack saved = chestSaveSlot >= 0 ? inv.getItem(chestSaveSlot) : null;
                if (chestSaveSlot < 0 || !ItemKinds.isEmpty(saved) && !isChestplate(saved)) {
                    int store = findFreeSlot();
                    if (store >= 0) {
                        bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
                        inv.setItem(store, chest.clone());
                    }
                } else {
                    bot.getEquipment().setItem(EquipmentSlot.CHEST, ItemKinds.isEmpty(saved) ? null : saved.clone());
                    inv.setItem(chestSaveSlot, chest.clone());
                }
                bot.setGliding(false);
            }
            InventoryUtils.select(ctx, maceSlot);
        }
        if (phaseTick > 10 && bot.isGliding()) {
            abort();
            return false;
        }
        if (!bot.isGliding()) {
            enterPhase(Phase.SMASHING);
        }
        return true;
    }

    private boolean tickSmashing(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (phaseTick > 60) {
            abort();
            return false;
        }
        InventoryUtils.select(ctx, maceSlot);
        smoothAimAtPoint(Ent.pos(target).x(), Ent.eyePos(target).y(), Ent.pos(target).z(), 18.0);
        double dist = Ent.dist(bot, target);
        boolean inRange = dist <= smashHitRange();
        boolean hasFall = bot.getFallDistance() > MIN_FALL_DIST && MaceSmashGates.botMayUseSmashFallBonus(ctx);
        boolean landed = bot.isOnGround() && phaseTick > 3;
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        if (!bot.isOnGround()) {
            Vec3 pos = Ent.pos(bot);
            Vec3 aimPt = diveLead
                    .lead(target, DiveLead.ticksToDrop(pos.y() - Ent.eyePos(target).y(), Ent.velocity(bot).y()))
                    .add(diveOffX, 0.0, diveOffZ);
            double hx = aimPt.x() - pos.x();
            double hz = aimPt.z() - pos.z();
            double hd = Math.sqrt(hx * hx + hz * hz);
            // setDeltaMovement drift replaced by air strafing along the target-aimed yaw.
            if (hd > 0.05) {
                ctx.controls().setForward((float) ElytraSkill.steer(ctx));
            }
        }
        if (inRange && hasFall) {
            if (bot.hasLineOfSight(target)) {
                ctx.controls().setSprinting(true);
                TechniqueConfig ssCfg = ctx.settings().techniques();
                boolean mayAirSlam = ssCfg.elytraStunSlam && ElytraSkill.level(ctx) >= 4;
                StunSlam.Result ssRes = stunSlam.tick(ctx, target, findAxeSlot(), maceSlot, ssCfg.stunSlamVariant, mayAirSlam);
                if (ssRes == StunSlam.Result.HOLD) {
                    return true;
                }
                if (ssRes != StunSlam.Result.SMASHED) {
                    InventoryUtils.select(ctx, maceSlot);
                    if (!ElytraSkill.whiff(ctx)) {
                        ctx.hands().attack(target);
                    }
                    ctx.hands().swing(EquipmentSlot.HAND);
                }
                cooldown = 0;
                diveSide *= -1;
                if (equipElytraNow()) {
                    engageFallFlying();
                    enterPhase(Phase.GLIDING);
                } else {
                    InventoryUtils.select(ctx, prevSelected);
                    armTicksLeft = ctx.tuning().integer(TuningKey.WIND_BURST_ARM_WINDOW_TICKS);
                    enterPhase(Phase.IDLE);
                }
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

    private boolean equipElytraNow() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (ElytraFlight.isUsable(chest)) {
            return true;
        }
        int slot = findElytraInInventory();
        if (slot < 0) {
            return false;
        }
        PlayerInventory inv = bot.getInventory();
        ItemStack elytra = inv.getItem(slot).clone();
        inv.setItem(slot, null);
        if (!ItemKinds.isEmpty(chest)) {
            chestSaveSlot = findFreeHotbarSlot();
            if (chestSaveSlot < 0) {
                chestSaveSlot = slot;
            }
            inv.setItem(chestSaveSlot, chest.clone());
        } else {
            chestSaveSlot = -1;
        }
        bot.getEquipment().setItem(EquipmentSlot.CHEST, elytra);
        return true;
    }

    private boolean lostCover(LivingEntity target) {
        return target.isOnGround() && !cover.reachable(target, ctx.tick());
    }

    private void abort() {
        if (bot.isOnGround()) {
            restoreChest();
        } else {
            ElytraFlight.beginGlide(ctx);
        }
        InventoryUtils.select(ctx, prevSelected);
        cooldown = ctx.tuning().integer(TuningKey.WIND_BURST_ABORT_COOLDOWN);
        enterPhase(Phase.IDLE);
    }

    private void restoreChest() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        if (ItemKinds.is(chest, Material.ELYTRA) && chestSaveSlot >= 0) {
            PlayerInventory inv = bot.getInventory();
            ItemStack saved = inv.getItem(chestSaveSlot);
            if (!ItemKinds.isEmpty(saved) && !isChestplate(saved)) {
                return;
            }
            bot.getEquipment().setItem(EquipmentSlot.CHEST, ItemKinds.isEmpty(saved) ? null : saved.clone());
            inv.setItem(chestSaveSlot, chest.clone());
        }
    }

    private void swapChestEquipment() {
        if (chestSaveSlot >= 0) {
            PlayerInventory inv = bot.getInventory();
            ItemStack chestSlotItem = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            ItemStack savedItem = inv.getItem(chestSaveSlot);
            bot.getEquipment().setItem(EquipmentSlot.CHEST, ItemKinds.isEmpty(savedItem) ? null : savedItem.clone());
            inv.setItem(chestSaveSlot, ItemKinds.isEmpty(chestSlotItem) ? null : chestSlotItem.clone());
        }
    }

    private void engageFallFlying() {
        if (!bot.isGliding()) {
            ElytraFlight.beginGlide(ctx);
        }
    }

    private boolean backstabAllowed() {
        TechniqueConfig t = ctx.settings().techniques();
        return t.elytraBackstab && !t.stunSlamEnabled();
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

    private void aimAt(LivingEntity target, boolean steep) {
        Vec3 eye = Ent.eyePos(bot);
        Vec3 t = Ent.pos(target);
        double dx = t.x() - eye.x();
        double dy = Ent.eyePos(target).y() - eye.y();
        double dz = t.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        if (steep) {
            pitch = Math.max(pitch, 60.0F);
        }
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float[] safe = ElytraObstacleGuard.towards(ctx, yaw, pitch, dist);
        ctx.controls().look(safe[0], safe[1]);
    }

    private void smoothAimAtPoint(double x, double y, double z, double maxStepDeg) {
        Vec3 eye = Ent.eyePos(bot);
        double dx = x - eye.x();
        double dy = y - eye.y();
        double dz = z - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        float[] safe = ElytraObstacleGuard.towards(ctx, wantYaw, wantPitch, Math.sqrt(dx * dx + dy * dy + dz * dz));
        float newYaw = approachAngle(bot.getLocation().getYaw(), safe[0], (float) maxStepDeg);
        float newPitch = approachAngle(bot.getLocation().getPitch(), safe[1], (float) maxStepDeg);
        ctx.controls().look(newYaw, newPitch);
    }

    static float approachAngle(float cur, float want, float maxStep) {
        float delta = (want - cur) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        }
        if (delta < -180.0F) {
            delta += 360.0F;
        }
        if (delta > maxStep) {
            delta = maxStep;
        }
        if (delta < -maxStep) {
            delta = -maxStep;
        }
        return cur + delta;
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

    private int findMaceInHotbar() {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        double bestScore = -1.0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, Material.MACE)) {
                double score = ItemEvaluator.scoreMace(s);
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

    private int findFreeSlot() {
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

    private static boolean isChestplate(ItemStack s) {
        return !ItemKinds.is(s, Material.ELYTRA) && ArmorEvaluator.getSlot(s) == EquipmentSlot.CHEST;
    }

    private enum Phase {
        IDLE,
        EQUIPPING,
        GLIDING,
        APEX_UNEQUIP,
        APEX_REEQUIP,
        DIVING,
        UNEQUIPPING,
        SMASHING
    }
}
