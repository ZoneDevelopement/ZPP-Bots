package dev.nandi0813.zppbots.settings;

import dev.nandi0813.zppbots.settings.Behavior.PlayStyleSetting;
import dev.nandi0813.zppbots.tuning.BotTuning;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Validated local choices for bot matches. Combat tuning is captured from the match profile. */
public final class SettingsLoader {
    private final int defaultLevel;
    private final PlayStyleSetting defaultStyle;
    private final List<PlayStyleSetting> allowedStyles;
    private final Map<String, Boolean> techniqueOverrides;
    private final String error;

    private SettingsLoader(
            int defaultLevel,
            PlayStyleSetting defaultStyle,
            List<PlayStyleSetting> allowedStyles,
            Map<String, Boolean> techniqueOverrides,
            String error) {
        this.defaultLevel = defaultLevel;
        this.defaultStyle = defaultStyle;
        this.allowedStyles = List.copyOf(allowedStyles);
        this.techniqueOverrides = Map.copyOf(techniqueOverrides);
        this.error = error;
    }

    public static SettingsLoader load(FileConfiguration config) {
        try {
            if (!config.isInt("DEFAULT-DIFFICULTY")
                    || config.getInt("DEFAULT-DIFFICULTY") < 1
                    || config.getInt("DEFAULT-DIFFICULTY") > 5) {
                throw new IllegalArgumentException("DEFAULT-DIFFICULTY must be a whole number from 1 to 5");
            }
            PlayStyleSetting defaultStyle = config.contains("BEHAVIOR.PLAY-STYLE")
                    ? style(config.get("BEHAVIOR.PLAY-STYLE"), "BEHAVIOR.PLAY-STYLE")
                    : PlayStyleSetting.DEFAULT;
            List<PlayStyleSetting> allowedStyles = readStyles(config);
            if (!allowedStyles.contains(defaultStyle)) {
                throw new IllegalArgumentException("BEHAVIOR.PLAY-STYLE must be one of BEHAVIOR.PLAYER-PLAY-STYLES");
            }
            return new SettingsLoader(config.getInt("DEFAULT-DIFFICULTY"), defaultStyle, allowedStyles,
                    readTechniqueOverrides(config), null);
        } catch (IllegalArgumentException invalid) {
            return new SettingsLoader(3, PlayStyleSetting.DEFAULT, List.of(PlayStyleSetting.values()), Map.of(),
                    invalid.getMessage());
        }
    }

    private static PlayStyleSetting style(Object value, String path) {
        if (!(value instanceof String text)) throw new IllegalArgumentException(path + " must be a play style name");
        try {
            return PlayStyleSetting.valueOf(text);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(path + " has an unknown play style: " + text);
        }
    }

    private static List<PlayStyleSetting> readStyles(FileConfiguration config) {
        String path = "BEHAVIOR.PLAYER-PLAY-STYLES";
        if (!config.contains(path)) return List.of(PlayStyleSetting.values());
        List<?> raw = config.getList(path);
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException(path + " must be a non-empty list of play styles");
        Set<PlayStyleSetting> styles = new LinkedHashSet<>();
        for (Object entry : raw) {
            PlayStyleSetting style = style(entry, path);
            if (!styles.add(style)) throw new IllegalArgumentException(path + " must not contain duplicates");
        }
        return List.copyOf(styles);
    }

    private static Map<String, Boolean> readTechniqueOverrides(FileConfiguration config) {
        String path = "BEHAVIOR.TECHNIQUES";
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            if (config.contains(path)) throw new IllegalArgumentException(path + " must be a section");
            return Map.of();
        }
        Set<String> fields = new LinkedHashSet<>();
        for (Field field : TechniqueConfig.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == boolean.class) fields.add(kebab(field.getName()));
        }
        Map<String, Boolean> overrides = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (!fields.contains(key) || !(value instanceof Boolean flag)) {
                throw new IllegalArgumentException(path + "." + key + " must be a known boolean technique");
            }
            overrides.put(key, flag);
        }
        return Map.copyOf(overrides);
    }

    /** Config VERSION 4: drops the old controller's tuning and the BEHAVIOR keys no ported class reads. */
    public static void migrateTo4(ConfigurationSection config) {
        for (String key : new String[] {"DIFFICULTIES", "BEHAVIOR.RIPTIDE-ANYWHERE", "BEHAVIOR.BOT-PING",
                "BEHAVIOR.FAST-SHIELD-COOLDOWN", "BEHAVIOR.DO-BLOCK-EXPLOSION"}) {
            config.set(key, null);
        }
    }

    /** Drops locally stored combat tuning and enum technique choices, retaining explicit boolean choices. */
    public static void migrateTo6(ConfigurationSection config) {
        config.set("LEVELS", null);
        ConfigurationSection behavior = config.getConfigurationSection("BEHAVIOR");
        if (behavior == null) return;
        for (String key : behavior.getKeys(false)) {
            if (!Set.of("PLAY-STYLE", "PLAYER-PLAY-STYLES", "TECHNIQUES").contains(key)) {
                behavior.set(key, null);
            }
        }
        ConfigurationSection techniques = behavior.getConfigurationSection("TECHNIQUES");
        if (techniques == null) return;
        Set<String> booleanFields = new LinkedHashSet<>();
        for (Field field : TechniqueConfig.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == boolean.class) booleanFields.add(kebab(field.getName()));
        }
        for (String key : techniques.getKeys(false)) {
            if (!booleanFields.contains(key) || !(techniques.get(key) instanceof Boolean)) techniques.set(key, null);
        }
    }

    public String error() { return error; }
    public int defaultLevel() { return defaultLevel; }
    public PlayStyleSetting defaultStyle() { return defaultStyle; }
    public List<PlayStyleSetting> allowedStyles() { return allowedStyles; }

    public BotSettings create(int level, Random random, BotTuning.Profile profile) {
        return create(level, defaultStyle, random, profile);
    }

    public BotSettings create(int level, PlayStyleSetting style, Random random, BotTuning.Profile profile) {
        if (error != null) throw new IllegalStateException("Invalid bot settings configuration: " + error);
        BotSettings settings = BotSettings.fromProfile(profile, level, style, allowedStyles, random);
        TechniqueConfig techniques = settings.techniques();
        for (Map.Entry<String, Boolean> override : techniqueOverrides.entrySet()) {
            try {
                TechniqueConfig.class.getField(camel(override.getKey())).setBoolean(techniques, override.getValue());
            } catch (ReflectiveOperationException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        return settings;
    }

    private static String kebab(String camel) {
        StringBuilder out = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) out.append('-');
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }

    private static String camel(String kebab) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-') upper = true;
            else {
                out.append(upper ? c : Character.toLowerCase(c));
                upper = false;
            }
        }
        return out.toString();
    }
}
