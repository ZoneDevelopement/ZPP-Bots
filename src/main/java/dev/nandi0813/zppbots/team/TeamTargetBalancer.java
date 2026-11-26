package dev.nandi0813.zppbots.team;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Splits one squad's bots across the live enemies; one instance per squad. */
public final class TeamTargetBalancer {

    private final Map<String, Long> lastMoved = new HashMap<>();
    private final Map<String, String> assignments = new HashMap<>();

    static boolean isLive(Player p) {
        return p != null && Ent.alive(p) && p.getGameMode() != GameMode.SPECTATOR;
    }

    /** Live enemies of the squad in name order; empty when the squad has no live bot. */
    List<Player> liveTargets(Squad squad) {
        List<BotContext> live = squad.liveMembers();
        List<Player> out = new ArrayList<>();
        if (live.isEmpty()) {
            return out;
        }
        for (Player enemy : live.get(0).match().enemies()) {
            if (isLive(enemy) && !out.contains(enemy)) {
                out.add(enemy);
            }
        }
        out.sort(Comparator.comparing(Player::getName));
        return out;
    }

    public void rebalance(Squad squad, long gameTime) {
        List<BotContext> live = squad.liveMembers();
        if (live.isEmpty()) {
            assignments.clear();
            return;
        }
        List<Player> targetPlayers = liveTargets(squad);
        if (targetPlayers.size() < 2) {
            assignments.clear();
            return;
        }

        List<String> targets = new ArrayList<>();
        for (Player t : targetPlayers) {
            targets.add(t.getName());
        }

        List<ScoredBot> bots = new ArrayList<>();
        for (BotContext b : live) {
            double[] d = new double[targetPlayers.size()];
            for (int i = 0; i < d.length; i++) {
                d[i] = Ent.dist(b.player(), targetPlayers.get(i));
            }
            bots.add(new ScoredBot(b.name(), BotCombatScoring.score(b.tuning(), b.player()), d));
        }
        if (bots.size() < 2) {
            assignments.clear();
            return;
        }

        Map<String, Integer> pinned = new HashMap<>();
        for (ScoredBot sb : bots) {
            int side = indexOfTarget(targets, assignments.get(sb.name));
            if (side >= 0) {
                pinned.put(sb.name, side);
            }
        }

        BotTuning.Profile profile = live.get(0).tuning();
        Map<String, Integer> sideOf = split(profile, bots, targets.size(), pinned, lastMoved, gameTime);

        assignments.clear();
        for (ScoredBot sb : bots) {
            assignments.put(sb.name, targets.get(sideOf.get(sb.name)));
        }
        if (lastMoved.size() > 4096) {
            lastMoved.values().removeIf(
                    t -> gameTime - t > profile.integer(TuningKey.TEAM_MOVE_COOLDOWN));
        }
    }

    /** The target this bot was split onto, or null when the squad does not split (fewer than two enemies or bots). */
    public Player assignedTarget(BotContext ctx) {
        String name = assignments.get(ctx.name());
        if (name == null) {
            return null;
        }
        for (Player enemy : ctx.match().enemies()) {
            if (isLive(enemy) && enemy.getName().equalsIgnoreCase(name)) {
                return enemy;
            }
        }
        return null;
    }

    public boolean isSplitTarget(Squad squad, Player p) {
        if (p == null) {
            return false;
        }
        List<Player> targets = liveTargets(squad);
        return targets.size() >= 2 && targets.contains(p);
    }

    public void clear() {
        assignments.clear();
        lastMoved.clear();
    }

