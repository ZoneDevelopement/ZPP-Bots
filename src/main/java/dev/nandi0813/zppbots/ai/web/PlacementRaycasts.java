package dev.nandi0813.zppbots.ai.web;

import dev.nandi0813.zppbots.brain.BlockHit;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;

public final class PlacementRaycasts {

    private PlacementRaycasts() {
    }

    public static boolean canSeePlacement(BotContext ctx, BlockPos pos) {
        Vec3 eye = Ent.eyePos(ctx.player());
        Vec3 to = Vec3.atCenterOf(pos);
        BlockHit hit = ctx.world().clip(eye, to, false, false);
        if (!hit.hit()) {
            return true;
        }
        BlockPos blocked = hit.pos();
        return blocked.equals(pos) || blocked.equals(pos.below());
    }
}
