package dev.nandi0813.zppbots.probe;

public final class BotParityTrace {

    public static final int MAX_SAMPLES = 600;
    public static final int MAX_EVENTS = 2400;
    public static final int MAX_CHARS = 1_048_576;
    public static final int MAX_LINE_CHARS = 16_384;
    public static final int FOOTER_RESERVE = 4096;

    private final StringBuilder lines = new StringBuilder();
    private int samples;
    private int events;
    private boolean truncated;
    private boolean finished;

    public BotParityTrace(String metadata) {
        validate(metadata);
        lines.append(metadata).append('\n');
    }

    public boolean sample(String line) {
        if (!append(line, samples, MAX_SAMPLES)) return false;
        samples++;
        return true;
    }

    public boolean event(String line) {
        if (!append(line, events, MAX_EVENTS)) return false;
        events++;
        return true;
    }

    private boolean append(String line, int count, int limit) {
        if (finished) throw new IllegalStateException("Trace is finished");
        validate(line);
        if (count >= limit || lines.length() + line.length() + 1 > MAX_CHARS - FOOTER_RESERVE) {
            truncated = true;
            return false;
        }
        lines.append(line).append('\n');
        return true;
    }

    private static void validate(String line) {
        if (line == null || line.isEmpty() || line.length() > MAX_LINE_CHARS
                || line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Expected a bounded, nonempty single line");
        }
    }

    public int sampleCount() {
        return samples;
    }

    public int eventCount() {
        return events;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String finish(String footer) {
        if (finished) throw new IllegalStateException("Trace is finished");
        validate(footer);
        if (footer.length() >= FOOTER_RESERVE) throw new IllegalArgumentException("Footer is too long");
        lines.append(footer).append('\n');
        finished = true;
        return lines.toString();
    }

    public static void main(String[] args) {
        BotParityTrace ordered = new BotParityTrace("metadata");
        check(ordered.sample("sample") && ordered.event("event"), "append");
        check(ordered.sampleCount() == 1 && ordered.eventCount() == 1 && !ordered.isTruncated(), "counts");
        check(ordered.finish("footer").equals("metadata\nsample\nevent\nfooter\n"), "ordering/newlines");
        expect(IllegalStateException.class, () -> ordered.sample("late"));
        expect(IllegalStateException.class, () -> ordered.event("late"));
        expect(IllegalStateException.class, () -> ordered.finish("again"));

        BotParityTrace samples = new BotParityTrace("metadata");
        for (int i = 0; i < MAX_SAMPLES; i++) check(samples.sample("s"), "sample within limit");
        check(!samples.sample("s") && samples.sampleCount() == MAX_SAMPLES && samples.isTruncated(), "sample limit");
        BotParityTrace events = new BotParityTrace("metadata");
        for (int i = 0; i < MAX_EVENTS; i++) check(events.event("e"), "event within limit");
        check(!events.event("e") && events.eventCount() == MAX_EVENTS && events.isTruncated(), "event limit");

        BotParityTrace chars = new BotParityTrace("m");
        int remaining = MAX_CHARS - FOOTER_RESERVE - 2;
        while (remaining > 0) {
            int length = Math.min(MAX_LINE_CHARS, remaining - 1);
            check(length > 0 && chars.event("x".repeat(length)), "character capacity");
            remaining -= length + 1;
        }
        check(!chars.event("x") && chars.isTruncated(), "character limit");
        check(chars.finish("f".repeat(FOOTER_RESERVE - 1)).length() == MAX_CHARS, "footer reservation");

        BotParityTrace invalid = new BotParityTrace("m");
        for (String line : new String[]{null, "", "a\nb", "a\rb", "x".repeat(MAX_LINE_CHARS + 1)}) {
            expect(IllegalArgumentException.class, () -> new BotParityTrace(line));
            expect(IllegalArgumentException.class, () -> invalid.sample(line));
            expect(IllegalArgumentException.class, () -> invalid.event(line));
            expect(IllegalArgumentException.class, () -> invalid.finish(line));
        }
        expect(IllegalArgumentException.class, () -> invalid.finish("x".repeat(FOOTER_RESERVE)));
        check(invalid.sampleCount() == 0 && invalid.eventCount() == 0, "invalid lines do not append");
        invalid.finish("f");
        System.out.println("BotParityTrace self-check passed");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            if (type.isInstance(exception)) return;
            throw new AssertionError("Wrong exception", exception);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
