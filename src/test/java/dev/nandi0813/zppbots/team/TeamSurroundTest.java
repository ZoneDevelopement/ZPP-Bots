package dev.nandi0813.zppbots.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class TeamSurroundTest {

    @Test
    void ringSlotsAreEvenlySpacedAtTheRadius() {
        Vec3 centre = new Vec3(10, 64, -4);
        int n = 5;
        Vec3 previous = null;
        for (int i = 0; i < n; i++) {
            Vec3 slot = TeamSurround.onRing(centre, TeamSurround.slotAngle(0.7, i, n), 8.0);
            assertEquals(8.0, slot.horizontal().distanceTo(centre.horizontal()), 1e-9);
            assertEquals(64, slot.y(), 1e-9);
            if (previous != null) {
                assertEquals(2 * 8.0 * Math.sin(Math.PI / n), slot.distanceTo(previous), 1e-9);
            }
            previous = slot;
        }
    }

    @Test
    void surroundUsesTunedMinimumAndStillRequiresOneEnemy() {
        BotTuning.Profile original = profile(PresetTuning.root(1));
        assertTrue(TeamSurround.shouldSurround(original, 3, 1));
        assertTrue(TeamSurround.shouldSurround(original, 4, 1));
        assertFalse(TeamSurround.shouldSurround(original, 2, 1));
        assertFalse(TeamSurround.shouldSurround(original, 3, 0));
        assertFalse(TeamSurround.shouldSurround(original, 3, 2));

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.TEAM_SURROUND_MIN_BOTS, 2);
        BotTuning.Profile changed = profile(changedRoot);
        assertTrue(TeamSurround.shouldSurround(changed, 2, 1));
        assertFalse(TeamSurround.shouldSurround(changed, 0, 1));
        assertFalse(TeamSurround.shouldSurround(changed, 2, 2));
    }

    @Test
    void profilesValidateAndInvalidTeamBoundsAreRejected() {
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(PresetTuning.root(1), difficulty.level());
        }

        JSONObject minBots = PresetTuning.root(2);
        set(minBots, TuningKey.TEAM_SURROUND_MIN_BOTS, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(minBots));

        JSONObject arc = PresetTuning.root(3);
        set(arc, TuningKey.TEAM_SURROUND_ARC_DEGREES, 181);
        assertThrows(IllegalArgumentException.class, () -> profile(arc));

        JSONObject maxSwaps = PresetTuning.root(4);
        set(maxSwaps, TuningKey.TEAM_MAX_SWAPS, -1);
        assertThrows(IllegalArgumentException.class, () -> profile(maxSwaps));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
