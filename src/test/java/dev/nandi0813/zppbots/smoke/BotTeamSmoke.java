package dev.nandi0813.zppbots.smoke;

import dev.nandi0813.api.Interface.BotFightView;
import dev.nandi0813.api.Interface.BotOpponent;
import dev.nandi0813.api.Interface.BotProvider;
import dev.nandi0813.api.Interface.BotRegistry;
import dev.nandi0813.api.ZonePracticeApi;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** 2v2 of native bots: no friendly fire, every bot gets hit by an enemy, teammates split targets. */
final class BotTeamSmoke implements Listener {

    private static final int CHECK_TICKS = 600;
    private static final double[][] SPAWNS = {
            {-6.5, -1.5, -90}, {-6.5, 2.5, -90}, {6.5, -1.5, 90}, {6.5, 2.5, 90}};

    private final JavaPlugin plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private final List<UUID> ids = new ArrayList<>();
    private final List<Location> spawns = new ArrayList<>();
    private final List<BotOpponent> bots = new ArrayList<>();
    private final List<TeamView> views = new ArrayList<>();
    private final Set<UUID> damagedByEnemy = new HashSet<>();
    private BotRegistry registry;
    private BukkitTask task;
    private boolean finished;
    private int ticks;
    private final Map<UUID, Integer> lastHitTick = new HashMap<>();
    private final Map<UUID, Integer> lastEdgeLog = new HashMap<>();
    private int allyHits;
    private int samples;
    private int splitSamples;
    private int knockOffs;

    BotTeamSmoke(JavaPlugin plugin, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.success = success;
        this.failure = failure;
    }

