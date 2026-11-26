package dev.nandi0813.zppbots.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class TeamTargetBalancerTest {

    private static TeamTargetBalancer.ScoredBot bot(String name, double power, double... dist) {
        return new TeamTargetBalancer.ScoredBot(name, power, dist);
    }

    @Test
    void twoVersusTwoSendsOneBotAtEachEnemy() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        var bots = List.of(bot("a", 10, 5, 5), bot("b", 10, 5, 5));
        Map<String, Integer> side = TeamTargetBalancer.split(profile, bots, 2, Map.of(), new HashMap<>(), 0);
        assertNotEquals(side.get("a"), side.get("b"));
    }

    @Test
    void threeVersusOneFocusesTheSingleEnemy() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        var bots = List.of(bot("a", 10, 5), bot("b", 8, 6), bot("c", 9, 7));
        Map<String, Integer> side = TeamTargetBalancer.split(profile, bots, 1, Map.of(), new HashMap<>(), 0);
        assertEquals(Map.of("a", 0, "b", 0, "c", 0), side);
    }

    @Test
    void pinnedBotsKeepTheirSideAndNewcomersFillTheEmptyOne() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        var bots = List.of(bot("a", 10, 5, 5), bot("b", 10, 5, 5));
        Map<String, Integer> side = TeamTargetBalancer.split(profile, bots, 2, Map.of("a", 1), new HashMap<>(), 0);
        assertEquals(1, side.get("a"));
        assertEquals(0, side.get("b"));
    }

    @Test
    void strongerBotsAreSpreadAcrossSides() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        var bots = strongAndWeakBots();
        Map<String, Integer> side = TeamTargetBalancer.split(profile, bots, 2, Map.of(), new HashMap<>(), 0);
        assertNotEquals(side.get("s1"), side.get("s2"));
    }

    @Test
    void swapCountAndCooldownUseCapturedTuning() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        var bots = strongAndWeakBots();
        Map<String, Integer> pinned = pinnedStrongSide();
        Map<String, Long> movedAtZero = lastMovedAtZero();

        Map<String, Integer> beforeCooldown = TeamTargetBalancer.split(
                original, bots, 2, pinned, new HashMap<>(movedAtZero), 199);
        assertEquals(pinned, beforeCooldown);

        Map<String, Integer> cooldownElapsed = TeamTargetBalancer.split(
                original, bots, 2, pinned, new HashMap<>(movedAtZero), 200);
        assertNotEquals(cooldownElapsed.get("s1"), cooldownElapsed.get("s2"));

        JSONObject noSwapsRoot = PresetTuning.root(2);
        set(noSwapsRoot, TuningKey.TEAM_MAX_SWAPS, 0);
        Map<String, Integer> noSwaps = TeamTargetBalancer.split(
                profile(noSwapsRoot), bots, 2, pinned, new HashMap<>(movedAtZero), 200);
        assertEquals(pinned, noSwaps);

        JSONObject longerCooldownRoot = PresetTuning.root(3);
        set(longerCooldownRoot, TuningKey.TEAM_MOVE_COOLDOWN, 300);
        Map<String, Integer> longerCooldown = TeamTargetBalancer.split(
                profile(longerCooldownRoot), bots, 2, pinned, new HashMap<>(movedAtZero), 200);
        assertEquals(pinned, longerCooldown);
    }

    private static List<TeamTargetBalancer.ScoredBot> strongAndWeakBots() {
        return List.of(
                bot("s1", 100, 5, 5),
                bot("s2", 100, 5, 5),
                bot("w1", 1, 5, 5),
                bot("w2", 1, 5, 5));
    }

    private static Map<String, Integer> pinnedStrongSide() {
        return Map.of("s1", 0, "s2", 0, "w1", 1, "w2", 1);
    }

    private static Map<String, Long> lastMovedAtZero() {
        return Map.of("s1", 0L, "s2", 0L, "w1", 0L, "w2", 0L);
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
