package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.TechniqueConfig;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.StunSlam;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class TridentMaceController {

    private static final String PHASE_CHANGE = "PHASE_CHANGE";
    public static final int REQUIRED_RIPTIDE_LEVEL = 3;
    private static final double MIN_FALL_DIST = 1.5;
    private static final int MAX_CHARGE_TICKS = 24;
    private static final int MAX_RIPTIDE_UP_TICKS = 50;
    private static final int MAX_DIVE_TICKS = 45;
    private static final int MAX_SMASH_TICKS = 50;
    private static final int RIPTIDE_UP_COUNT = 2;
    private static final int RIPTIDE_COOLDOWN = 6;
    private static final float UP_PITCH = -89.0F;
    private static final int CHARGE_TICKS = 12;

    private final BotContext ctx;
    private final Player bot;
    private final StunSlam stunSlam = new StunSlam();
    private Phase phase = Phase.IDLE;
    private int phaseTick;
    private int cooldown;
    private int riptidesLeft;
    private int riptideCooldown;
    private int chargeTicks;
    private int tridentSlot = -1;
    private int maceSlot = -1;
    private int prevSlot;

    public TridentMaceController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (riptideCooldown > 0) {
            riptideCooldown--;
        }
        return switch (phase) {
            case IDLE -> tickIdle(target, dist);
            case CHARGING -> tickCharging();
            case RIPTIDE_UP -> tickRiptideUp(target);
            case RIPTIDE_DIVE -> tickRiptideDive(target);
            case SMASHING -> tickSmashing(target);
        };
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public void reset() {
        if (phase != Phase.IDLE && bot.isOnGround()) {
            abort();
        }
    }

    public static boolean hasComboInHotbar(BotContext ctx) {
        return findRiptideTridentSlot(ctx) >= 0 && findMaceSlot(ctx) >= 0;
    }

    public static boolean canAttempt(BotContext ctx) {
        return hasComboInHotbar(ctx) && mayRiptide(ctx);
    }

    private boolean tickIdle(LivingEntity target, double dist) {
        if (target == null || !Ent.alive(target)) {
            return false;
        } else if (cooldown > 0) {
            return false;
        } else if (dist > ctx.tuning().number(TuningKey.TRIDENT_MACE_TRIGGER_RANGE).doubleValue()) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        } else if (!mayRiptide(ctx)) {
            return false;
        } else {
            tridentSlot = findRiptideTridentSlot(ctx);
            maceSlot = findMaceSlot(ctx);
            if (tridentSlot < 0 || maceSlot < 0) {
                return false;
            } else if (!ctx.settings().roll()) {
                return false;
            } else {
                prevSlot = InventoryUtils.selected(ctx);
                riptidesLeft = RIPTIDE_UP_COUNT;
                chargeTicks = 0;
                enterPhase(Phase.CHARGING);
                return true;
            }
        }
    }

    private boolean tickCharging() {
        phaseTick++;
        if (!comboItemsPresent()) {
            abort();
            return false;
        } else if (phaseTick > MAX_CHARGE_TICKS) {
            abort();
            return false;
        } else {
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
            InventoryUtils.select(ctx, tridentSlot);
            ctx.controls().look(bot.getLocation().getYaw(), UP_PITCH);
            ItemStack trident = bot.getInventory().getItemInMainHand();
            if (!ItemKinds.is(trident, Material.TRIDENT)) {
                abort();
                return false;
            } else {
                if (!bot.isHandRaised()) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }

                chargeTicks++;
                if (chargeTicks >= CHARGE_TICKS) {
                    enterPhase(Phase.RIPTIDE_UP);
                }

                return true;
            }
        }
    }

    private boolean tickRiptideUp(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (!comboItemsPresent()) {
            abort();
            return false;
        } else if (phaseTick > MAX_RIPTIDE_UP_TICKS) {
            chargeTicks = 0;
            enterPhase(Phase.RIPTIDE_DIVE);
            return true;
        } else {
            InventoryUtils.select(ctx, tridentSlot);
            ctx.controls().look(bot.getLocation().getYaw(), UP_PITCH);
            if (riptidesLeft <= 0) {
                chargeTicks = 0;
                enterPhase(Phase.RIPTIDE_DIVE);
                return true;
            } else if (riptideCooldown > 0) {
                return true;
            } else {
                if (fireRiptide(UP_PITCH, bot.getLocation().getYaw())) {
                    riptidesLeft--;
                    riptideCooldown = RIPTIDE_COOLDOWN;
                }

                return true;
            }
        }
    }

    private boolean tickRiptideDive(LivingEntity target) {
        phaseTick++;
        if (target == null || !Ent.alive(target)) {
            abort();
            return false;
        } else if (!comboItemsPresent()) {
            abort();
            return false;
        } else if (phaseTick > MAX_DIVE_TICKS) {
            InventoryUtils.select(ctx, maceSlot);
            enterPhase(Phase.SMASHING);
            return true;
        } else {
            faceTowardTarget(target);
            InventoryUtils.select(ctx, tridentSlot);
            if (riptideCooldown <= 0 && mayRiptide(ctx)
                    && fireRiptide(bot.getLocation().getPitch(), bot.getLocation().getYaw())) {
                riptideCooldown = RIPTIDE_COOLDOWN;
                InventoryUtils.select(ctx, maceSlot);
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
        } else if (!comboItemsPresent()) {
            abort();
            return false;
        } else if (phaseTick > MAX_SMASH_TICKS) {
            abort();
            return false;
        } else {
            InventoryUtils.select(ctx, maceSlot);
            faceTowardTarget(target);
            driftToward(target);
            double dist = Ent.dist(bot, target);
            boolean inRange = dist <= ctx.settings().attackRange();
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

                        abort();
                        cooldown = ctx.tuning().integer(TuningKey.TRIDENT_MACE_COOLDOWN_TICKS);
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

    private boolean fireRiptide(float pitch, float yaw) {
        if (!mayRiptide(ctx)) {
            return false;
        } else {
            ctx.controls().look(yaw, pitch);
            InventoryUtils.select(ctx, tridentSlot);
            ItemStack stack = bot.getInventory().getItemInMainHand();
            if (!ItemKinds.is(stack, Material.TRIDENT)) {
                return false;
            } else {
                if (!bot.isHandRaised()) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }

                chargeTicks++;
                if (chargeTicks < CHARGE_TICKS) {
                    return false;
                } else {
                    boolean launched = launchRiptide(stack);
                    if (launched) {
                        chargeTicks = 0;
                    }

                    return launched;
                }
            }
        }
    }

    /** The server launches the riptide when the charged trident is released in water or rain; no velocity is written. */
    private boolean launchRiptide(ItemStack stack) {
        if (EnchantmentUtils.getEnchantLevel(stack, "riptide") <= 0) {
            return false;
        }

        ctx.hands().releaseUseItem();
        return bot.isRiptiding();
    }

    private static boolean mayRiptide(BotContext ctx) {
        Player bot = ctx.player();
        return bot.isInWater() || bot.isInRain();
    }

    private void faceTowardTarget(LivingEntity target) {
        Vec3 eye = Ent.eyePos(bot);
        Vec3 tpos = Ent.pos(target);
        double dx = tpos.x() - eye.x();
        double dy = tpos.y() + target.getHeight() * 0.35 - eye.y();
        double dz = tpos.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        ctx.controls().look(yaw, pitch);
    }

    private void driftToward(LivingEntity target) {
        double dist = Ent.dist(bot, target);
        if (dist > ctx.tuning().number(TuningKey.TRIDENT_MACE_DRIFT_STOP_DIST).doubleValue()) {
            ctx.controls().moveForward();
            ctx.controls().setSprinting(dist > ctx.tuning().number(TuningKey.TRIDENT_MACE_DRIFT_SPRINT_DIST).doubleValue());
        } else {
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
        }
    }

    private boolean comboItemsPresent() {
        return findRiptideTridentSlot(ctx) >= 0 && findMaceSlot(ctx) >= 0;
    }

    private void enterPhase(Phase next) {
        ctx.log().decision("TridentMaceController", PHASE_CHANGE, "[TridentMace] phase " + phase + " -> " + next + " (chargeTicks=" + chargeTicks
                + ", riptidesLeft=" + riptidesLeft + ", onGround=" + bot.isOnGround() + ")");
        phase = next;
        phaseTick = 0;
    }

    private void abort() {
        if (bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        InventoryUtils.select(ctx, prevSlot);
        cooldown = ctx.tuning().integer(TuningKey.TRIDENT_MACE_ABORT_COOLDOWN);
        riptidesLeft = 0;
        riptideCooldown = 0;
        chargeTicks = 0;
        enterPhase(Phase.IDLE);
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

    private static int findRiptideTridentSlot(BotContext ctx) {
        PlayerInventory inv = ctx.player().getInventory();

        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, Material.TRIDENT) && EnchantmentUtils.hasEnchantAtLeast(s, "riptide", REQUIRED_RIPTIDE_LEVEL)) {
                return i;
            }
        }

        return -1;
    }

    private static int findMaceSlot(BotContext ctx) {
        PlayerInventory inv = ctx.player().getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.MACE)) {
                return i;
            }
        }

        return -1;
    }

    private enum Phase {
        IDLE,
        CHARGING,
        RIPTIDE_UP,
        RIPTIDE_DIVE,
        SMASHING
    }
}
