package dev.nandi0813.zppbots.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class BotLogTest {

    @Test
    void emitsStructuredDecisionsAndRespectsConsoleSeverity() {
        Logger logger = Logger.getLogger("BotLogTest");
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logger.addHandler(handler);
        try {
            List<BotLog.Decision> decisions = new ArrayList<>();
            BotLog quiet = new BotLog(logger, "quiet", false, decisions::add);
            quiet.decision("Targeting", "PICK", "near");
            assertEquals(List.of(new BotLog.Decision("Targeting", "PICK", "near")), decisions);
            assertTrue(records.isEmpty());

            BotLog loud = new BotLog(logger, "loud", true, decisions::add);
            loud.decision("Targeting", "PICK", "near");
            assertTrue(records.get(0).getMessage().contains("Targeting:PICK near"));

            records.clear();
            quiet.decision("Targeting", "RECOVERY", "wedged", true);
            assertEquals(1, records.size());
            assertEquals(java.util.logging.Level.WARNING, records.get(0).getLevel());
            assertEquals(3, decisions.size());
            assertFalse(new BotLog(logger, "default", false).enabled());
        } finally {
            logger.removeHandler(handler);
        }
    }
}
