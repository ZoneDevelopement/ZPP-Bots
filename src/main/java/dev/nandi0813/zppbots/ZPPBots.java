package dev.nandi0813.zppbots;

import dev.nandi0813.api.ZonePracticeApi;
import dev.nandi0813.zppbots.command.BotPracticeCommand;
import dev.nandi0813.zppbots.config.Messages;
import dev.nandi0813.zppbots.menu.BotSetupMenu;
import dev.nandi0813.zppbots.platform.BotPlatform;
import dev.nandi0813.zppbots.platform.PaperBotPlatform;
import dev.nandi0813.zppbots.provider.BotDamageListener;
import dev.nandi0813.zppbots.provider.NativeBotProvider;
import dev.nandi0813.zppbots.settings.SettingsLoader;
import io.papermc.paper.ServerBuildInfo;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

public final class ZPPBots extends JavaPlugin {

    /** Bump with config.yml VERSION when keys are added; missing keys are copied, existing values kept. */
    private static final int CONFIG_VERSION = 10;
    /** Bump with messages.yml VERSION; see {@link Messages#migrateTo1}. */
    private static final int MESSAGES_VERSION = 6;

    @Getter
    private static ZPPBots instance;

    @Getter
    private Messages messages;
    @Getter
    private SettingsLoader settings;
    @Getter
    private BotPlatform platform;
    @Getter
    private String unavailableReason;
    @Getter
    private NativeBotProvider provider;
    @Getter
    private Object probe;
    private Runnable probeCleanup;
    @Getter
    private Object diagnostics;
    private Runnable diagnosticsCleanup;

