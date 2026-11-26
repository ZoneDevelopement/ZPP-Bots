package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import org.bukkit.entity.LivingEntity;

public final class DiveCover {

    public static final double SKY_HEIGHT = 12.0;
    public static final double LAUNCH_HEADROOM = 6.0;
    private static final double HALF = 0.3;
    private static final int CACHE_TICKS = 5;

    private final BotContext ctx;
    private int cachedAt = Integer.MIN_VALUE;
    private int cachedId = Integer.MIN_VALUE;
    private boolean cached;

    public DiveCover(BotContext ctx) {
        this.ctx = ctx;
    }

    public boolean reachable(LivingEntity target, int now) {
        if (target.getEntityId() == cachedId && now - cachedAt < CACHE_TICKS && now >= cachedAt) {
            return cached;
        }
        cachedId = target.getEntityId();
        cachedAt = now;
        cached = reachable(ctx, target);
        return cached;
    }

    public static boolean reachable(BotContext ctx, LivingEntity target) {
        return !dugIn(ctx, target) && openAbove(ctx, target, SKY_HEIGHT);
    }

    public static boolean openAbove(BotContext ctx, LivingEntity e, double height) {
        AABB box = Ent.box(e);
        double cx = (box.minX() + box.maxX()) / 2.0;
        double cz = (box.minZ() + box.maxZ()) / 2.0;
        double y0 = box.maxY() + 0.05;
        double[][] offs = {{0.0, 0.0}, {HALF, HALF}, {HALF, -HALF}, {-HALF, HALF}, {-HALF, -HALF}};

        for (double[] o : offs) {
            Vec3 from = new Vec3(cx + o[0], y0, cz + o[1]);
            Vec3 to = from.add(0.0, height, 0.0);
            if (ctx.world().clip(from, to, false, false).hit()) {
                return false;
            }
        }
        return true;
    }

    public static boolean dugIn(BotContext ctx, LivingEntity t) {
        BlockPos eye = BlockPos.containing(Ent.eyePos(t));
        int walls = 0;
        for (Direction d : Direction.HORIZONTAL) {
            if (ctx.world().blocksMotion(eye.relative(d))) {
                walls++;
            }
        }
        return walls >= 3;
    }
}
