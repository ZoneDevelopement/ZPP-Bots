package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.platform.BotHands;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.settings.DifficultyParams;
import dev.nandi0813.zppbots.team.Squad;
import dev.nandi0813.zppbots.tuning.BotTuning;
import org.bukkit.entity.Player;

import java.util.Random;

/** Everything one bot's brain works with. Main thread only. */
public final class BotContext {

    private final Player player;
    private final BotHands hands;
    private final BotSettings settings;
    private final MatchView match;
    private final Random random;
    private final BotLog log;
    private final BotWorld world;
    private final BotControls controls;
    private Squad squad;
    private BotTuning.Profile tuning;
    private int tick;
    private boolean naturalRegenOff;

    public BotContext(Player player, BotHands hands, BotSettings settings, MatchView match, Random random, BotLog log) {
        this.player = player;
        this.hands = hands;
        this.settings = settings;
        this.match = match;
        this.random = random;
        this.log = log;
        this.world = new BotWorld(player);
        this.controls = new BotControls(this);
    }

    public Player player() {
        return player;
    }

    public BotControls controls() {
        return controls;
    }

    public BotHands hands() {
        return hands;
    }

    public BotWorld world() {
        return world;
    }

    public BotSettings settings() {
        return settings;
    }

    public DifficultyParams difficulty() {
        return settings.difficultyParams();
    }

    public BotTuning.Profile tuning() {
        if (tuning == null) throw new IllegalStateException("Tuning unavailable");
        return tuning;
    }

    public void setTuning(BotTuning.Profile tuning) {
        if (tuning == null) throw new NullPointerException("tuning");
        if (this.tuning != null) throw new IllegalStateException("Tuning already assigned");
        this.tuning = tuning;
    }

    public MatchView match() {
        return match;
    }

    public Squad squad() {
        if (squad == null) squad = Squad.solo(this);
        return squad;
    }

    public void setSquad(Squad squad) {
        this.squad = squad;
    }

    public Random random() {
        return random;
    }

    public BotLog log() {
        return log;
    }

    public String name() {
        return player.getName();
    }

    public int tick() {
        return tick;
    }

    public void advanceTick() {
        tick++;
    }

    /** Natural regeneration is off in this match: the core cancelled a SATIATED regain, so food heals nothing. */
    public boolean naturalRegenOff() {
        return naturalRegenOff;
    }

    public void noteNaturalRegenOff() {
        naturalRegenOff = true;
    }
}
