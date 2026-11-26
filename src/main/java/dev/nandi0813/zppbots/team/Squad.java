package dev.nandi0813.zppbots.team;

import dev.nandi0813.zppbots.ai.gear.RestockPlan;
import dev.nandi0813.zppbots.ai.move.ChaseSpread;
import dev.nandi0813.zppbots.ai.move.FormationAnchor;
import dev.nandi0813.zppbots.ai.move.SpreadOut;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** State the bots of one ZPP team share . Main thread only. */
public final class Squad {

    private final String key;
    private final int team;
    private final List<BotContext> members = new ArrayList<>();
    private final RestockPlan restockPlan = new RestockPlan();
    private final FormationAnchor formationAnchor = new FormationAnchor();
    private final TeamTactics tactics = new TeamTactics();
    private final TeamTargetBalancer balancer = new TeamTargetBalancer();
    private final SpreadOut spreadOut = new SpreadOut(this);
    private final ChaseSpread chaseSpread = new ChaseSpread(this);
    private final Map<UUID, LivingEntity> combatTargets = new HashMap<>();
    private TeamSurround surround;
    private Player surroundSpent;
    private long lastRebalance = Long.MIN_VALUE;

    public Squad(String key, int team) {
        this.key = key;
        this.team = team;
    }

    public static Squad solo(BotContext ctx) {
        Squad squad = new Squad(ctx.name(), ctx.match().team());
        squad.add(ctx);
        return squad;
    }

    public String key() {
        return key;
    }

    public int team() {
        return team;
    }

    public List<BotContext> members() {
        return members;
    }

    public void add(BotContext member) {
        if (!members.contains(member)) members.add(member);
    }

    public void remove(BotContext member) {
        members.remove(member);
        combatTargets.remove(member.player().getUniqueId());
    }

    public RestockPlan restockPlan() {
        return restockPlan;
    }

    public FormationAnchor formationAnchor() {
        return formationAnchor;
    }

    public TeamTactics tactics() {
        return tactics;
    }

    public SpreadOut spreadOut() {
        return spreadOut;
    }

    public ChaseSpread chaseSpread() {
        return chaseSpread;
    }

    /** True when any member's settings retreat (BEHAVIOR.RETREAT, play style applied). */
    public boolean retreatWhenLow() {
        for (BotContext member : members) {
            if (member.settings().retreat()) return true;
        }
        return false;
    }

    /** The brain reports its current combat target here; ChaseSpread groups chasers by it. */
    public void setCombatTarget(BotContext member, LivingEntity target) {
        if (target == null) {
            combatTargets.remove(member.player().getUniqueId());
        } else {
            combatTargets.put(member.player().getUniqueId(), target);
        }
    }

    public LivingEntity combatTarget(BotContext member) {
        return combatTargets.get(member.player().getUniqueId());
    }

    public Player assignedTarget(BotContext ctx) {
        return balancer.assignedTarget(ctx);
    }

    public boolean isSplitTarget(Player p) {
        return balancer.isSplitTarget(this, p);
    }

    /** The active surround, or null. */
    public TeamSurround surround() {
        return surround;
    }

    /** False when no surround runs, else {@link TeamSurround#tick}. */
    public boolean tickSurround(BotContext ctx, Consumer<Vec3> chase) {
        return surround != null && surround.tick(ctx, chase);
    }

    List<BotContext> liveMembers() {
        List<BotContext> live = new ArrayList<>();
        for (BotContext member : members) {
            if (TeamTargetBalancer.isLive(member.player())) live.add(member);
        }
        return live;
    }

    public void tick(long gameTime) {
        if (members.isEmpty()) {
            lastRebalance = gameTime;
            balancer.rebalance(this, gameTime);
            updateSurround();
            return;
        }

        if (lastRebalance == Long.MIN_VALUE
                || gameTime - lastRebalance >= members.get(0).tuning().integer(TuningKey.TEAM_REBALANCE_INTERVAL)
                || gameTime < lastRebalance) {
            lastRebalance = gameTime;
            balancer.rebalance(this, gameTime);
        }

        updateSurround();
    }

    private void updateSurround() {
        List<BotContext> live = liveMembers();
        Player lone = null;
        int nearby = 0;
        if (!live.isEmpty()) {
            double cx = 0.0;
            double cy = 0.0;
            double cz = 0.0;
            for (BotContext member : live) {
                Vec3 p = Ent.pos(member.player());
                cx += p.x();
                cy += p.y();
                cz += p.z();
            }
            Vec3 centroid = new Vec3(cx / live.size(), cy / live.size(), cz / live.size());
            for (Player enemy : live.get(0).match().enemies()) {
                if (TeamTargetBalancer.isLive(enemy) && enemy.getWorld().equals(live.get(0).player().getWorld())
                        && Ent.pos(enemy).distanceTo(centroid)
                                <= live.get(0).tuning().number(TuningKey.TEAM_SURROUND_RANGE).doubleValue()) {
                    nearby++;
                    lone = enemy;
                }
            }
        }

        if (live.isEmpty() || !TeamSurround.shouldSurround(live.get(0).tuning(), live.size(), nearby)) {
            surround = null;
            surroundSpent = null;
            return;
        }
        if (surround != null && surround.engaged()) {
            surroundSpent = surround.target();
            surround = null;
        }
        if (surroundSpent != lone) surroundSpent = null;
        if (surround != null && surround.target() != lone) surround = null;
        if (surround == null && surroundSpent == null) {
            surround = TeamSurround.start(
                    this,
                    lone,
                    live.get(0).tuning().number(TuningKey.TEAM_SURROUND_RADIUS).doubleValue(),
                    live.get(0).tuning().number(TuningKey.TEAM_SURROUND_ENGAGE).doubleValue());
        }
    }

    public void clear() {
        members.clear();
        combatTargets.clear();
        balancer.clear();
        surround = null;
        surroundSpent = null;
    }
}
