package dev.nandi0813.zppbots.ai.elytra;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ArenaGuard;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ElytraObstacleGuard {

    private static final double MAX_LOOK_AHEAD = 24.0;
    private static final double MIN_LOOK_AHEAD = 6.0;
    private static final int LOOK_AHEAD_TICKS = 16;
    private static final float CLIMB_TRY_PITCH = -35.0F;
    private static final float[] YAW_TRIES = {18.0F, 34.0F, 52.0F, 74.0F, 100.0F, 130.0F};
    private static final double HALF_WIDTH = 0.45;

    private ElytraObstacleGuard() {
    }

    public static float[] avoid(BotContext ctx, float desiredYaw, float desiredPitch) {
        return avoid(ctx, desiredYaw, desiredPitch, lookAhead(ctx));
    }

    /** Steers a glide aim toward a target {@code aimDistance} away; the ray stops short of the aim point. */
    public static float[] towards(BotContext ctx, float yaw, float pitch, double aimDistance) {
        if (!ctx.player().isGliding()) return new float[] {yaw, pitch};
        double minReach = ctx.tuning().number(TuningKey.ELYTRA_GUARD_MIN_REACH).doubleValue();
        double reach = Math.min(lookAhead(ctx), Math.max(aimDistance - 1.5, minReach));
        if (reach < 1.0) return new float[] {yaw, pitch};
        return avoid(ctx, yaw, pitch, reach);
    }

    private static float[] avoid(BotContext ctx, float desiredYaw, float desiredPitch, double reach) {
        if (clear(ctx, desiredYaw, desiredPitch, reach)) {
            return new float[] {desiredYaw, desiredPitch};
        } else if (clear(ctx, desiredYaw, CLIMB_TRY_PITCH, reach)) {
            return new float[] {desiredYaw, CLIMB_TRY_PITCH};
        }
        for (float off : YAW_TRIES) {
            for (int side = 0; side < 2; side++) {
                float yaw = Mth.wrapDegrees(desiredYaw + (side == 0 ? off : -off));
                if (clear(ctx, yaw, desiredPitch, reach)) {
                    return new float[] {yaw, desiredPitch};
                }
                if (clear(ctx, yaw, CLIMB_TRY_PITCH, reach)) {
                    return new float[] {yaw, CLIMB_TRY_PITCH};
                }
            }
        }
        // Every heading is blocked: hold the current yaw and climb, instead of turning back into the wall.
        float climbPitch = (float) ctx.tuning().number(TuningKey.ELYTRA_GUARD_CLIMB_PITCH).doubleValue();
        return new float[] {ctx.player().getLocation().getYaw(), -climbPitch};
    }

    /** Casts from eye height and from one body height above and below it, so a wall at chest or feet level is seen too. */
    private static boolean clear(BotContext ctx, float yaw, float pitch, double reach) {
        Vec3 eye = Ent.eyePos(ctx.player());
        double bodyHeight = ctx.tuning().number(TuningKey.ELYTRA_GUARD_BODY_HEIGHT).doubleValue();
        for (double dy : new double[] {0.0, bodyHeight, -bodyHeight}) {
            if (!clearFrom(ctx, eye.add(new Vec3(0.0, dy, 0.0)), yaw, pitch, reach)) return false;
        }
        return true;
    }

    private static boolean clearFrom(BotContext ctx, Vec3 from, float yaw, float pitch, double reach) {
        Vec3 dir = fromAngles(yaw, pitch);
        double len = Math.sqrt(dir.x() * dir.x() + dir.z() * dir.z());
        Vec3 side = len < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-dir.z() / len, 0.0, dir.x() / len);

        for (double off : new double[] {0.0, HALF_WIDTH, -HALF_WIDTH}) {
            Vec3 origin = from.add(side.scale(off));
            Vec3 end = origin.add(dir.scale(reach));
            // The arena edge carries barrier walls, and a ray into an unloaded chunk reports no hit.
            if (ArenaGuard.outside(ctx.match().bounds(), end, ctx.tuning().number(TuningKey.ELYTRA_GUARD_ARENA_INSET).doubleValue())
                    || !ctx.world().isLoaded(BlockPos.containing(end))) {
                return false;
            }
            if (ctx.world().clip(origin, end, false, false).hit()) {
                return false;
            }
        }
        return true;
    }

    private static double lookAhead(BotContext ctx) {
        Vec3 v = Ent.velocity(ctx.player());
        double speed = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        return Mth.clamp(speed * LOOK_AHEAD_TICKS, MIN_LOOK_AHEAD, MAX_LOOK_AHEAD);
    }

    private static Vec3 fromAngles(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        double cosP = Math.cos(p);
        return new Vec3(-Math.sin(y) * cosP, -Math.sin(p), Math.cos(y) * cosP);
    }

    public static final class CrashDetector {

        private final BotContext ctx;
        private Vec3 lastPos;
        private int stuckTicks;

        public CrashDetector(BotContext ctx) {
            this.ctx = ctx;
        }

        public void reset() {
            lastPos = null;
            stuckTicks = 0;
        }

        public boolean crashed() {
            Player bot = ctx.player();
            Vec3 pos = Ent.pos(bot);
            if (lastPos == null) {
                lastPos = pos;
                return false;
            }
            Vec3 step = pos.subtract(lastPos);
            double moved = step.length();
            lastPos = pos;
            boolean going = moved >= ctx.tuning().number(TuningKey.ELYTRA_CRASH_SPEED).doubleValue();
            if (going && !horizontalCollision(bot, step)) {
                stuckTicks = 0;
                return false;
            }
            return ++stuckTicks >= ctx.tuning().integer(TuningKey.ELYTRA_CRASH_TICKS);
        }

        /** Bukkit has no horizontalCollision flag: probe the body one step further along its horizontal motion. */
        private static boolean horizontalCollision(Player bot, Vec3 step) {
            double len = Math.sqrt(step.x() * step.x() + step.z() * step.z());
            if (len < 1.0E-4) {
                return false;
            }
            Location ahead = bot.getLocation().add(step.x() / len * 0.1, 0.0, step.z() / len * 0.1);
            return bot.collidesAt(ahead);
        }
    }
}
