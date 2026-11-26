package dev.nandi0813.zppbots.platform;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotNamesTest {

    private static final Predicate<String> NOBODY = name -> false;

    @Test
    void lowestFreeIndexIsReusedAfterRelease() {
        BotNames names = new BotNames("bot_");
        assertEquals("bot_1", names.acquire(NOBODY));
        assertEquals("bot_2", names.acquire(NOBODY));
        names.release("bot_1");
        assertEquals("bot_1", names.acquire(NOBODY));
    }

    @Test
    void takenNamesAreSkipped() {
        BotNames names = new BotNames("bot_");
        assertEquals("bot_2", names.acquire(name -> name.equals("bot_1")));
    }

    @Test
    void prefixValidation() {
        assertTrue(BotNames.validPrefix("bot_"));
        assertTrue(BotNames.validPrefix("abcdefghijkl"));
        assertFalse(BotNames.validPrefix(""));
        assertFalse(BotNames.validPrefix("a-b"));
        assertFalse(BotNames.validPrefix("abcdefghijklm"));
        assertFalse(BotNames.validPrefix(null));
    }

    @Test
    void longestNameFitsPlayerLimit() {
        String prefix = "abcdefghijkl";
        BotNames names = new BotNames(prefix);
        Set<String> taken = Set.of();
        String last = null;
        for (int i = 0; i < 9999; i++) last = names.acquire(taken::contains);
        assertEquals(prefix + "9999", last);
        assertTrue(last.length() <= 16);
        assertThrows(IllegalStateException.class, () -> names.acquire(taken::contains));
    }

    @Test
    void releaseOfForeignNameIsNoOp() {
        BotNames names = new BotNames("bot_");
        assertEquals("bot_1", names.acquire(NOBODY));
        names.release("Steve");
        names.release("bot_x");
        assertEquals("bot_2", names.acquire(NOBODY));
    }
}
