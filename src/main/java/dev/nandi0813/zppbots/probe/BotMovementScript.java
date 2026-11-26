package dev.nandi0813.zppbots.probe;

import dev.nandi0813.zppbots.platform.BotFrame;

import java.util.List;
import java.util.Set;

public record BotMovementScript(String scenario, String fixture, List<Step> frames) {

    /** One tick of keys and a yaw change. */
    public record Step(BotFrame frame, float yawDelta) {
    }


    private static final List<String> SCENARIOS = List.of("idle", "walk", "sprint", "strafe", "diagonal", "release",
            "jump", "sprint-jump", "sneak", "air-turn", "step", "wall", "fall", "knockback-ground",
            "knockback-air", "knockback-wall");
    private static final Set<String> SCENARIO_SET = Set.copyOf(SCENARIOS);

    public BotMovementScript {
        if (!SCENARIO_SET.contains(scenario)) throw new IllegalArgumentException("Unknown movement scenario");
        if (fixture == null || fixture.isEmpty() || fixture.length() > 4096) throw new IllegalArgumentException("Invalid fixture");
        if (frames == null || frames.isEmpty() || frames.size() > 600 || frames.stream().anyMatch(frame -> frame == null)) {
            throw new IllegalArgumentException("Invalid movement frames");
        }
        frames = List.copyOf(frames);
    }

    public static List<String> scenarios() {
        return SCENARIOS;
    }

    public static BotMovementScript create(String scenario, String fixture) {
        if (!SCENARIO_SET.contains(scenario)) throw new IllegalArgumentException("Unknown movement scenario");
        List<Step> frames = new java.util.ArrayList<>(160);
        for (int index = 0; index < 160; index++) {
            boolean active = index >= 20 && index < 100;
            boolean forward = active && switch (scenario) {
                case "walk", "release", "step", "wall", "fall", "sneak", "diagonal", "air-turn" -> true;
                case "sprint", "sprint-jump" -> true;
                case "knockback-wall" -> index < 40;
                default -> false;
            };
            boolean sprint = active && (scenario.equals("sprint") || scenario.equals("sprint-jump"));
            boolean left = active && (scenario.equals("strafe") || scenario.equals("diagonal"));
            boolean sneak = active && scenario.equals("sneak");
            boolean jump = index == 20 && (scenario.equals("jump") || scenario.equals("sprint-jump") || scenario.equals("knockback-air"));
            boolean airTurnJump = scenario.equals("air-turn") && index == 20;
            float yaw = scenario.equals("air-turn") && index == 30 ? 45.0f : 0.0f;
            frames.add(new Step(new BotFrame(forward ? 1.0f : 0.0f, left ? 1.0f : 0.0f, jump || airTurnJump, sneak, sprint), yaw));
        }
        return new BotMovementScript(scenario, fixture, frames);
    }
}
