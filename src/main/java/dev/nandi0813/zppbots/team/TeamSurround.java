package dev.nandi0813.zppbots.team;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** One squad's ring around a lone enemy; the squad starts and stops it. */
public final class TeamSurround {

    private final Squad squad;
    private final Player target;
    private final double radius;
    private final double engageDist;
    private final List<UUID> ring = new ArrayList<>();
    private double baseAngle;
    private boolean placed;
    private final Set<UUID> settled = new HashSet<>();
    private long checkedTick = -1;
    private boolean engaged;

    TeamSurround(Squad squad, Player target, double radius, double engageDist) {
        this.squad = squad;
        this.target = target;
        this.radius = radius;
        this.engageDist = engageDist;
    }

    public static TeamSurround start(Squad squad, Player target, double radius, double engageDist) {
        return new TeamSurround(squad, target, radius, engageDist);
    }

    static boolean shouldSurround(BotTuning.Profile profile, int liveBots, int nearbyEnemies) {
        return liveBots >= profile.integer(TuningKey.TEAM_SURROUND_MIN_BOTS) && nearbyEnemies == 1;
    }

    public Player target() {
        return target;
    }

    /** True once the target came close or hit a ring member; the squad then drops the surround. */
    public boolean engaged() {
        return engaged;
    }

    public boolean tick(BotContext ctx, Consumer<Vec3> chase) {
        Player bot = ctx.player();
        if (engaged || !TeamTargetBalancer.isLive(target)) {
            return false;
        }
        if (!target.getWorld().equals(bot.getWorld())) {
            return false;
        }
        List<BotContext> live = liveRing(target);
        if (live.isEmpty()) {
            return false;
        }
        UUID id = bot.getUniqueId();
        if (!placed || !ring.contains(id)) {
            place(live);
        }

        long now = ctx.world().gameTime();
        if (checkedTick != now) {
            checkedTick = now;
            for (BotContext m : live) {
                boolean close = settled.contains(m.player().getUniqueId()) && Ent.dist(m.player(), target) <= engageDist;
                boolean hit = m.match().lastAttackerOf(
                                m.player(), m.tick(), m.tuning().integer(TuningKey.TEAM_SURROUND_HIT_TICKS))
                        == target;
                if (close || hit) {
                    engaged = true;
                    return false;
                }
            }
        }

        LivingEntity hurtBy = ctx.match().lastAttackerOf(
                bot, ctx.tick(), ctx.tuning().integer(TuningKey.TEAM_SURROUND_HIT_TICKS));
        if (hurtBy != null && hurtBy != target && Ent.alive(hurtBy)) {
            return false;
        }

        int n = 0;
        int mine = -1;
        for (UUID member : ring) {
            if (live.stream().anyMatch(m -> m.player().getUniqueId().equals(member))) {
                if (member.equals(id)) {
                    mine = n;
                }
                n++;
            }
        }
        if (mine < 0 || n == 0) {
            return false;
        }

        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        Vec3 spot = onRing(targetPos, slotAngle(baseAngle, mine, n), radius);
        double dx = spot.x() - botPos.x();
        double dz = spot.z() - botPos.z();
        double away = Math.sqrt(dx * dx + dz * dz);
        if (away > ctx.tuning().number(TuningKey.TEAM_SURROUND_SLOT_ARRIVE).doubleValue()) {
            double a = slotAngle(baseAngle, mine, n);
            double cur = Math.atan2(botPos.z() - targetPos.z(), botPos.x() - targetPos.x());
            double diff = Math.atan2(Math.sin(a - cur), Math.cos(a - cur));
            double arcStep = Math.toRadians(ctx.tuning().number(TuningKey.TEAM_SURROUND_ARC_DEGREES).doubleValue());
            Vec3 go = Math.abs(diff) > arcStep ? onRing(targetPos, cur + Math.copySign(arcStep, diff), radius) : spot;
            ctx.controls().setSprinting(away > ctx.tuning().number(TuningKey.TEAM_SURROUND_SPRINT).doubleValue());
            chase.accept(go);
        } else {
            settled.add(id);
            ctx.controls().stopMoving();
            ctx.controls().setSprinting(false);
            face(ctx);
        }
        return true;
    }

    static double slotAngle(double baseAngle, int index, int count) {
        return baseAngle + (Math.PI * 2) * index / count;
    }

    static Vec3 onRing(Vec3 center, double angle, double radius) {
        return new Vec3(center.x() + Math.cos(angle) * radius, center.y(), center.z() + Math.sin(angle) * radius);
    }

    private List<BotContext> liveRing(Player target) {
        List<BotContext> out = new ArrayList<>();
        for (BotContext m : squad.members()) {
            if (Ent.alive(m.player()) && m.player().getWorld().equals(target.getWorld())) {
                out.add(m);
            }
        }
        return out;
    }

    private void place(List<BotContext> live) {
        List<BotContext> sorted = new ArrayList<>(live);
        sorted.sort(Comparator.comparingDouble(m -> bearing(m.player())));
        ring.clear();
        for (BotContext m : sorted) {
            ring.add(m.player().getUniqueId());
        }
        baseAngle = sorted.isEmpty() ? 0.0 : bearing(sorted.get(0).player());
        placed = true;
    }

    private double bearing(Player p) {
        Vec3 pos = Ent.pos(p);
        Vec3 t = Ent.pos(target);
        return Math.atan2(pos.z() - t.z(), pos.x() - t.x());
    }

    private void face(BotContext ctx) {
        Vec3 bot = Ent.pos(ctx.player());
        Vec3 t = Ent.pos(target);
        double dx = t.x() - bot.x();
        double dz = t.z() - bot.z();
        double dy = Ent.eyePos(target).y() - Ent.eyePos(ctx.player()).y();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        ctx.controls().look(yaw, pitch);
    }
}
