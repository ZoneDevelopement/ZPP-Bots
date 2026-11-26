package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

public final class ShieldController {

    private static final NamespacedKey SPEED_ID = new NamespacedKey("zppbots", "shield_slow");
    private static final double SPEED_REDUCTION = -0.7;

    private final BotContext ctx;
    private final Player bot;
    private boolean isBlocking = false;
    private int blockTicks = 0;
    private int cooldown = 0;
    private float prevTargCharge = 0.0F;
    private int predictHoldTicks = 0;
    private int blockhitTicksLeft = 0;
    private int blockhitRequested = 0;
    private boolean blockhitPunishPending = false;
    private int approachTicks = 0;
    private int baitTicksLeft = 0;
    private boolean sacrifice = false;

    public ShieldController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public void tick(LivingEntity target, boolean shieldInOffhand) {
        if (shieldInOffhand && target != null) {
            if (bot.hasCooldown(bot.getInventory().getItemInOffHand())) {
                if (isBlocking) {
                    stopBlocking();
                }
                clearTransientWindows();
            } else if (cooldown > 0) {
                cooldown--;
                if (isBlocking) {
                    stopBlocking();
                }
            } else {
                double dist = Ent.dist(bot, target);
                float botCharge = Ent.attackStrength(bot, 0.0F);
                float targCharge = target instanceof Player p ? Ent.attackStrength(p, 0.0F) : 1.0F;
                boolean wantBlock = shouldBlock(dist, botCharge, targCharge, target);
                if (baitTicksLeft > 0) {
                    baitTicksLeft--;
                    wantBlock = true;
                }

                if (sacrifice) {
                    wantBlock = true;
                }

                if (wantBlock) {
                    if (!isBlocking) {
                        startBlocking();
                    } else if (!bot.isHandRaised()) {
                        ctx.hands().useItem(EquipmentSlot.OFF_HAND);
                        applySpeedPenalty();
                    }
                } else if (isBlocking) {
                    stopBlocking();
                }

                if (isBlocking) {
                    blockTicks++;
                    if (blockTicks >= (sacrifice ? ctx.tuning().integer(TuningKey.SHIELD_SACRIFICE_MAX_TICKS) : ctx.tuning().integer(TuningKey.SHIELD_MAX_BLOCK_TICKS))) {
                        stopBlocking();
                        cooldown = ctx.tuning().integer(TuningKey.SHIELD_REBLOCK_COOLDOWN);
                    }
                }
            }
        } else {
            if (isBlocking) {
                stopBlocking();
            }
            clearTransientWindows();
        }
    }

    public void reset() {
        if (isBlocking) {
            stopBlocking();
        }

        cooldown = 0;
        blockhitTicksLeft = 0;
        blockhitPunishPending = false;
        predictHoldTicks = 0;
        approachTicks = 0;
        baitTicksLeft = 0;
        sacrifice = false;
    }

    private void clearTransientWindows() {
        blockhitTicksLeft = 0;
        blockhitPunishPending = false;
        baitTicksLeft = 0;
        predictHoldTicks = 0;
        approachTicks = 0;
    }

    public boolean isBlocking() {
        return isBlocking;
    }

    public float getPrevTargetCharge() {
        return prevTargCharge;
    }

    public void requestBlockhit(int ticks) {
        blockhitTicksLeft = ticks;
        blockhitRequested = ticks;
    }

    public void requestBait(int ticks) {
        if (cooldown == 0) {
            baitTicksLeft = ticks;
        }
    }

    public void setSacrifice(boolean on) {
        sacrifice = on;
    }