    void start() {
        try {
            check(Bukkit.getOnlinePlayers().isEmpty(), "team smoke requires zero online players");
            registry = ZonePracticeApi.getInstance().getBotRegistry();
            BotProvider provider = registry.getProvider();
            check(provider != null && "zpp-bots".equals(provider.getId()) && provider.isAvailable(), "zpp-bots provider is unavailable");
            World world = Bukkit.getWorlds().getFirst();
            prepareTerrain(world);

            for (int i = 0; i < 4; i++) {
                UUID id = registry.reserveEntityId(plugin);
                ids.add(id);
                double[] s = SPAWNS[i];
                Location spawn = new Location(world, s[0], 100.0, s[1], (float) s[2], 0.0f);
                spawns.add(spawn);
                BotOpponent bot = provider.spawn(id, spawn.clone(), provider.getDefaultOptions());
                bots.add(bot);
                Player p = bot.getPlayer();
                p.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                p.getInventory().setHeldItemSlot(0);
                p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200.0);
                p.setHealth(200.0);
                bot.pause();
            }
            BoundingBox bounds = new BoundingBox(-24, 99, -24, 25, 110, 25);
            for (int i = 0; i < 4; i++) {
                int team = i / 2;
                views.add(new TeamView(team, List.of(bots.get(2 - team * 2).getPlayer(), bots.get(3 - team * 2).getPlayer()),
                        List.of(bots.get(i ^ 1).getPlayer()), bounds));
            }
            Bukkit.getPluginManager().registerEvents(this, plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void prepareTerrain(World world) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.getChunkAt(x, z).setForceLoaded(true);
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            world.getBlockAt(x, 99, z).setType(Material.STONE, false);
            for (int y = 100; y <= 110; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        world.setSpawnFlags(false, false);
        world.setTime(6000L);
        world.setDifficulty(Difficulty.NORMAL);
    }

    private void tick() {
        if (finished) return;
        try {
            ticks++;
            for (int i = 0; i < 4; i++) {
                views.get(i).roundTicks = ticks;
                Player body = bots.get(i).getPlayer();
                if (body.getLocation().getY() < 98.0) {
                    Integer hit = lastHitTick.get(body.getUniqueId());
                    if (hit == null || ticks - hit > 30) throw new IllegalStateException("bot " + i + " walked off the floor at tick " + ticks
                            + " lastHit=" + (hit == null ? -1 : ticks - hit) + " loc=" + body.getLocation());
                    body.teleport(spawns.get(i).clone());
                    body.setFallDistance(0);
                    body.setVelocity(new Vector());
                    knockOffs++;
                    plugin.getLogger().info("BOT_TEAM_KNOCKOFF tick=" + ticks + " bot=" + i + " lastHit=" + (ticks - hit));
                }
                if (!body.isValid() || body.isDead()) throw new IllegalStateException("bot " + i + " died at tick " + ticks + " health=" + body.getHealth() + " loc=" + body.getLocation()
                        + " lastHit=" + (lastHitTick.containsKey(body.getUniqueId()) ? ticks - lastHitTick.get(body.getUniqueId()) : -1));
                Location at = body.getLocation();
                if (body.isOnGround() && (at.getX() < -23 || at.getX() > 24 || at.getZ() < -23 || at.getZ() > 24)
                        && ticks - lastEdgeLog.getOrDefault(body.getUniqueId(), -100) >= 20) {
                    lastEdgeLog.put(body.getUniqueId(), ticks);
                    plugin.getLogger().info("BOT_TEAM_EDGE tick=" + ticks + " bot=" + i + " loc=" + at);
                }
                bots.get(i).tick(views.get(i));
            }
            if (ticks % 5 == 0 && ticks > 40) {
                for (int team = 0; team < 2; team++) {
                    LivingEntity a = target(bots.get(team * 2));
                    LivingEntity b = target(bots.get(team * 2 + 1));
                    if (a == null || b == null) continue;
                    samples++;
                    if (!a.equals(b)) splitSamples++;
                }
            }
            if (ticks % 100 == 0) {
                plugin.getLogger().info(String.format("BOT_TEAM_TRACE tick=%d allyHits=%d damaged=%d split=%d/%d knockOffs=%d",
                        ticks, allyHits, damagedByEnemy.size(), splitSamples, samples, knockOffs));
            }
            if (ticks == CHECK_TICKS) {
                check(allyHits == 0, "bot damaged a teammate: " + allyHits);
                check(damagedByEnemy.size() == 4, "not every bot was hit by an enemy: " + damagedByEnemy.size() + " of 4");
                check(samples > 0 && splitSamples * 2 >= samples,
                        "teammates split targets in " + splitSamples + " of " + samples + " samples");
                plugin.getLogger().info("BOT_TEAM_SMOKE PASS split=" + splitSamples + "/" + samples + " knockOffs=" + knockOffs);
                finished = true;
                cleanup();
                success.run();
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    // NativeBotOpponent is package-private in another package, so reach getTarget reflectively.
    private static LivingEntity target(BotOpponent opponent) throws ReflectiveOperationException {
        Method method = opponent.getClass().getMethod("getTarget");
        method.setAccessible(true);
        return (LivingEntity) method.invoke(opponent);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (finished) return;
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) attacker = shooter;
        int victim = indexOf(event.getEntity().getUniqueId());
        int source = indexOf(attacker.getUniqueId());
        if (victim < 0 || source < 0) return;
        lastHitTick.put(event.getEntity().getUniqueId(), ticks);
        if (victim / 2 == source / 2) allyHits++;
        else damagedByEnemy.add(event.getEntity().getUniqueId());
    }

    private int indexOf(UUID id) {
        for (int i = 0; i < bots.size(); i++) if (bots.get(i).getPlayer().getUniqueId().equals(id)) return i;
        return -1;
    }

    private void fail(Throwable error) {
        if (finished) return;
        finished = true;
        try { cleanup(); } catch (Throwable cleanupError) { error.addSuppressed(cleanupError); }
        failure.accept(error);
    }

    private void cleanup() {
        if (task != null) { task.cancel(); task = null; }
        HandlerList.unregisterAll(this);
        for (BotOpponent bot : bots) bot.remove();
        bots.clear();
        for (UUID id : ids) registry.releaseEntityId(id);
        ids.clear();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class TeamView implements BotFightView {
        private final int team;
        private final List<Player> enemies;
        private final List<Player> allies;
        private final BoundingBox bounds;
        private int roundTicks;

        private TeamView(int team, List<Player> enemies, List<Player> allies, BoundingBox bounds) {
            this.team = team;
            this.enemies = enemies;
            this.allies = allies;
            this.bounds = bounds;
        }

        @Override public int getTeam() { return team; }
        @Override public List<Player> getEnemies() { return enemies; }
        @Override public List<Player> getAllies() { return allies; }
        @Override public String getMatchId() { return "team-smoke"; }
        @Override public int getRoundNumber() { return 1; }
        @Override public int getRoundTicks() { return roundTicks; }
        @Override public boolean isLive() { return true; }
        @Override public BoundingBox getArenaBounds() { return bounds; }
        @Override public int getDeadZoneY() { return 90; }
        @Override public String getLadderName() { return "team-smoke"; }
    }
}
