package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.ai.escape.WaterExit;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.path.MovementHelper;
import dev.nandi0813.zppbots.util.BotMath;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.NearbyBlocks;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.BiPredicate;

public final class PathFollower {

    private final BotContext ctx;
    private final Player bot;
    private final LocalPlanner planner;
    private final WaterExit waterExit;
    private Vec3 lastPos = null;
    private int reactiveStuckTicks = 0;
    private int reactiveAvoidDir = 0;
    private int reactiveAvoidTicks = 0;
    private double avoidStartGoalDist = Double.MAX_VALUE;
    private int avoidCommitTicks = 0;
    private boolean avoidHazard = false;
    private double bestGoalDist = Double.MAX_VALUE;
    private Vec3 bestGoalRef = null;
    private int reactiveJumpCd = 0;
    private int windChargeCd = 0;
    private int reactiveKnockTicks = 0;
    private boolean chasing = false;
    private boolean wasSwimming = false;

    public PathFollower(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.planner = new LocalPlanner(ctx);
        this.waterExit = new WaterExit(ctx);
    }

    public void chase(Entity target) {
        reactiveChase(Ent.pos(target), null);
    }

    public void chase(Vec3 goalPos) {
        reactiveChase(goalPos, null);
    }

    public void chase(Entity target, Vec3 facePos) {
        reactiveChase(Ent.pos(target), facePos);
    }

    public void chase(Vec3 goalPos, Vec3 facePos) {
        reactiveChase(goalPos, facePos);
    }

