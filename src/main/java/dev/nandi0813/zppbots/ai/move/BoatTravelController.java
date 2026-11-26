package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.ai.melee.WeaponSelector;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Tag;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

public final class BoatTravelController {

    private final BotContext ctx;
    private final Player bot;
    private Phase phase = null;
    private Boat boat = null;
    private int phaseTicks = 0;
    private int cooldown = 0;
    private double bestDist = Double.POSITIVE_INFINITY;
    private int noProgress = 0;
    private Vec3 breakPos = null;
    private Vec3 lastTargetPos = null;
    private Vec3 targetVel = Vec3.ZERO;

    public BoatTravelController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean isActive() {
        return phase != null;
    }

    public void reset() {
        if (phase != null) {
            ctx.controls().stopMoving();
            if (bot.getVehicle() instanceof Boat b) {
                ctx.hands().dismount();
                scuttle(b);
            } else if (boat != null && Ent.alive(boat)) {
                scuttle(boat);
            }
        }

        phase = null;
        boat = null;
        phaseTicks = 0;
        bestDist = Double.POSITIVE_INFINITY;
        noProgress = 0;
        breakPos = null;
        lastTargetPos = null;
        targetVel = Vec3.ZERO;
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (target != null && Ent.alive(target)) {
            trackTarget(target);
            if (phase == null) {
                if (cooldown > 0
                        || dist < ctx.tuning().number(TuningKey.BOAT_MIN_DIST).doubleValue()
                        || dist > ctx.tuning().number(TuningKey.BOAT_MAX_DIST).doubleValue()) {
                    return false;
                }

                if (Math.abs(Ent.pos(target).y() - Ent.pos(bot).y())
                        > ctx.tuning().number(TuningKey.BOAT_MAX_DY).doubleValue()) {
                    return false;
                }

                if (!bot.isOnGround() && !bot.isInWater()) {
                    return false;
                }

                if (findBoatItemSlot() < 0) {
                    return false;
                }

                if (!openWaterToward(target)) {
                    return false;
                }

                phase = Phase.PLACE;
                phaseTicks = 0;
            }

            phaseTicks++;
            switch (phase) {
                case PLACE:
                    tickPlace(target);
                    break;
                case MOUNT:
                    tickMount();
                    break;
                case DRIVE:
                    tickDrive(target);
                    break;
                case BREAK:
                    tickBreak();
                    break;
                case PICKUP:
                    tickPickup(target);
            }

            return phase != null;
        } else {
            reset();
            return false;
        }
    }

    private void tickPlace(LivingEntity target) {
        ctx.controls().stopMoving();
        int slot = findBoatItemSlot();
        if (slot < 0) {
            abort();
        } else {
            faceToward(Ent.pos(target), ctx.tuning().number(TuningKey.BOAT_PLACE_PITCH).floatValue());
            EquipmentSlot hand = EquipmentSlot.HAND;
            if (slot == 40) {
                hand = EquipmentSlot.OFF_HAND;
            } else {
                InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, slot));
            }

            PlayerInventory inv = bot.getInventory();
            ItemStack stack = hand == EquipmentSlot.OFF_HAND ? inv.getItemInOffHand() : inv.getItemInMainHand();
            if (isBoatItem(stack)) {
                ctx.hands().swing(hand);
                ctx.hands().useItem(hand);
            }

