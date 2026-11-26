package dev.nandi0813.zppbots.ai.melee;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ShieldControllerTest {

    @Test
    void blockhitDropsBeyondShieldRange() {
        BotTuning.Profile profile = PresetTuning.profile(PresetTuning.root(1), Difficulty.MEDIUM.level());
        assertFalse(ShieldController.blockhitReleasesFar(profile, 3.0));
        assertFalse(ShieldController.blockhitReleasesFar(profile, 4.5));
        assertTrue(ShieldController.blockhitReleasesFar(profile, 7.0));
    }

    @Test
    void changedShieldRangeMovesBlockhitRelease() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.SHIELD_RANGE, 6.0);
        BotTuning.Profile profile = profile(root);
        assertFalse(ShieldController.blockhitReleasesFar(profile, 6.0));
        assertTrue(ShieldController.blockhitReleasesFar(profile, 6.01));
    }

    @Test
    void shieldAndSwapBoundsAreEnforced() {
        JSONObject shieldRange = PresetTuning.root(3);
        set(shieldRange, TuningKey.SHIELD_RANGE, 65);
        assertThrows(IllegalArgumentException.class, () -> profile(shieldRange));

        JSONObject minCharge = PresetTuning.root(4);
        set(minCharge, TuningKey.SWAP_MIN_CHARGE, 1.01);
        assertThrows(IllegalArgumentException.class, () -> profile(minCharge));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