    private void reactiveChase(Vec3 goalPos, Vec3 facePos) {
        if (!ctx.match().canMove()) {
            ctx.controls().stopMoving();
        } else {
            chasing = true;
            Vec3 botPos = Ent.pos(bot);
            boolean combat = facePos != null;

            if (reactiveJumpCd > 0) {
                reactiveJumpCd--;
            }

            if (windChargeCd > 0) {
                windChargeCd--;
            }

            if (reactiveAvoidTicks > 0) {
                reactiveAvoidTicks--;
            }

            Vec3 vel = Ent.velocity(bot);
            double horizVel = Math.sqrt(vel.x() * vel.x() + vel.z() * vel.z());
            if (knockedBack(ctx.tuning(), horizVel, reactiveKnockTicks)) {
                reactiveKnockTicks++;
                ctx.controls().stopMoving();
                ctx.controls().setSprinting(false);
                lastPos = botPos;
            } else {
                reactiveKnockTicks = 0;
                if ((!bot.isInWater() || bot.isOnGround() && !bot.isUnderWater()) && (!waterExit.active() || bot.isOnGround())) {
                    NearbyBlocks blocks = new NearbyBlocks(ctx.world());
                    if (!bot.isInWater() && waterAhead(blocks, botPos, goalPos, waterMinDist(ctx.tuning(), combat))) {
                        swimChase(goalPos, facePos);
                        wasSwimming = true;
                        lastPos = botPos;
                    } else {
                        if (wasSwimming) {
                            ctx.controls().setSprinting(false);
                            ctx.controls().setJumping(false);
                            wasSwimming = false;
                        }

                        if (bot.isOnGround()) {
                            waterExit.reset();
                        }

                        Vec3 steerPt = planner.tick(goalPos, combat);
                        double dx;
                        double dz;
                        if (steerPt != null) {
                            dx = steerPt.x() - botPos.x();
                            dz = steerPt.z() - botPos.z();
                        } else {
                            dx = goalPos.x() - botPos.x();
                            dz = goalPos.z() - botPos.z();
                        }

                        double horizDist = Math.sqrt(dx * dx + dz * dz);
                        if (horizDist > 0.1) {
                            dx /= horizDist;
                            dz /= horizDist;
                        }

                        double ggx = goalPos.x() - botPos.x();
                        double ggz = goalPos.z() - botPos.z();
                        double goalLen = Math.sqrt(ggx * ggx + ggz * ggz);
                        if (goalLen > 0.1) {
                            ggx /= goalLen;
                            ggz /= goalLen;
                        }

                        if (bestGoalRef == null || bestGoalRef.distanceTo(goalPos) > ctx.tuning().number(TuningKey.FOLLOWER_GOAL_RESET_DIST).doubleValue()) {
                            bestGoalRef = goalPos;
                            bestGoalDist = goalLen;
                            reactiveStuckTicks = 0;
                        }

                        if (madeProgress(ctx.tuning(), goalLen, bestGoalDist)) {
                            bestGoalDist = goalLen;
                            reactiveStuckTicks = 0;
                        } else if (bot.isOnGround()) {
                            reactiveStuckTicks++;
                        }

                        boolean pressing = reactiveStuckTicks > 0;
                        if (lastPos != null && pressing && reactiveStuckTicks > ctx.tuning().integer(TuningKey.FOLLOWER_STUCK_TICKS) && reactiveAvoidTicks <= 0) {
                            planner.invalidate();
                            boolean fresh = reactiveAvoidDir == 0;
                            boolean progress = goalLen < avoidStartGoalDist - ctx.tuning().number(TuningKey.FOLLOWER_AVOID_PROGRESS).doubleValue();
                            boolean expired = avoidCommitTicks > ctx.tuning().integer(TuningKey.FOLLOWER_COMMIT_MAX);
                            if (fresh || progress || expired) {
                                int openL = sideOpenness(blocks, botPos, ggx, ggz, 1);
                                int openR = sideOpenness(blocks, botPos, ggx, ggz, -1);
                                if (openL != openR) {
                                    reactiveAvoidDir = openL > openR ? 1 : -1;
                                } else if (expired) {
                                    reactiveAvoidDir = -reactiveAvoidDir;
                                } else if (fresh) {
                                    reactiveAvoidDir = 1;
                                }

                                avoidStartGoalDist = goalLen;
                                avoidCommitTicks = 0;
                            }

                            avoidHazard = false;
                            reactiveAvoidTicks = ctx.tuning().integer(TuningKey.FOLLOWER_ESCAPE_TICKS);
                            if (reactiveJumpCd <= 0) {
                                ctx.controls().jumpOnce();
                                reactiveJumpCd = ctx.tuning().integer(TuningKey.FOLLOWER_ESCAPE_JUMP_CD);
                            }

                            reactiveStuckTicks = 0;
                        }

                        lastPos = botPos;
                        if (reactiveAvoidTicks > 0) {
                            avoidCommitTicks++;
                            boolean frontOpen = !isSolidAt(blocks, BlockPos.containing(botPos.x() + ggx * ctx.tuning().number(TuningKey.FOLLOWER_FRONT_PROBE).doubleValue(), botPos.y(), botPos.z() + ggz * ctx.tuning().number(TuningKey.FOLLOWER_FRONT_PROBE).doubleValue()))
                                    && !isSolidAt(blocks, BlockPos.containing(botPos.x() + ggx * ctx.tuning().number(TuningKey.FOLLOWER_FRONT_PROBE).doubleValue(), botPos.y() + 1.0, botPos.z() + ggz * ctx.tuning().number(TuningKey.FOLLOWER_FRONT_PROBE).doubleValue()))
                                    && !scanAhead(blocks, botPos, ggx, ggz, ctx.tuning().number(TuningKey.FOLLOWER_WEB_PROBE).doubleValue(), PathFollower::isWebAt)
                                    && !scanAhead(blocks, botPos, ggx, ggz, ctx.tuning().number(TuningKey.FOLLOWER_LAVA_PROBE).doubleValue(), PathFollower::isLavaAt);
                            if (!horizontalCollision() && frontOpen) {
                                reactiveAvoidTicks = 0;
                            } else {
                                double px = reactiveAvoidDir > 0 ? -ggz : ggz;
                                double pz = reactiveAvoidDir > 0 ? ggx : -ggx;
                                double fwd = avoidHazard ? 0.0 : ctx.tuning().number(TuningKey.FOLLOWER_AVOID_FORWARD).doubleValue();
                                dx = ggx * fwd + px;
                                dz = ggz * fwd + pz;
                                double len = Math.sqrt(dx * dx + dz * dz);
                                if (len > 1.0E-6) {
                                    dx /= len;
                                    dz /= len;
                                }
                            }
                        }

                        BlockPos feetAhead = BlockPos.containing(botPos.x() + dx * 0.5, botPos.y(), botPos.z() + dz * 0.5);
                        BlockPos headAhead = feetAhead.above();
                        BlockPos aboveHead = feetAhead.above(2);
                        boolean blockFeet = isSolidAt(blocks, feetAhead);
                        boolean blockHead = isSolidAt(blocks, headAhead);
                        boolean canJumpUp = blockFeet && !blockHead && !isSolidAt(blocks, aboveHead);
                        boolean isWall = blockFeet && blockHead;
                        BlockPos here = Ent.blockPos(bot);
                        boolean onLadder = isClimbableAt(blocks, here) || isClimbableAt(blocks, here.above());
                        BlockPos groundFront = BlockPos.containing(botPos.x() + dx * ctx.tuning().number(TuningKey.FOLLOWER_GROUND_PROBE).doubleValue(), botPos.y() - 1.0, botPos.z() + dz * ctx.tuning().number(TuningKey.FOLLOWER_GROUND_PROBE).doubleValue());
                        boolean holeAhead = !isSolidAt(blocks, groundFront) && !isSolidAt(blocks, groundFront.below());
                        double goalHoriz = Math.hypot(goalPos.x() - botPos.x(), goalPos.z() - botPos.z());
                        boolean webAhead = goalHoriz > ctx.tuning().number(TuningKey.FOLLOWER_WEB_MIN_DIST).doubleValue() && scanAhead(blocks, botPos, dx, dz, ctx.tuning().number(TuningKey.FOLLOWER_WEB_PROBE).doubleValue(), PathFollower::isWebAt);
                        boolean lavaAhead = scanAhead(blocks, botPos, dx, dz, ctx.tuning().number(TuningKey.FOLLOWER_LAVA_PROBE).doubleValue(), PathFollower::isLavaAt);
                        maybeOpenDoorAhead(botPos, new Vec3(botPos.x() + dx, botPos.y(), botPos.z() + dz));
                        if ((webAhead || lavaAhead) && reactiveAvoidTicks <= 0) {
                            int openL = sideOpenness(blocks, botPos, ggx, ggz, 1);
                            int openR = sideOpenness(blocks, botPos, ggx, ggz, -1);
                            if (openL != openR) {
                                reactiveAvoidDir = openL > openR ? 1 : -1;
                            } else if (reactiveAvoidDir == 0) {
                                reactiveAvoidDir = 1;
                            }

                            avoidHazard = true;
                            reactiveAvoidTicks = ctx.tuning().integer(TuningKey.FOLLOWER_ESCAPE_TICKS);
                            avoidStartGoalDist = goalLen;
                            avoidCommitTicks = 0;
                        }

                        if (bot.isOnGround() && reactiveJumpCd <= 0 && !webAhead && !lavaAhead) {
                            if (canJumpUp) {
                                ctx.controls().jumpOnce();
                                reactiveJumpCd = ctx.tuning().integer(TuningKey.FOLLOWER_STEP_JUMP_CD);
                            } else if (onLadder) {
                                ctx.controls().jumpOnce();
                                reactiveJumpCd = ctx.tuning().integer(TuningKey.FOLLOWER_LADDER_JUMP_CD);
                            } else if (holeAhead) {
                                ctx.controls().jumpOnce();
                                reactiveJumpCd = ctx.tuning().integer(TuningKey.FOLLOWER_GAP_JUMP_CD);
                            } else if (isWall && reactiveAvoidTicks <= 0) {
                                if (tryWindChargeOverWall(feetAhead, goalPos.y() - botPos.y())) {
                                    reactiveJumpCd = ctx.tuning().integer(TuningKey.FOLLOWER_WALL_JUMP_CD);
                                } else {
                                    int openL = sideOpenness(blocks, botPos, ggx, ggz, 1);
                                    int openR = sideOpenness(blocks, botPos, ggx, ggz, -1);
                                    reactiveAvoidDir = openL >= openR ? 1 : -1;
                                    reactiveAvoidTicks = ctx.tuning().integer(TuningKey.FOLLOWER_ESCAPE_TICKS);
                                    ctx.controls().jumpOnce();
                                    reactiveJumpCd = ctx.tuning().integer(TuningKey.FOLLOWER_ESCAPE_JUMP_CD);
                                }
                            }
                        }

                        if (onLadder) {
                            if (facePos == null) {
                                faceMovement(dx, dz);
                                ctx.controls().setMovement(1.0F, 0.0F);
                            } else {
                                moveAlong(dx, dz);
                            }
                            ctx.controls().setSprinting(false);
                        } else if (combat) {
                            Vec3 steer = new Vec3(botPos.x() + dx, botPos.y(), botPos.z() + dz);
                            steerStrafe(botPos, steer, facePos);
                        } else {
                            faceMovement(dx, dz);
                            ctx.controls().moveForward();
                        }
                    }
                } else {
                    swimChase(goalPos, facePos);
                    wasSwimming = true;
                    lastPos = botPos;
                }
            }
        }
    }

