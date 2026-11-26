package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.team.Squad;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Fans the chasers of one target out sideways; one instance per squad, so only squadmates share a plan. */
public final class ChaseSpread {
    private final Squad squad;
    private final Map<UUID, Plan> plans = new HashMap<>();

    public ChaseSpread(Squad squad) {
        this.squad = squad;
    }

    public Vec3 offsetFor(BotContext ctx, LivingEntity target, double dist) {
        BotTuning.Profile profile = ctx.tuning();
        double noneAt = profile.number(TuningKey.CHASE_NONE_AT).doubleValue();
        if (target != null && !(dist <= noneAt)) {
            long tick = ctx.world().gameTime();
            Plan plan = plans.get(target.getUniqueId());
            if (plan == null || plan.tick() != tick || plan.profile() != profile) {
                if (plans.size() > 256) {
                    plans.clear();
                }
                plan = build(target, tick, profile);
                plans.put(target.getUniqueId(), plan);
            }

            double[] slot = plan.slots().get(ctx.player().getUniqueId());
            if (slot == null) {
                return null;
            }
            double scale = scale(profile, dist);
            double px = -plan.fz();
            double pz = plan.fx();
            double ox = (px * slot[0] - plan.fx() * slot[1]) * scale;
            double oz = (pz * slot[0] - plan.fz() * slot[1]) * scale;
            return new Vec3(ox, 0.0, oz);
        }
        return null;
    }

    private Plan build(LivingEntity target, long tick, BotTuning.Profile profile) {
        List<Player> chasers = new ArrayList<>();
        Vec3 targetPos = Ent.pos(target);
        double noneAt = profile.number(TuningKey.CHASE_NONE_AT).doubleValue();
        for (BotContext member : squad.members()) {
            Player b = member.player();
            if (squad.combatTarget(member) == target
                    && Ent.alive(b)
                    && b.getWorld().equals(target.getWorld())
                    && !(Ent.pos(b).distanceTo(targetPos) <= noneAt)) {
                chasers.add(b);
            }
        }

        Map<UUID, double[]> slots = new HashMap<>();
        if (chasers.size() < profile.integer(TuningKey.CHASE_MIN_CHASERS)) {
            return new Plan(tick, profile, 0.0, 1.0, slots);
        }
        double minB = Double.MAX_VALUE;
        double maxB = -Double.MAX_VALUE;
        double sumSin = 0.0;
        double sumCos = 0.0;

        for (Player b : chasers) {
            Vec3 p = Ent.pos(b);
            double a = Math.atan2(p.z() - targetPos.z(), p.x() - targetPos.x());
            sumSin += Math.sin(a);
            sumCos += Math.cos(a);
        }

        double mean = Math.atan2(sumSin, sumCos);

        for (Player b : chasers) {
            Vec3 p = Ent.pos(b);
            double a = Math.atan2(p.z() - targetPos.z(), p.x() - targetPos.x());
            double d = Math.toDegrees(Math.atan2(Math.sin(a - mean), Math.cos(a - mean)));
            minB = Math.min(minB, d);
            maxB = Math.max(maxB, d);
        }

        double maxFanDegrees = profile.number(TuningKey.CHASE_MAX_FAN_DEGREES).doubleValue();
        if (maxB - minB > maxFanDegrees) {
            return new Plan(tick, profile, 0.0, 1.0, slots);
        }
        double cx = 0.0;
        double cz = 0.0;

        for (Player b : chasers) {
            Vec3 p = Ent.pos(b);
            cx += p.x();
            cz += p.z();
        }

        cx /= chasers.size();
        cz /= chasers.size();
        double fx = targetPos.x() - cx;
        double fz = targetPos.z() - cz;
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 0.001) {
            return new Plan(tick, profile, 0.0, 1.0, slots);
        }
        fx /= len;
        fz /= len;
        double pxs = -fz;
        double pzs = fx;
        double ccx = cx;
        double ccz = cz;
        chasers.sort(Comparator.comparingDouble(b -> Ent.pos(b).distanceToSqr(targetPos)));

        int perRank = profile.integer(TuningKey.CHASE_PER_RANK);
        for (int r = 0; r * perRank < chasers.size(); r++) {
            List<Player> rank = new ArrayList<>(chasers.subList(
                    r * perRank,
                    Math.min(chasers.size(), (r + 1) * perRank)));
            rank.sort(Comparator.comparingDouble(
                    b -> (Ent.pos(b).x() - ccx) * pxs + (Ent.pos(b).z() - ccz) * pzs));

            for (int k = 0; k < rank.size(); k++) {
                slots.put(rank.get(k).getUniqueId(), slotFor(profile, rank.size(), k, r));
            }
        }
        return new Plan(tick, profile, fx, fz, slots);
    }

    static double scale(BotTuning.Profile profile, double dist) {
        double noneAt = profile.number(TuningKey.CHASE_NONE_AT).doubleValue();
        double fadeSpan = profile.number(TuningKey.CHASE_FADE_SPAN).doubleValue();
        return Math.min(1.0, (dist - noneAt) / fadeSpan);
    }

    static double[] slotFor(BotTuning.Profile profile, int rowSize, int index, int rank) {
        double spacing = profile.number(TuningKey.CHASE_SPACING).doubleValue();
        double rankGap = profile.number(TuningKey.CHASE_RANK_GAP).doubleValue();
        return new double[] {(index - (rowSize - 1) / 2.0) * spacing, rank * rankGap};
    }

    private record Plan(long tick, BotTuning.Profile profile, double fx, double fz, Map<UUID, double[]> slots) {}
}
