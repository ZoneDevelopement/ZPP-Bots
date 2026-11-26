package dev.nandi0813.zppbots.ai.web;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.WebPlacer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class PredictionWebController {

    private static final double PLACE_REACH = 4.5;
    private static final int WINDUP_TICKS = 2;

    private final BotContext ctx;
    private final Player bot;
    private int cooldown = 0;
    private int windup = 0;
    private BlockPos pendingPos = null;
    private int prevSlot = -1;

    public PredictionWebController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isExecuting() {
        return windup > 0;
    }

    public void reset() {
        if (windup > 0 && prevSlot >= 0 && prevSlot < 9) {
            InventoryUtils.select(ctx, prevSlot);
        }

        cooldown = Math.max(cooldown, ctx.tuning().integer(TuningKey.PREDICTION_WEB_COOLDOWN_FAIL));
        windup = 0;
        pendingPos = null;
        prevSlot = -1;
    }

    public boolean tick(LivingEntity target) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (windup > 0) {
            return tickWindup();
        } else if (target == null || !Ent.alive(target)) {
            return false;
        } else if (cooldown > 0) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        } else {
            if (!ctx.settings().useCobwebs()) {
                return false;
            } else {
                double dist = Ent.dist(bot, target);
                if (dist < ctx.tuning().number(TuningKey.PREDICTION_WEB_MIN_RANGE).doubleValue()
                        || dist > ctx.tuning().number(TuningKey.PREDICTION_WEB_MAX_RANGE).doubleValue()) {
                    return false;
                } else if (!bot.hasLineOfSight(target)) {
                    return false;
                } else if (!WebPlacer.hasWeb(ctx)) {
                    return false;
                } else {
                    BlockPos spot = predictLane(target);
                    if (spot == null) {
                        return false;
                    } else if (Ent.pos(bot).distanceTo(Vec3.atCenterOf(spot)) > PLACE_REACH) {
                        return false;
                    } else {
                        int slot = WebPlacer.equipWeb(ctx);
                        if (slot < 0) {
                            return false;
                        } else {
                            prevSlot = InventoryUtils.selected(ctx);
                            InventoryUtils.select(ctx, slot);
                            pendingPos = spot;
                            windup = WINDUP_TICKS;
                            return true;
                        }
                    }
                }
            }
        }
    }

    private boolean tickWindup() {
        windup--;
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        int webSlot = WebPlacer.equipWeb(ctx);
        if (webSlot >= 0) {
            InventoryUtils.select(ctx, webSlot);
        }

        if (pendingPos != null) {
            WebPlacer.lookAt(ctx, pendingPos);
        }

        if (windup > 0) {
            return true;
        } else {
            boolean placed = pendingPos != null && WebPlacer.placeAt(ctx, pendingPos);
            if (prevSlot >= 0 && prevSlot < 9) {
                InventoryUtils.select(ctx, prevSlot);
            }

            cooldown = placed
                    ? ctx.tuning().integer(TuningKey.PREDICTION_WEB_COOLDOWN_OK)
                    : ctx.tuning().integer(TuningKey.PREDICTION_WEB_COOLDOWN_FAIL);
            pendingPos = null;
            prevSlot = -1;
            return placed;
        }
    }

    private BlockPos predictLane(LivingEntity target) {
        Vec3 vel = ctx.match().velocityOf(target);
        double speed = Math.hypot(vel.x(), vel.z());
        if (speed < ctx.tuning().number(TuningKey.PREDICTION_WEB_MIN_SPEED).doubleValue()) {
            return null;
        } else {
            Vec3 botPos = Ent.pos(bot);
            Vec3 targetPos = Ent.pos(target);
            Vec3 toBot = new Vec3(botPos.x() - targetPos.x(), 0.0, botPos.z() - targetPos.z());
            double closing = (toBot.x() * vel.x() + toBot.z() * vel.z()) / Math.max(0.001, toBot.length());
            if (closing < ctx.tuning().number(TuningKey.PREDICTION_WEB_MIN_CLOSING).doubleValue()) {
                return null;
            } else {
                int leadMax = ctx.tuning().integer(TuningKey.PREDICTION_WEB_LEAD_TICKS_MAX);
                int leadMin = ctx.tuning().integer(TuningKey.PREDICTION_WEB_LEAD_TICKS_MIN);
                for (int lead = leadMax; lead >= leadMin; lead--) {
                    double px = targetPos.x() + vel.x() * lead;
                    double pz = targetPos.z() + vel.z() * lead;
                    BlockPos feet = BlockPos.containing(px, targetPos.y() + 0.1, pz);
                    if (ctx.world().isAir(feet)
                            && !ctx.world().isAir(feet.below())
                            && !feet.equals(Ent.blockPos(target))
                            && !AABB.ofBlock(feet).intersects(Ent.box(bot).inflate(0.3))
                            && !(botPos.distanceTo(Vec3.atCenterOf(feet)) > PLACE_REACH)) {
                        return feet;
                    }
                }

                return null;
            }
        }
    }
}
