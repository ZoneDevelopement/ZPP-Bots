package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.team.Squad;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Holds a big squad in a grid at standoff range until the fight starts; one instance per squad. */
public final class SpreadOut {

    private final Squad squad;
    private long engaged = Long.MIN_VALUE;
    private boolean hasEngaged;
    private Roster roster;

    public SpreadOut(Squad squad) {
        this.squad = squad;
    }

    public void clearEngaged() {
        hasEngaged = false;
    }

    public Vec3 standoffFor(BotContext ctx, LivingEntity target) {
        if (target == null || !ctx.settings().spreadOut()) {
            return null;
        }
        Player bot = ctx.player();
        BotTuning.Profile profile = ctx.tuning();
        long now = ctx.world().gameTime();
        // hurtTime <= 0: the 10-tick hurt animation is the first half of the invulnerability window
        if (bot.getNoDamageTicks() > bot.getMaximumNoDamageTicks() / 2
                || Ent.dist(bot, target) <= profile.number(TuningKey.SPREAD_ENGAGE_RANGE).doubleValue()) {
            engaged = now;
            hasEngaged = true;
            return null;
        }
        if (hasEngaged && now - engaged >= 0L && now - engaged < profile.integer(TuningKey.SPREAD_ENGAGED_TICKS)) {
            return null;
        }
        List<BotContext> members = squad.members();
        int sourceSize = members.size();
        if (sourceSize <= profile.integer(TuningKey.SPREAD_MIN_TEAM)) {
            return null;
        }

        if (roster == null || roster.gameTime() != now || roster.sourceSize() != sourceSize) {
            List<String> sorted = new ArrayList<>();
            for (BotContext m : members) {
                sorted.add(m.name());
            }
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            Map<String, Integer> ranks = new HashMap<>(sorted.size() * 2);
            for (int i = 0; i < sorted.size(); i++) {
                ranks.putIfAbsent(sorted.get(i).toLowerCase(Locale.ROOT), i);
            }
            roster = new Roster(now, sourceSize, sorted.size(), ranks);
        }

        if (roster.size() <= profile.integer(TuningKey.SPREAD_MIN_TEAM)) {
            return null;
        }
        Integer found = roster.rankByName().get(ctx.name().toLowerCase(Locale.ROOT));
        if (found == null) {
            return null;
        }
        int rank = found;
        int holders = roster.size();
        FormationAnchor.Anchored anchor = squad.formationAnchor().of(ctx, target, now);
        float yaw = anchor.yaw();
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        Vec3 a = anchor.pos();
        double standoff = profile.number(TuningKey.SPREAD_STANDOFF).doubleValue();
        Vec3 base = new Vec3(a.x() - fx * standoff, a.y(), a.z() - fz * standoff);
        return FollowFormation.slotWorldSpaced(
                FollowFormation.Type.GRID,
                rank,
                holders,
                widthFor(holders, profile),
                base,
                yaw,
                profile.number(TuningKey.SPREAD_SPACING).doubleValue());
    }

    static int widthFor(int holders, BotTuning.Profile profile) {
        int minimumWidth = profile.integer(TuningKey.SPREAD_MIN_WIDTH);
        double aspect = profile.number(TuningKey.SPREAD_ASPECT).doubleValue();
        return Math.max(minimumWidth, (int) Math.ceil(Math.sqrt(holders * aspect)));
    }

    private record Roster(long gameTime, int sourceSize, int size, Map<String, Integer> rankByName) {
    }
}