    static boolean madeProgress(BotTuning.Profile profile, double dist, double best) {
        return dist < best - profile.number(TuningKey.FOLLOWER_PROGRESS_EPS).doubleValue();
    }

    static boolean knockedBack(BotTuning.Profile profile, double velocity, int ticks) {
        return velocity > profile.number(TuningKey.FOLLOWER_KNOCKBACK_VEL).doubleValue()
                && ticks < profile.integer(TuningKey.FOLLOWER_KNOCK_TICKS);
    }

    static float swimPitch(BotTuning.Profile profile, float pitch) {
        float limit = profile.number(TuningKey.FOLLOWER_SWIM_PITCH_LIMIT).floatValue();
        return Math.max(-limit, Math.min(limit, pitch));
    }

    static double waterMinDist(BotTuning.Profile profile, boolean combat) {
        TuningKey key = combat ? TuningKey.FOLLOWER_WATER_MIN_COMBAT : TuningKey.FOLLOWER_WATER_MIN_TRAVEL;
        return profile.number(key).doubleValue();
    }

    /** Bukkit has no horizontalCollision flag: probe the body a step ahead along its facing. */
    private boolean horizontalCollision() {
        double rad = Math.toRadians(bot.getLocation().getYaw());
        Location ahead = bot.getLocation().add(-Math.sin(rad) * 0.1, 0.0, Math.cos(rad) * 0.1);
        return bot.collidesAt(ahead);
    }

