package dev.nandi0813.zppbots.smoke;

/**
 * One bounded keyboard, look, action and inventory intention for a native bot tick. An inventory move swaps two
 * player inventory slots (0-35), the server result of a player's inventory click.
 */
public record BotInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump,
                       boolean sneak, boolean sprint, float yawDelta, float pitchDelta, int heldSlot, boolean attack,
                       java.util.UUID attackTarget, UseAction useAction, int inventoryFrom, int inventoryTo) {

    public enum UseAction { KEEP, START_MAIN, START_OFF, RELEASE }

    public BotInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump,
                    boolean sneak, boolean sprint, float yawDelta, float pitchDelta, int heldSlot, boolean attack,
                    java.util.UUID attackTarget, UseAction useAction) {
        this(forward, backward, left, right, jump, sneak, sprint, yawDelta, pitchDelta, heldSlot, attack, attackTarget,
                useAction, -1, -1);
    }

    public BotInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump,
                    boolean sneak, boolean sprint, float yawDelta, float pitchDelta) {
        this(forward, backward, left, right, jump, sneak, sprint, yawDelta, pitchDelta, -1, false, null, UseAction.KEEP,
                -1, -1);
    }

    public BotInput {
        if (heldSlot < -1 || heldSlot > 8 || useAction == null || (!attack && attackTarget != null)
                || (attack && (useAction == UseAction.START_MAIN || useAction == UseAction.START_OFF))) {
            throw new IllegalArgumentException("Invalid bot action intention");
        }
        if (!Float.isFinite(yawDelta) || !Float.isFinite(pitchDelta)
                || Math.abs(yawDelta) > 180.0f || Math.abs(pitchDelta) > 90.0f) {
            throw new IllegalArgumentException("Bot look deltas must be finite and within bounds");
        }
        boolean move = inventoryFrom >= 0 || inventoryTo >= 0;
        if (inventoryFrom < -1 || inventoryTo < -1
                || (move && (inventoryFrom < 0 || inventoryFrom > 35 || inventoryTo < 0 || inventoryTo > 35
                        || inventoryFrom == inventoryTo || attack || useAction != UseAction.KEEP))) {
            throw new IllegalArgumentException("Invalid bot inventory move");
        }
    }

    public static BotInput neutral() {
        return new BotInput(false, false, false, false, false, false, false, 0.0f, 0.0f);
    }
}
