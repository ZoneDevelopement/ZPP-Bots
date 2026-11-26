package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.WaterRules;
import dev.nandi0813.zppbots.util.WebDetect;
import dev.nandi0813.zppbots.util.WebPlacer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

public final class CobwebEscapeController {

    private final BotContext ctx;
    private final Player bot;
    private boolean escaping = false;
    private int pickupTimer = 0;
    private int scoopTicks = 0;
    private BlockPos waterPos = null;
    private BlockPos cobwebPos = null;
    private int bucketSlot = -1;
    private int prevSelected = -1;
    private int pearlRetry = 0;
    private int reactTicks = -1;
    private boolean mining = false;
    private BlockPos miningPos = null;
    private int miningTicks = 0;

    public CobwebEscapeController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick() {
        if (pearlRetry > 0) {
            pearlRetry--;
        }

        if (!escaping && !ctx.settings().webEscape()) {
            if (mining) {
                endSwordMine();
            }

            return false;
        } else if (mining) {
            if (!isTrappedInCobweb()) {
                endSwordMine();
                return false;
            } else {
                return tickSwordMine();
            }
        } else if (!escaping) {
            BlockPos web = WebDetect.firstWeb(bot);
            if (web == null) {
                reactTicks = -1;
                return false;
            } else {
                if (reactTicks < 0) {
                    reactTicks = ctx.difficulty().webEscapeDelayTicks();
                }

                if (reactTicks > 0) {
                    reactTicks--;
                    return false;
                } else {
                    reactTicks = -1;
                    if (WebPlacer.hasShears(ctx)) {
                        return tickSwordMine();
                    } else {
                        BlockPos waterPlace = WaterRules.evaporates(ctx, web) ? null : findWaterPlacementPos(web);
                        if (waterPlace != null && findWaterBucket() >= 0) {
                            startWaterEscape(web, waterPlace);
                            return true;
                        } else if (pearlRetry <= 0 && tryPearlEscape()) {
                            pearlRetry = ctx.tuning().integer(TuningKey.WEB_ESCAPE_PEARL_RETRY);
                            return true;
                        } else {
                            return findPearl() < 0 ? tickSwordMine() : true;
                        }
                    }
                }
            }
        } else {
            boolean cobwebGone = cobwebPos == null || ctx.world().type(cobwebPos) != Material.COBWEB;
            if (!cobwebGone && --pickupTimer > 0) {
                return true;
            } else {
                BlockPos waterAt = findWaterSourceNearby();
                if (waterAt == null) {
                    endWaterEscape();
                    return false;
                } else if (++scoopTicks > ctx.tuning().integer(TuningKey.WEB_ESCAPE_SCOOP_TIMEOUT)) {
                    endWaterEscape();
                    return false;
                } else if (ItemKinds.is(bot.getInventory().getItemInOffHand(), Material.BUCKET)) {
                    aimAtBlock(waterAt);
                    ctx.hands().useItem(EquipmentSlot.OFF_HAND);
                    return true;
                } else {
                    int emptyBucket = findEmptyBucket();
                    if (emptyBucket < 0) {
                        endWaterEscape();
                        return false;
                    } else {
                        emptyBucket = InventoryUtils.moveToHotbarSafe(ctx, emptyBucket);
                        InventoryUtils.select(ctx, emptyBucket);
                        aimAtBlock(waterAt);
                        ctx.hands().useItem(EquipmentSlot.HAND);
                        return true;
                    }
                }
            }
        }
    }

    private boolean tickSwordMine() {
        BlockPos web = pickCobwebToMine();
        if (web == null) {
            endSwordMine();
            return false;
        } else {
            if (!mining || !web.equals(miningPos)) {
                miningPos = web;
                miningTicks = 0;
                equipSword();
                if (!mining) {
                    mining = true;
                    ctx.controls().mineContinuous();
                }
            }

            miningTicks++;
            aimAtBlock(miningPos);
            ctx.hands().swing(EquipmentSlot.HAND);
            return true;
        }
    }

    private void endSwordMine() {
        if (mining) {
            ctx.controls().stopMining();
        }

        mining = false;
        miningPos = null;
        miningTicks = 0;
    }

    private BlockPos pickCobwebToMine() {
        Player threat = nearestPlayer(ctx.tuning().number(TuningKey.WEB_ESCAPE_THREAT_RANGE).doubleValue());
        if (threat != null) {
            Vec3 pos = Ent.pos(bot);
            Vec3 threatPos = Ent.pos(threat);
            double dx = pos.x() - threatPos.x();
            double dz = pos.z() - threatPos.z();
            double len = Math.hypot(dx, dz);
            if (len > 0.001) {
                dx /= len;
                dz /= len;
                BlockPos feet = Ent.blockPos(bot);
                BlockPos[] outward = new BlockPos[]{
                        BlockPos.containing(pos.x() + dx, pos.y() + 0.1, pos.z() + dz),
                        BlockPos.containing(pos.x() + dx, Ent.eyePos(bot).y(), pos.z() + dz)
                };

                for (BlockPos p : outward) {
                    if (!p.equals(feet) && ctx.world().type(p) == Material.COBWEB) {
                        return p;
                    }
                }
            }
        }

        return WebDetect.firstWeb(bot);
    }

