package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;

public final class MeleeWallGuard {

    private static final double PROBE = 0.55;

    private MeleeWallGuard() {
    }

    public static float[] deflect(BotContext ctx, float forward, float strafe) {
        float[] unchanged = new float[] {forward, strafe};
        if (!ctx.player().isOnGround()) {
            return unchanged;
        }
        if (forward == 0.0F && strafe == 0.0F) {
            return unchanged;
        }
        double yaw = Math.toRadians(ctx.player().getLocation().getYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double sx = Math.cos(yaw);
        double sz = Math.sin(yaw);
        boolean forwardBlocked = forward != 0.0F && blocked(ctx, fx * Math.signum(forward), fz * Math.signum(forward));
        boolean strafeBlocked = strafe != 0.0F && blocked(ctx, sx * Math.signum(strafe), sz * Math.signum(strafe));
        return !forwardBlocked && !strafeBlocked
                ? unchanged
                : new float[] {forwardBlocked ? 0.0F : forward, strafeBlocked ? 0.0F : strafe};
    }

    private static boolean blocked(BotContext ctx, double dirX, double dirZ) {
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1.0E-4) {
            return false;
        }
        double nx = dirX / len * PROBE;
        double nz = dirZ / len * PROBE;
        Vec3 p = Ent.pos(ctx.player());
        return solid(ctx, p.x() + nx, p.y(), p.z() + nz) || solid(ctx, p.x() + nx, p.y() + 1.0, p.z() + nz);
    }

    private static boolean solid(BotContext ctx, double x, double y, double z) {
        return ctx.world().blocksMotion(BlockPos.containing(x, y, z));
    }
}
