package dev.nandi0813.zppbots.tuning;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class BotTuning {
    private static final Profile[] PROFILES = new Profile[5];

    private BotTuning() {}

    public static synchronized Profile profile(int difficulty) {
        if (difficulty < 1 || difficulty > 5) throw new IllegalArgumentException("difficulty");
        Profile cached = PROFILES[difficulty - 1];
        if (cached != null) return cached;
        EnumMap<TuningKey, Object> values = new EnumMap<>(TuningKey.class);
        for (TuningKey key : TuningKey.values()) values.put(key, key.checked(BuiltInTuning.raw(key, difficulty)));
        return PROFILES[difficulty - 1] = new Profile(Collections.unmodifiableMap(values));
    }

    /** Builds a profile from explicit values (tests); every key must be present and valid. */
    public static Profile of(Map<TuningKey, Object> values) {
        EnumMap<TuningKey, Object> checked = new EnumMap<>(TuningKey.class);
        for (TuningKey key : TuningKey.values()) {
            if (!values.containsKey(key)) throw new IllegalArgumentException(key.key());
            checked.put(key, key.checked(values.get(key)));
        }
        return new Profile(Collections.unmodifiableMap(checked));
    }

    public static final class Profile {
        private final Map<TuningKey, Object> values;

        private Profile(Map<TuningKey, Object> values) {
            this.values = values;
        }

        public BigDecimal number(TuningKey key) {
            check(key);
            if (key.type() == TuningKey.Type.BOOLEAN) throw new IllegalArgumentException(key.key());
            Object value = values.get(key);
            return value instanceof Integer integer ? BigDecimal.valueOf(integer) : (BigDecimal) value;
        }

        public int integer(TuningKey key) {
            check(key);
            if (key.type() != TuningKey.Type.INTEGER) throw new IllegalArgumentException(key.key());
            return (Integer) values.get(key);
        }

        public boolean flag(TuningKey key) {
            check(key);
            if (key.type() != TuningKey.Type.BOOLEAN) throw new IllegalArgumentException(key.key());
            return (Boolean) values.get(key);
        }

        private void check(TuningKey key) {
            if (key == null || !values.containsKey(key)) {
                throw new IllegalArgumentException(key == null ? "key" : key.key());
            }
        }
    }
}
