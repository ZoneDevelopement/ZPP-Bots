package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayDeque;

public final class TunnelController {

    private final BotContext ctx;
    private final Player bot;
    private boolean active = false;
    private Mode mode = Mode.MINING;
    private final ArrayDeque<BlockPos> cells = new ArrayDeque<>();
    private boolean stepUp = false;
    private int cellTicks = 0;
    private int walkTicks = 0;
    private int blockedTicks = 0;
    private double bestDist = Double.POSITIVE_INFINITY;
    private int noProgress = 0;
    private int cooldown = 0;
    private double bestTriggerDist = Double.POSITIVE_INFINITY;

    public TunnelController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isActive() {
        return active;
    }

    public boolean stuck() {
        return !active && blockedTicks >= ctx.tuning().integer(TuningKey.TUNNEL_STUCK_TICKS_FOR_PEARL);
    }

    public void reset() {
        if (active) {
            ctx.controls().stopMining();
            ctx.controls().stopMoving();
        }

        active = false;
        cells.clear();
        cellTicks = 0;
        walkTicks = 0;
        blockedTicks = 0;
        bestDist = Double.POSITIVE_INFINITY;
        noProgress = 0;
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (target != null && Ent.alive(target)) {
            if (!active) {
                double verticalThreshold =
                        ctx.tuning().number(TuningKey.TUNNEL_VERTICAL_THRESHOLD).doubleValue();
                boolean below = Ent.pos(target).y() - Ent.pos(bot).y() < -verticalThreshold
                        && horizontal(target)
                                <= ctx.tuning()
                                        .number(TuningKey.TUNNEL_BELOW_HORIZONTAL)
                                        .doubleValue()
                        && dist > ctx.tuning().number(TuningKey.TUNNEL_MELEE_REACH).doubleValue();
                boolean walled = dist
                                <= ctx.tuning().number(TuningKey.TUNNEL_MAX_DIST).doubleValue()
                        && (below || !bot.hasLineOfSight(target));
                if (walled && triggerBlocked(ctx.tuning(), dist, bestTriggerDist)) {
                    blockedTicks++;
                } else {
                    blockedTicks = 0;
                    bestTriggerDist = dist;
                }

                if (!walled) {
                    bestTriggerDist = Double.POSITIVE_INFINITY;
                }

                if (cooldown > 0 || blockedTicks < ctx.tuning().integer(TuningKey.TUNNEL_BLOCKED_TRIGGER_TICKS)) {
                    return false;
                }

                if (!bot.isOnGround()) {
                    return false;
                }

                if (!planStep(target)) {
                    blockedTicks = 0;
                    return false;
                }

                active = true;
                mode = Mode.MINING;
                bestDist = dist;
                noProgress = 0;
            }

            if (bot.hasLineOfSight(target)
                    && dist <= ctx.tuning().number(TuningKey.TUNNEL_EXIT_SIGHT_DIST).doubleValue()
                    && (dist <= ctx.tuning().number(TuningKey.TUNNEL_MELEE_REACH).doubleValue()
                            || Ent.pos(target).y() - Ent.pos(bot).y()
                                    >= -ctx.tuning()
                                            .number(TuningKey.TUNNEL_VERTICAL_THRESHOLD)
                                            .doubleValue())) {
                reset();
                return false;
            } else if (dist > ctx.tuning().number(TuningKey.TUNNEL_ABORT_DIST).doubleValue()) {
                abort();
                return false;
            } else {
                if (madeProgress(ctx.tuning(), dist, bestDist)) {
                    bestDist = dist;
                    noProgress = 0;
                } else if (++noProgress > ctx.tuning().integer(TuningKey.TUNNEL_STUCK_TICKS)) {
                    abort();
                    return false;
                }

                return mode == Mode.MINING ? tickMining(target) : tickWalking(target);
            }
        } else {
            reset();
            return false;
        }
    }