    /** Scoring and split core on plain numbers. {@code pinned} maps bot name to the side it already holds. */
    static Map<String, Integer> split(
            BotTuning.Profile profile,
            List<ScoredBot> bots,
            int n,
            Map<String, Integer> pinned,
            Map<String, Long> lastMoved,
            long now) {
        Map<String, Integer> sideOf = new HashMap<>();
        List<ScoredBot> unplaced = new ArrayList<>();
        for (ScoredBot sb : bots) {
            Integer side = pinned.get(sb.name);
            if (side != null) {
                sideOf.put(sb.name, side);
            } else {
                unplaced.add(sb);
            }
        }

        int[] count = new int[n];
        double[] power = new double[n];
        for (ScoredBot sb : bots) {
            Integer s = sideOf.get(sb.name);
            if (s != null) {
                count[s]++;
                power[s] += sb.power;
            }
        }

        unplaced.sort((a, b) -> Double.compare(b.power, a.power));
        for (ScoredBot sb : unplaced) {
            int s = placeSide(profile, sb, count, power);
            sideOf.put(sb.name, s);
            count[s]++;
            power[s] += sb.power;
        }

        for (int guard = 0; guard < bots.size(); guard++) {
            int big = 0;
            int small = 0;
            for (int i = 1; i < n; i++) {
                if (count[i] > count[big]) {
                    big = i;
                }
                if (count[i] < count[small]) {
                    small = i;
                }
            }
            if (count[big] - count[small] <= 1) {
                break;
            }

            double want = (power[big] - power[small]) / 2.0;
            ScoredBot pick = null;
            double pickScore = Double.MAX_VALUE;
            for (ScoredBot sb : bots) {
                if (sideOf.get(sb.name) == big) {
                    double score = Math.abs(sb.power - want)
                            + profile.number(TuningKey.TEAM_TRAVEL_WEIGHT).doubleValue() * sb.dist[small];
                    if (score < pickScore) {
                        pickScore = score;
                        pick = sb;
                    }
                }
            }
            if (pick == null) {
                break;
            }

            sideOf.put(pick.name, small);
            count[big]--;
            count[small]++;
            power[big] -= pick.power;
            power[small] += pick.power;
            lastMoved.put(pick.name.toLowerCase(Locale.ROOT), now);
        }

        for (int swaps = 0; swaps < profile.integer(TuningKey.TEAM_MAX_SWAPS); swaps++) {
            int strong = 0;
            int weak = 0;
            for (int i = 1; i < n; i++) {
                if (power[i] > power[strong]) {
                    strong = i;
                }
                if (power[i] < power[weak]) {
                    weak = i;
                }
            }

            double gap = power[strong] - power[weak];
            double avg = 0.0;
            for (double p : power) {
                avg += p;
            }
            avg /= n;
            if (strong == weak
                    || avg <= 0.0
                    || gap < profile.number(TuningKey.TEAM_GEAR_GAP).doubleValue() * avg) {
                break;
            }

            ScoredBot bestA = null;
            ScoredBot bestB = null;
            double bestGap = gap;
            for (ScoredBot a : bots) {
                if (sideOf.get(a.name) == strong && !recentlyMoved(profile, lastMoved, a.name, now)) {
                    for (ScoredBot b : bots) {
                        if (sideOf.get(b.name) == weak && !recentlyMoved(profile, lastMoved, b.name, now)) {
                            double d = a.power - b.power;
                            if (!(d <= 0.0)) {
                                double newGap = Math.abs(gap - 2.0 * d);
                                double travel = a.dist[weak] + b.dist[strong] - (a.dist[strong] + b.dist[weak]);
                                double score = newGap
                                        + profile.number(TuningKey.TEAM_TRAVEL_WEIGHT).doubleValue()
                                                * Math.max(0.0, travel);
                                if (newGap < gap * profile.number(TuningKey.TEAM_SWAP_IMPROVEMENT).doubleValue()
                                        && score < bestGap) {
                                    bestGap = score;
                                    bestA = a;
                                    bestB = b;
                                }
                            }
                        }
                    }
                }
            }
            if (bestA == null) {
                break;
            }

            sideOf.put(bestA.name, weak);
            sideOf.put(bestB.name, strong);
            power[strong] += bestB.power - bestA.power;
            power[weak] += bestA.power - bestB.power;
            lastMoved.put(bestA.name.toLowerCase(Locale.ROOT), now);
            lastMoved.put(bestB.name.toLowerCase(Locale.ROOT), now);
        }
        return sideOf;
    }

    private static int placeSide(BotTuning.Profile profile, ScoredBot sb, int[] count, double[] power) {
        int min = Integer.MAX_VALUE;
        for (int c : count) {
            min = Math.min(min, c);
        }

        int nearest = -1;
        int weakest = -1;
        double avg = 0.0;
        for (double p : power) {
            avg += p;
        }
        avg /= power.length;

        for (int i = 0; i < count.length; i++) {
            if (count[i] == min) {
                if (nearest < 0 || sb.dist[i] < sb.dist[nearest]) {
                    nearest = i;
                }
                if (weakest < 0 || power[i] < power[weakest] - 1.0E-6) {
                    weakest = i;
                }
            }
        }
        return power[nearest] - power[weakest]
                        > profile.number(TuningKey.TEAM_GEAR_GAP).doubleValue()
                                * Math.max(avg, profile.number(TuningKey.TEAM_POWER_FLOOR).doubleValue())
                ? weakest
                : nearest;
    }

    private static boolean recentlyMoved(
            BotTuning.Profile profile, Map<String, Long> lastMoved, String botName, long now) {
        Long t = lastMoved.get(botName.toLowerCase(Locale.ROOT));
        return t != null && now - t < profile.integer(TuningKey.TEAM_MOVE_COOLDOWN);
    }

    private static int indexOfTarget(List<String> targets, String pinned) {
        if (pinned == null) {
            return -1;
        }
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).equalsIgnoreCase(pinned)) {
                return i;
            }
        }
        return -1;
    }

    record ScoredBot(String name, double power, double[] dist) {
    }
}
