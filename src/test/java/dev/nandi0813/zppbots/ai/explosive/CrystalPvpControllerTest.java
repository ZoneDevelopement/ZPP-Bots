package dev.nandi0813.zppbots.ai.explosive;

import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalPvpControllerTest {

    @Test
    void blastFallsOffLinearlyAndStopsAtTwelve() {
        Vec3 origin = new Vec3(0, 0, 0);
        assertEquals(1.0, CrystalPvpController.blastAt(origin, origin), 1.0E-9);
        assertEquals(0.5, CrystalPvpController.blastAt(origin, new Vec3(6, 0, 0)), 1.0E-9);
        assertEquals(0.0, CrystalPvpController.blastAt(origin, new Vec3(12, 0, 0)), 1.0E-9);
    }

    @Test
    void behindBonusOnlyForCrystalsBehindABlockingTarget() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        Vec3 target = new Vec3(0, 0, 0);
        Vec3 facing = new Vec3(0, 0, 1);
        assertEquals(0.35, CrystalPvpController.behindBonus(profile, new Vec3(0, 0, -2), target, facing), 1.0E-9);
        assertEquals(0.0, CrystalPvpController.behindBonus(profile, new Vec3(0, 0, 2), target, facing), 1.0E-9);
        assertEquals(0.0, CrystalPvpController.behindBonus(profile, new Vec3(0, 0, -2), target, null), 1.0E-9);
    }

    @Test
    void changedShieldBonusScalesTheBehindCase() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.CRYSTAL_SHIELD_BONUS, 0.5);
        Vec3 target = new Vec3(0, 0, 0);
        Vec3 facing = new Vec3(0, 0, 1);
        assertEquals(0.5, CrystalPvpController.behindBonus(profile(root), new Vec3(0, 0, -2), target, facing), 1.0E-9);
    }

    @Test
    void anchorSpamRoundsSpanMustBePositive() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.ANCHOR_SPAM_ROUNDS_SPAN, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    @Test
    void dangerousOnlyAtBotHeightNearTarget() {
        Vec3 bot = new Vec3(0, 64, 0);
        Vec3 target = new Vec3(3, 64, 0);
        assertTrue(CrystalPvpController.dangerous(bot, new Vec3(2, 64, 0), target, 1.0, 4.5));
        assertFalse(CrystalPvpController.dangerous(bot, new Vec3(2, 66, 0), target, 1.0, 4.5));
        assertFalse(CrystalPvpController.dangerous(bot, new Vec3(-5, 64, 0), target, 1.0, 4.5));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
