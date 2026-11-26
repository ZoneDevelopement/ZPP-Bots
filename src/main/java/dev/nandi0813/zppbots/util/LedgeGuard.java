package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;

public final class LedgeGuard {

    private LedgeGuard() {
    }

    public static boolean wouldFallDangerously(BotContext ctx, double dirX, double dirZ, int maxSafeDrop) {
        if (!ctx.player().isOnGround()) {
            return false;
        }
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1.0E-4) {
            return false;
        }
        double nx = dirX / len;
        double nz = dirZ / len;
        Vec3 p = Ent.pos(ctx.player());
        int bx = (int) Math.floor(p.x() + nx);
        int bz = (int) Math.floor(p.z() + nz);
        int feetY = (int) Math.floor(p.y());

        for (int depth = 0; depth <= maxSafeDrop + 1; depth++) {
            BlockPos cand = new BlockPos(bx, feetY - depth, bz);
            if (ctx.world().blocksMotion(cand) || ctx.world().isWater(cand)) {
                return false;
            }
        }
        return true;
    }
}
