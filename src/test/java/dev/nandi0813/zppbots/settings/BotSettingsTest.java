package dev.nandi0813.zppbots.settings;

import static org.junit.jupiter.api.Assertions.*;

import dev.nandi0813.zppbots.tuning.TuningKey;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class BotSettingsTest {

    private static BotSettings withStyle(Behavior.PlayStyleSetting style, long seed) {
        return BotSettings.fromProfile(
                PresetTuning.profile(PresetTuning.root(1), 3),
                3,
                style,
                List.of(Behavior.PlayStyleSetting.values()),
                new Random(seed));
    }

    @Test
    void safeRetreatsEarly() {
        BotSettings settings = withStyle(Behavior.PlayStyleSetting.SAFE, 1);
        assertTrue(settings.retreat());
        assertEquals(7.0, settings.retreatHealthHearts());
    }

    @Test
    void aggressiveNeverRetreats() {
        BotSettings settings = withStyle(Behavior.PlayStyleSetting.AGGRESSIVE, 1);
        assertFalse(settings.retreat());
        assertEquals(2.0, settings.retreatHealthHearts());
    }

    @Test
    void retreatOrderOverridesStyle() {
        BotSettings settings = withStyle(Behavior.PlayStyleSetting.AGGRESSIVE, 1);
        settings.setRetreatOrdered(true);
        assertTrue(settings.retreat());
    }

    @Test
    void rollsAreDeterministicForASeed() {
        BotSettings a = withStyle(Behavior.PlayStyleSetting.DEFAULT, 42);
        BotSettings b = withStyle(Behavior.PlayStyleSetting.DEFAULT, 42);
        for (int i = 0; i < 50; i++) {
            assertEquals(a.roll(), b.roll());
            assertEquals(a.rollJumpReset(), b.rollJumpReset());
            assertEquals(a.rollCounterStun(), b.rollCounterStun());
            assertEquals(a.rollStunSlam(), b.rollStunSlam());
        }
    }

    @Test
    void adaptiveFollowsResolvedStyle() {
        BotSettings settings = withStyle(Behavior.PlayStyleSetting.ADAPTIVE, 1);
        assertEquals(BotSettings.PlayStyle.DEFAULT, settings.playStyle());
        settings.setAdaptiveResolved(BotSettings.PlayStyle.SAFE);
        assertEquals(BotSettings.PlayStyle.SAFE, settings.playStyle());
        assertTrue(settings.retreat());
    }

    @Test
    void behaviorAndTechniqueProfilesRoundTripEveryField() throws ReflectiveOperationException {
        Behavior expected = PresetTuning.behavior();
        Behavior actual = Behavior.fromProfile(
                PresetTuning.profile(PresetTuning.root(1), 3),
                Behavior.PlayStyleSetting.DEFAULT,
                List.of(Behavior.PlayStyleSetting.values()));
        for (var component : Behavior.class.getRecordComponents()) {
            Object expectedValue = component.getAccessor().invoke(expected);
            Object actualValue = component.getAccessor().invoke(actual);
            if (component.getName().equals("techniques")) {
                for (Field field : TechniqueConfig.class.getFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        assertEquals(field.get(expectedValue), field.get(actualValue), field.getName());
                    }
                }
            } else {
                assertEquals(expectedValue, actualValue, component.getName());
            }
        }
        assertEquals(6, actual.playStyleChances().size());
    }

    @Test
    void profilePerturbationsChangeBehaviorAndRetreatRules() {
        var root = PresetTuning.root(1);
        var defaults = root.getJSONObject("defaults");
        defaults.put(TuningKey.BEHAVIOR_USE_POTIONS.key(), false);
        defaults.put(TuningKey.BEHAVIOR_BUNNY_HOP_MODE.key(), 3);
        defaults.put(TuningKey.TECHNIQUE_WC_SELF_RESET.key(), true);
        defaults.put(TuningKey.STYLE_SAFE_RETREAT_ADD.key(), 4.5);
        var profile = PresetTuning.profile(root, 3);
        Behavior behavior = Behavior.fromProfile(
                profile,
                Behavior.PlayStyleSetting.DEFAULT,
                List.of(Behavior.PlayStyleSetting.values()));
        assertFalse(behavior.usePotions());
        assertEquals(BotSettings.BunnyHopMode.SMART, behavior.bunnyHopMode());
        assertTrue(behavior.techniques().wcSelfReset);
        BotSettings safe = BotSettings.fromProfile(
                profile,
                3,
                Behavior.PlayStyleSetting.SAFE,
                List.of(Behavior.PlayStyleSetting.values()),
                new Random(1));
        assertEquals(8.5, safe.retreatHealthHearts());
    }

    @Test
    void profileFactoryRejectsMissingRequiredKeysAndNullProfile() {
        var root = PresetTuning.root(1);
        root.getJSONObject("defaults").remove(TuningKey.BEHAVIOR_USE_POTIONS.key());
        assertThrows(IllegalArgumentException.class, () -> PresetTuning.profile(root, 3));
        assertThrows(
                NullPointerException.class,
                () -> BotSettings.fromProfile(
                        null,
                        3,
                        Behavior.PlayStyleSetting.DEFAULT,
                        List.of(Behavior.PlayStyleSetting.values()),
                        new Random(1)));
    }
}