    private boolean tickMining(LivingEntity target) {
        while (!cells.isEmpty() && !needsMining(cells.peekFirst())) {
            cells.pollFirst();
            cellTicks = 0;
        }

        if (cells.isEmpty()) {
            ctx.controls().stopMining();
            mode = Mode.WALKING;
            walkTicks = 0;
            return true;
        } else {
            BlockPos cell = cells.peekFirst();
            if (ctx.world().type(cell).getHardness() < 0.0F) {
                abort();
                return false;
            } else {
                int toolSlot = bestToolSlot(ctx.world().data(cell));
                if (toolSlot < 0) {
                    abort();
                    return false;
                } else {
                    ctx.controls().stopMoving();
                    ctx.controls().setSprinting(false);
                    InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, toolSlot));
                    faceBlock(cell);
                    ctx.controls().mineContinuous();
                    if (++cellTicks > ctx.tuning().integer(TuningKey.TUNNEL_PER_BLOCK_TIMEOUT)) {
                        abort();
                        return false;
                    } else {
                        return true;
                    }
                }
            }
        }
    }

    private boolean tickWalking(LivingEntity target) {
        faceStepDirection(target);
        ctx.controls().setMovement(1.0F, 0.0F);
        if (stepUp && bot.isOnGround()) {
            ctx.controls().jumpOnce();
        }

        if (++walkTicks >= ctx.tuning().integer(TuningKey.TUNNEL_WALK_TICKS)) {
            ctx.controls().stopMoving();
            if (!planStep(target)) {
                if (bot.hasLineOfSight(target)) {
                    reset();
                    return false;
                }

                abort();
                return false;
            }

            mode = Mode.MINING;
        }

        return true;
    }

    private boolean planStep(LivingEntity target) {
        BlockPos feet = Ent.blockPos(bot);
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double tx = targetPos.x() - botPos.x();
        double tz = targetPos.z() - botPos.z();
        double dy = targetPos.y() - botPos.y();
        Direction dir = ScaffoldController.stepDirection(tx, tz);
        boolean up = dy > ctx.tuning().number(TuningKey.TUNNEL_VERTICAL_THRESHOLD).doubleValue();
        boolean down = dy < -ctx.tuning().number(TuningKey.TUNNEL_VERTICAL_THRESHOLD).doubleValue();
        stepUp = up;
        cells.clear();
        if (up) {
            addIfMinable(feet.above(2));
            addIfMinable(feet.relative(dir).above(1));
            addIfMinable(feet.relative(dir).above(2));
        } else if (down) {
            boolean straight = horizontal(target) < ctx.tuning().number(TuningKey.TUNNEL_STRAIGHT_HORIZONTAL).doubleValue();
            BlockPos drop = straight ? feet.below() : feet.relative(dir).below();
            if (lavaAt(drop) || lavaAt(drop.below())) {
                return false;
            }

            if (!straight) {
                addIfMinable(feet.relative(dir).above(1));
                addIfMinable(feet.relative(dir));
            }

            addIfMinable(drop);
        } else {
            addIfMinable(feet.relative(dir).above(1));
            addIfMinable(feet.relative(dir));
        }

        cellTicks = 0;
        if (cells.isEmpty()) {
            return false;
        } else {
            for (BlockPos c : cells) {
                if (ctx.world().type(c).getHardness() < 0.0F) {
                    return false;
                }

                if (bestToolSlot(ctx.world().data(c)) < 0) {
                    return false;
                }
            }

            return true;
        }
    }

    private double horizontal(LivingEntity target) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double dx = targetPos.x() - botPos.x();
        double dz = targetPos.z() - botPos.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean lavaAt(BlockPos p) {
        return ctx.world().isLava(p);
    }

    private void addIfMinable(BlockPos p) {
        if (needsMining(p)) {
            cells.addLast(p);
        }
    }

    private boolean needsMining(BlockPos p) {
        return !ctx.world().isAir(p) && !ctx.world().collisionBoxes(p).isEmpty();
    }

    private int bestToolSlot(BlockData state) {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        float bestSpeed = ctx.tuning().number(TuningKey.TUNNEL_MIN_TOOL_SPEED).floatValue();

        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!ItemKinds.isEmpty(st)) {
                float sp = ItemKinds.destroySpeed(st, state);
                if (sp > bestSpeed) {
                    bestSpeed = sp;
                    best = i;
                }
            }
        }

        return best;
    }

    static boolean triggerBlocked(BotTuning.Profile profile, double dist, double best) {
        return dist > best - profile.number(TuningKey.TUNNEL_TRIGGER_PROGRESS).doubleValue();
    }

    static boolean madeProgress(BotTuning.Profile profile, double dist, double best) {
        return dist < best - profile.number(TuningKey.TUNNEL_PROGRESS).doubleValue();
    }

    private void abort() {
        reset();
        cooldown = ctx.tuning().integer(TuningKey.TUNNEL_ABORT_COOLDOWN);
    }

    private void faceBlock(BlockPos p) {
        Vec3 c = Vec3.atCenterOf(p);
        Vec3 eye = Ent.eyePos(bot);
        double dx = c.x() - eye.x();
        double dy = c.y() - eye.y();
        double dz = c.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, Math.max(horiz, 0.001)))));
    }

    private void faceStepDirection(LivingEntity target) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double dx = targetPos.x() - botPos.x();
        double dz = targetPos.z() - botPos.z();
        ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), 0.0F);
    }

    private enum Mode {
        MINING,
        WALKING
    }
}