    private Player nearestPlayer(double range) {
        Player nearest = null;
        double best = range * range;
        for (Player p : ctx.world().players()) {
            if (p == bot || p.getGameMode() == GameMode.SPECTATOR || !Ent.alive(p)) {
                continue;
            }

            double d = Ent.distSqr(bot, p);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }

        return nearest;
    }

    private void equipSword() {
        WebPlacer.equipWebCutter(ctx);
    }

    private boolean isTrappedInCobweb() {
        return WebDetect.touchingWeb(bot);
    }

    private BlockPos findWaterPlacementPos(BlockPos web) {
        BlockPos above = web.above();
        if (ctx.world().type(above) == Material.COBWEB) {
            return ctx.world().isAir(above.above()) ? above.above() : null;
        } else {
            return ctx.world().isAir(above) ? above : null;
        }
    }

    private void startWaterEscape(BlockPos web, BlockPos waterPlace) {
        bucketSlot = findWaterBucket();
        if (bucketSlot >= 0) {
            waterPos = waterPlace;
            cobwebPos = web;
            prevSelected = InventoryUtils.selected(ctx);
            bucketSlot = InventoryUtils.moveToHotbarSafe(ctx, bucketSlot);
            InventoryUtils.select(ctx, bucketSlot);
            // the bucket ray places water against the face it hits, so aim at the top of the block under the spot
            aimAtPoint(Vec3.atCenterOf(waterPlace.below()).add(0.0, 0.5, 0.0));
            ctx.hands().useItem(EquipmentSlot.HAND);
            ctx.hands().swing(EquipmentSlot.HAND);
            escaping = true;
            pickupTimer = ctx.tuning().integer(TuningKey.WEB_ESCAPE_PICKUP_DELAY);
        }
    }

    private BlockPos findWaterSourceNearby() {
        BlockPos feet = Ent.blockPos(bot);
        BlockPos[] candidates = waterPos == null
                ? new BlockPos[]{feet, feet.above()}
                : new BlockPos[]{waterPos, waterPos.below(), waterPos.below(2), feet, feet.above()};

        for (BlockPos p : candidates) {
            if (ctx.world().isWaterSource(p)) {
                return p;
            }
        }

        return null;
    }

