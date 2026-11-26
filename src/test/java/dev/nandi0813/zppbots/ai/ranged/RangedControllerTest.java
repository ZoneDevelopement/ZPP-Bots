package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.Material;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RangedControllerTest {

    @Test
    void stopReleasesOnlyRangedDraws() {
        assertTrue(RangedController.releasesOnStop(true, false, Material.AIR));
        assertTrue(RangedController.releasesOnStop(false, true, Material.BOW));
        assertTrue(RangedController.releasesOnStop(false, true, Material.CROSSBOW));
        assertFalse(RangedController.releasesOnStop(false, true, Material.POTION));
        assertFalse(RangedController.releasesOnStop(false, true, Material.GOLDEN_APPLE));
    }

    @Test
    void bowLockHoldsUntilItExpiresOrTheTargetFlees() {
        assertTrue(RangedController.bowLockActive(100, 180, 12.0, 20.0));
        assertFalse(RangedController.bowLockActive(180, 180, 12.0, 20.0));
        assertFalse(RangedController.bowLockActive(100, 180, 21.0, 20.0));
    }

    @Test
    void twoShotsSpendTheVolley() {
        assertFalse(RangedController.volleySpent(1, 2));
        assertTrue(RangedController.volleySpent(2, 2));
    }

    @Test
    void aimErrorScalesWithAccuracy() {
        BotTuning.Profile profile = profile(PresetTuning.root(1));
        assertEquals(0.0F, RangedController.aimError(profile, 1.0, 2.0), 1.0E-6F);
        assertEquals(8.0F, RangedController.aimError(profile, 0.0, 1.0), 1.0E-6F);
        assertEquals(-2.0F, RangedController.aimError(profile, 0.75, -1.0), 1.0E-6F);
    }

    @Test
    void changedAimErrorControlsSpread() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.RANGED_AIM_ERROR_DEG, 4);
        assertEquals(4.0F, RangedController.aimError(profile(root), 0.0, 1.0), 1.0E-6F);
    }

    @Test
    void rangedBoundsAreEnforced() {
        JSONObject root = PresetTuning.root(3);
        set(root, TuningKey.RANGED_FACING_TOLERANCE, 181);
        assertThrows(IllegalArgumentException.class, () -> profile(root));
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
