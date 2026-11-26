package dev.nandi0813.zppbots.platform;

import java.util.TreeSet;
import java.util.function.Predicate;

/** Hands out bot player names as {@code <prefix><number>}, lowest free number first. Pure Java. */
public final class BotNames {

    public static final String DEFAULT_PREFIX = "bot_";

    private final String prefix;
    private final TreeSet<Integer> used = new TreeSet<>();

    /** The caller passes a prefix that {@link #validPrefix} accepts. */
    public BotNames(String prefix) {
        this.prefix = prefix;
    }

    public static boolean validPrefix(String raw) {
        return raw != null && raw.matches("[A-Za-z0-9_]{1,12}");
    }

    public String acquire(Predicate<String> taken) {
        for (int n = 1; n <= 9999; n++) {
            if (used.contains(n) || taken.test(prefix + n)) continue;
            used.add(n);
            return prefix + n;
        }
        throw new IllegalStateException("No free bot name");
    }

    public void release(String name) {
        if (!name.startsWith(prefix)) return;
        try {
            used.remove(Integer.parseInt(name.substring(prefix.length())));
        } catch (NumberFormatException ignored) {
            // Not a name this instance handed out.
        }
    }
}