    private boolean shouldBlock(double dist, float botCharge, float targCharge, LivingEntity target) {
        float chargeVel = targCharge - prevTargCharge;
        prevTargCharge = targCharge;
        Vec3 tv = ctx.match().velocityOf(target);
        double hs = Math.sqrt(tv.x() * tv.x() + tv.z() * tv.z());
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double tx = botPos.x() - targetPos.x();
        double tz = botPos.z() - targetPos.z();
        double td = Math.sqrt(tx * tx + tz * tz);
        boolean towardUs = hs >= ctx.tuning().number(TuningKey.SHIELD_APPROACH_MIN_SPEED).doubleValue() && td > 0.001 && (tv.x() * tx + tv.z() * tz) / (hs * td) >= ctx.tuning().number(TuningKey.SHIELD_APPROACH_DOT).doubleValue();
        approachTicks = towardUs ? approachTicks + 1 : 0;
        if (blockhitTicksLeft > 0) {
            if (blockhitReleasesFar(ctx.tuning(), dist)) {
                blockhitTicksLeft = 0;
                blockhitPunishPending = false;
                return false;
            }
            if (chargeVel <= ctx.tuning().number(TuningKey.SHIELD_PUNISH_CHARGE_DROP).floatValue() && dist <= ctx.tuning().number(TuningKey.SHIELD_PUNISH_RANGE).doubleValue()) {
                blockhitPunishPending = true;
            }

            if (blockhitPunishPending && botCharge >= ctx.tuning().number(TuningKey.SHIELD_RELEASE_CHARGE).floatValue()) {
                blockhitTicksLeft = 0;
                blockhitPunishPending = false;
                return false;
            }
            int held = blockhitRequested - blockhitTicksLeft;
            if (held >= ctx.tuning().integer(TuningKey.SHIELD_BLOCKHIT_MIN_HOLD) && botCharge >= ctx.tuning().number(TuningKey.SHIELD_RELEASE_CHARGE).floatValue()) {
                blockhitTicksLeft = 0;
                blockhitPunishPending = false;
                return false;
            }
            blockhitTicksLeft--;
            return true;
        }
        blockhitPunishPending = false;
        if (botCharge >= ctx.tuning().number(TuningKey.SHIELD_RELEASE_CHARGE).floatValue()) {
            return false;
        } else if (dist <= ctx.tuning().number(TuningKey.SHIELD_APPROACH_RAISE_RANGE).doubleValue() && approachTicks >= ctx.tuning().integer(TuningKey.SHIELD_APPROACH_SUSTAIN)) {
            predictHoldTicks = ctx.tuning().integer(TuningKey.SHIELD_PREDICT_HOLD_TICKS);
            return true;
        } else if (dist > ctx.tuning().number(TuningKey.SHIELD_RANGE).doubleValue()) {
            return false;
        } else if (predictHoldTicks > 0) {
            predictHoldTicks--;
            return true;
        } else if (target instanceof Player blocker && blocker.isBlocking()) {
            return true;
        }
        if (targCharge >= ctx.tuning().number(TuningKey.SHIELD_MIN_BLOCK_CHARGE).floatValue() && chargeVel > 0.001F) {
            float remaining = (1.0F - targCharge) / chargeVel;
            if (remaining <= ctx.tuning().integer(TuningKey.SHIELD_PREDICT_LEAD_TICKS)) {
                predictHoldTicks = ctx.tuning().integer(TuningKey.SHIELD_PREDICT_HOLD_TICKS);
                return true;
            }
        }

        return dist <= ctx.tuning().number(TuningKey.SHIELD_RUSH_RANGE).doubleValue() && target instanceof Player p && p.isSprinting() ? true : targCharge >= ctx.tuning().number(TuningKey.SHIELD_LATE_BLOCK_CHARGE).floatValue();
    }

    // Knockback after a trade leaves both sides out of reach; holding the shield there only costs speed and swings.
    static boolean blockhitReleasesFar(BotTuning.Profile profile, double dist) {
        return dist > profile.number(TuningKey.SHIELD_RANGE).doubleValue();
    }

    private void startBlocking() {
        if (!bot.isHandRaised()) {
            isBlocking = true;
            blockTicks = 0;
            ctx.hands().useItem(EquipmentSlot.OFF_HAND);
            applySpeedPenalty();
        }
    }

    private void stopBlocking() {
        isBlocking = false;
        blockTicks = 0;
        if (bot.isHandRaised() && bot.getActiveItemHand() == EquipmentSlot.OFF_HAND) {
            ctx.hands().releaseUseItem();
        }
        removeSpeedPenalty();
    }

    public void clearModifiers() {
        removeSpeedPenalty();
    }

    private void applySpeedPenalty() {
        AttributeInstance attr = bot.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(SPEED_ID);
            attr.addTransientModifier(new AttributeModifier(SPEED_ID, SPEED_REDUCTION, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void removeSpeedPenalty() {
        AttributeInstance attr = bot.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(SPEED_ID);
        }
    }
}