    private int findEmptyBucket() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.BUCKET)) {
                return i;
            }
        }

        return -1;
    }

    private void endWaterEscape() {
        if (prevSelected >= 0) {
            InventoryUtils.select(ctx, prevSelected);
            prevSelected = -1;
        }

        escaping = false;
        waterPos = null;
        cobwebPos = null;
        bucketSlot = -1;
        scoopTicks = 0;
    }

    private boolean tryPearlEscape() {
        if (bot.hasCooldown(Material.ENDER_PEARL)) {
            return false;
        } else {
            int pearlSlot = findPearl();
            if (pearlSlot < 0) {
                return false;
            } else {
                Vec3 target = findPearlTarget();
                if (target == null) {
                    return false;
                } else {
                    PlayerInventory inv = bot.getInventory();
                    pearlSlot = InventoryUtils.moveToHotbarSafe(ctx, pearlSlot);
                    prevSelected = InventoryUtils.selected(ctx);
                    InventoryUtils.select(ctx, pearlSlot);
                    aimAtPoint(target);
                    if (!ItemKinds.is(inv.getItem(pearlSlot), Material.ENDER_PEARL)) {
                        InventoryUtils.select(ctx, prevSelected);
                        prevSelected = -1;
                        return false;
                    } else {
                        try {
                            ctx.hands().useItem(EquipmentSlot.HAND);
                        } catch (Exception e) {
                            InventoryUtils.select(ctx, prevSelected);
                            prevSelected = -1;
                            return false;
                        }

                        InventoryUtils.select(ctx, prevSelected);
                        prevSelected = -1;
                        return true;
                    }
                }
            }
        }
    }

    private Vec3 findPearlTarget() {
        BlockPos origin = Ent.blockPos(bot);
        float awayYaw = awayFromWallYaw();
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int radius = ctx.tuning().integer(TuningKey.WEB_ESCAPE_PEARL_RADIUS);
        double minDist = ctx.tuning().number(TuningKey.WEB_ESCAPE_PEARL_MIN_DIST).doubleValue();
        double maxDist = ctx.tuning().number(TuningKey.WEB_ESCAPE_PEARL_MAX_DIST).doubleValue();
        int verticalRange = ctx.tuning().integer(TuningKey.WEB_ESCAPE_PEARL_VERTICAL_RANGE);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double horizSq = (double) dx * dx + (double) dz * dz;
                if (!(horizSq < minDist * minDist) && !(horizSq > maxDist * maxDist)) {
                    for (int dy = verticalRange; dy >= -verticalRange; dy--) {
                        BlockPos stand = origin.offset(dx, dy, dz);
                        if (isStandableLanding(stand)) {
                            double dist = Math.sqrt(horizSq);
                            double dirX = dx / dist;
                            double dirZ = dz / dist;
                            double awayRad = Math.toRadians(awayYaw);
                            double awayX = -Math.sin(awayRad);
                            double awayZ = Math.cos(awayRad);
                            double alignScore = dirX * awayX + dirZ * awayZ;
                            double score = pearlScore(ctx.tuning(), dist, alignScore);
                            if (score > bestScore) {
                                bestScore = score;
                                best = Vec3.atCenterOf(stand);
                            }
                        }
                    }
                }
            }
        }

        return best != null ? best : fallbackPearlTarget();
    }

    private float awayFromWallYaw() {
        BlockPos feet = Ent.blockPos(bot);
        float botYaw = bot.getLocation().getYaw();
        float bestWallYaw = botYaw;
        int bestSolid = -1;

        for (Direction dir : Direction.HORIZONTAL) {
            int solid = 0;

            for (int d = 0; d <= ctx.tuning().integer(TuningKey.WEB_ESCAPE_WALL_PROBE); d++) {
                BlockPos check = feet.relative(dir, d);
                if (isSolidAt(check) || isSolidAt(check.above())) {
                    solid++;
                }
            }

            if (solid > bestSolid) {
                bestSolid = solid;
                bestWallYaw = dir.toYRot();
            }
        }

        return bestSolid <= 0 ? botYaw : bestWallYaw + 180.0F;
    }

    private Vec3 fallbackPearlTarget() {
        float awayYaw = awayFromWallYaw();
        double rad = Math.toRadians(awayYaw);
        Vec3 pos = Ent.pos(bot);
        return new Vec3(pos.x() - Math.sin(rad) * ctx.tuning().number(TuningKey.WEB_ESCAPE_FALLBACK_DISTANCE).doubleValue(), pos.y(), pos.z() + Math.cos(rad) * ctx.tuning().number(TuningKey.WEB_ESCAPE_FALLBACK_DISTANCE).doubleValue());
    }

    private boolean isStandableLanding(BlockPos stand) {
        BlockPos below = stand.below();
        if (ctx.world().collisionBoxes(below).isEmpty()) {
            return false;
        } else if (ctx.world().type(below) == Material.COBWEB) {
            return false;
        } else if (ctx.world().type(stand) == Material.COBWEB) {
            return false;
        } else if (!ctx.world().collisionBoxes(stand).isEmpty()) {
            return false;
        } else {
            return ctx.world().collisionBoxes(stand.above()).isEmpty()
                    && ctx.world().type(stand.above()) != Material.COBWEB;
        }
    }

    private void aimAtBlock(BlockPos pos) {
        aimAtPoint(Vec3.atCenterOf(pos));
    }

    private void aimAtPoint(Vec3 point) {
        Vec3 eye = Ent.eyePos(bot);
        double dx = point.x() - eye.x();
        double dy = point.y() - eye.y();
        double dz = point.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = pearlPitch(ctx.tuning(), horiz, dy);
        ctx.controls().look(yaw, pitch);
    }

    static double pearlScore(BotTuning.Profile profile, double distance, double alignment) {
        return 1.0 - Math.abs(distance - profile.number(TuningKey.WEB_ESCAPE_PEARL_IDEAL_DIST).doubleValue())
                / profile.number(TuningKey.WEB_ESCAPE_PEARL_DISTANCE_SCALE).doubleValue()
                + alignment * profile.number(TuningKey.WEB_ESCAPE_PEARL_ALIGNMENT_WEIGHT).doubleValue();
    }

    static float pearlPitch(BotTuning.Profile profile, double horiz, double dy) {
        return horiz > profile.number(TuningKey.WEB_ESCAPE_AIM_MIN_HORIZ).doubleValue()
                ? (float) (-Math.toDegrees(Math.atan2(dy, horiz)))
                : profile.number(TuningKey.WEB_ESCAPE_PEARL_PITCH).floatValue();
    }

    private boolean isSolidAt(BlockPos pos) {
        return !ctx.world().collisionBoxes(pos).isEmpty();
    }

    private int findWaterBucket() {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), Material.WATER_BUCKET)) {
                return i;
            }
        }

        return -1;
    }

    private int findPearl() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.ENDER_PEARL)) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.ENDER_PEARL)) {
                return i;
            }
        }

        return -1;
    }
}
