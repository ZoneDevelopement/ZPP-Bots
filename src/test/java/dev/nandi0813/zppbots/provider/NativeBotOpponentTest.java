package dev.nandi0813.zppbots.provider;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class NativeBotOpponentTest {

    @Test
    void seedDiffersPerBotAndRoundButIsStable() {
        UUID a = new UUID(1L, 2L);
        UUID b = new UUID(1L, 3L);
        assertNotEquals(NativeBotOpponent.seed("m", 1, a), NativeBotOpponent.seed("m", 1, b));
        assertNotEquals(NativeBotOpponent.seed("m", 1, a), NativeBotOpponent.seed("m", 2, a));
        assertEquals(NativeBotOpponent.seed("m", 1, a), NativeBotOpponent.seed("m", 1, a));
    }
}