    @Override
    public void onEnable() {
        instance = this;

        if (!ZonePracticeApi.isAvailable() || runtimeApiVersion() < 7) {
            getLogger().severe("ZonePractice Pro with API 2.8.0 or newer (API level 7) is required.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        int storedVersion = getConfig().contains("VERSION", true) ? getConfig().getInt("VERSION") : 0;
        if (storedVersion < CONFIG_VERSION) {
            if (storedVersion < 4) SettingsLoader.migrateTo4(getConfig());
            if (storedVersion < 6) SettingsLoader.migrateTo6(getConfig());
            getConfig().options().copyDefaults(true);
            getConfig().set("VERSION", CONFIG_VERSION);
            saveConfig();
        }
        settings = SettingsLoader.load(getConfig());
        if (settings.error() != null) {
            getLogger().severe("Invalid bot settings configuration, bot practice is unavailable: " + settings.error());
        }
        migrateMessages();
        messages = new Messages(this);

        loadPlatform();

        if (platform == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        startBotClassPreload();
        provider = new NativeBotProvider(this);
        try {
            ZonePracticeApi.getInstance().getBotRegistry().register(this, provider);
        } catch (IllegalStateException | IllegalArgumentException e) {
            provider = null;
            getLogger().severe("Could not register the bot provider: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (Boolean.getBoolean("zpp.bots.debugTools")) {
            dev.nandi0813.zppbots.probe.BotParityProbe probeInstance = new dev.nandi0813.zppbots.probe.BotParityProbe(this);
            dev.nandi0813.zppbots.probe.BotBodyDiagnostics diagnosticsInstance =
                    new dev.nandi0813.zppbots.probe.BotBodyDiagnostics(this, probeInstance);
            probe = probeInstance;
            diagnostics = diagnosticsInstance;
            probeCleanup = probeInstance::close;
            diagnosticsCleanup = diagnosticsInstance::close;
            Objects.requireNonNull(getCommand("botprobe")).setExecutor(probeInstance);
            Objects.requireNonNull(getCommand("botprobe")).setTabCompleter(probeInstance);
            Objects.requireNonNull(getCommand("botbody")).setExecutor(diagnosticsInstance);
            Objects.requireNonNull(getCommand("botbody")).setTabCompleter(diagnosticsInstance);
            getServer().getPluginManager().registerEvents(probeInstance, this);
            getServer().getPluginManager().registerEvents(diagnosticsInstance, this);
        }
        BotPracticeCommand botPractice = new BotPracticeCommand(this);
        Objects.requireNonNull(getCommand("botpractice")).setExecutor(botPractice);
        Objects.requireNonNull(getCommand("botpractice")).setTabCompleter(botPractice);
        getServer().getPluginManager().registerEvents(new BotSetupMenu.Clicks(), this);
        getServer().getPluginManager().registerEvents(new BotDamageListener(provider), this);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof BotSetupMenu) player.closeInventory();
        }
        if (diagnosticsCleanup != null) diagnosticsCleanup.run();
        diagnosticsCleanup = null;
        diagnostics = null;
        if (probeCleanup != null) probeCleanup.run();
        probeCleanup = null;
        probe = null;
        if (provider != null && ZonePracticeApi.isAvailable()) {
            ZonePracticeApi.getInstance().getBotRegistry().unregister(provider);
        }
        if (provider != null) provider.clear();
        provider = null;
        settings = null;
        platform = null;
        instance = null;
    }

    private void migrateMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            saveResource("messages.yml", false);
            return;
        }
        YamlConfiguration stored = YamlConfiguration.loadConfiguration(file);
        int storedVersion = stored.getInt("VERSION", 0);
        if (storedVersion >= MESSAGES_VERSION) return;
        if (storedVersion < 1) Messages.migrateTo1(stored);
        try (InputStream resource = getResource("messages.yml")) {
            if (resource != null) {
                stored.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8)));
                stored.options().copyDefaults(true);
            }
            Messages.removeObsoleteSections(stored);
            stored.set("VERSION", MESSAGES_VERSION);
            stored.save(file);
        } catch (IOException e) {
            getLogger().warning("Could not migrate messages.yml: " + e.getMessage());
        }
    }

    /** Loading the bot classes off the main thread keeps the first match from paying for it in one tick. */
    private void startBotClassPreload() {
        BotPlatform preloadPlatform = platform;
        Thread thread = new Thread(() -> {
            try (JarFile jar = new JarFile(getFile())) {
                for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                    String name = entries.nextElement().getName();
                    if (!name.endsWith(".class") || name.startsWith("META-INF/")
                            || name.endsWith("module-info.class") || name.endsWith("package-info.class")) continue;
                    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    try {
                        Class.forName(className, false, getClassLoader());
                    } catch (ClassNotFoundException | LinkageError ignored) {
                        // skipped: the class is loaded on demand if it is ever needed
                    }
                }
            } catch (IOException e) {
                getLogger().log(Level.FINE, "Bot class preload stopped: " + e.getMessage());
            }
            if (preloadPlatform instanceof PaperBotPlatform paper) paper.primeNativeAccessors();
        }, "ZPPBots-preload");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadPlatform() {
        ServerBuildInfo build = ServerBuildInfo.buildInfo();
        String brand = build.brandId().toString();
        String version = build.minecraftVersionId();
        String buildNumber = build.buildNumber().isPresent() ? Integer.toString(build.buildNumber().getAsInt()) : "unknown";
        if (!version.equals("1.21.11") && !version.startsWith("26.")) {
            unavailableReason = "Unsupported bot server version: brand=" + brand + ", version=" + version + ", build=" + buildNumber;
            getLogger().warning(unavailableReason);
            return;
        }
        try {
            platform = new PaperBotPlatform(this);
            unavailableReason = null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            platform = null;
            unavailableReason = "Could not initialize native body backend: brand=" + brand + ", version="
                    + version + ", build=" + buildNumber;
            getLogger().log(Level.SEVERE, unavailableReason, exception);
        }
    }

    private static int runtimeApiVersion() {
        try {
            return ZonePracticeApi.class.getField("API_VERSION").getInt(null);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }
}
