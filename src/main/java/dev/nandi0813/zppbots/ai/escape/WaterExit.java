package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.ai.move.ScaffoldController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class WaterExit {

    private final BotContext ctx;
    private final Player bot;
    private Vec3 exit;
    private int exitTicks;
    private BlockPos dig;
    private BlockPos digColumn;
    private int digTicks;
    private int stuck;
    private int cooldown;
    private Vec3 steerTo;
    private boolean busy;

    public WaterExit(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public Vec3 steerTo() {
        return steerTo;
    }

    public boolean busy() {
        return busy;
    }

    public boolean active() {
        return exit != null || dig != null;
    }

    public void reset() {
        if (dig != null) {
            ctx.controls().stopMining();
        }

        exit = null;
        dig = null;
        digColumn = null;
        stuck = 0;
        steerTo = null;
        busy = false;
    }

    public void pause() {
        if (dig != null) {
            ctx.controls().stopMining();
        }
    }

    public void tick(Vec3 pos, Vec3 goal, double surface) {
        steerTo = null;
        busy = false;
        if (cooldown > 0) {
            cooldown--;
        }

        int sy = Mth.floor(surface - 0.01);
        if (dig != null) {
            tickDig(sy);
        } else {
            if (exit != null) {
                BlockPos land = BlockPos.containing(exit.x(), exit.y() - 0.5, exit.z());
                if (++exitTicks <= ctx.tuning().integer(TuningKey.WATER_EXIT_EXIT_TIMEOUT) && !Double.isNaN(landTop(ctx, land, surface))) {
                    steerTo = exit;
                    return;
                }

                exit = null;
                cooldown = ctx.tuning().integer(TuningKey.WATER_EXIT_EXIT_COOLDOWN);
            }

            float yaw = bot.getLocation().getYaw();
            if (!highBankAhead(ctx, pos, yaw, surface)) {
                stuck = 0;
            } else {
                if (horizontalCollision(yaw)) {
                    stuck++;
                } else if (stuck > 0) {
                    stuck--;
                }

                if (stuck >= ctx.tuning().integer(TuningKey.WATER_EXIT_STUCK_TRIGGER) && cooldown <= 0) {
                    stuck = 0;
                    Vec3 low = findLowExit(pos, goal, surface, sy, false);
                    if (low != null) {
                        setExit(low);
                    } else {
                        BlockPos step = placeStep(pos, yaw, sy);
                        if (step != null) {
                            setExit(new Vec3(step.getX() + 0.5, step.getY() + 1.0, step.getZ() + 0.5));
                        } else {
                            if (ctx.tuning().flag(TuningKey.WATER_EXIT_GOTO_MINING)) {
                                BlockPos column = ahead(ctx.tuning(), pos, yaw, sy);
                                BlockPos lip = column.above();
                                if (solid(ctx, lip) && canDig(lip)) {
                                    digColumn = column;
                                    dig = lip;
                                    digTicks = 0;
                                    tickDig(sy);
                                    return;
                                }
                            }

                            Vec3 far = findLowExit(pos, goal, surface, sy, true);
                            if (far != null) {
                                setExit(far);
                            } else {
                                cooldown = ctx.tuning().integer(TuningKey.WATER_EXIT_NO_EXIT_COOLDOWN);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Bukkit has no horizontalCollision flag: probe the body a step ahead along its facing. */
    private boolean horizontalCollision(float yaw) {
        double rad = Math.toRadians(yaw);
        Location ahead = bot.getLocation().add(-Math.sin(rad) * 0.1, 0.0, Math.cos(rad) * 0.1);
        return bot.collidesAt(ahead);
    }

    private void setExit(Vec3 top) {
        exit = top;
        exitTicks = 0;
        steerTo = top;
    }

    public static double landTop(BotContext ctx, BlockPos column, double surface) {
        int sy = Mth.floor(surface - 0.01);

        for (int y = sy; y <= sy + 1; y++) {
            BlockPos b = new BlockPos(column.getX(), y, column.getZ());
            if (!ctx.world().isWater(b)) {
                double top = ctx.world().collisionTop(b);
                if (!Double.isNaN(top) && free(ctx, b.above()) && free(ctx, b.above(2))) {
                    return top;
                }
            }
        }

        return Double.NaN;
    }

    static boolean highBankAhead(BotContext ctx, Vec3 pos, float yaw, double surface) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        int sy = Mth.floor(surface - 0.01);

        for (double step = ctx.tuning().number(TuningKey.WATER_EXIT_BANK_START).doubleValue(); step <= ctx.tuning().number(TuningKey.WATER_EXIT_BANK_END).doubleValue(); step += ctx.tuning().number(TuningKey.WATER_EXIT_BANK_STEP).doubleValue()) {
            int x = Mth.floor(pos.x() + fx * step);
            int z = Mth.floor(pos.z() + fz * step);
            BlockPos lip = new BlockPos(x, sy + 1, z);
            if (solid(ctx, lip)) {
                double top = landTop(ctx, new BlockPos(x, sy, z), surface);
                return Double.isNaN(top) || top - surface > ctx.tuning().number(TuningKey.WATER_EXIT_CLIMBABLE_LIP).doubleValue();
            }
        }

        return false;
    }

    private Vec3 findLowExit(Vec3 pos, Vec3 goal, double surface, int sy, boolean anyDetour) {
        BlockPos start = new BlockPos(Mth.floor(pos.x()), sy, Mth.floor(pos.z()));
        Map<BlockPos, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        dist.put(start, 0);
        queue.add(start);
        BlockPos best = null;
        double bestTop = 0.0;
        double bestScore = Double.MAX_VALUE;

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int d = dist.get(p);

            for (Direction dir : Direction.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                if (Math.abs(n.getX() - start.getX()) <= ctx.tuning().integer(TuningKey.WATER_EXIT_SEARCH_RADIUS) && Math.abs(n.getZ() - start.getZ()) <= ctx.tuning().integer(TuningKey.WATER_EXIT_SEARCH_RADIUS)
                        && !dist.containsKey(n)) {
                    if (ctx.world().isWater(n) && free(ctx, n.above())) {
                        dist.put(n, d + 1);
                        queue.add(n);
                    } else {
                        double top = landTop(ctx, n, surface);
                        if (!Double.isNaN(top) && !(top - surface > ctx.tuning().number(TuningKey.WATER_EXIT_CLIMBABLE_LIP).doubleValue())) {
                            double gx = goal.x() - (n.getX() + 0.5);
                            double gz = goal.z() - (n.getZ() + 0.5);
                            double goalDistance = Math.sqrt(gx * gx + gz * gz);
                            double score = exitScore(ctx.tuning(), d, goalDistance);
                            if (score < bestScore) {
                                bestScore = score;
                                best = n;
                                bestTop = top;
                            }
                        }
                    }
                }
            }
        }

        double sx = goal.x() - pos.x();
        double sz = goal.z() - pos.z();
        return best != null && (anyDetour || acceptsDetour(ctx.tuning(), bestScore, Math.sqrt(sx * sx + sz * sz)))
                ? new Vec3(best.getX() + 0.5, bestTop, best.getZ() + 0.5)
                : null;
    }

    static double exitScore(BotTuning.Profile profile, int distance, double goalDistance) {
        return (distance + 1) * profile.number(TuningKey.WATER_EXIT_PATH_WEIGHT).doubleValue()
                + goalDistance * profile.number(TuningKey.WATER_EXIT_GOAL_WEIGHT).doubleValue();
    }

    static boolean acceptsDetour(BotTuning.Profile profile, double best, double direct) {
        return !(best > direct + profile.number(TuningKey.WATER_EXIT_MAX_DETOUR).doubleValue());
    }

    private BlockPos placeStep(Vec3 pos, float yaw, int sy) {
        int slot = blockSlot();
        if (slot < 0) {
            return null;
        } else {
            Direction toBank = Direction.fromYRot(yaw);
            BlockPos here = new BlockPos(Mth.floor(pos.x()), sy, Mth.floor(pos.z()));
            AABB body = Ent.box(bot);

            for (Direction side : new Direction[]{toBank.getClockWise(), toBank.getCounterClockWise()}) {
                BlockPos cell = here.relative(side);
                if (ctx.world().canReplace(cell) && !AABB.ofBlock(cell).intersects(body)
                        && free(ctx, cell.above()) && free(ctx, cell.above(2))) {
                    BlockPos click;
                    Direction face;
                    if (solid(ctx, cell.relative(toBank))) {
                        click = cell.relative(toBank);
                        face = toBank.getOpposite();
                    } else {
                        if (!solid(ctx, cell.below())) {
                            continue;
                        }

                        click = cell.below();
                        face = Direction.UP;
                    }

                    EquipmentSlot hand = slot == 40 ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
                    if (hand == EquipmentSlot.HAND) {
                        InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, slot));
                    }

                    Vec3 hitVec = Vec3.atCenterOf(click).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                    faceBlock(click);
                    ctx.hands().useItemOn(hand, click, face, hitVec);
                    ctx.hands().swing(hand);
                    if (solid(ctx, cell)) {
                        return cell;
                    }
                }
            }

            return null;
        }
    }

    private int blockSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ScaffoldController.isPillarBlock(ctx, inv.getItem(i))) {
                return i;
            }
        }

        return ScaffoldController.isPillarBlock(ctx, inv.getItemInOffHand()) ? 40 : -1;
    }

    private void tickDig(int sy) {
        busy = true;
        if (!solid(ctx, dig)) {
            ctx.controls().stopMining();
            BlockPos over = dig.above();
            if (dig.getY() != digColumn.getY() + 1 || !solid(ctx, over) || !canDig(over)) {
                dig = null;
                busy = false;
                setExit(new Vec3(digColumn.getX() + 0.5, digColumn.getY() + 1.0, digColumn.getZ() + 0.5));
                return;
            }

            dig = over;
            digTicks = 0;
        }

        if (++digTicks <= ctx.tuning().integer(TuningKey.WATER_EXIT_DIG_TIMEOUT) && canDig(dig)) {
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
            ctx.controls().setJumping(bot.isUnderWater());
            int tool = toolSlot(ctx.world().data(dig));
            if (tool >= 0) {
                InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, tool));
            }

            faceBlock(dig);
            ctx.controls().mineContinuous();
        } else {
            ctx.controls().stopMining();
            dig = null;
            busy = false;
            cooldown = ctx.tuning().integer(TuningKey.WATER_EXIT_DIG_COOLDOWN);
        }
    }

    private boolean canDig(BlockPos p) {
        float hardness = ctx.world().type(p).getHardness();
        boolean tile = ctx.world().block(p).getState(false) instanceof TileState;
        return !(hardness < 0.0F) && !tile ? toolSlot(ctx.world().data(p)) >= 0 || hardness <= ctx.tuning().number(TuningKey.WATER_EXIT_HAND_HARDNESS).floatValue() : false;
    }

    private int toolSlot(BlockData state) {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        float bestSpeed = ctx.tuning().number(TuningKey.WATER_EXIT_TOOL_MIN_SPEED).floatValue();

        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!ItemKinds.isEmpty(s)) {
                float sp = ItemKinds.destroySpeed(s, state);
                if (sp > bestSpeed) {
                    bestSpeed = sp;
                    best = i;
                }
            }
        }

        return best;
    }

    static BlockPos ahead(BotTuning.Profile profile, Vec3 pos, float yaw, int sy) {
        double rad = Math.toRadians(yaw);
        return new BlockPos(Mth.floor(pos.x() - Math.sin(rad) * profile.number(TuningKey.WATER_EXIT_AHEAD_PROBE).doubleValue()), sy, Mth.floor(pos.z() + Math.cos(rad) * profile.number(TuningKey.WATER_EXIT_AHEAD_PROBE).doubleValue()));
    }

    private void faceBlock(BlockPos p) {
        Vec3 c = Vec3.atCenterOf(p);
        Vec3 botPos = Ent.pos(bot);
        double dx = c.x() - botPos.x();
        double dy = c.y() - Ent.eyePos(bot).y();
        double dz = c.z() - botPos.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        ctx.controls().look(
                (float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) (-Math.toDegrees(Math.atan2(dy, Math.max(horiz, 0.001)))));
    }

    private static boolean solid(BotContext ctx, BlockPos p) {
        return ctx.world().blocksMotion(p);
    }

    private static boolean free(BotContext ctx, BlockPos p) {
        return !ctx.world().blocksMotion(p);
    }
}
