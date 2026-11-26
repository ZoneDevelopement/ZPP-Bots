package dev.nandi0813.zppbots.provider;

import dev.nandi0813.api.Interface.BotOpponent;
import dev.nandi0813.api.Interface.BotOptions;
import dev.nandi0813.api.Interface.BotProvider;
import dev.nandi0813.api.Interface.Match;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotGuards;
import dev.nandi0813.zppbots.platform.BotPlatform;
import dev.nandi0813.zppbots.settings.Behavior.PlayStyleSetting;
import dev.nandi0813.zppbots.team.MatchSquads;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NativeBotProvider implements BotProvider {

    public static final String DIFFICULTY_KEY = "difficulty";
    public static final String PLAY_STYLE_KEY = "playstyle";
    private static final int MAX_LABEL = 32;

    private final ZPPBots plugin;
    private final Map<String, MatchSquads> squads = new HashMap<>();
    private final Set<NativeBotOpponent> opponents = new LinkedHashSet<>();

    public NativeBotProvider(ZPPBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "zpp-bots";
    }

    @Override
    public boolean isAvailable() {
        return plugin.isEnabled() && plugin.getPlatform() != null && plugin.getSettings() != null
                && plugin.getSettings().error() == null;
    }

    @Override
    public String getUnavailableReason() {
        if (!plugin.isEnabled()) return "ZPP-Bots is disabled";
        if (plugin.getPlatform() == null) return plugin.getUnavailableReason();
        if (plugin.getSettings() == null) return "Bot settings configuration is not loaded";
        if (plugin.getSettings().error() != null) return "Invalid bot settings configuration: " + plugin.getSettings().error();
        return null;
    }

    @Override
    public BotOptions getDefaultOptions() {
        return options(defaultLevel(), defaultStyle());
    }

    /** Level the setup menu starts on. */
    public int defaultLevel() {
        return plugin.getSettings() == null ? 3 : plugin.getSettings().defaultLevel();
    }

    /** Style the setup menu starts on and the style of a match that does not choose one. */
    public PlayStyleSetting defaultStyle() {
        return plugin.getSettings() == null ? PlayStyleSetting.DEFAULT : plugin.getSettings().defaultStyle();
    }

    /** The level name from messages.yml as plain text, or "Level n". */
    public String difficultyName(int level) {
        return plainName("DIFFICULTY." + level + ".NAME", "Level " + level);
    }

    /** The style name from messages.yml as plain text, or the capitalised enum name. */
    public String styleName(PlayStyleSetting style) {
        return plainName("PLAY-STYLE." + style.name() + ".NAME",
                style.name().charAt(0) + style.name().substring(1).toLowerCase(Locale.ROOT));
    }

    private String plainName(String path, String fallback) {
        String name = plugin.getMessages() == null ? ""
                : PlainTextComponentSerializer.plainText().serialize(plugin.getMessages().get(path)).strip();
        return name.isEmpty() || name.indexOf('<') >= 0 || name.indexOf('>') >= 0 ? fallback : name;
    }

    /** The styles players may pick, from BEHAVIOR.PLAYER-PLAY-STYLES in menu order. */
    public List<PlayStyleSetting> allowedStyles() {
        return plugin.getSettings() == null ? List.of(PlayStyleSetting.DEFAULT)
                : plugin.getSettings().allowedStyles();
    }

    /** Options for one level and style; the label is "level · style", cut to 32 characters. */
    public BotOptions options(int level, PlayStyleSetting style) {
        return new BotOptions(label(difficultyName(level), styleName(style)),
                Map.of(DIFFICULTY_KEY, Integer.toString(level), PLAY_STYLE_KEY, style.name()));
    }

    static String label(String difficultyName, String styleName) {
        String label = difficultyName + " · " + styleName;
        return label.length() > MAX_LABEL ? label.substring(0, MAX_LABEL) : label;
    }

    @Override
    public String getSetupCommand() {
        return "botpractice";
    }

    @Override
    public boolean isSupportedItem(ItemStack item) {
        return true;
    }

    @Override
    public int getMaxActiveMatches() {
        return limit("MAX-ACTIVE-MATCHES", 16);
    }

    @Override
    public int getMaxTotalBots() {
        return limit("MAX-TOTAL-BOTS", 32);
    }

    @Override
    public int getMaxBotsPerMatch() {
        return limit("MAX-BOTS-PER-MATCH", 7);
    }

    @Override
    public int getStartTimeoutTicks() {
        return limit("START-TIMEOUT-TICKS", 200);
    }

    @Override
    public int getMaxMatchDurationSeconds() {
        return limit("MAX-MATCH-DURATION-SECONDS", 600);
    }

    /** Core clamps out-of-range values; a missing key uses the default. */
    private int limit(String key, int fallback) {
        return plugin.getConfig().getInt("LIMITS." + key, fallback);
    }

    @Override
    public BotOpponent spawn(UUID entityId, Location location, BotOptions options) {
        BotGuards.requireMainThread();
        BotPlatform platform = plugin.getPlatform();
        if (platform == null) throw new IllegalStateException("Native bot backend is unavailable");
        if (plugin.getSettings() == null || plugin.getSettings().error() != null) {
            throw new IllegalStateException("Bot settings configuration is invalid");
        }
        Map<String, String> values = options == null ? Map.of() : options.values();
        int level = level(values);
        PlayStyleSetting style = style(values, allowedStyles(), defaultStyle());
        BotActor actor = platform.spawn(new BodyToken(UUID.randomUUID(), entityId, 1L), location, Map.of());
        NativeBotOpponent opponent = new NativeBotOpponent(this, plugin, platform, actor, level, style);
        opponents.add(opponent);
        return opponent;
    }

    static int level(Map<String, String> values) {
        String raw = values.get(DIFFICULTY_KEY);
        try {
            int level = Integer.parseInt(raw);
            if (level < 1 || level > 5) throw new NumberFormatException();
            return level;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Unknown bot difficulty: " + raw);
        }
    }

    /** A missing style means the default; a style that is unknown or not allowed is rejected. */
    static PlayStyleSetting style(Map<String, String> values, Collection<PlayStyleSetting> allowed, PlayStyleSetting fallback) {
        String raw = values.get(PLAY_STYLE_KEY);
        if (raw == null) return fallback;
        for (PlayStyleSetting style : allowed) {
            if (style.name().equals(raw)) return style;
        }
        throw new IllegalArgumentException("Unknown bot play style: " + raw);
    }

    MatchSquads squads(String matchId) {
        return squads.computeIfAbsent(matchId, id -> new MatchSquads());
    }

    /** Drops the opponent and, once its match has no bot members left, the match's squads. */
    void release(NativeBotOpponent opponent, String matchId, MatchSquads matchSquads) {
        opponents.remove(opponent);
        if (matchSquads == null) return;
        boolean empty = true;
        for (NativeBotOpponent other : opponents) {
            if (matchId.equals(other.matchId())) empty = false;
        }
        if (empty && squads.remove(matchId, matchSquads)) matchSquads.clear();
    }

    Collection<NativeBotOpponent> opponents() {
        return new ArrayList<>(opponents);
    }

    /** Plugin disable: no match survives. */
    public void clear() {
        for (MatchSquads matchSquads : squads.values()) matchSquads.clear();
        squads.clear();
        opponents.clear();
    }
}
