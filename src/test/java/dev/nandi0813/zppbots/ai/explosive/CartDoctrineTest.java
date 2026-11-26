package dev.nandi0813.zppbots.ai.explosive;

import dev.nandi0813.zppbots.ai.explosive.CartDoctrine.Reason;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartDoctrineTest {

    @Test
    void relentlessIgnoresHealth() {
        BotTuning.Profile p = profile(PresetTuning.root(1));
        assertEquals(Reason.RELENTLESS, CartDoctrine.decide(p, false, 2, 20, 1, 0, 0));
    }

    @Test
    void finishWinsOverEverythingElseWhenGuiding() {
        BotTuning.Profile p = profile(PresetTuning.root(1));
        assertEquals(Reason.FINISH, CartDoctrine.decide(p, true, 5, 8, 1, 0, 0));
    }

    @Test
    void punishWindowsAreOrdered() {
        BotTuning.Profile p = profile(PresetTuning.root(1));
        assertEquals(Reason.PUNISH_PEARL, CartDoctrine.decide(p, true, 20, 20, 5, 40, 0));
        assertEquals(Reason.PUNISH_MISS, CartDoctrine.decide(p, true, 20, 20, 5, 41, 30));
    }

    @Test
    void lowAndLosingDoesNotCart() {
        BotTuning.Profile p = profile(PresetTuning.root(1));
        assertEquals(Reason.NONE, CartDoctrine.decide(p, true, 15, 16, 5, 99, 99));
    }

    @Test
    void closeAndNotAheadDoesNotCartButOpenGapDoes() {
        BotTuning.Profile p = profile(PresetTuning.root(1));
        assertEquals(Reason.NONE, CartDoctrine.decide(p, true, 20, 20, 3, 99, 99));
        assertEquals(Reason.OPEN_GAP, CartDoctrine.decide(p, true, 20, 20, 5, 99, 99));
    }

    @Test
    void changedFinishHpTurnsFinishIntoPunishPearl() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.CART_DOCTRINE_FINISH_HP, 4.0);
        assertEquals(Reason.PUNISH_PEARL, CartDoctrine.decide(profile(root), true, 5, 8, 1, 0, 0));
    }

    @Test
    void crystalSkillLevelClampsAndRollRespectsZeroChance() {
        assertEquals(1, CrystalSkill.clampLevel(-3));
        assertEquals(5, CrystalSkill.clampLevel(99));
        assertFalse(CrystalSkill.roll(new Random(1), 0.0));
        assertTrue(CrystalSkill.roll(new Random(1), 1.0));
    }

    @Test
    void crystalSkillFumbleBoundIsEnforced() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.CRYSTAL_SKILL_FUMBLE_L2, 1.5);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
