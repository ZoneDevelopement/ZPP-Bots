package dev.nandi0813.zppbots.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BotFrameTest {

    @Test
    void validFramesConstruct() {
        assertDoesNotThrow(() -> new BotFrame(1f, -1f, true, false, true));
        assertDoesNotThrow(() -> new BotFrame(0.5f, 0f, false, false, false));
    }

    @Test
    void invalidImpulsesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BotFrame(Float.NaN, 0f, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new BotFrame(1.01f, 0f, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new BotFrame(0f, -1.01f, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new BotFrame(0f, Float.POSITIVE_INFINITY, false, false, false));
    }

    @Test
    void neutralIsZero() {
        BotFrame frame = BotFrame.neutral();
        assertEquals(0f, frame.forward());
        assertEquals(0f, frame.strafe());
        assertFalse(frame.jump() || frame.sneak() || frame.sprint());
    }
}
