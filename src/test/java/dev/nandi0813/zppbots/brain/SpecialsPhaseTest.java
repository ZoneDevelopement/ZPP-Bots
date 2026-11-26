package dev.nandi0813.zppbots.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nandi0813.zppbots.brain.SpecialsPhase.CpvpPick;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class SpecialsPhaseTest {

    private static final BotTuning.Profile MEDIUM = profile(PresetTuning.root(1));

    private static CpvpPick pick(boolean chainHp, boolean high, int tick, int afterChain, boolean anchorAfter, int sinceAnchor, int sinceCrystal, boolean prefer) {
        return pick(MEDIUM, chainHp, high, tick, afterChain, anchorAfter, sinceAnchor, sinceCrystal, prefer);
    }

    private static CpvpPick pick(BotTuning.Profile profile, boolean chainHp, boolean high, int tick, int afterChain, boolean anchorAfter, int sinceAnchor, int sinceCrystal, boolean prefer) {
        return SpecialsPhase.pickCpvp(profile, chainHp, high, tick, afterChain, anchorAfter, sinceAnchor, sinceCrystal, () -> prefer);
    }

    @Test
    void highGroundAlwaysCrystal() {
        assertEquals(CpvpPick.CRYSTAL, pick(true, true, 100, 0, true, 0, 0, true));
    }

    @Test
    void crystalWindowAfterChain() {
        assertEquals(CpvpPick.CRYSTAL, pick(true, false, 10, 70, false, 0, 0, true));
    }

    @Test
    void chainWhenTargetHealthy() {
        assertEquals(CpvpPick.CHAIN, pick(true, false, 100, 0, false, 999, 999, false));
        assertEquals(CpvpPick.CHAIN, pick(true, false, 100, 0, true, 999, 999, false));
        assertEquals(CpvpPick.ANCHOR, pick(false, false, 100, 0, true, 999, 999, false));
    }

    @Test
    void mirrorsRecentOpponentTool() {
        assertEquals(CpvpPick.ANCHOR, pick(false, false, 100, 0, false, 20, 30, false));
        assertEquals(CpvpPick.CRYSTAL, pick(false, false, 100, 0, false, 40, 30, true));
    }

    @Test
    void fallsBackToPreference() {
        assertEquals(CpvpPick.ANCHOR, pick(false, false, 100, 0, false, 999, 999, true));
        assertEquals(CpvpPick.CRYSTAL, pick(false, false, 100, 0, false, 999, 999, false));
    }

    @Test
    void anchorScoreAddsAndSubtracts() {
        assertEquals(0.97, SpecialsPhase.anchorScore(MEDIUM, true, 20.0F, 0.0, false), 1e-9);
        assertEquals(0.7, SpecialsPhase.anchorScore(MEDIUM, false, 10.0F, 1.0, false), 1e-9);
        assertEquals(0.75, SpecialsPhase.anchorScore(MEDIUM, true, 20.0F, 1.0, true), 1e-9);
    }

    @Test
    void recentToolWindowReadsFromProfile() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.SPECIALS_CPVP_RECENT_TOOL_TICKS, 10);
        assertEquals(CpvpPick.CRYSTAL, pick(profile(root), false, false, 100, 0, false, 20, 30, false));
        assertEquals(CpvpPick.ANCHOR, pick(MEDIUM, false, false, 100, 0, false, 20, 30, false));
    }

    @Test
    void anchorBaseReadsFromProfile() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.SPECIALS_ANCHOR_BASE, 0.5);
        assertEquals(0.5, SpecialsPhase.anchorScore(profile(root), false, 10.0F, 1.0, false), 1e-9);
    }

    @Test
    void boundsAreEnforced() {
        JSONObject hpFraction = PresetTuning.root(4);
        set(hpFraction, TuningKey.SPECIALS_CHAIN_HP_FRACTION, 1.5);
        assertThrows(IllegalArgumentException.class, () -> profile(hpFraction));

        JSONObject strafeSpan = PresetTuning.root(5);
        set(strafeSpan, TuningKey.SPECIALS_CPVP_STRAFE_TICKS_SPAN, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(strafeSpan));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
