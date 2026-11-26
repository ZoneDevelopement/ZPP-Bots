package dev.nandi0813.zppbots.provider;

import dev.nandi0813.zppbots.settings.Behavior.PlayStyleSetting;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NativeBotProviderTest {

    private static final Set<PlayStyleSetting> ALL = EnumSet.allOf(PlayStyleSetting.class);

    private static Map<String, String> options(String difficulty, String style) {
        return Map.of("difficulty", difficulty, "playstyle", style);
    }

    @Test
    void validOptionsParse() {
        Map<String, String> options = options("4", "AGGRESSIVE");
        assertEquals(4, NativeBotProvider.level(options));
        assertEquals(PlayStyleSetting.AGGRESSIVE, NativeBotProvider.style(options, ALL, PlayStyleSetting.DEFAULT));
        assertEquals(PlayStyleSetting.RANDOM,
                NativeBotProvider.style(options("1", "RANDOM"), ALL, PlayStyleSetting.DEFAULT));
    }

    @Test
    void missingStyleUsesTheDefault() {
        Map<String, String> options = Map.of("difficulty", "2");
        assertEquals(PlayStyleSetting.SAFE, NativeBotProvider.style(options, ALL, PlayStyleSetting.SAFE));
    }

    @Test
    void unknownStyleAndLevelAreRejected() {
        IllegalArgumentException style = assertThrows(IllegalArgumentException.class,
                () -> NativeBotProvider.style(options("3", "WILD"), ALL, PlayStyleSetting.DEFAULT));
        assertEquals("Unknown bot play style: WILD", style.getMessage());
        assertThrows(IllegalArgumentException.class, () -> NativeBotProvider.level(options("6", "DEFAULT")));
        assertThrows(IllegalArgumentException.class, () -> NativeBotProvider.level(options("x", "DEFAULT")));
    }

    @Test
    void disallowedStyleIsRejected() {
        Set<PlayStyleSetting> allowed = EnumSet.of(PlayStyleSetting.DEFAULT, PlayStyleSetting.SAFE);
        assertThrows(IllegalArgumentException.class,
                () -> NativeBotProvider.style(options("3", "AGGRESSIVE"), allowed, PlayStyleSetting.DEFAULT));
    }

    @Test
    void longLabelIsCutTo32Characters() {
        assertEquals("Expert · Aggressive", NativeBotProvider.label("Expert", "Aggressive"));
        String cut = NativeBotProvider.label("A very long difficulty name", "Defensive");
        assertEquals(32, cut.length());
        assertEquals("A very long difficulty name \u00b7 De", cut);
    }
}
