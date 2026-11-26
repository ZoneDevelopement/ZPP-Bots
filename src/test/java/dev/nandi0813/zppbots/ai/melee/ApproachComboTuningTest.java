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

final class ApproachComboTuningTest {

    @Test
    void originalApproachSprintHopAndPauseThresholdsArePreserved() {
        ApproachStyle approach = new ApproachStyle(context(PresetTuning.root(1)));
        assertTrue(approach.shouldSprint(5, 50));
        assertFalse(approach.shouldSprint(5.1, 50));
        assertTrue(approach.shouldSprint(11, 0));
        assertFalse(approach.shouldSprint(11.1, 0));
        assertTrue(approach.shouldHop(10));
        assertFalse(approach.shouldHop(10.1));

        approach.tick(6, 50, true);
        assertTrue(approach.paused());
        assertFalse(approach.shouldSprint(5, 50));
        assertFalse(approach.shouldHop(10));
        approach.tick(6, 50, false);
        assertFalse(approach.paused());
    }

    @Test
    void changedApproachProfileControlsSprintHopAndPause() {
        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.APPROACH_SPRINT_BASE, 20);
        set(root, TuningKey.APPROACH_SPRINT_SKILL, 0);
        set(root, TuningKey.APPROACH_HOP_DISTANCE, 2);
        set(root, TuningKey.APPROACH_PAUSE_DISTANCE, 10);

        ApproachStyle approach = new ApproachStyle(context(root));
        assertTrue(approach.shouldSprint(20, 50));
        assertFalse(approach.shouldSprint(20.1, 50));
        assertTrue(approach.shouldHop(2));
        assertFalse(approach.shouldHop(2.1));

        approach.tick(6, 50, true);
        assertFalse(approach.paused());
        approach.tick(11, 50, true);
        assertTrue(approach.paused());
    }

    @Test
    void allProfilesValidateAndApproachComboBoundsAreEnforced() {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            PresetTuning.profile(root, difficulty.level());
        }

        JSONObject taperWidth = PresetTuning.root(2);
        set(taperWidth, TuningKey.APPROACH_TAPER_WIDTH, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(taperWidth));

        JSONObject pauseChance = PresetTuning.root(3);
        set(pauseChance, TuningKey.APPROACH_PAUSE_CHANCE_IN, 0);
        assertThrows(IllegalArgumentException.class, () -> profile(pauseChance));

        JSONObject jumpCooldown = PresetTuning.root(4);
        set(jumpCooldown, TuningKey.COMBO_BREAK_JUMP_COOLDOWN, -1);
        assertThrows(IllegalArgumentException.class, () -> profile(jumpCooldown));

        JSONObject comboLink = PresetTuning.root(5);
        set(comboLink, TuningKey.FIGHT_COMBO_LINK_TICKS, -1);
        assertThrows(IllegalArgumentException.class, () -> profile(comboLink));

        JSONObject opening = PresetTuning.root(6);
        set(opening, TuningKey.FIGHT_OPENING_TICKS, 72001);
        assertThrows(IllegalArgumentException.class, () -> profile(opening));
    }

    @Test
    void fightComboThresholdsReadFromTuning() {
        FightState fight = new FightState(context(PresetTuning.root(1)));
        assertFalse(fight.comboing());
        assertFalse(fight.beingCombod());

        JSONObject root = PresetTuning.root(2);
        set(root, TuningKey.FIGHT_COMBO_MIN_HITS, 0);
        fight = new FightState(context(root));
        assertTrue(fight.comboing());
        assertTrue(fight.beingCombod());
        fight.tick(null);
        assertTrue(fight.comboing());
        assertTrue(fight.beingCombod());
        assertFalse(fight.inFight());
        assertFalse(fight.isOpening());
        assertFalse(fight.inEncounter());
    }

    private static BotContext context(JSONObject root) {
        BotContext context = new BotContext(null, null, null, null, new ZeroRandom(), null);
        context.setTuning(profile(root));
        return context;
    }

    private static BotTuning.Profile profile(JSONObject root) {
        return PresetTuning.profile(root, Difficulty.MEDIUM.level());
    }

    private static void set(JSONObject root, TuningKey key, Object value) {
        root.getJSONObject("defaults").put(key.key(), value);
    }

    private static final class ZeroRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public double nextDouble() {
            return 0.0;
        }
    }
}
