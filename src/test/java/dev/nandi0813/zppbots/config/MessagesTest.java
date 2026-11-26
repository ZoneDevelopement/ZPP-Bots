package dev.nandi0813.zppbots.config;

import dev.nandi0813.zppbots.settings.Behavior;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class MessagesTest {

    private static YamlConfiguration shipped() {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/messages.yml"));
    }

    @Test
    void shippedMessagesHaveEveryStyleAndTheSetupKeys() {
        YamlConfiguration messages = shipped();
        assertEquals(6, messages.getInt("VERSION"));
        assertFalse(messages.contains("LICENSE"));
        assertFalse(messages.contains("DIFFICULTY-MENU"));
        for (Behavior.PlayStyleSetting style : Behavior.PlayStyleSetting.values()) {
            assertTrue(messages.contains("PLAY-STYLE." + style.name() + ".NAME"), style.name());
            assertFalse(messages.getStringList("PLAY-STYLE." + style.name() + ".LORE").isEmpty(), style.name());
        }
        for (String key : new String[] {"TITLE", "ITEM-NAME", "DEFAULT", "FOOTER", "SELECTED", "START.NAME", "START.LORE"}) {
            assertTrue(messages.contains("SETUP-MENU." + key), key);
        }
        assertTrue(messages.contains("DIFFICULTY.3.NAME"));
    }

    @Test
    void migrateTo1KeepsChangedValuesAndDropsOldDefaults() {
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("DIFFICULTY-MENU.TITLE", "<red>My title");
        messages.set("DIFFICULTY-MENU.ITEM-NAME", "<gold><name>");
        messages.set("DIFFICULTY-MENU.DEFAULT", "<green>Start here");
        messages.set("DIFFICULTY-MENU.FOOTER", List.of("", "<yellow>Click to choose a kit"));
        messages.set("DIFFICULTY.1.NAME", "Beginner");
        Messages.migrateTo1(messages);
        assertFalse(messages.contains("DIFFICULTY-MENU"));
        assertEquals("<red>My title", messages.getString("SETUP-MENU.TITLE"));
        assertEquals("<green>Start here", messages.getString("SETUP-MENU.DEFAULT"));
        assertFalse(messages.contains("SETUP-MENU.ITEM-NAME"));
        assertFalse(messages.contains("SETUP-MENU.FOOTER"));
        assertEquals("Beginner", messages.getString("DIFFICULTY.1.NAME"));
    }

    @Test
    void migrationDropsObsoleteSections() {
        YamlConfiguration stored = new YamlConfiguration();
        stored.set("LICENSE.NO-KEY", "<red>Edited by the owner");
        stored.set("TELEMETRY.ON", "x");
        stored.set("FEEDBACK.ASK", "x");
        stored.set("PREFIX", "<gold>Bots");
        Messages.removeObsoleteSections(stored);
        assertFalse(stored.contains("LICENSE"));
        assertFalse(stored.contains("TELEMETRY"));
        assertFalse(stored.contains("FEEDBACK"));
        assertEquals("<gold>Bots", stored.getString("PREFIX"));
    }
}
