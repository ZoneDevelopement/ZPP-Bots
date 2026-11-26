package dev.nandi0813.zppbots.ai.move;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class ChaseAnchorTuningTest {
    @Test
    void scalePreservesOriginalFadeBoundaries() {
        BotTuning.Profile profile = PresetTuning.profile(PresetTuning.root(1), 3);

        assertEquals(0.0, ChaseSpread.scale(profile, 7.0));
        assertEquals(0.5, ChaseSpread.scale(profile, 12.0));
        assertEquals(1.0, ChaseSpread.scale(profile, 17.0));
    }

    @Test
    void slotsPreserveOriginalRowSpacingAndRankGap() {
        BotTuning.Profile profile = PresetTuning.profile(PresetTuning.root(1), 3);

        assertArrayEquals(new double[] {-2.2, 0.0}, ChaseSpread.slotFor(profile, 3, 0, 0), 1.0E-9);
        assertArrayEquals(new double[] {0.0, 0.0}, ChaseSpread.slotFor(profile, 3, 1, 0), 1.0E-9);
        assertArrayEquals(new double[] {2.2, 5.0}, ChaseSpread.slotFor(profile, 3, 2, 2), 1.0E-9);
    }

    @Test
    void profilePerturbationsChangeChaseAndAnchorHelpers() {
        BotTuning.Profile original = PresetTuning.profile(PresetTuning.root(1), 3);
        JSONObject changedRoot = PresetTuning.root(2);
        JSONObject defaults = changedRoot.getJSONObject("defaults");
        defaults.put(TuningKey.CHASE_NONE_AT.key(), 5.0);
        defaults.put(TuningKey.CHASE_FADE_SPAN.key(), 20.0);
        defaults.put(TuningKey.CHASE_SPACING.key(), 3.0);
        defaults.put(TuningKey.CHASE_RANK_GAP.key(), 4.0);
        defaults.put(TuningKey.ANCHOR_MOVING_SPEED_SQR.key(), 1.0);
        defaults.put(TuningKey.ANCHOR_REANCHOR_DIST_SQR.key(), 100.0);
        defaults.put(TuningKey.ANCHOR_STALE_AFTER_TICKS.key(), 50);
        BotTuning.Profile changed = PresetTuning.profile(changedRoot, 3);

        assertNotEquals(ChaseSpread.scale(original, 12.0), ChaseSpread.scale(changed, 12.0));
        assertArrayEquals(new double[] {-3.0, 0.0}, ChaseSpread.slotFor(changed, 3, 0, 0), 1.0E-9);
        assertArrayEquals(new double[] {0.0, 8.0}, ChaseSpread.slotFor(changed, 3, 1, 2), 1.0E-9);
        assertFalse(FormationAnchor.reusable(original, 0.5, 0.0, 0));
        assertTrue(FormationAnchor.reusable(changed, 0.5, 0.0, 0));
        assertFalse(FormationAnchor.reusable(original, 0.0, 90.0, 0));
        assertTrue(FormationAnchor.reusable(changed, 0.0, 90.0, 0));
        assertFalse(FormationAnchor.reusable(original, 0.0, 0.0, 49));
        assertTrue(FormationAnchor.reusable(changed, 0.0, 0.0, 49));
    }

    @Test
    void anchorReusePreservesExactThresholdsAndReverseTimeBehavior() {
        BotTuning.Profile profile = PresetTuning.profile(PresetTuning.root(1), 3);

        assertTrue(FormationAnchor.reusable(profile, 0.0025, 64.0, 40));
        assertFalse(FormationAnchor.reusable(profile, 0.0025001, 0.0, 0));
        assertFalse(FormationAnchor.reusable(profile, 0.0, 64.0001, 0));
        assertFalse(FormationAnchor.reusable(profile, 0.0, 0.0, 41));
        assertTrue(FormationAnchor.reusable(profile, 0.0, 0.0, -1));
    }

    @Test
    void allFiveProfilesValidateAndInvalidPerRankOrFadeSpanAreRejected() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty level : Difficulty.values()) {
            PresetTuning.profile(root, level.level());
        }

        JSONObject zeroPerRank = PresetTuning.root(2);
        zeroPerRank.getJSONObject("defaults").put(TuningKey.CHASE_PER_RANK.key(), 0);
        assertThrows(IllegalArgumentException.class, () -> PresetTuning.profile(zeroPerRank, 3));

        JSONObject zeroFadeSpan = PresetTuning.root(3);
        zeroFadeSpan.getJSONObject("defaults").put(TuningKey.CHASE_FADE_SPAN.key(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> PresetTuning.profile(zeroFadeSpan, 3));
    }
}