    private float yaw() {
        return bot.getLocation().getYaw();
    }

    private float pitch() {
        return bot.getLocation().getPitch();
    }

    private void steerStrafe(Vec3 botPos, Vec3 steer, Vec3 facePos) {
        float yaw = yaw();
        double tdx = steer.x() - botPos.x();
        double tdz = steer.z() - botPos.z();
        double tlen = Math.sqrt(tdx * tdx + tdz * tdz);
        if (tlen < ctx.tuning().number(TuningKey.FOLLOWER_ARRIVAL_DIST).doubleValue()) {
            ctx.controls().stopMoving();
        } else {
            float[] input = strafeInput(yaw, tdx / tlen, tdz / tlen);
            ctx.controls().setMovement(input[0], input[1]);
        }
    }

    /** Forward and strafe impulses that walk along the unit vector (tdx, tdz) while facing yawDeg. */
    static float[] strafeInput(double yawDeg, double tdx, double tdz) {
        double yawRad = Math.toRadians(yawDeg);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        return new float[]{(float) (-sin * tdx + cos * tdz), (float) (cos * tdx + sin * tdz)};
    }

    private boolean waterAhead(NearbyBlocks blocks, Vec3 from, Vec3 goal, double minDist) {
        double dx = goal.x() - from.x();
        double dz = goal.z() - from.z();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < minDist) {
            return false;
        } else {
            dx /= d;
            dz /= d;
            int fy = Mth.floor(from.y());

            for (double step = ctx.tuning().number(TuningKey.FOLLOWER_WATER_SCAN_START).doubleValue(); step <= ctx.tuning().number(TuningKey.FOLLOWER_WATER_SCAN_END).doubleValue(); step += ctx.tuning().number(TuningKey.FOLLOWER_WATER_SCAN_STEP).doubleValue()) {
                int x = Mth.floor(from.x() + dx * step);
                int z = Mth.floor(from.z() + dz * step);
                BlockPos feet = new BlockPos(x, fy, z);
                if (blocks.solid(feet)) {
                    return false;
                }

                for (int y = fy; y >= fy - ctx.tuning().integer(TuningKey.FOLLOWER_WATER_SCAN_DEPTH); y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (blocks.lava(p)) {
                        return false;
                    }

                    if (MovementHelper.isWater(blocks.world(), x, y, z)) {
                        return true;
                    }

                    if (y < fy && blocks.solid(p)) {
                        break;
                    }
                }
            }

            return false;
        }
    }

    private void swimChase(Vec3 goalPos, Vec3 facePos) {
        Vec3 botPos = Ent.pos(bot);
        boolean combat = facePos != null;
        boolean inWater = bot.isInWater() && (!bot.isOnGround() || bot.isUnderWater());
        boolean lowAir = bot.isUnderWater() && bot.getRemainingAir() < ctx.tuning().integer(TuningKey.FOLLOWER_SWIM_LOW_AIR);
        boolean afloat = inWater || !bot.isInWater() && !bot.isOnGround() && waterExit.active();
        double surface = waterSurfaceY(botPos);
        boolean goalOnLand = !Double.isNaN(surface) && goalPos.y() >= surface - ctx.tuning().number(TuningKey.FOLLOWER_LAND_SURFACE_MARGIN).doubleValue();
        Vec3 exitPt = null;
        if (afloat && goalOnLand) {
            waterExit.tick(botPos, goalPos, surface);
            if (waterExit.busy()) {
                return;
            }

            exitPt = waterExit.steerTo();
        }

        Vec3 aim = exitPt != null ? exitPt : (combat ? facePos : goalPos);
        double dx = aim.x() - botPos.x();
        double dz = aim.z() - botPos.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > 1.0E-4) {
            float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            ctx.controls().look(BotMath.smoothYawToward(yaw(), wantYaw, ctx.tuning().number(TuningKey.FOLLOWER_MAX_TURN).floatValue()), pitch());
        }

        if (afloat && goalOnLand && bankAhead(botPos, yaw(), surface)) {
            ctx.controls().setSprinting(false);
            ctx.controls().setMovement(1.0F, 0.0F);
            ctx.controls().setJumping(true);
        } else {
            ctx.controls().setSprinting(true);
            ctx.controls().setMovement(1.0F, 0.0F);
            if (!inWater) {
                ctx.controls().setJumping(false);
            } else if (lowAir) {
                ctx.controls().look(yaw(), ctx.tuning().number(TuningKey.FOLLOWER_LOW_AIR_PITCH).floatValue());
                ctx.controls().setJumping(!bot.isSwimming());
            } else {
                ctx.controls().setJumping(false);
                // Sink nudge (setDeltaMovement y - 0.04 while floating) is not used: motion only through controls.

                if (!combat) {
                    double aimY = goalOnLand ? surface - ctx.tuning().number(TuningKey.FOLLOWER_SWIM_AIM_DEPTH).doubleValue() : goalPos.y();
                    double dyEye = aimY - Ent.eyePos(bot).y();
                    float wantPitch = (float) (-Math.toDegrees(Math.atan2(dyEye, Math.max(horiz, ctx.tuning().number(TuningKey.FOLLOWER_SWIM_PITCH_MIN_HORIZ).doubleValue()))));
                    if (horizontalCollision()) {
                        wantPitch = ctx.tuning().number(TuningKey.FOLLOWER_SWIM_COLLISION_PITCH).floatValue();
                    }

                    ctx.controls().look(yaw(), swimPitch(ctx.tuning(), wantPitch));
                }
            }
        }
    }

    private double waterSurfaceY(Vec3 pos) {
        int x = Mth.floor(pos.x());
        int z = Mth.floor(pos.z());
        int y = Mth.floor(pos.y());
        int found = Integer.MIN_VALUE;

        for (int yy = y - 1; yy <= y + 2; yy++) {
            if (ctx.world().isWater(new BlockPos(x, yy, z))) {
                found = yy;
                break;
            }
        }

        if (found == Integer.MIN_VALUE) {
            return Double.NaN;
        } else {
            int top = found;

            for (int i = 0; i < 24; i++) {
                if (!ctx.world().isWater(new BlockPos(x, top + 1, z))) {
                    break;
                }

                top++;
            }

            return top + waterHeight(new BlockPos(x, top, z));
        }
    }

    private double waterHeight(BlockPos pos) {
        BlockData data = ctx.world().data(pos);
        return data.getMaterial() == Material.WATER && data instanceof Levelled levelled ? levelHeight(levelled.getLevel()) : levelHeight(0);
    }

    /** Own height of a water block from its level (0 source, 1-7 flowing, 8+ falling). */
    static double levelHeight(int level) {
        return (level >= 8 ? 8 : 8 - level) / 9.0;
    }

    private boolean bankAhead(Vec3 pos, float yaw, double surface) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        int sy = Mth.floor(surface - 0.01);

        for (double step = ctx.tuning().number(TuningKey.FOLLOWER_BANK_SCAN_START).doubleValue(); step <= ctx.tuning().number(TuningKey.FOLLOWER_BANK_SCAN_END).doubleValue(); step += ctx.tuning().number(TuningKey.FOLLOWER_BANK_SCAN_STEP).doubleValue()) {
            int x = Mth.floor(pos.x() + fx * step);
            int z = Mth.floor(pos.z() + fz * step);
            double top = WaterExit.landTop(ctx, new BlockPos(x, sy, z), surface);
            if (!Double.isNaN(top)) {
                return top - surface <= ctx.tuning().number(TuningKey.FOLLOWER_BANK_MAX_RISE).doubleValue();
            }
        }

        return false;
    }

    /** Combat: the aim stays with the combat code, so the heading becomes inputs relative to the current yaw. */
    private void moveAlong(double dx, double dz) {
        if (dx * dx + dz * dz > 1.0E-6) {
            float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float[] input = yawToInput(yaw(), wantYaw);
            ctx.controls().setMovement(input[0], input[1]);
        }
    }

    /** Forward and strafe (clamped to [-1, 1], strafe > 0 is left) that walk toward desiredYaw while facing currentYaw. */
    public static float[] yawToInput(float currentYaw, float desiredYaw) {
        double delta = Math.toRadians(BotMath.wrapDegrees(desiredYaw - currentYaw));
        return new float[]{clamp((float) Math.cos(delta)), clamp((float) -Math.sin(delta))};
    }

    private static float clamp(float v) {
        return Math.max(-1.0F, Math.min(1.0F, v));
    }

    private void faceMovement(double dx, double dz) {
        if (dx * dx + dz * dz > 1.0E-6) {
            float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            ctx.controls().look(BotMath.smoothYawToward(yaw(), wantYaw, ctx.tuning().number(TuningKey.FOLLOWER_MAX_TURN).floatValue()), pitch());
        }
    }

    private int sideOpenness(NearbyBlocks blocks, Vec3 botPos, double gx, double gz, int sign) {
        double px = sign > 0 ? -gz : gz;
        double pz = sign > 0 ? gx : -gx;
        double hx = gx * ctx.tuning().number(TuningKey.FOLLOWER_SIDE_FORWARD).doubleValue() + px;
        double hz = gz * ctx.tuning().number(TuningKey.FOLLOWER_SIDE_FORWARD).doubleValue() + pz;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len > 1.0E-6) {
            hx /= len;
            hz /= len;
        }

        int open = 0;

        for (int d = 1; d <= ctx.tuning().integer(TuningKey.FOLLOWER_SIDE_PROBE_LEN); d++) {
            BlockPos f = BlockPos.containing(botPos.x() + hx * d, botPos.y(), botPos.z() + hz * d);
            if (isSolidAt(blocks, f)
                    || isSolidAt(blocks, f.above())
                    || isWebAt(blocks, f)
                    || isWebAt(blocks, f.above())
                    || isLavaAt(blocks, f)
                    || isLavaAt(blocks, f.below())
                    || !isSolidAt(blocks, f.below()) && !isSolidAt(blocks, f.below().below())) {
                break;
            }

            open++;
        }

        return open;
    }

    private static boolean scanAhead(NearbyBlocks blocks, Vec3 botPos, double dx, double dz, double reach, BiPredicate<NearbyBlocks, BlockPos> test) {
        for (double d = 0.5; d <= reach + 1.0E-9; d++) {
            BlockPos f = BlockPos.containing(botPos.x() + dx * d, botPos.y(), botPos.z() + dz * d);
            if (test.test(blocks, f) || test.test(blocks, f.above())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isWebAt(NearbyBlocks blocks, BlockPos pos) {
        return blocks.state(pos).getMaterial() == Material.COBWEB;
    }

    private static boolean isLavaAt(NearbyBlocks blocks, BlockPos pos) {
        return blocks.lava(pos);
    }

    private static boolean isSolidAt(NearbyBlocks blocks, BlockPos pos) {
        return blocks.solid(pos);
    }

    private boolean tryWindChargeOverWall(BlockPos wallFeet, double goalDy) {
        if (windChargeCd > 0 || goalDy < ctx.tuning().number(TuningKey.FOLLOWER_WIND_MIN_RISE).doubleValue()) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        } else {
            int wcSlot = findWindChargeHotbarSlot();
            if (wcSlot < 0) {
                return false;
            } else {
                int prev = InventoryUtils.selected(ctx);
                InventoryUtils.select(ctx, wcSlot);
                ctx.controls().look(yaw(), ctx.tuning().number(TuningKey.FOLLOWER_WIND_PITCH).floatValue());
                ctx.controls().jumpOnce();
                if (ItemKinds.is(bot.getInventory().getItemInMainHand(), Material.WIND_CHARGE)) {
                    ctx.hands().useItem(EquipmentSlot.HAND);
                }

                InventoryUtils.select(ctx, prev);
                windChargeCd = ctx.tuning().integer(TuningKey.FOLLOWER_WIND_CD);
                return true;
            }
        }
    }

    private int findWindChargeHotbarSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.WIND_CHARGE)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isClimbableAt(NearbyBlocks blocks, BlockPos pos) {
        return Tag.CLIMBABLE.isTagged(blocks.state(pos).getMaterial());
    }

    public boolean hasPath() {
        return chasing;
    }

    public boolean isSwimNav() {
        return wasSwimming;
    }

    public void reset() {
        chasing = false;
        lastPos = null;
        reactiveStuckTicks = 0;
        reactiveAvoidTicks = 0;
        reactiveAvoidDir = 0;
        avoidStartGoalDist = Double.MAX_VALUE;
        avoidCommitTicks = 0;
        avoidHazard = false;
        bestGoalDist = Double.MAX_VALUE;
        bestGoalRef = null;
        reactiveJumpCd = 0;
        windChargeCd = 0;
        reactiveKnockTicks = 0;
        if (wasSwimming) {
            ctx.controls().setJumping(false);
        }

        wasSwimming = false;
        waterExit.pause();
        planner.reset();
        ctx.controls().stopMoving();
    }

    private void maybeOpenDoorAhead(Vec3 botPos, Vec3 steer) {
        if (ctx.settings().openDoors()) {
            int fy = (int) Math.floor(botPos.y() + 0.1);
            openDoorAt(BlockPos.containing(botPos.x(), fy, botPos.z()));
            double dx = steer.x() - botPos.x();
            double dz = steer.z() - botPos.z();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz > 0.01) {
                double ax = botPos.x() + dx / horiz * ctx.tuning().number(TuningKey.FOLLOWER_DOOR_PROBE).doubleValue();
                double az = botPos.z() + dz / horiz * ctx.tuning().number(TuningKey.FOLLOWER_DOOR_PROBE).doubleValue();
                openDoorAt(BlockPos.containing(ax, fy, az));
            }
        }
    }

    private void openDoorAt(BlockPos pos) {
        if (ctx.world().data(pos) instanceof Door door && !door.isOpen()) {
            BlockPos lower = door.getHalf() == Bisected.Half.TOP ? pos.below() : pos;
            if (ctx.world().data(lower) instanceof Door lowerDoor && !lowerDoor.isOpen() && canOpenByHand(lowerDoor.getMaterial())) {
                Vec3 bp = Ent.pos(bot);
                Direction face = doorFace(bp.x() - (lower.getX() + 0.5), bp.z() - (lower.getZ() + 0.5));
                Vec3 hit = Vec3.atCenterOf(lower).add(face.getStepX() * 0.5, 0.0, face.getStepZ() * 0.5);
                ctx.controls().lookAt(Vec3.atCenterOf(lower));
                ctx.hands().useItemOn(EquipmentSlot.HAND, lower, face, hit);
            }
        }
    }

    /** Iron doors only open with redstone; every other door (wood, copper) opens by hand. */
    static boolean canOpenByHand(Material door) {
        return door != Material.IRON_DOOR;
    }

    /** Horizontal face of a door block that looks toward an offset (dx, dz) from its centre. */
    static Direction doorFace(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
