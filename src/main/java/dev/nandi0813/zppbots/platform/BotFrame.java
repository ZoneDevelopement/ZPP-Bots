package dev.nandi0813.zppbots.platform;

/** Movement keys for one physics step. forward/strafe in [-1, 1]; strafe > 0 is left (Mojang xxa). */
public record BotFrame(float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {

    public BotFrame {
        if (!(Math.abs(forward) <= 1.0f) || !(Math.abs(strafe) <= 1.0f)) {
            throw new IllegalArgumentException("forward and strafe must be finite and within [-1, 1]");
        }
    }

    public static BotFrame neutral() {
        return new BotFrame(0f, 0f, false, false, false);
    }
}
