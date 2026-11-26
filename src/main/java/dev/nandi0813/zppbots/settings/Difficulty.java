package dev.nandi0813.zppbots.settings;

public enum Difficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
    PERFECT;

    public int level() {
        return ordinal() + 1;
    }

    public static Difficulty ofLevel(int level) {
        if (level < 1 || level > values().length) {
            throw new IllegalArgumentException("Difficulty level must be 1-5: " + level);
        }
        return values()[level - 1];
    }
}
