package dev.nandi0813.zppbots.config;

import dev.nandi0813.zppbots.ZPPBots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Messages {

    private final YamlConfiguration config;

    public Messages(ZPPBots plugin) {
        this.config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));

        try (InputStream resource = plugin.getResource("messages.yml")) {
            if (resource != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load the default messages.yml: " + e.getMessage());
        }
    }

    Messages(YamlConfiguration config) {
        this.config = config;
    }

    /** messages.yml VERSION 6: the license, telemetry and feedback texts are gone. */
    public static void removeObsoleteSections(YamlConfiguration messages) {
        for (String key : new String[] {"LICENSE", "TELEMETRY", "FEEDBACK"}) messages.set(key, null);
    }

    /**
     * messages.yml VERSION 1: the difficulty menu became the setup menu. A value the server owner changed moves to
     * its new key; an untouched old default is dropped so the new default text applies.
     */
    public static void migrateTo1(ConfigurationSection messages) {
        moveIfChanged(messages, "TITLE", "<dark_gray>Choose a bot difficulty");
        moveIfChanged(messages, "ITEM-NAME", "<gold><name>");
        moveIfChanged(messages, "DEFAULT", "<green>Default level");
        moveIfChanged(messages, "FOOTER", List.of("", "<yellow>Click to choose a kit"));
        messages.set("DIFFICULTY-MENU", null);
    }

    private static void moveIfChanged(ConfigurationSection messages, String key, Object oldDefault) {
        Object value = messages.get("DIFFICULTY-MENU." + key);
        if (value != null && !value.equals(oldDefault)) messages.set("SETUP-MENU." + key, value);
    }

    public Component get(String path, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(raw(path), resolvers);
    }

    public List<Component> getList(String path, TagResolver... resolvers) {
        List<String> lines = config.getStringList(path);
        if (lines.isEmpty()) return List.of(get(path, resolvers));

        return lines.stream()
                .map(line -> MiniMessage.miniMessage().deserialize(line, resolvers))
                .toList();
    }

    public String raw(String path) {
        String value = config.getString(path);
        return value != null ? value : path;
    }

    public void send(CommandSender sender, String path, TagResolver... resolvers) {
        sender.sendMessage(get("PREFIX").append(get(path, resolvers)));
    }

    public void sendList(CommandSender sender, String path) {
        for (Component line : getList(path)) {
            sender.sendMessage(line);
        }
    }

}
