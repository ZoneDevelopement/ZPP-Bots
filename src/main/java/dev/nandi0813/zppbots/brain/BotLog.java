package dev.nandi0813.zppbots.brain;

import java.util.function.Consumer;
import java.util.logging.Logger;

/** Emits structured decisions to the sink and logs ordinary decisions when config DEBUG is true. */
public final class BotLog {

    public record Decision(String source, String code, String detail) {}

    private final Logger logger;
    private final String prefix;
    private final boolean enabled;
    private final Consumer<Decision> sink;

    public BotLog(Logger logger, String botName, boolean enabled) {
        this(logger, botName, enabled, decision -> {});
    }

    public BotLog(Logger logger, String botName, boolean enabled, Consumer<Decision> sink) {
        this.logger = logger;
        this.prefix = "[bot " + botName + "] ";
        this.enabled = enabled;
        this.sink = sink;
    }

    public void decision(String source, String code, String detail) {
        decision(source, code, detail, false);
    }

    public void decision(String source, String code, String detail, boolean warning) {
        sink.accept(new Decision(source, code, detail));
        if (warning) logger.warning(prefix + source + ":" + code + " " + detail);
        else if (enabled) logger.info(prefix + source + ":" + code + " " + detail);
    }

    public boolean enabled() {
        return enabled;
    }
}
