package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.block.TileState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ScaffoldController {

    private int blocksSincePlaceReset = 0;
    private static final java.util.Map<Material, Boolean> PILLAR = new java.util.concurrent.ConcurrentHashMap<>();

    private final BotContext ctx;
    private final Player bot;
    private boolean active = false;
    private Mode mode = Mode.TOWER;
    private boolean targetLaunched = false;
    private BlockPos supportPos = null;
    private double bestY = Double.NEGATIVE_INFINITY;
    private double bestHoriz = Double.POSITIVE_INFINITY;
    private int ticksWithoutProgress = 0;
    private int placeCooldown = 0;
    private int cooldown = 0;

    private double towerMinDy() {
        return ctx.settings().buildUpMinHeight();
    }

    public ScaffoldController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isActive() {
        return active;
    }

    public void reset() {
        if (active) {
            ctx.controls().setSneaking(false);
            ctx.controls().stopMoving();
        }

        active = false;
        supportPos = null;
        bestY = Double.NEGATIVE_INFINITY;
        bestHoriz = Double.POSITIVE_INFINITY;
        ticksWithoutProgress = 0;
        placeCooldown = 0;
        targetLaunched = false;
    }

    public boolean tick(LivingEntity target) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (placeCooldown > 0) {
            placeCooldown--;
        }

        if (target != null && Ent.alive(target)) {
            Vec3 botPos = Ent.pos(bot);
            Vec3 targetPos = Ent.pos(target);
            double dy = targetPos.y() - botPos.y();
            double horiz = Math.hypot(targetPos.x() - botPos.x(), targetPos.z() - botPos.z());
            if (ctx.match().velocityOf(target).y() > ctx.tuning().number(TuningKey.SCAFFOLD_LAUNCH_VY).doubleValue() && !target.isGliding()) {
                targetLaunched = true;
            }

            if (dy < towerMinDy()) {
                targetLaunched = false;
            }

            if (!active) {
                if (cooldown > 0 || findBlock() == null) {
                    return false;
                }

                if (dy >= towerMinDy() && horiz <= ctx.tuning().number(TuningKey.SCAFFOLD_START_MAX_HORIZ).doubleValue() && !targetLaunched) {
                    mode = Mode.TOWER;
                } else {
                    if (!(dy >= ctx.tuning().number(TuningKey.SCAFFOLD_BRIDGE_MIN_DY).doubleValue()) || !(dy < towerMinDy()) || !(horiz > ctx.tuning().number(TuningKey.SCAFFOLD_BRIDGE_DONE_HORIZ).doubleValue()) || !(horiz <= ctx.tuning().number(TuningKey.SCAFFOLD_START_MAX_HORIZ).doubleValue()) || !bot.isOnGround() || !gapTowards(target)) {
                        return false;
                    }

                    mode = Mode.BRIDGE;
                }

                active = true;
                supportPos = null;
                bestY = botPos.y();
                bestHoriz = horiz;
                ticksWithoutProgress = 0;
            }

            if (findBlock() != null && !(horiz > ctx.tuning().number(TuningKey.SCAFFOLD_MAX_HORIZ).doubleValue())) {
                if (mode == Mode.BRIDGE && dy >= towerMinDy()) {
                    mode = Mode.TOWER;
                    supportPos = null;
                    bestY = botPos.y();
                    ticksWithoutProgress = 0;
                } else if (mode == Mode.TOWER && dy <= ctx.tuning().number(TuningKey.SCAFFOLD_DONE_DY).doubleValue() && bot.isOnGround()) {
                    if (horiz <= ctx.tuning().number(TuningKey.SCAFFOLD_BRIDGE_DONE_HORIZ).doubleValue()) {
                        reset();
                        return false;
                    }

                    mode = Mode.BRIDGE;
                    ctx.controls().setSneaking(false);
                    bestHoriz = horiz;
                    ticksWithoutProgress = 0;
                }

                return mode == Mode.TOWER ? tickTower() : tickBridge(target, horiz);
            } else {
                abort();
                return false;
            }
        } else {
            reset();
            return false;
        }
    }

    private boolean tickTower() {
        BlockSlot blocks = findBlock();
        double y = bot.getLocation().getY();
        if (y > bestY + ctx.tuning().number(TuningKey.SCAFFOLD_TOWER_PROGRESS).doubleValue()) {
            bestY = y;
            ticksWithoutProgress = 0;
        } else if (++ticksWithoutProgress > ctx.tuning().integer(TuningKey.SCAFFOLD_STUCK_TICKS)) {
            abort();
            return false;
        }

        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        ctx.controls().look(bot.getLocation().getYaw(), 90.0F);
        holdBlock(blocks);
        if (bot.isOnGround()) {
            supportPos = Ent.blockPos(bot).below();
            ctx.controls().jumpOnce();
            return true;
        } else {
            if (supportPos != null && y >= supportPos.getY() + 2.0) {
                BlockPos placePos = supportPos.above();
                if (ctx.world().canReplace(placePos) && !ctx.world().isAir(supportPos)) {
                    Vec3 hitVec = Vec3.atCenterOf(supportPos).add(0.0, 0.5, 0.0);
                    ctx.hands().useItemOn(blocks.hand, supportPos, Direction.UP, hitVec);
                    ctx.hands().swing(blocks.hand);
                    supportPos = null;
                }
            }

            return true;
        }
    }

    private boolean tickBridge(LivingEntity target, double horiz) {
        if (horiz <= ctx.tuning().number(TuningKey.SCAFFOLD_BRIDGE_DONE_HORIZ).doubleValue()) {
            reset();
            return false;
        } else {
            BlockSlot blocks = findBlock();
            if (horiz < bestHoriz - ctx.tuning().number(TuningKey.SCAFFOLD_BRIDGE_PROGRESS).doubleValue()) {
                bestHoriz = horiz;
                ticksWithoutProgress = 0;
            } else if (++ticksWithoutProgress > ctx.tuning().integer(TuningKey.SCAFFOLD_STUCK_TICKS)) {
                abort();
                return false;
            }

            Vec3 botPos = Ent.pos(bot);
            Vec3 targetPos = Ent.pos(target);
            double tx = targetPos.x() - botPos.x();
            double tz = targetPos.z() - botPos.z();
            BotSettings.BridgeMode mode = ctx.settings().bridgeMode();
            float awayYaw = (float) Math.toDegrees(Math.atan2(tx, -tz));
            float towardYaw = awayYaw + 180.0F;
            holdBlock(blocks);
            switch (mode) {
                case GOD:
                    ctx.controls().look(towardYaw, ctx.tuning().number(TuningKey.SCAFFOLD_GOD_PITCH).floatValue());
                    ctx.controls().setSprinting(true);
                    ctx.controls().setMovement(ctx.tuning().number(TuningKey.SCAFFOLD_GOD_FORWARD).floatValue(), 0.0F);
                    break;
                case MOONWALK:
                    float sideYaw = towardYaw + ctx.tuning().number(TuningKey.SCAFFOLD_MOONWALK_YAW_OFF).floatValue();
                    ctx.controls().look(sideYaw, ctx.tuning().number(TuningKey.SCAFFOLD_MOONWALK_PITCH).floatValue());
                    ctx.controls().setSprinting(false);
                    ctx.controls().setMovement(
                            ctx.tuning().number(TuningKey.SCAFFOLD_MOONWALK_FORWARD).floatValue(),
                            ctx.tuning().number(TuningKey.SCAFFOLD_MOONWALK_STRAFE).floatValue());
                    break;
                default:
                    ctx.controls().look(awayYaw, ctx.tuning().number(TuningKey.SCAFFOLD_NINJA_PITCH).floatValue());
                    ctx.controls().setSprinting(false);
                    ctx.controls().setMovement(ctx.tuning().number(TuningKey.SCAFFOLD_NINJA_FORWARD).floatValue(), 0.0F);
            }

            if (!bot.isOnGround()) {
                return true;
            } else {
                BlockPos support = Ent.blockPos(bot).below();
                if (ctx.world().isAir(support)) {
                    return true;
                } else {
                    Direction step = stepDirection(tx, tz);
                    BlockPos next = support.relative(step);
                    if (ctx.world().canReplace(next)) {
                        boolean crouchBridge = mode == BotSettings.BridgeMode.NINJA;
                        ctx.controls().setSneaking(crouchBridge);

                        double toEdge = switch (step) {
                            case NORTH -> botPos.z() - support.getZ();
                            case SOUTH -> support.getZ() + 1 - botPos.z();
                            case WEST -> botPos.x() - support.getX();
                            default -> support.getX() + 1 - botPos.x();
                        };
                        double placeFrac = placeFraction(ctx.tuning(), mode);
                        if (toEdge <= placeFrac && placeCooldown == 0) {
                            Vec3 hitVec = Vec3.atCenterOf(support).add(step.getStepX() * 0.5, 0.0, step.getStepZ() * 0.5);
                            ctx.hands().useItemOn(blocks.hand, support, step, hitVec);
                            ctx.hands().swing(blocks.hand);
                            placeCooldown = placeDelay(ctx.tuning(), mode);
                            int resetEvery = resetEvery(ctx.tuning(), mode);
                            if (resetEvery > 0 && ++blocksSincePlaceReset >= resetEvery) {
                                blocksSincePlaceReset = 0;
                                if (bot.isOnGround()) {
                                    ctx.controls().jumpOnce();
                                }
                            }
                        }
                    } else {
                        ctx.controls().setSneaking(false);
                    }

                    return true;
                }
            }
        }
    }

    static double placeFraction(BotTuning.Profile profile, BotSettings.BridgeMode mode) {
        return mode == BotSettings.BridgeMode.NINJA
                ? profile.number(TuningKey.SCAFFOLD_EDGE_PLACE_FRAC).doubleValue()
                : profile.number(TuningKey.SCAFFOLD_EARLY_PLACE_FRAC).doubleValue();
    }

    static int placeDelay(BotTuning.Profile profile, BotSettings.BridgeMode mode) {
        return mode == BotSettings.BridgeMode.NINJA
                ? profile.integer(TuningKey.SCAFFOLD_NINJA_PLACE_DELAY)
                : profile.integer(TuningKey.SCAFFOLD_EARLY_PLACE_DELAY);
    }

    static int resetEvery(BotTuning.Profile profile, BotSettings.BridgeMode mode) {
        return mode == BotSettings.BridgeMode.GOD
                ? profile.integer(TuningKey.SCAFFOLD_GOD_RESET_EVERY)
                : (mode == BotSettings.BridgeMode.MOONWALK
                        ? profile.integer(TuningKey.SCAFFOLD_MOONWALK_RESET_EVERY)
                        : 0);
    }

    static Direction stepDirection(double tx, double tz) {
        return Math.abs(tx) >= Math.abs(tz) ? (tx > 0.0 ? Direction.EAST : Direction.WEST) : (tz > 0.0 ? Direction.SOUTH : Direction.NORTH);
    }

    private boolean gapTowards(LivingEntity target) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double tx = targetPos.x() - botPos.x();
        double tz = targetPos.z() - botPos.z();
        Direction step = stepDirection(tx, tz);
        BlockPos next = Ent.blockPos(bot).below().relative(step);
        return ctx.world().canReplace(next) && ctx.world().canReplace(next.below());
    }

    private void abort() {
        blocksSincePlaceReset = 0;
        reset();
        cooldown = ctx.tuning().integer(TuningKey.SCAFFOLD_ABORT_COOLDOWN);
    }

    private BlockSlot findBlock() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (isPillarBlock(ctx, inv.getItem(i))) {
                return new BlockSlot(i, EquipmentSlot.HAND);
            }
        }

        return isPillarBlock(ctx, inv.getItemInOffHand()) ? new BlockSlot(40, EquipmentSlot.OFF_HAND) : null;
    }

    /** Full solid blocks that are safe to stand on; no tile-entity, falling or harmful blocks. */
    public static boolean isPillarBlock(BotContext ctx, ItemStack stack) {
        return !ItemKinds.isEmpty(stack) && PILLAR.computeIfAbsent(stack.getType(), ScaffoldController::pillarMaterial);
    }

    private static boolean pillarMaterial(Material block) {
        if (!block.isBlock() || block.hasGravity() || block.createBlockData().createBlockState() instanceof TileState) {
            return false;
        }

        return block != Material.CACTUS && block != Material.MAGMA_BLOCK && block != Material.TNT
                && block != Material.SLIME_BLOCK && block != Material.HONEY_BLOCK && block.isSolid();
    }

    private void holdBlock(BlockSlot blocks) {
        if (blocks.hand != EquipmentSlot.OFF_HAND) {
            int slot = InventoryUtils.moveToHotbarSafe(ctx, blocks.slot);
            InventoryUtils.select(ctx, slot);
        }
    }

    private record BlockSlot(int slot, EquipmentSlot hand) {
    }

    private enum Mode {
        TOWER,
        BRIDGE
    }
}
