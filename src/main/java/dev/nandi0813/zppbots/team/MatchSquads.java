package dev.nandi0813.zppbots.team;

import dev.nandi0813.zppbots.ai.gear.PlacedBoxClaims;
import dev.nandi0813.zppbots.brain.BotContext;

import java.util.HashMap;
import java.util.Map;

/** The squads and shared box claims of one bot match. Main thread only. */
public final class MatchSquads {

    private final Map<Integer, Squad> squads = new HashMap<>();
    private final PlacedBoxClaims boxClaims = new PlacedBoxClaims();
    private long lastTick = Long.MIN_VALUE;

    public Squad squad(int team) {
        return squads.computeIfAbsent(team, t -> new Squad("team" + t, t));
    }

    public PlacedBoxClaims boxClaims() {
        return boxClaims;
    }

    /** Once per server tick for the match. */
    public void tick(long gameTime) {
        for (Squad squad : squads.values()) squad.tick(gameTime);
    }

    /** Ticks the squads when the server tick changed, so every bot of the match can call it. */
    public void tickOnce(long gameTime) {
        if (gameTime == lastTick) return;
        lastTick = gameTime;
        tick(gameTime);
    }

    public void remove(BotContext ctx) {
        for (Squad squad : squads.values()) squad.remove(ctx);
    }

    public void clear() {
        for (Squad squad : squads.values()) squad.clear();
        squads.clear();
    }
}
