package dev.nandi0813.zppbots.util;

public final class BotMath {

    public static final float MAX_BODY_ROT_PER_TICK = 15.0F;

    private BotMath() {
    }

    public static float[] getAngles(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizDist)));
        return new float[] {yaw, pitch};
    }

    public static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        }
        if (angle < -180.0F) {
            angle += 360.0F;
        }
        return angle;
    }

    public static float smoothBodyYaw(float currentYaw, float desiredYaw) {
        float delta = wrapDegrees(desiredYaw - currentYaw);
        if (delta > 15.0F) {
            delta = 15.0F;
        }
        if (delta < -15.0F) {
            delta = -15.0F;
        }
        return currentYaw + delta;
    }

    public static float smoothYawToward(float currentYaw, float desiredYaw, float maxStepDeg) {
        float delta = wrapDegrees(desiredYaw - currentYaw);
        if (delta > maxStepDeg) {
            delta = maxStepDeg;
        }
        if (delta < -maxStepDeg) {
            delta = -maxStepDeg;
        }
        return currentYaw + delta;
    }
}