            phase = Phase.MOUNT;
            phaseTicks = 0;
        }
    }

    private void tickMount() {
        Boat b = nearestEmptyBoat(
                ctx.tuning().number(TuningKey.BOAT_MOUNT_RADIUS).doubleValue(),
                phaseTicks + ctx.tuning().integer(TuningKey.BOAT_MOUNT_AGE_MARGIN));
        if (b != null && ctx.hands().interact(b, EquipmentSlot.HAND, Vec3.ZERO)) {
            boat = b;
            phase = Phase.DRIVE;
            phaseTicks = 0;
            bestDist = Double.POSITIVE_INFINITY;
            noProgress = 0;
        } else {
            if (phaseTicks > ctx.tuning().integer(TuningKey.BOAT_MOUNT_TIMEOUT)) {
                abort();
            }
        }
    }

    private void tickDrive(LivingEntity target) {
        if (bot.getVehicle() instanceof Boat b) {
            boat = b;
            double dist = Ent.dist(bot, target);
            if (dist < bestDist - ctx.tuning().number(TuningKey.BOAT_PROGRESS_EPS).doubleValue()) {
                bestDist = dist;
                noProgress = 0;
            } else if (++noProgress > ctx.tuning().integer(TuningKey.BOAT_DRIVE_STUCK_TICKS)) {
                disembark();
                return;
            }

            Vec3 lead = leadPoint(target, dist);
            Vec3 bp = Ent.pos(b);
            double toLead = Math.hypot(lead.x() - bp.x(), lead.z() - bp.z());
            if (continueDriving(ctx.tuning(), dist, toLead) && !shoreAhead(b, lead)) {
                double dx = lead.x() - bp.x();
                double dz = lead.z() - bp.z();
                float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float diff = Mth.wrapDegrees(desiredYaw - b.getLocation().getYaw());
                float strafe = steering(ctx.tuning(), diff);
                ctx.controls().setMovement(1.0F, strafe);
                faceToward(Ent.pos(target), ctx.tuning().number(TuningKey.BOAT_DRIVE_PITCH).floatValue());
            } else {
                disembark();
            }
        } else {
            phase = boat != null && Ent.alive(boat) ? Phase.BREAK : null;
            if (phase == null) {
                cooldown = ctx.tuning().integer(TuningKey.BOAT_ABORT_COOLDOWN);
            }

            phaseTicks = 0;
        }
    }

    private void tickBreak() {
        ctx.controls().stopMoving();
        if (boat != null && Ent.alive(boat)) {
            faceToward(Ent.pos(boat), ctx.tuning().number(TuningKey.BOAT_BREAK_PITCH).floatValue());
            breakPos = Ent.pos(boat);
            int tool = findBoatBreakTool();
            if (tool >= 0) {
                InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, tool));
            }

            if (phaseTicks % ctx.tuning().integer(TuningKey.BOAT_ATTACK_PERIOD) == 1) {
                ctx.hands().swing(EquipmentSlot.HAND);
                ctx.hands().attack(boat);
            }

            if (phaseTicks > 40) {
                // attacks at this cadence may not break the boat; remove it so the arena stays clean
                if (Ent.alive(boat)) {
                    boat.remove();
                }
                boat = null;
                finish();
            }
        } else {
            phase = Phase.PICKUP;
            phaseTicks = 0;
        }
    }

    private int findBoatBreakTool() {
        PlayerInventory inv = bot.getInventory();
        int sword = -1;

        for (int i = 0; i < 36; i++) {
            WeaponSelector.WeaponType t = WeaponSelector.getType(inv.getItem(i));
            if (t == WeaponSelector.WeaponType.AXE) {
                return i;
            }

            if (t == WeaponSelector.WeaponType.SWORD && sword < 0) {
                sword = i;
            }
        }

        return sword;
    }

    private void tickPickup(LivingEntity target) {
        Item drop = ourBoatDrop();
        if (drop != null
                && phaseTicks <= ctx.tuning().integer(TuningKey.BOAT_PICKUP_TIMEOUT)
                && !(Ent.dist(bot, target) <= ctx.tuning().number(TuningKey.BOAT_FIGHT_FIRST).doubleValue())) {
            Vec3 dpos = Ent.pos(drop);
            if (Ent.pos(bot).distanceTo(dpos) > ctx.tuning().number(TuningKey.BOAT_PICKUP_DIST).doubleValue()) {
                faceToward(dpos, ctx.tuning().number(TuningKey.BOAT_PICKUP_PITCH).floatValue());
                ctx.controls().setMovement(1.0F, 0.0F);
            } else {
                ctx.controls().stopMoving();
            }
        } else {
            finish();
        }
    }

    private void disembark() {
        Boat b = bot.getVehicle() instanceof Boat vb ? vb : boat;
        ctx.controls().stopMoving();
        ctx.hands().dismount();
        boat = b;
        phase = Phase.BREAK;
        phaseTicks = 0;
    }

    private void finish() {
        phase = null;
        boat = null;
        breakPos = null;
        cooldown = ctx.tuning().integer(TuningKey.BOAT_DONE_COOLDOWN);
        ctx.controls().stopMoving();
    }

    private void abort() {
        phase = null;
        boat = null;
        cooldown = ctx.tuning().integer(TuningKey.BOAT_ABORT_COOLDOWN);
        ctx.controls().stopMoving();
    }

    private void scuttle(Boat b) {
        try {
            b.remove();
        } catch (Throwable ignored) {
        }

        boat = null;
    }

    private boolean openWaterToward(LivingEntity target) {
        Vec3 bp = Ent.pos(bot);
        Vec3 tp = Ent.pos(target);
        double dx = tp.x() - bp.x();
        double dz = tp.z() - bp.z();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return false;
        } else {
            dx /= len;
            dz /= len;
            int water = 0;

            for (int i = ctx.tuning().integer(TuningKey.BOAT_WATER_START); i <= ctx.tuning().integer(TuningKey.BOAT_WATER_END); i++) {
                BlockPos p = BlockPos.containing(bp.x() + dx * i, bp.y(), bp.z() + dz * i);
                if (isWaterAt(p)
                        || isWaterAt(p.below())
                        || isWaterAt(p.below(ctx.tuning().integer(TuningKey.BOAT_WATER_DEEP)))) {
                    water++;
                }
            }

            return water >= ctx.tuning().integer(TuningKey.BOAT_WATER_SAMPLES);
        }
    }

    private boolean shoreAhead(Boat b, Vec3 toward) {
        Vec3 bp = Ent.pos(b);
        double dx = toward.x() - bp.x();
        double dz = toward.z() - bp.z();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return false;
        } else {
            dx /= len;
            dz /= len;
            double probe = ctx.tuning().number(TuningKey.BOAT_SHORE_PROBE).doubleValue();
            BlockPos ahead = BlockPos.containing(bp.x() + dx * probe, bp.y(), bp.z() + dz * probe);
            boolean landAhead = !isWaterAt(ahead) && !isWaterAt(ahead.below()) && ctx.world().blocksMotion(ahead.below());
            // Bukkit exposes no horizontalCollision flag: a boat that barely moves while driven counts as blocked.
            boolean blocked = phaseTicks > ctx.tuning().integer(TuningKey.BOAT_BLOCKED_GRACE)
                    && Ent.velocity(b).horizontalDistanceSqr()
                            < ctx.tuning().number(TuningKey.BOAT_BLOCKED_SPEED_SQR).doubleValue();
            return landAhead || blocked;
        }
    }

    private boolean isWaterAt(BlockPos p) {
        return ctx.world().isWater(p);
    }

    private static boolean isBoatItem(ItemStack stack) {
        return stack != null && Tag.ITEMS_BOATS.isTagged(stack.getType());
    }

    private int findBoatItemSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (isBoatItem(inv.getItem(i))) {
                return i;
            }
        }

        return isBoatItem(inv.getItemInOffHand()) ? 40 : -1;
    }

    private Boat nearestEmptyBoat(double r, int maxAge) {
        List<Boat> boats = ctx.world().entities(Boat.class, Ent.box(bot).inflate(r),
                bx -> Ent.alive(bx) && bx.getPassengers().isEmpty() && bx.getTicksLived() <= maxAge);
        Boat best = null;
        double bestD = Double.MAX_VALUE;

        for (Boat b : boats) {
            double d = Ent.distSqr(bot, b);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }

        return best;
    }

    private Item ourBoatDrop() {
        if (breakPos == null) {
            return null;
        } else {
            List<Item> items = ctx.world().entities(
                    Item.class,
                    new AABB(breakPos, breakPos).inflate(ctx.tuning().number(TuningKey.BOAT_DROP_RADIUS).doubleValue()),
                    it -> Ent.alive(it)
                            && it.getTicksLived() < ctx.tuning().integer(TuningKey.BOAT_DROP_MAX_AGE)
                            && isBoatItem(it.getItemStack()));
            Item best = null;
            double bestD = Double.MAX_VALUE;

            for (Item it : items) {
                double d = Ent.pos(it).distanceToSqr(breakPos);
                if (d < bestD) {
                    bestD = d;
                    best = it;
                }
            }

            return best;
        }
    }

    private void trackTarget(LivingEntity target) {
        Vec3 now = Ent.pos(target);
        if (lastTargetPos != null) {
            Vec3 step = now.subtract(lastTargetPos);
            targetVel = filteredVelocity(ctx.tuning(), targetVel, step);
        }

        lastTargetPos = now;
    }

    private Vec3 leadPoint(LivingEntity target, double dist) {
        return leadPoint(ctx.tuning(), Ent.pos(target), targetVel, dist);
    }

    static boolean continueDriving(BotTuning.Profile profile, double dist, double toLead) {
        return !(dist <= profile.number(TuningKey.BOAT_ARRIVE_DIST).doubleValue())
                && (!(toLead <= profile.number(TuningKey.BOAT_LEAD_ARRIVE).doubleValue())
                        || !(dist <= profile.number(TuningKey.BOAT_LEAD_CLOSE_DIST).doubleValue()));
    }

    static float steering(BotTuning.Profile profile, float diff) {
        float deadzone = profile.number(TuningKey.BOAT_STEER_DEADZONE).floatValue();
        return diff < -deadzone ? 1.0F : (diff > deadzone ? -1.0F : 0.0F);
    }

    static Vec3 filteredVelocity(BotTuning.Profile profile, Vec3 old, Vec3 step) {
        if (step.horizontalDistanceSqr() > profile.number(TuningKey.BOAT_TARGET_JUMP_SQR).doubleValue()) {
            step = Vec3.ZERO;
        }

        return old.scale(profile.number(TuningKey.BOAT_TARGET_OLD_WEIGHT).doubleValue())
                .add(step.scale(profile.number(TuningKey.BOAT_TARGET_NEW_WEIGHT).doubleValue()));
    }

    static Vec3 leadPoint(BotTuning.Profile profile, Vec3 pos, Vec3 velocity, double dist) {
        double vx = velocity.x();
        double vz = velocity.z();
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed < profile.number(TuningKey.BOAT_TARGET_MIN_SPEED).doubleValue()) {
            return pos;
        } else {
            double t = Math.min(profile.integer(TuningKey.BOAT_MAX_LEAD_TICKS),
                    dist / profile.number(TuningKey.BOAT_SPEED).doubleValue());
            return new Vec3(
                    pos.x() + vx * t + vx / speed * profile.number(TuningKey.BOAT_LEAD_EXTRA).doubleValue(),
                    pos.y(),
                    pos.z() + vz * t + vz / speed * profile.number(TuningKey.BOAT_LEAD_EXTRA).doubleValue());
        }
    }

    private void faceToward(Vec3 pos, float pitch) {
        Vec3 bp = Ent.pos(bot);
        double dx = pos.x() - bp.x();
        double dz = pos.z() - bp.z();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        ctx.controls().look(yaw, pitch);
    }

    private enum Phase {
        PLACE,
        MOUNT,
        DRIVE,
        BREAK,
        PICKUP
    }
}
