package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;

/** Anchors of the leaders one squad follows; one instance per squad instead of a static map. */
public final class FormationAnchor {
    private final Map<UUID, Anchor> anchors = new HashMap<>();

    public Anchored of(BotContext ctx, LivingEntity leader, long gameTime) {
        UUID key = leader.getUniqueId();
        Anchor prev = anchors.get(key);
        Vec3 pos = Ent.pos(leader);
        BotTuning.Profile profile = ctx.tuning();
        double speedSqr = ctx.match().velocityOf(leader).horizontalDistanceSqr();
        double movedSqr = prev == null ? 0.0 : prev.pos.distanceToSqr(pos);
        long elapsed = prev == null ? 0L : gameTime - prev.lastSeen;
        if (prev != null && reusable(profile, speedSqr, movedSqr, elapsed)) {
            anchors.put(key, new Anchor(prev.pos, prev.yaw, gameTime));
            return new Anchored(prev.pos, prev.yaw, false);
        }
        Anchor now = new Anchor(pos, leader.getBodyYaw(), gameTime);
        anchors.put(key, now);
        return new Anchored(now.pos, now.yaw, true);
    }

    static boolean reusable(BotTuning.Profile profile, double speedSqr, double movedSqr, long elapsed) {
        double movingSpeedSqr = profile.number(TuningKey.ANCHOR_MOVING_SPEED_SQR).doubleValue();
        double reanchorDistSqr = profile.number(TuningKey.ANCHOR_REANCHOR_DIST_SQR).doubleValue();
        int staleAfterTicks = profile.integer(TuningKey.ANCHOR_STALE_AFTER_TICKS);
        return !(speedSqr > movingSpeedSqr)
                && !(movedSqr > reanchorDistSqr)
                && !(elapsed > staleAfterTicks);
    }

    public void forget(UUID leaderId) {
        if (leaderId != null) {
            anchors.remove(leaderId);
        }
    }

    private record Anchor(Vec3 pos, float yaw, long lastSeen) {}

    public record Anchored(Vec3 pos, float yaw, boolean moving) {}
}
