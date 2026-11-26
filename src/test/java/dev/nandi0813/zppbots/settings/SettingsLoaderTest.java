package dev.nandi0813.zppbots.settings;

import dev.nandi0813.zppbots.settings.Behavior.PlayStyleSetting;
import dev.nandi0813.zppbots.tuning.TuningKey;
import java.io.File;
import java.util.List;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SettingsLoaderTest {
    private static YamlConfiguration shipped() {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
    }

    @Test
    void shippedConfigContainsOnlyValidLocalChoicesAndCreatesProfileSettings() {
        SettingsLoader loader = SettingsLoader.load(shipped());
        assertNull(loader.error());
        assertEquals(3, loader.defaultLevel());
        assertEquals(PlayStyleSetting.DEFAULT, loader.defaultStyle());
        assertEquals(List.of(PlayStyleSetting.values()), loader.allowedStyles());
        JSONObject root = PresetTuning.root(1);
        for (int level = 1; level <= 5; level++) {
            BotSettings settings = loader.create(level, new Random(1), PresetTuning.profile(root, level));
            assertEquals(Difficulty.ofLevel(level), settings.difficulty());
            assertEquals(PresetTuning.forPreset(Difficulty.ofLevel(level)).accuracy,
                    settings.difficultyParams().accuracy);
        }
    }

    @Test
    void localTechniqueOverridesApplyAndAbsentKeysKeepProfileValues() {
        JSONObject root = PresetTuning.root(1);
        var profile = PresetTuning.profile(root, 3);
        YamlConfiguration config = shipped();
        config.set("BEHAVIOR.TECHNIQUES.MACE-SWAP", true);
        config.set("BEHAVIOR.TECHNIQUES.SPEAR-DASH", false);
        config.set("BEHAVIOR.USE-POTIONS", false);
        SettingsLoader loader = SettingsLoader.load(config);
        BotSettings settings = loader.create(3, new Random(1), profile);
        assertTrue(settings.techniques().maceSwap);
        assertFalse(settings.techniques().spearDash);
        assertEquals(profile.flag(TuningKey.BEHAVIOR_USE_POTIONS), settings.usePotions());
        assertEquals(profile.flag(TuningKey.TECHNIQUE_WINDCHARGE_MACE), settings.techniques().windchargeMace);

        BotSettings untouched = SettingsLoader.load(shipped()).create(3, new Random(1), profile);
        assertEquals(profile.flag(TuningKey.TECHNIQUE_MACE_SWAP), untouched.techniques().maceSwap);
    }

    @Test
    void randomStyleUsesCapturedWeights() {
        JSONObject root = PresetTuning.root(1);
        JSONObject defaults = root.getJSONObject("defaults");
        for (BotSettings.PlayStyle style : BotSettings.PlayStyle.values()) {
            defaults.put("settings.styleWeight." + style.name(), style == BotSettings.PlayStyle.AGGRESSIVE ? 100 : 0);
        }
        YamlConfiguration config = shipped();
        config.set("BEHAVIOR.PLAY-STYLE", "RANDOM");
        BotSettings settings = SettingsLoader.load(config).create(1, new Random(7), PresetTuning.profile(root, 1));
        assertEquals(BotSettings.PlayStyle.AGGRESSIVE, settings.playStyle());
    }

    @Test
    void rejectsInvalidLevelsStylesAndStyleLists() {
        YamlConfiguration config = shipped();
        config.set("DEFAULT-DIFFICULTY", 6);
        assertTrue(SettingsLoader.load(config).error().contains("DEFAULT-DIFFICULTY"));
        config = shipped();
        config.set("DEFAULT-DIFFICULTY", "3");
        assertTrue(SettingsLoader.load(config).error().contains("DEFAULT-DIFFICULTY"));
        config = shipped();
        config.set("BEHAVIOR.PLAY-STYLE", "UNKNOWN");
        assertTrue(SettingsLoader.load(config).error().contains("PLAY-STYLE"));
        config = shipped();
        config.set("BEHAVIOR.PLAYER-PLAY-STYLES", List.of());
        assertTrue(SettingsLoader.load(config).error().contains("PLAYER-PLAY-STYLES"));
        config.set("BEHAVIOR.PLAYER-PLAY-STYLES", List.of("DEFAULT", "DEFAULT"));
        assertTrue(SettingsLoader.load(config).error().contains("duplicates"));
        config.set("BEHAVIOR.PLAYER-PLAY-STYLES", List.of("DEFAULT", "WILD"));
        assertTrue(SettingsLoader.load(config).error().contains("PLAYER-PLAY-STYLES"));
        YamlConfiguration invalid = shipped();
        invalid.set("BEHAVIOR.PLAYER-PLAY-STYLES", List.of("SAFE", "AGGRESSIVE"));
        SettingsLoader invalidLoader = SettingsLoader.load(invalid);
        assertTrue(invalidLoader.error().contains("PLAY-STYLE"));
        assertThrows(IllegalStateException.class,
                () -> invalidLoader.create(3, new Random(1), PresetTuning.profile(PresetTuning.root(1), 3)));
    }

    @Test
    void rejectsUnknownNonBooleanAndEnumTechniqueOverrides() {
        for (Object bad : new Object[] {"yes", 1}) {
            YamlConfiguration config = shipped();
            config.set("BEHAVIOR.TECHNIQUES.MACE-SWAP", bad);
            assertTrue(SettingsLoader.load(config).error().contains("MACE-SWAP"));
        }
        YamlConfiguration enumValue = shipped();
        enumValue.set("BEHAVIOR.TECHNIQUES.WC-HIT-PREFERENCE", "SMART");
        assertTrue(SettingsLoader.load(enumValue).error().contains("WC-HIT-PREFERENCE"));
        YamlConfiguration malformedSection = shipped();
        malformedSection.set("BEHAVIOR.TECHNIQUES", "MACE-SWAP: true");
        assertTrue(SettingsLoader.load(malformedSection).error().contains("BEHAVIOR.TECHNIQUES"));
        YamlConfiguration unknown = shipped();
        unknown.set("BEHAVIOR.TECHNIQUES.NOT-A-TECHNIQUE", true);
        assertTrue(SettingsLoader.load(unknown).error().contains("NOT-A-TECHNIQUE"));
    }

    @Test
    void legacyLocalCombatValuesCannotOverrideTheProfile() {
        JSONObject root = PresetTuning.root(1);
        YamlConfiguration config = shipped();
        config.set("LEVELS.3.ACCURACY", 0.01);
        config.set("BEHAVIOR.USE-POTIONS", false);
        BotSettings settings = SettingsLoader.load(config).create(3, new Random(1), PresetTuning.profile(root, 3));
        assertEquals(PresetTuning.forPreset(Difficulty.HARD).accuracy, settings.difficultyParams().accuracy);
        assertEquals(PresetTuning.profile(root, 3).flag(TuningKey.BEHAVIOR_USE_POTIONS), settings.usePotions());
    }

    @Test
    void migrateTo4RemovesOnlyTheDeadKeys() {
        YamlConfiguration config = shipped();
        config.set("LEVELS.3.ACCURACY", 0.75);
        config.set("DIFFICULTIES.1.AIM-ERROR-DEGREES", 8.0);
        config.set("BEHAVIOR.RIPTIDE-ANYWHERE", true);
        config.set("BEHAVIOR.BOT-PING", 5);
        config.set("BEHAVIOR.FAST-SHIELD-COOLDOWN", true);
        config.set("BEHAVIOR.DO-BLOCK-EXPLOSION", false);
        config.set("BEHAVIOR.USE-POTIONS", false);
        SettingsLoader.migrateTo4(config);
        for (String key : new String[] {"DIFFICULTIES", "BEHAVIOR.RIPTIDE-ANYWHERE", "BEHAVIOR.BOT-PING",
                "BEHAVIOR.FAST-SHIELD-COOLDOWN", "BEHAVIOR.DO-BLOCK-EXPLOSION"}) {
            assertFalse(config.contains(key), key);
        }
        assertFalse(config.getBoolean("BEHAVIOR.USE-POTIONS"));
        assertTrue(config.contains("LEVELS.3.ACCURACY"));
    }

    @Test
    void migrateTo6RemovesTuningButKeepsChoicesAndUnrelatedValues() {
        YamlConfiguration config = shipped();
        config.set("LICENSE.KEY", "example");
        config.set("DEBUG", true);
        config.set("BEHAVIOR.USE-POTIONS", false);
        config.set("BEHAVIOR.TECHNIQUES.MACE-SWAP", true);
        config.set("BEHAVIOR.TECHNIQUES.WC-HIT-PREFERENCE", "HORIZONTAL");
        config.set("BEHAVIOR.TECHNIQUES.UNKNOWN", true);
        SettingsLoader.migrateTo6(config);
        assertFalse(config.contains("LEVELS"));
        assertFalse(config.contains("BEHAVIOR.USE-POTIONS"));
        assertTrue(config.getBoolean("BEHAVIOR.TECHNIQUES.MACE-SWAP"));
        assertFalse(config.contains("BEHAVIOR.TECHNIQUES.WC-HIT-PREFERENCE"));
        assertFalse(config.contains("BEHAVIOR.TECHNIQUES.UNKNOWN"));
        assertEquals("example", config.getString("LICENSE.KEY"));
        assertTrue(config.getBoolean("DEBUG"));
        assertTrue(config.contains("BEHAVIOR.PLAY-STYLE"));
        assertTrue(config.contains("BEHAVIOR.PLAYER-PLAY-STYLES"));
    }
}
