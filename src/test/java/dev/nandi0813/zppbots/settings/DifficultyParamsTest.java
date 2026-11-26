package dev.nandi0813.zppbots.settings;

import org.junit.jupiter.api.Test;

import dev.nandi0813.zppbots.tuning.TuningKey;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.*;

final class DifficultyParamsTest {

    @Test
    void easyPreset() {
        DifficultyParams p = PresetTuning.forPreset(Difficulty.EASY);
        assertEquals(0.35, p.missChance);
        assertEquals(6, p.reactionMinTicks);
        assertEquals(14, p.reactionMaxTicks);
        assertEquals(20, p.potIntervalTicks());
        assertEquals(30, p.webDrainDelayTicks());
        assertEquals(80, p.cartSkipChance);
    }

    @Test
    void perfectPreset() {
        DifficultyParams p = PresetTuning.forPreset(Difficulty.PERFECT);
        assertEquals(0.0, p.missChance);
        assertEquals(0, p.reactionMinTicks);
        assertEquals(1, p.potIntervalTicks());
        assertEquals(2, p.webDrainDelayTicks());
        assertEquals(5, p.crystalPvpLevel);
    }

    @Test
    void copyIsIndependent() {
        DifficultyParams p = PresetTuning.forPreset(Difficulty.HARD);
        DifficultyParams copy = p.copy();
        copy.accuracy = 0.1;
        assertEquals(0.75, p.accuracy);
    }

    @Test
    void levelsMapToDifficulties() {
        assertEquals(Difficulty.HARD, Difficulty.ofLevel(3));
        assertEquals(5, Difficulty.PERFECT.level());
        assertThrows(IllegalArgumentException.class, () -> Difficulty.ofLevel(0));
        assertThrows(IllegalArgumentException.class, () -> Difficulty.ofLevel(6));
    }

    @Test
    void presetsRoundTripThroughProfilesAndKeysCoverRegistry() throws IllegalAccessException {
        JSONObject root = PresetTuning.root(1);
        for (Difficulty difficulty : Difficulty.values()) {
            DifficultyParams expected = PresetTuning.forPreset(difficulty);
            DifficultyParams actual = DifficultyParams.fromProfile(
                    PresetTuning.profile(root, difficulty.level()));
            Set<String> keys = root.getJSONObject("difficulty")
                    .getJSONObject(Integer.toString(difficulty.level())).keySet();
            Set<String> registeredKeys =
                    java.util.Arrays.stream(TuningKey.values())
                            .map(TuningKey::key)
                            .filter(key -> key.startsWith("settings.difficulty."))
                            .collect(Collectors.toSet());
            assertEquals(registeredKeys, keys);
            for (Field field : DifficultyParams.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    assertEquals(field.get(expected), field.get(actual), field.getName());
                }
            }
        }
    }

    @Test
    void profileOverridesOnlySelectedDifficulty() {
        JSONObject root = PresetTuning.root(1);
        JSONObject levelThree = root.getJSONObject("difficulty").getJSONObject("3");
        levelThree.put(TuningKey.ACCURACY.key(), 0.25);
        levelThree.put(TuningKey.REACTION_MIN_TICKS.key(), 2);
        DifficultyParams changed = DifficultyParams.fromProfile(PresetTuning.profile(root, 3));
        DifficultyParams other = DifficultyParams.fromProfile(PresetTuning.profile(root, 2));
        assertEquals(0.25, changed.accuracy);
        assertEquals(2, changed.reactionMinTicks);
        assertEquals(PresetTuning.forPreset(Difficulty.MEDIUM).accuracy, other.accuracy);
        assertEquals(PresetTuning.forPreset(Difficulty.MEDIUM).reactionMinTicks, other.reactionMinTicks);
    }

    @Test
    void rejectsNullProfile() {
        assertThrows(NullPointerException.class, () -> DifficultyParams.fromProfile(null));
    }

}
