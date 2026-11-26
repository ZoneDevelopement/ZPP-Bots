package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.json.JSONObject;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PathFollowerTest {

    @Test
    void strafeInputIsRelativeToFacing() {
        float[] south = PathFollower.strafeInput(0.0, 0.0, 1.0);
        assertEquals(1.0F, south[0], 1e-6F);
        assertEquals(0.0F, south[1], 1e-6F);
        float[] sideways = PathFollower.strafeInput(0.0, 1.0, 0.0);
        assertEquals(0.0F, sideways[0], 1e-6F);
        assertEquals(1.0F, sideways[1], 1e-6F);
    }

    @Test
    void waterHeightFollowsLevel() {
        assertEquals(8.0 / 9.0, PathFollower.levelHeight(0), 1e-9);
        assertEquals(1.0 / 9.0, PathFollower.levelHeight(7), 1e-9);
        assertEquals(8.0 / 9.0, PathFollower.levelHeight(9), 1e-9);
    }

    @Test
    void doorsFaceTheBotAndIronStaysShut() {
        assertEquals(Direction.EAST, PathFollower.doorFace(1.2, 0.3));
        assertEquals(Direction.NORTH, PathFollower.doorFace(0.1, -1.0));
        assertFalse(PathFollower.canOpenByHand(Material.IRON_DOOR));
        assertTrue(PathFollower.canOpenByHand(Material.OAK_DOOR));
    }

    @Test
    void yawToInputIsRelativeToCurrentYaw() {
        assertArrayEquals(new float[]{1.0F, 0.0F}, PathFollower.yawToInput(30.0F, 30.0F), 1e-6F);
        float[] left = PathFollower.yawToInput(0.0F, 90.0F);
        assertEquals(0.0F, left[0], 1e-6F);
        assertEquals(-1.0F, left[1], 1e-6F);
        assertArrayEquals(new float[]{-1.0F, 0.0F}, PathFollower.yawToInput(0.0F, 180.0F), 1e-6F);
        float[] diag = PathFollower.yawToInput(0.0F, 45.0F);
        assertEquals(0.70710678F, diag[0], 1e-5F);
        assertEquals(-0.70710678F, diag[1], 1e-5F);
    }
    @Test
    void progressAndKnockbackKeepStrictOriginalBoundariesAndRespondToTuning() {
        BotTuning.Profile original = PresetTuning.profile(PresetTuning.root(1), 3);
        assertTrue(PathFollower.madeProgress(original, 9.649, 10.0));
        assertFalse(PathFollower.madeProgress(original, 9.65, 10.0));
        assertFalse(PathFollower.madeProgress(original, Double.NaN, 10.0));
        assertFalse(PathFollower.madeProgress(original, 9.0, Double.NaN));
        assertFalse(PathFollower.knockedBack(original, 0.6, 4));
        assertTrue(PathFollower.knockedBack(original, 0.6001, 4));
        assertFalse(PathFollower.knockedBack(original, 0.7, 5));
        assertFalse(PathFollower.knockedBack(original, Double.NaN, 0));

        JSONObject changedRoot = PresetTuning.root(2);
        JSONObject defaults = changedRoot.getJSONObject("defaults");
        defaults.put(TuningKey.FOLLOWER_PROGRESS_EPS.key(), 0.5);
        defaults.put(TuningKey.FOLLOWER_KNOCKBACK_VEL.key(), 0.8);
        defaults.put(TuningKey.FOLLOWER_KNOCK_TICKS.key(), 6);
        BotTuning.Profile changed = PresetTuning.profile(changedRoot, 3);
        assertTrue(PathFollower.madeProgress(changed, 9.49, 10.0));
        assertFalse(PathFollower.madeProgress(changed, 9.5, 10.0));
        assertFalse(PathFollower.knockedBack(changed, 0.8, 4));
        assertTrue(PathFollower.knockedBack(changed, 0.8001, 5));
        assertFalse(PathFollower.knockedBack(changed, 0.9, 6));
    }

    @Test
    void swimPitchAndWaterDistancesUseTheirOwnTuningKeys() {
        BotTuning.Profile original = PresetTuning.profile(PresetTuning.root(1), 3);
        assertEquals(85.0F, PathFollower.swimPitch(original, 100.0F));
        assertEquals(-85.0F, PathFollower.swimPitch(original, -100.0F));
        assertEquals(20.0F, PathFollower.swimPitch(original, 20.0F));
        assertEquals(3.0, PathFollower.waterMinDist(original, true));
        assertEquals(1.2, PathFollower.waterMinDist(original, false));

        JSONObject changedRoot = PresetTuning.root(2);
        JSONObject defaults = changedRoot.getJSONObject("defaults");
        defaults.put(TuningKey.FOLLOWER_SWIM_PITCH_LIMIT.key(), 60.0);
        defaults.put(TuningKey.FOLLOWER_WATER_MIN_COMBAT.key(), 5.0);
        defaults.put(TuningKey.FOLLOWER_WATER_MIN_TRAVEL.key(), 0.5);
        BotTuning.Profile changed = PresetTuning.profile(changedRoot, 3);
        assertEquals(60.0F, PathFollower.swimPitch(changed, 100.0F));
        assertEquals(-60.0F, PathFollower.swimPitch(changed, -100.0F));
        assertEquals(5.0, PathFollower.waterMinDist(changed, true));
        assertEquals(0.5, PathFollower.waterMinDist(changed, false));
    }

    @Test
    void allFiveProfilesValidateAndInvalidFollowerScanValuesAreRejected() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty level : Difficulty.values()) {
            PresetTuning.profile(root, level.level());
        }

        JSONObject waterStep = PresetTuning.root(2);
        waterStep.getJSONObject("defaults").put(TuningKey.FOLLOWER_WATER_SCAN_STEP.key(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> PresetTuning.profile(waterStep, 3));

        JSONObject bankStep = PresetTuning.root(3);
        bankStep.getJSONObject("defaults").put(TuningKey.FOLLOWER_BANK_SCAN_STEP.key(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> PresetTuning.profile(bankStep, 3));

        JSONObject swimDenominator = PresetTuning.root(4);
        swimDenominator.getJSONObject("defaults").put(TuningKey.FOLLOWER_SWIM_PITCH_MIN_HORIZ.key(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> PresetTuning.profile(swimDenominator, 3));
    }

}
