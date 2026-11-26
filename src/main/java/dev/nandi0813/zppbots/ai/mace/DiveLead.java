package dev.nandi0813.zppbots.ai.mace;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;

public final class DiveLead {

    private final BotContext ctx;
    private UUID id;
    private Vec3 last;
    private long lastTick;
    private Vec3 vel = Vec3.ZERO;

    public DiveLead(BotContext ctx) {
        this.ctx = ctx;
    }

    public Vec3 track(LivingEntity t) {
        long now = ctx.world().gameTime();
        Vec3 pos = Ent.pos(t);
        if (t.getUniqueId().equals(id) && last != null) {
            if (now != lastTick) {
                long gap = Math.max(1L, now - lastTick);
                Vec3 step = pos.subtract(last).scale(1.0 / gap);
                vel = smooth(ctx.tuning(), vel, step);
                last = pos;
                lastTick = now;
            }
            return vel;
        }
        id = t.getUniqueId();
        last = pos;
        lastTick = now;
        vel = Vec3.ZERO;
        return vel;
    }

    public boolean moving(LivingEntity t) {
        Vec3 v = track(t);
        return !t.isOnGround()
                || Math.sqrt(v.x() * v.x() + v.z() * v.z())
                        > ctx.tuning().number(TuningKey.DIVE_LEAD_MOVING_SPEED).doubleValue();
    }

    public Vec3 lead(LivingEntity t, int ticks) {
        Vec3 v = track(t);
        return projected(ctx.tuning(), Ent.pos(t), v, ticks);
    }

    static Vec3 smooth(BotTuning.Profile profile, Vec3 previous, Vec3 step) {
        if (step.lengthSqr() > profile.number(TuningKey.DIVE_LEAD_MAX_STEP_SQUARED).doubleValue()) {
            step = Vec3.ZERO;
        }
        return previous.scale(profile.number(TuningKey.DIVE_LEAD_OLD_WEIGHT).doubleValue())
                .add(step.scale(profile.number(TuningKey.DIVE_LEAD_STEP_WEIGHT).doubleValue()));
    }

    static Vec3 projected(BotTuning.Profile profile, Vec3 pos, Vec3 velocity, int ticks) {
        double k = Math.min(Math.max(ticks, 0), profile.integer(TuningKey.DIVE_LEAD_MAX_TICKS))
                * profile.number(TuningKey.DIVE_LEAD_TRUST).doubleValue();
        return new Vec3(pos.x() + velocity.x() * k, pos.y(), pos.z() + velocity.z() * k);
    }

    public static int ticksToDrop(double height, double vy) {
        if (height <= 0.0) {
            return 0;
        }
        double y = 0.0;
        double v = vy;
        for (int t = 1; t <= 60; t++) {
            v = (v - 0.08) * 0.98;
            y += v;
            if (-y >= height) {
                return t;
            }
        }
        return 60;
    }

    public void reset() {
        id = null;
        last = null;
        vel = Vec3.ZERO;
    }
}
