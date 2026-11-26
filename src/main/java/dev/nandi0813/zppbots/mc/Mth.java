package dev.nandi0813.zppbots.mc;

/** Mojang {@code util.Mth} with the same names and degree semantics. */
public final class Mth {

    public static final float PI = (float) Math.PI;
    public static final float DEG_TO_RAD = PI / 180.0f;
    public static final float RAD_TO_DEG = 180.0f / PI;

    private Mth() {
    }

    public static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    public static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    public static int wrapDegrees(int value) {
        int wrapped = value % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(value, min));
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(max, Math.max(value, min));
    }

    public static float clamp(float value, float min, float max) {
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(value, min));
    }

    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static long lfloor(double value) {
        long l = (long) value;
        return value < l ? l - 1L : l;
    }

    public static int ceil(double value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static int ceil(float value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    public static float invSqrt(float value) {
        return (float) (1.0 / Math.sqrt(value));
    }

    public static double invSqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    public static float sin(float value) {
        return (float) Math.sin(value);
    }

    public static float cos(float value) {
        return (float) Math.cos(value);
    }

    public static double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static float degreesDifference(float a, float b) {
        return wrapDegrees(b - a);
    }

    public static float degreesDifferenceAbs(float a, float b) {
        return Math.abs(degreesDifference(a, b));
    }

    public static float approach(float current, float target, float step) {
        float s = Math.abs(step);
        if (current < target) return Math.min(current + s, target);
        if (current > target) return Math.max(current - s, target);
        return target;
    }

    public static float approachDegrees(float current, float target, float step) {
        float diff = degreesDifference(current, target);
        return approach(current, current + diff, step);
    }

    public static int sign(double value) {
        if (value == 0.0) return 0;
        return value > 0.0 ? 1 : -1;
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    public static int abs(int value) {
        return Math.abs(value);
    }

    public static float square(float value) {
        return value * value;
    }

    public static double square(double value) {
        return value * value;
    }

    public static int square(int value) {
        return value * value;
    }

    public static double frac(double value) {
        return value - lfloor(value);
    }

    public static int positiveModulo(int x, int y) {
        return Math.floorMod(x, y);
    }

    public static double positiveModulo(double x, double y) {
        return ((x % y) + y) % y;
    }

    public static double length(double x, double y, double z) {
        return Math.sqrt(lengthSquared(x, y, z));
    }

    public static double lengthSquared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }
}
