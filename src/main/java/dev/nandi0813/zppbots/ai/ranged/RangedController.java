package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.ai.melee.WeaponSelector;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class RangedController {

    private static final int FULL_DRAW_TICKS = 20;
    private static final int CROSSBOW_LOAD_TICKS = 30;

    private final BotContext ctx;
    private final Player bot;
    private int drawTicks = 0;
    private boolean isDrawing = false;
    private int loadTicks = 0;
    private int crossbowCooldown = 0;
    private int shots = 0;
    private float aimYawErr = 0.0F;
    private float aimPitchErr = 0.0F;

    public RangedController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick(LivingEntity target, WeaponSelector.WeaponType type) {
        double dist = Ent.dist(bot, target);
        if (dist > ctx.tuning().number(TuningKey.RANGED_MAX_RANGE).doubleValue()) {
            stopUsing();
            return false;
        } else {
            aimAtTarget(target);

            return switch (type) {
                case BOW -> tickBow(target);
                case CROSSBOW -> tickCrossbow(target);
                default -> false;
            };
        }
    }

    public void reset() {
        stopUsing();
    }

    /** Arrows or bolts released so far; the brain counts volleys from this. */
    public int shotsFired() {
        return shots;
    }

    public void tickPreload() {
        ItemStack held = bot.getInventory().getItemInMainHand();
        if (ItemKinds.is(held, Material.CROSSBOW)) {
            if (ItemKinds.isCrossbowCharged(held)) {
                if (isDrawing && bot.isHandRaised() && bot.getActiveItem().getType() == Material.CROSSBOW) {
                    ctx.hands().releaseUseItem();
                }

                isDrawing = false;
                loadTicks = 0;
            } else {
                if (isDrawing && !bot.isHandRaised()) {
                    isDrawing = false;
                    loadTicks = 0;
                }

                if (!isDrawing) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                    isDrawing = true;
                    loadTicks = 0;
                }

                loadTicks++;
                if (loadTicks >= CROSSBOW_LOAD_TICKS) {
                    ctx.hands().releaseUseItem();
                    isDrawing = false;
                    loadTicks = 0;
                }
            }
        }
    }

    private boolean tickBow(LivingEntity target) {
        if (isDrawing && (!bot.isHandRaised() || bot.getActiveItem().getType() != Material.BOW)) {
            isDrawing = false;
            drawTicks = 0;
        }

        if (!isDrawing) {
            rollAimError();
            ctx.hands().useItem(EquipmentSlot.HAND);
            isDrawing = true;
            drawTicks = 0;
        }

        drawTicks++;
        if (drawTicks >= FULL_DRAW_TICKS
                && isFacingTarget(target, ctx.tuning().number(TuningKey.RANGED_FACING_TOLERANCE).floatValue())) {
            ctx.hands().releaseUseItem();
            shots++;
            isDrawing = false;
            drawTicks = 0;
        }

        return true;
    }

    private boolean tickCrossbow(LivingEntity target) {
        if (crossbowCooldown > 0) {
            crossbowCooldown--;
            return true;
        } else {
            ItemStack held = CrossbowHelper.selectedStack(ctx);
            if (!ItemKinds.isCrossbowCharged(held)) {
                if (isDrawing && !bot.isHandRaised()) {
                    isDrawing = false;
                    loadTicks = 0;
                }

                if (!isDrawing) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                    isDrawing = true;
                    loadTicks = 0;
                }

                loadTicks++;
                if (loadTicks >= CROSSBOW_LOAD_TICKS) {
                    ctx.hands().releaseUseItem();
                    isDrawing = false;
                    loadTicks = 0;
                }

                return true;
            } else {
                isDrawing = false;
                loadTicks = 0;
                if (isFacingTarget(target, ctx.tuning().number(TuningKey.RANGED_FACING_TOLERANCE).floatValue())) {
                    if (CrossbowHelper.fireIfCharged(ctx)) {
                        shots++;
                        crossbowCooldown = ctx.tuning().integer(TuningKey.RANGED_CROSSBOW_FIRE_COOLDOWN);
                        rollAimError();
                    } else {
                        CrossbowHelper.clearCharge(ctx);
                    }
                }

                return true;
            }
        }
    }

    private void aimAtTarget(LivingEntity target) {
        Vec3 eye = Ent.eyePos(bot);
        Vec3 tpos = Ent.pos(target);
        double dx = tpos.x() - eye.x();
        double dz = tpos.z() - eye.z();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        double flightTime = horizDist / 3.0;
        double gravComp = 0.025 * flightTime * flightTime;
        double adjustedDy = Ent.eyePos(target).y() - eye.y() + gravComp;
        float pitch = (float) (-Math.toDegrees(Math.atan2(adjustedDy, horizDist)));
        pitch = Mth.clamp(pitch + aimPitchErr, -90.0F, 90.0F);
        ctx.controls().look(yaw + aimYawErr, pitch);
    }

    private void rollAimError() {
        aimYawErr = aimError(ctx.tuning(), ctx.difficulty().accuracy, ctx.random().nextGaussian());
        aimPitchErr = aimError(ctx.tuning(), ctx.difficulty().accuracy, ctx.random().nextGaussian());
    }

    static float aimError(BotTuning.Profile profile, double accuracy, double gaussian) {
        return (float) (gaussian * (1.0 - accuracy) * profile.number(TuningKey.RANGED_AIM_ERROR_DEG).doubleValue());
    }

    private boolean isFacingTarget(LivingEntity target, float toleranceDeg) {
        Vec3 self = Ent.pos(bot);
        Vec3 tpos = Ent.pos(target);
        double dx = tpos.x() - self.x();
        double dz = tpos.z() - self.z();
        float requiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Math.abs(Mth.wrapDegrees(bot.getLocation().getYaw() - requiredYaw)) < toleranceDeg;
    }

    private void stopUsing() {
        if (releasesOnStop(isDrawing, bot.isHandRaised(), bot.getActiveItem().getType())) {
            ctx.hands().releaseUseItem();
        }

        isDrawing = false;
        drawTicks = 0;
        loadTicks = 0;
        crossbowCooldown = 0;
    }

    // Only a drawn bow or crossbow is ours to release; a raised potion or food belongs to the healing controllers.
    static boolean releasesOnStop(boolean drawing, boolean handRaised, Material active) {
        return drawing || handRaised && (active == Material.BOW || active == Material.CROSSBOW);
    }

    // The bow lock holds the ranged branch off until its tick, but a target fleeing past the max range re-arms the bow.
    public static boolean bowLockActive(int tick, int lockUntil, double dist, double maxDist) {
        return tick < lockUntil && dist <= maxDist;
    }

    public static boolean volleySpent(int shotsSinceLock, int volleyShots) {
        return shotsSinceLock >= volleyShots;
    }
}
