package dev.nandi0813.zppbots.provider;

import dev.nandi0813.api.Interface.BotFightView;
import dev.nandi0813.api.Interface.BotOpponent;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.brain.BotBrain;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.brain.BotLog;
import dev.nandi0813.zppbots.brain.MatchView;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotFrame;
import dev.nandi0813.zppbots.platform.BotGuards;
import dev.nandi0813.zppbots.platform.BotPlatform;
import dev.nandi0813.zppbots.settings.Behavior;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.team.MatchSquads;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

final class NativeBotOpponent implements BotOpponent {

    private static final String SKIN_COPIED = "SKIN_COPIED";

    private final NativeBotProvider provider;
    private final ZPPBots plugin;
    private final BotPlatform platform;
    private final BotActor actor;
    private final int level;
    private final Behavior.PlayStyleSetting style;
    private final MatchView matchView = new MatchView();
    private BotContext ctx;
    private BotBrain brain;
    private MatchSquads squads;
    private String matchId;
    private boolean forfeited;
    private int roundTick;
    private boolean skinApplied;

    NativeBotOpponent(NativeBotProvider provider, ZPPBots plugin, BotPlatform platform, BotActor actor, int level,
                      Behavior.PlayStyleSetting style) {
        this.provider = provider;
        this.plugin = plugin;
        this.platform = platform;
        this.actor = actor;
        this.level = level;
        this.style = style;
    }

    @Override
    public Player getPlayer() {
        return actor.player();
    }

    @Override
    public void tick(BotFightView view) {
        if (forfeited) return;
        Player self = actor.player();
        matchView.update(view, self);
        roundTick = view.getRoundTicks();
        if (ctx == null) {
            long seed = seed(view.getMatchId(), view.getRoundNumber(), self.getUniqueId());
            Random random = new Random(seed);
            BotTuning.Profile profile = BotTuning.profile(level);
            BotSettings settings = plugin.getSettings().create(level, style, random, profile);
            ctx = new BotContext(self, platform.hands(actor), settings, matchView, random,
                    new BotLog(plugin.getLogger(), self.getName(), plugin.getConfig().getBoolean("DEBUG")));
            ctx.setTuning(profile);
            matchId = view.getMatchId();
            squads = provider.squads(matchId);
            ctx.setSquad(squads.squad(view.getTeam()));
            ctx.squad().add(ctx);
            brain = new BotBrain(ctx, squads);
            plugin.getLogger().info("Bot " + actor.entityId() + " level " + level + " style " + style + " seed " + seed
                    + " (match " + matchId + ", round " + view.getRoundNumber() + ")");
        }
        if (!skinApplied && plugin.getConfig().getBoolean("SKINS.COPY-OPPONENT") && view.getEnemies().size() == 1
                && view.getAllies().stream().noneMatch(ally -> !BotGuards.isBot(ally.getUniqueId()))) {
            skinApplied = true;
            copySkin(view.getEnemies().get(0), self);
        }
        if (view.getEnemies().isEmpty()) {
            platform.tick(actor, BotFrame.neutral());
            return;
        }
        squads.tickOnce(Bukkit.getCurrentTick());
        ctx.advanceTick();
        brain.tick();
        platform.tick(actor, ctx.controls().update());
    }

    /** Keeps the bot's own UUID and name; offline-mode opponents have no textures and keep the default skin. */
    private void copySkin(Player enemy, Player self) {
        ProfileProperty textures = enemy.getPlayerProfile().getProperties().stream()
                .filter(p -> p.getName().equals("textures")).findFirst().orElse(null);
        if (textures == null) return;
        PlayerProfile profile = self.getPlayerProfile();
        profile.setProperty(textures);
        self.setPlayerProfile(profile);
        platform.refresh(actor);
        ctx.log().decision("NativeBotOpponent", SKIN_COPIED, "wears the opponent's skin");
    }

    @Override
    public void pause() {
        if (brain != null) brain.reset();
        platform.pause(actor);
    }

    @Override
    public void idle() {
        pause();
        if (forfeited) return;
        platform.tick(actor, BotFrame.neutral());
    }

    @Override
    public void show(Player viewer) {
        platform.show(actor, viewer);
    }

    @Override
    public void hide(Player viewer) {
        platform.hide(actor, viewer);
    }

    @Override
    public void remove() {
        if (brain != null) {
            brain.shutdown();
            squads.remove(ctx);
        }
        provider.release(this, matchId, squads);
        platform.remove(actor);
    }

    public LivingEntity getTarget() {
        return brain == null ? null : brain.getTarget();
    }

    String matchId() {
        return matchId;
    }

    /** Whether this bot's fight view contains the entity, as itself, an enemy or an ally. */
    boolean tracks(Entity entity) {
        return ctx != null && (entity.equals(actor.player()) || matchView.isEnemy(entity) || matchView.isAlly(entity));
    }

    /** The core cancels a SATIATED regain when natural regeneration is off for the match. */
    void noteRegenCancelled(Entity entity) {
        if (ctx != null && entity.equals(actor.player())) ctx.noteNaturalRegenOff();
    }

    void recordAttack(Entity victim, UUID attacker) {
        matchView.recordAttack(victim.getUniqueId(), attacker, ctx.tick());
        if (victim.equals(actor.player())) brain.notifyHurtBy(attacker);
    }

    /** Mixes match, round and bot so seeds of consecutive rounds and of bots in one match share no low bits. */
    static long seed(String matchId, int round, UUID bot) {
        long z = matchId.hashCode() * 0x9E3779B97F4A7C15L + round;
        z ^= bot.getMostSignificantBits() * 0xD1B54A32D192ED03L ^ bot.getLeastSignificantBits();
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }
}
