package dev.nandi0813.zppbots.ai.web;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.WebPlacer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

public final class HeadWebController {

    private static final int WALL_MIN_HEIGHT = 2;
    private static final int SETTLE_TICKS = 2;
    private static final int HEAD_RISE = 1;
    private static final int WINDUP_TICKS = 2;
    private static final double PLACE_REACH = 4.5;
    private static final int MAX_PHASE_TICKS = 20;

    private final BotContext ctx;
    private final Player bot;
    private Phase phase = Phase.NONE;
    private int phaseTick = 0;
    private int cooldown = 0;
    private int windup = 0;
    private LivingEntity victim = null;
    private BlockPos headSpot = null;
    private int prevSlot = -1;

    public HeadWebController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isExecuting() {
        return phase != Phase.NONE;
    }

    public void reset() {
        if (phase != Phase.NONE && prevSlot >= 0 && prevSlot < 9) {
            InventoryUtils.select(ctx, prevSlot);
        }

        phase = Phase.NONE;
        phaseTick = 0;
        windup = 0;
        victim = null;
        headSpot = null;
        prevSlot = -1;
        cooldown = Math.max(cooldown, ctx.tuning().integer(TuningKey.HEAD_WEB_COOLDOWN_FAIL));
    }

    public boolean tick(LivingEntity target) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (phase != Phase.NONE) {
            return drive();
        } else if (target == null || !Ent.alive(target)) {
            return false;
        } else if (cooldown > 0) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        } else if (Ent.dist(bot, target) > ctx.tuning().number(TuningKey.HEAD_WEB_HIT_RANGE).doubleValue()) {
            return false;
        } else if (!bot.hasLineOfSight(target)) {
            return false;
        } else {
            if (!ctx.settings().useCobwebs()) {
                return false;
            } else if (!WebPlacer.hasWeb(ctx)) {
                return false;
            } else if (wallBehind(target) == null) {
                return false;
            } else if (Ent.attackStrength(bot, 0.0F)
                    < ctx.tuning().number(TuningKey.HEAD_WEB_MIN_CHARGE).floatValue()) {
                return false;
            } else {
                lookAtEntity(target);
                ctx.controls().setSprinting(true);
                ctx.hands().attack(target);
                ctx.hands().swing(EquipmentSlot.HAND);
                victim = target;
                phase = Phase.WAIT;
                phaseTick = 0;
                return true;
            }
        }
    }

    private boolean drive() {
        phaseTick++;
        if (victim == null || !Ent.alive(victim)) {
            fail();
            return false;
        } else if (phaseTick > MAX_PHASE_TICKS) {
            fail();
            return false;
        } else {
            switch (phase) {
                case WAIT:
                    ctx.controls().stopMoving();
                    ctx.controls().setSprinting(false);
                    if (phaseTick < SETTLE_TICKS) {
                        return true;
                    } else {
                        headSpot = predictHeadAgainstWall(victim);
                        if (headSpot == null) {
                            fail();
                            return false;
                        } else if (Ent.pos(bot).distanceTo(Vec3.atCenterOf(headSpot)) > PLACE_REACH) {
                            fail();
                            return false;
                        } else {
                            int slot = WebPlacer.equipWeb(ctx);
                            if (slot < 0) {
                                fail();
                                return false;
                            }

                            prevSlot = InventoryUtils.selected(ctx);
                            InventoryUtils.select(ctx, slot);
                            windup = WINDUP_TICKS;
                            phase = Phase.PLACE;
                            phaseTick = 0;
                            return true;
                        }
                    }
                case PLACE:
                    ctx.controls().stopMoving();
                    int webSlot = WebPlacer.equipWeb(ctx);
                    if (webSlot >= 0) {
                        InventoryUtils.select(ctx, webSlot);
                    }

                    WebPlacer.lookAt(ctx, headSpot);
                    if (windup > 0) {
                        windup--;
                        return true;
                    }

                    boolean placed = WebPlacer.placeAt(ctx, headSpot);
                    if (prevSlot >= 0 && prevSlot < 9) {
                        InventoryUtils.select(ctx, prevSlot);
                    }

                    cooldown = placed
                            ? ctx.tuning().integer(TuningKey.HEAD_WEB_COOLDOWN_OK)
                            : ctx.tuning().integer(TuningKey.HEAD_WEB_COOLDOWN_FAIL);
                    phase = Phase.NONE;
                    victim = null;
                    headSpot = null;
                    prevSlot = -1;
                    return placed;
                default:
                    fail();
                    return false;
            }
        }
    }

    private void fail() {
        if (prevSlot >= 0 && prevSlot < 9) {
            InventoryUtils.select(ctx, prevSlot);
        }

        phase = Phase.NONE;
        windup = 0;
        victim = null;
        headSpot = null;
        prevSlot = -1;
        cooldown = ctx.tuning().integer(TuningKey.HEAD_WEB_COOLDOWN_FAIL);
    }

    private BlockPos wallBehind(LivingEntity target) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double dx = targetPos.x() - botPos.x();
        double dz = targetPos.z() - botPos.z();
        double len = Math.hypot(dx, dz);
        if (len < 0.001) {
            return null;
        } else {
            dx /= len;
            dz /= len;

            double wallMaxDist = ctx.tuning().number(TuningKey.HEAD_WEB_WALL_MAX_DIST).doubleValue();
            for (double d = 0.6; d <= wallMaxDist; d += 0.5) {
                BlockPos at = BlockPos.containing(targetPos.x() + dx * d, Ent.eyePos(target).y(), targetPos.z() + dz * d);
                if (isWall(at)) {
                    boolean tall = true;

                    for (int h = 0; h < WALL_MIN_HEIGHT; h++) {
                        if (!isWall(at.above(h))) {
                            tall = false;
                            break;
                        }
                    }

                    if (tall) {
                        return at;
                    }
                }
            }

            return null;
        }
    }

    private boolean isWall(BlockPos pos) {
        return ctx.world().isFullBlock(pos);
    }

    private BlockPos predictHeadAgainstWall(LivingEntity target) {
        BlockPos wall = wallBehind(target);
        if (wall == null) {
            return null;
        } else {
            Vec3 botPos = Ent.pos(bot);
            Vec3 targetPos = Ent.pos(target);
            double dx = targetPos.x() - botPos.x();
            double dz = targetPos.z() - botPos.z();
            double len = Math.hypot(dx, dz);
            if (len < 0.001) {
                return null;
            } else {
                dx /= len;
                dz /= len;
                BlockPos against = BlockPos.containing(wall.getX() + 0.5 - dx, Ent.eyePos(target).y(), wall.getZ() + 0.5 - dz);
                BlockPos high = against.above(HEAD_RISE);
                if (ctx.world().isAir(high)) {
                    return high;
                } else {
                    return ctx.world().isAir(against) ? against : null;
                }
            }
        }
    }

    private void lookAtEntity(LivingEntity target) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double dx = targetPos.x() - botPos.x();
        double dy = Ent.eyePos(target).y() - Ent.eyePos(bot).y();
        double dz = targetPos.z() - botPos.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, horiz))));
    }

    private enum Phase {
        NONE,
        WAIT,
        PLACE
    }
}
