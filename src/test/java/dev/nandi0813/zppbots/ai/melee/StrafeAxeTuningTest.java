package dev.nandi0813.zppbots.ai.melee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.settings.Difficulty;
import dev.nandi0813.zppbots.settings.PresetTuning;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.util.Random;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

final class StrafeAxeTuningTest {

    @Test
    void originalStrafeUsesTunedRandomSequenceAndValues() {
        int seed = 7319;
        BotContext context = context(PresetTuning.root(1), seed);
        StrafeController strafe = new StrafeController(context);
        Random expected = new Random(seed);
        expected.nextInt(11);

        float firstJitter = (expected.nextFloat() - 0.5F) * 6.0F * 2.0F;
        assertEquals(40.0F + firstJitter, strafe.getYawOffset(0, 40.0F));

        float attack = 0.3F + expected.nextFloat() * 0.25F;
        assertEquals(attack, strafe.attackTickStrafe());
        strafe.onAttack();
        expected.nextInt(11);

        float nextJitter = (expected.nextFloat() - 0.5F) * 6.0F * 2.0F;
        assertEquals(-40.0F + nextJitter, strafe.getYawOffset(1, 40.0F));
    }

    @Test
    void changedStrafeProfileControlsFlipAttackAndBurst() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.STRAFE_FLIP_MIN, 3);
        set(root, TuningKey.STRAFE_FLIP_SPAN, 1);
        set(root, TuningKey.STRAFE_JITTER, 0);
        set(root, TuningKey.STRAFE_ATTACK_BASE, 0.8);
        set(root, TuningKey.STRAFE_ATTACK_SPAN, 0);
        set(root, TuningKey.STRAFE_BURST_ANGLE, 60);
        set(root, TuningKey.STRAFE_BURST_MIN, 2);
        set(root, TuningKey.STRAFE_BURST_SPAN, 1);

        StrafeController strafe = new StrafeController(context(root, 7319));
        assertEquals(40.0F, strafe.getYawOffset(0, 40.0F));
        assertEquals(40.0F, strafe.getYawOffset(1, 40.0F));
        assertEquals(-40.0F, strafe.getYawOffset(2, 40.0F));
        assertEquals(-0.8F, strafe.attackTickStrafe());

        strafe.triggerBurst();
        assertEquals(60.0F, Math.abs(strafe.getYawOffset(3, 40.0F)));
        assertTrue(strafe.isBursting());
        assertEquals(60.0F, Math.abs(strafe.getYawOffset(4, 40.0F)));
        assertFalse(strafe.isBursting());
    }

    @Test
    void axePunishWindowUsesCapturedDurationAndReset() {
        AxeController original = new AxeController(context(PresetTuning.root(1), 1));
        original.onDisableSwung();
        for (int i = 0; i < 29; i++) {
            assertTrue(original.punishActive());
            original.tickWindows();
        }
        assertTrue(original.punishActive());
        original.tickWindows();
        assertFalse(original.punishActive());

        JSONObject changedRoot = PresetTuning.root(2);
        set(changedRoot, TuningKey.AXE_PUNISH_TICKS, 4);
        AxeController changed = new AxeController(context(changedRoot, 2));
        changed.onDisableSwung();
        for (int i = 0; i < 3; i++) {
            assertTrue(changed.punishActive());
            changed.tickWindows();
        }
        assertTrue(changed.punishActive());
        changed.tickWindows();
        assertFalse(changed.punishActive());

        changed.onDisableSwung();
        assertTrue(changed.punishActive());
        changed.reset();
        assertFalse(changed.punishActive());
    }

    @Test
    void allProfilesValidateAndStrafeBoundsAreEnforced() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(root, difficulty.level());
        }

        JSONObject flipSpan = PresetTuning.root(2);
        set(flipSpan, TuningKey.STRAFE_FLIP_SPAN, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(flipSpan));

        JSONObject attackBase = PresetTuning.root(3);
        set(attackBase, TuningKey.STRAFE_ATTACK_BASE, 1.01);
        assertThrows(IllegalArgumentException.class, () -> profile(attackBase));
    }

    private static BotContext context(JSONObject root, long seed) {
        BotContext context = new BotContext(null, null, null, null, new Random(seed), null);
        context.setTuning(profile(root));
        return context;
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }
}
