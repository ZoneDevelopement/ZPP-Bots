package dev.nandi0813.zppbots.ai.elytra;

import dev.nandi0813.zppbots.ai.gear.ArmorEvaluator;
import dev.nandi0813.zppbots.ai.mace.DiveCover;
import dev.nandi0813.zppbots.ai.mace.MaceSmashGates;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ElytraFlight;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.SwapEngine;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class RocketlessElytraController {

    private static final int TICK_FIRE_PEARL = 1;
    private static final int TICK_FIRE_WC = 4;
    private static final int TICK_EQUIP_ELYTRA = 7;
    private static final int TICK_FIRE_WC_ONLY = 1;
    private static final int TICK_WC_ONLY_ELYTRA = 4;
    private static final double MIN_FALL_DIST = 1.5;
    private static final int GLIDE_MAX_TICKS = 260;
    private static final int SMASH_MAX_TICKS = 60;
    private static final int SETTLE_MAX = 30;

    private final BotContext ctx;
    private final Player bot;
    private final DiveCover cover;
    private Runnable onSmashLanded = null;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldownLeft = 0;
    private int reheightCd = 0;
    private boolean settled = false;
    private int settleTicks = 0;
    private boolean pearlLaunch = false;
    private int prevSlot = 0;
    private int maceSlot = -1;
    private int windChargeSlot = -1;
    private int pearlSlot = -1;
    private int chestStashSlot = -1;

    public RocketlessElytraController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.cover = new DiveCover(ctx);
    }

    private double smashRange() {
        return ctx.settings().attackRange();
    }

    public void setOnSmashLanded(Runnable cb) {
        this.onSmashLanded = cb;
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldownLeft > 0) {
            cooldownLeft--;
        }
        if (reheightCd > 0) {
            reheightCd--;
        }
        if (phase == Phase.IDLE) {
            if (!canStart(target, dist)) {
                return false;
            }
            beginLaunch();
            return true;
        } else if (target != null && Ent.alive(target)) {
            return switch (phase) {
                case LAUNCHING -> tickLaunching();
                case GLIDING -> tickGliding(target);
                case REHEIGHT -> tickReheight(target);
                case SMASHING -> tickSmashing(target);
                default -> false;
            };
        }
        abort();
        return false;
    }

    private boolean canStart(LivingEntity target, double dist) {
        if (cooldownLeft > 0 || target == null || !Ent.alive(target)) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        }
        boolean targetAirborne = !target.isOnGround() && target.getFallDistance() > 1.0;
        if (!targetAirborne && (dist < ctx.tuning().number(TuningKey.ROCKETLESS_MIN_TRIGGER_DIST).doubleValue()
                || dist > ctx.tuning().number(TuningKey.ROCKETLESS_MAX_TRIGGER_DIST).doubleValue())) {
            return false;
        }
        maceSlot = findHotbar(Material.MACE);
        if (maceSlot < 0) {
            return false;
        } else if (!hasUsableElytra()) {
            return false;
        }
        windChargeSlot = findAnywhere(Material.WIND_CHARGE);
        if (windChargeSlot < 0) {
            return false;
        } else if (bot.hasCooldown(new ItemStack(Material.WIND_CHARGE))) {
            return false;
        } else if (!targetAirborne && !cover.reachable(target, ctx.tick())) {
            return false;
        } else if (!DiveCover.openAbove(ctx, bot, DiveCover.LAUNCH_HEADROOM)) {
            return false;
        } else if (ElytraSkill.skipLaunch(ctx)) {
            cooldownLeft = ElytraSkill.hesitateTicks(ctx);
            return false;
        }
        pearlLaunch = ctx.settings().usePearlCatch();
        if (pearlLaunch) {
            pearlSlot = findAnywhere(Material.ENDER_PEARL);
            if (pearlSlot < 0) {
                pearlLaunch = false;
            }
        }
        return true;
    }

    private void beginLaunch() {
        if (pearlLaunch && pearlSlot >= 9) {
            InventoryUtils.moveToHotbarSafe(ctx, pearlSlot);
        }
        windChargeSlot = findAnywhere(Material.WIND_CHARGE);
        if (windChargeSlot >= 9) {
            windChargeSlot = InventoryUtils.moveToHotbarSafe(ctx, windChargeSlot);
        }
        if (pearlLaunch) {
            pearlSlot = findHotbar(Material.ENDER_PEARL);
            if (pearlSlot < 0) {
                pearlLaunch = false;
            }
        }
        prevSlot = InventoryUtils.selected(ctx);
        chestStashSlot = -1;
        settled = false;
        settleTicks = 0;
        enterPhase(Phase.LAUNCHING);
    }

    private boolean tickLaunching() {
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        ctx.controls().stopLookInterpolation();
        ctx.controls().look(bot.getLocation().getYaw(), pearlLaunch ? -90.0F : 80.0F);
        if (!settled) {
            // The settle wait used the speed after it was zeroed (always 0); the real speed is read instead.
            if (bot.isOnGround() && horizontalSpeed() < 0.03 || ++settleTicks >= SETTLE_MAX) {
                settled = true;
                phaseTick = 0;
            }
            return true;
        }
        phaseTick++;
        if (pearlLaunch) {
            InventoryUtils.select(ctx, maceSlot >= 0 ? maceSlot : prevSlot);
            if (phaseTick == TICK_FIRE_PEARL) {
                InventoryUtils.select(ctx, pearlSlot);
                firePearlUp();
            } else if (phaseTick == TICK_FIRE_WC) {
                InventoryUtils.select(ctx, windChargeSlot);
                fireWindCharge();
            } else if (phaseTick > TICK_FIRE_WC && (!bot.isOnGround() || phaseTick >= TICK_EQUIP_ELYTRA)) {
                return equipAndGlide();
            }
        } else if (phaseTick == TICK_FIRE_WC_ONLY) {
            InventoryUtils.select(ctx, windChargeSlot);
            fireWindCharge();
        } else if (phaseTick > TICK_FIRE_WC_ONLY && (!bot.isOnGround() || phaseTick >= TICK_WC_ONLY_ELYTRA)) {
            return equipAndGlide();
        }
        return true;
    }

    private double horizontalSpeed() {
        return Math.hypot(Ent.velocity(bot).x(), Ent.velocity(bot).z());
    }

    private void firePearlUp() {
        if (ItemKinds.is(bot.getInventory().getItemInMainHand(), Material.ENDER_PEARL)) {
            ctx.hands().useItem(EquipmentSlot.HAND);
        }
    }

    private boolean equipAndGlide() {
        if (!equipElytra()) {
            abort();
            return false;
        }
        ElytraFlight.beginGlide(ctx);
        enterPhase(Phase.GLIDING);
        return true;
    }

    private void fireWindCharge() {
        SwapEngine.fireWindCharge(ctx, windChargeSlot, 80.0F);
    }

    private void maybeWindChargeBoost(double dist) {
        if (!ctx.settings().techniques().elytraWcBoost) {
            return;
        }
        double minDist = ctx.tuning().number(TuningKey.ROCKETLESS_WC_BOOST_MIN_DIST).doubleValue();
        int interval = ctx.tuning().integer(TuningKey.ROCKETLESS_WC_BOOST_INTERVAL);
        if (!(dist <= minDist) && phaseTick % interval == 0) {
            int wc = windChargeSlot;
            if (wc >= 0 && wc <= 8) {
                float prevYaw = bot.getLocation().getYaw();
                float prevPitch = bot.getLocation().getPitch();
                ctx.controls().look(prevYaw + 180.0F, prevPitch);
                SwapEngine.fireWindCharge(ctx, wc, 10.0F);
                // the boost shot is backward only for the use; the aim returns to where it was
                ctx.controls().look(prevYaw, prevPitch);
            }
        }
    }

    private boolean tickGliding(LivingEntity target) {
        maybeWindChargeBoost(Ent.dist(bot, target));
        phaseTick++;
        if (phaseTick > GLIDE_MAX_TICKS) {
            abort();
            return false;
        } else if (bot.isOnGround() && phaseTick > 5) {
            abort();
            return false;
        } else if (lostCover(target)) {
            abort();
            return false;
        }
        ElytraFlight.beginGlide(ctx);
        if (pearlLaunch
                && reheightCd == 0
                && Ent.pos(target).y() - Ent.pos(bot).y()
                        > ctx.tuning().number(TuningKey.ROCKETLESS_REHEIGHT_DELTA).doubleValue()
                && findAnywhere(Material.ENDER_PEARL) >= 0
                && ctx.settings().techniques().wcReset) {
            enterPhase(Phase.REHEIGHT);
            return true;
        }
        InventoryUtils.select(ctx, maceSlot >= 0 ? maceSlot : prevSlot);
        faceTarget(target);
        // Direct setDeltaMovement pull toward the target is not used: the glide is steered by look only.
        boolean above = Ent.pos(bot).y() > Ent.pos(target).y() + 1.5;
        if (above
                && Ent.dist(bot, target) <= ctx.tuning().number(TuningKey.ROCKETLESS_SMASH_ENTER_DIST).doubleValue()) {
            unequipElytra();
            enterPhase(Phase.SMASHING);
        }
        return true;
    }

    private boolean tickReheight(LivingEntity target) {
        phaseTick++;
        ctx.controls().stopLookInterpolation();
        ctx.controls().look(bot.getLocation().getYaw(), -90.0F);
        ElytraFlight.beginGlide(ctx);
        if (phaseTick == 1) {
            int slot = findAnywhere(Material.ENDER_PEARL);
            if (slot < 0) {
                backToGlide();
                return true;
            }
            if (slot >= 9) {
                slot = InventoryUtils.moveToHotbarSafe(ctx, slot);
            }
            InventoryUtils.select(ctx, slot);
            firePearlUp();
        } else if (phaseTick == 4) {
            int slot = findAnywhere(Material.WIND_CHARGE);
            if (slot >= 0) {
                if (slot >= 9) {
                    slot = InventoryUtils.moveToHotbarSafe(ctx, slot);
                }
                InventoryUtils.select(ctx, slot);
                fireWindCharge();
            }
        } else if (phaseTick >= 7) {
            backToGlide();
        }
        return true;
    }

    private void backToGlide() {
        reheightCd = ctx.tuning().integer(TuningKey.ROCKETLESS_REHEIGHT_COOLDOWN);
        ElytraFlight.beginGlide(ctx);
        InventoryUtils.select(ctx, maceSlot >= 0 ? maceSlot : prevSlot);
        enterPhase(Phase.GLIDING);
    }

    private boolean tickSmashing(LivingEntity target) {
        phaseTick++;
        if (phaseTick > SMASH_MAX_TICKS) {
            finish();
            return false;
        }
        InventoryUtils.select(ctx, maceSlot >= 0 ? maceSlot : prevSlot);
        faceTarget(target);
        double dx = Ent.pos(target).x() - Ent.pos(bot).x();
        double dz = Ent.pos(target).z() - Ent.pos(bot).z();
        // setDeltaMovement drift replaced by forward input along the target-aimed yaw.
        ctx.controls().setForward(smashForward(Math.sqrt(dx * dx + dz * dz)));
        boolean canSmash = bot.getFallDistance() > MIN_FALL_DIST && !bot.isGliding() && MaceSmashGates.botMayUseSmashFallBonus(ctx);
        if (canSmash && Ent.dist(bot, target) <= smashRange() && bot.hasLineOfSight(target)) {
            ctx.hands().attack(target);
            ctx.hands().swing(EquipmentSlot.HAND);
            if (onSmashLanded != null) {
                onSmashLanded.run();
            }
            finish();
            return false;
        } else if (bot.isOnGround() && phaseTick > 3) {
            finish();
            return false;
        }
        return true;
    }

    /** Forward input for the horizontal distance to the target: drift step min(0.42, hd * 0.5) as a fraction of 0.42. */
    static float smashForward(double horizontalDist) {
        if (horizontalDist <= 0.3) {
            return 0.0F;
        }
        return (float) (Math.min(0.42, horizontalDist * 0.5) / 0.42);
    }

    private boolean hasUsableElytra() {
        if (ElytraFlight.isUsable(bot.getEquipment().getItem(EquipmentSlot.CHEST))) {
            return true;
        }
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ElytraFlight.isUsable(inv.getItem(i))) {
                return true;
            }
        }
        return false;
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
            ItemStack stashedNow = inv.getItem(chestStashSlot);
            boolean stashIntact = ItemKinds.isEmpty(stashedNow)
                    || !ItemKinds.is(stashedNow, Material.ELYTRA) && ArmorEvaluator.getSlot(stashedNow) == EquipmentSlot.CHEST;
            if (!stashIntact) {
                for (int i = 0; i < 36; i++) {
                    if (ItemKinds.isEmpty(inv.getItem(i))) {
                        inv.setItem(i, chest.clone());
                        bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
                        return;
                    }
                }
            }
            ItemStack stashed = ItemKinds.isEmpty(stashedNow) ? null : stashedNow.clone();
            inv.setItem(chestStashSlot, chest.clone());
            bot.getEquipment().setItem(EquipmentSlot.CHEST, stashed);
        } else {
            // The elytra is not dropped when the inventory was full; with no stack-drop action it stays worn instead.
            for (int i = 0; i < 36; i++) {
                if (ItemKinds.isEmpty(inv.getItem(i))) {
                    inv.setItem(i, chest.clone());
                    bot.getEquipment().setItem(EquipmentSlot.CHEST, null);
                    return;
                }
            }
        }
    }

    private void faceTarget(LivingEntity target) {
        Vec3 pos = Ent.pos(bot);
        Vec3 t = Ent.pos(target);
        double dx = t.x() - pos.x();
        double dy = t.y() + target.getHeight() * 0.5 - Ent.eyePos(bot).y();
        double dz = t.z() - pos.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        float[] safe = ElytraObstacleGuard.towards(ctx, yaw, pitch, Math.sqrt(dx * dx + dy * dy + dz * dz));
        ctx.controls().look(safe[0], safe[1]);
    }

    private int findHotbar(Material material) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), material)) {
                return i;
            }
        }
        return -1;
    }

    private int findAnywhere(Material material) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), material)) {
                return i;
            }
        }
        return -1;
    }

    private void enterPhase(Phase p) {
        phase = p;
        phaseTick = 0;
    }

    private void finish() {
        cleanupFlight();
        cooldownLeft = ctx.tuning().integer(TuningKey.ROCKETLESS_COOLDOWN_TICKS);
        enterPhase(Phase.IDLE);
    }

    private boolean lostCover(LivingEntity target) {
        return target.isOnGround() && !cover.reachable(target, ctx.tick());
    }

    private void abort() {
        cleanupFlight();
        cooldownLeft = ctx.tuning().integer(TuningKey.ROCKETLESS_ABORT_COOLDOWN);
        enterPhase(Phase.IDLE);
    }

    private void cleanupFlight() {
        if (bot.isOnGround()) {
            unequipElytra();
        }
        if (prevSlot >= 0 && prevSlot < 9) {
            InventoryUtils.select(ctx, prevSlot);
        }
        ctx.controls().stopMoving();
    }

    public void reset() {
        if (phase != Phase.IDLE) {
            abort();
        }
    }

    private enum Phase {
        IDLE,
        LAUNCHING,
        GLIDING,
        REHEIGHT,
        SMASHING
    }
}
