package dev.nandi0813.zppbots.smoke;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import dev.nandi0813.api.Interface.BotFightView;
import dev.nandi0813.api.Interface.BotOpponent;
import dev.nandi0813.api.Interface.BotProvider;
import dev.nandi0813.api.Interface.BotRegistry;
import dev.nandi0813.api.ZonePracticeApi;
import dev.nandi0813.zppbots.provider.NativeBotProvider;
import dev.nandi0813.zppbots.settings.Behavior.PlayStyleSetting;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Real-time load: bot pairs fight in separate arenas while tick durations, controller cost and spawn latency are recorded. */
public final class BotLoadSmoke implements Listener {

    private static final int ARENA_HALF = 15;
    private static final int ARENA_SPACING = 64;
    private static final int FLOOR_Y = 150;
    private static final int FIGHT_TIMEOUT_TICKS = 6000;
    private static final int BASELINE_TICKS = 200;
    private static final int WARMUP_TICKS = 200;
    private static final int MEASURE_TICKS = 1200;
    private static final String KIT = System.getProperty("bot.smoke.load.kit", "sword");

    private final JavaPlugin plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private final int pairs = Integer.getInteger("bot.smoke.load.pairs", 1);
    private final int level = Integer.getInteger("bot.smoke.load.level", 5);
    private final List<Pair> arenas = new ArrayList<>();
    private final Map<UUID, Pair> owners = new HashMap<>();
    private final List<Double> baselineTicks = new ArrayList<>();
    private final List<Double> loadTicks = new ArrayList<>();
    private final List<Double> spawnMillis = new ArrayList<>();
    private BotRegistry registry;
    private NativeBotProvider provider;
    private World world;
    private BukkitTask task;
    private int phase;
    private int phaseTick;
    private long controllerNanos;
    private long botTicks;
    private int hits;
    private boolean finished;

    public BotLoadSmoke(JavaPlugin plugin, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.success = success;
        this.failure = failure;
    }

    public void start() {
        try {
            check(Bukkit.getOnlinePlayers().isEmpty(), "load smoke requires zero online players");
            check(pairs >= 1 && pairs <= 32, "bot.smoke.load.pairs must be 1-32");
            check(level >= 1 && level <= 5, "bot.smoke.load.level must be 1-5");
            check(KIT.equals("sword") || KIT.equals("nodebuff"), "bot.smoke.load.kit must be sword or nodebuff");
            registry = ZonePracticeApi.getInstance().getBotRegistry();
            BotProvider registered = registry.getProvider();
            check(registered instanceof NativeBotProvider && registered.isAvailable(), "zpp-bots provider is unavailable");
            provider = (NativeBotProvider) registered;
            world = Bukkit.getWorlds().getFirst();
            world.setSpawnFlags(false, false);
            world.setTime(6000L);
            world.setDifficulty(Difficulty.NORMAL);
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Player)) entity.remove();
            }
            for (int i = 0; i < pairs; i++) {
                Pair pair = new Pair(i * ARENA_SPACING);
                buildArena(pair.centerX);
                arenas.add(pair);
            }
            Bukkit.getPluginManager().registerEvents(this, plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void buildArena(int centerX) {
        for (int cx = ((centerX - ARENA_HALF - 1) >> 4) - 1; cx <= ((centerX + ARENA_HALF + 1) >> 4) + 1; cx++) {
            for (int cz = ((-ARENA_HALF - 1) >> 4) - 1; cz <= ((ARENA_HALF + 1) >> 4) + 1; cz++) {
                world.getChunkAt(cx, cz).setForceLoaded(true);
            }
        }
        for (int x = centerX - ARENA_HALF - 1; x <= centerX + ARENA_HALF + 1; x++) {
            for (int z = -ARENA_HALF - 1; z <= ARENA_HALF + 1; z++) {
                boolean wall = Math.abs(x - centerX) == ARENA_HALF + 1 || Math.abs(z) == ARENA_HALF + 1;
                world.getBlockAt(x, FLOOR_Y - 1, z).setType(Material.STONE, false);
                for (int y = FLOOR_Y; y <= FLOOR_Y + 5; y++) {
                    world.getBlockAt(x, y, z).setType(wall && y <= FLOOR_Y + 3 ? Material.GLASS : Material.AIR, false);
                }
            }
        }
    }

    /** Phase 0 records an idle baseline, phase 1 warms the fights up, phase 2 measures. */
    private void tick() {
        if (finished) return;
        try {
            phaseTick++;
            if (phase == 0) {
                if (phaseTick >= BASELINE_TICKS) {
                    phase = 1;
                    phaseTick = 0;
                }
                return;
            }
            for (Pair pair : arenas) pair.step();
            if (phase == 1 && phaseTick >= WARMUP_TICKS) {
                phase = 2;
                phaseTick = 0;
                controllerNanos = 0;
                botTicks = 0;
                hits = 0;
            } else if (phase == 2 && phaseTick >= MEASURE_TICKS) {
                complete();
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        if (finished) return;
        if (phase == 0) baselineTicks.add(event.getTickDuration());
        else if (phase == 2) loadTicks.add(event.getTickDuration());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getLocation().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        Pair pair = owners.get(event.getEntity().getUniqueId());
        if (pair == null || !(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() < victim.getHealth()) return;
        event.setCancelled(true);
        pair.over = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (phase == 2 && owners.containsKey(event.getEntity().getUniqueId())) hits++;
    }

    private void complete() {
        finished = true;
        plugin.getLogger().info(String.format(Locale.ROOT,
                "BOT_LOAD pairs=%d bots=%d level=%d kit=%s baselineMs{%s} loadMs{%s} controllerUsPerBotTick=%.2f spawns=%d spawnMs{%s} hits=%d",
                pairs, pairs * 2, level, KIT, percentiles(baselineTicks), percentiles(loadTicks),
                botTicks == 0 ? 0.0 : controllerNanos / 1000.0 / botTicks, spawnMillis.size(),
                percentiles(spawnMillis), hits));
        cleanup();
        if (hits == 0) {
            failure.accept(new IllegalStateException("BOT_LOAD FAIL: no hits during measurement"));
        } else {
            plugin.getLogger().info("BOT_LOAD PASS");
            success.run();
        }
    }

    private static String percentiles(List<Double> values) {
        if (values.isEmpty()) return "n=0";
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return String.format(Locale.ROOT, "n=%d p50=%.2f p95=%.2f p99=%.2f max=%.2f", sorted.size(),
                at(sorted, 0.50), at(sorted, 0.95), at(sorted, 0.99), sorted.getLast());
    }

    private static double at(List<Double> sorted, double quantile) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(quantile * sorted.size()) - 1));
    }

    private void fail(Throwable error) {
        if (finished) return;
        finished = true;
        try {
            cleanup();
        } catch (Throwable cleanupError) {
            error.addSuppressed(cleanupError);
        }
        failure.accept(error);
    }

    private void cleanup() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        for (Pair pair : arenas) pair.dispose();
        owners.clear();
    }

    private static void equip(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        switch (KIT) {
            case "nodebuff" -> {
                inventory.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                for (int slot : new int[] {1, 2, 3, 4, 5, 6, 8}) {
                    inventory.setItem(slot, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
                }
                inventory.setItem(7, potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS));
                for (int slot = 9; slot <= 32; slot++) {
                    inventory.setItem(slot, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
                }
                inventory.setItem(33, potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS));
                inventory.setArmorContents(new ItemStack[] {new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.DIAMOND_LEGGINGS),
                        new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_HELMET)});
            }
            default -> {
                inventory.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inventory.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 2));
                inventory.setItemInOffHand(new ItemStack(Material.SHIELD));
                inventory.setArmorContents(new ItemStack[] {new ItemStack(Material.IRON_BOOTS), new ItemStack(Material.IRON_LEGGINGS),
                        new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.IRON_HELMET)});
            }
        }
        inventory.setHeldItemSlot(0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
    }

    private static ItemStack potion(Material material, PotionType type) {
        ItemStack item = new ItemStack(material);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean alive(BotOpponent bot) {
        return bot != null && bot.getPlayer().isValid() && !bot.getPlayer().isDead();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** One arena with one bot pair; a new pair spawns after a lethal hit or a timeout. */
    private final class Pair {
        private final int centerX;
        private final BoundingBox bounds;
        private UUID idA;
        private UUID idB;
        private BotOpponent botA;
        private BotOpponent botB;
        private View viewA;
        private View viewB;
        private int ticks;
        private boolean over = true;

        private Pair(int centerX) {
            this.centerX = centerX;
            this.bounds = new BoundingBox(centerX - ARENA_HALF, FLOOR_Y - 1, -ARENA_HALF,
                    centerX + ARENA_HALF + 1, FLOOR_Y + 6, ARENA_HALF + 1);
        }

        private void step() {
            if (over) {
                dispose();
                spawn();
            }
            ticks++;
            viewA.roundTicks = ticks;
            viewB.roundTicks = ticks;
            check(alive(botA) && alive(botB), "load arena " + centerX + ": a bot body is gone");
            long startNanos = System.nanoTime();
            if ((ticks & 1) == 0) {
                botA.tick(viewA);
                botB.tick(viewB);
            } else {
                botB.tick(viewB);
                botA.tick(viewA);
            }
            controllerNanos += System.nanoTime() - startNanos;
            botTicks += 2;
            if (ticks > FIGHT_TIMEOUT_TICKS) over = true;
        }

        private void spawn() {
            Location west = new Location(world, centerX - 4.5, FLOOR_Y, 0.5, -90.0f, 0.0f);
            Location east = new Location(world, centerX + 5.5, FLOOR_Y, 0.5, 90.0f, 0.0f);
            long startNanos = System.nanoTime();
            idA = registry.reserveEntityId(plugin);
            botA = provider.spawn(idA, west, provider.options(level, PlayStyleSetting.DEFAULT));
            equip(botA.getPlayer());
            botA.pause();
            spawnMillis.add((System.nanoTime() - startNanos) / 1_000_000.0);
            startNanos = System.nanoTime();
            idB = registry.reserveEntityId(plugin);
            botB = provider.spawn(idB, east, provider.options(level, PlayStyleSetting.DEFAULT));
            equip(botB.getPlayer());
            botB.pause();
            spawnMillis.add((System.nanoTime() - startNanos) / 1_000_000.0);
            String matchId = "load-" + centerX;
            viewA = new View(botB.getPlayer(), bounds, matchId + "-A");
            viewB = new View(botA.getPlayer(), bounds, matchId + "-B");
            owners.put(idA, this);
            owners.put(idB, this);
            ticks = 0;
            over = false;
        }

        private void dispose() {
            if (idA != null) owners.remove(idA);
            if (idB != null) owners.remove(idB);
            if (botA != null) {
                botA.remove();
                botA = null;
            }
            if (idA != null) {
                registry.releaseEntityId(idA);
                idA = null;
            }
            if (botB != null) {
                botB.remove();
                botB = null;
            }
            if (idB != null) {
                registry.releaseEntityId(idB);
                idB = null;
            }
        }
    }

    private static final class View implements BotFightView {
        private final Player opponent;
        private final BoundingBox bounds;
        private final String matchId;
        private int roundTicks;

        private View(Player opponent, BoundingBox bounds, String matchId) {
            this.opponent = opponent;
            this.bounds = bounds;
            this.matchId = matchId;
        }

        @Override public int getTeam() { return 1; }
        @Override public List<Player> getEnemies() { return List.of(opponent); }
        @Override public List<Player> getAllies() { return List.of(); }
        @Override public String getMatchId() { return matchId; }
        @Override public int getRoundNumber() { return 1; }
        @Override public int getRoundTicks() { return roundTicks; }
        @Override public boolean isLive() { return true; }
        @Override public BoundingBox getArenaBounds() { return bounds; }
        @Override public int getDeadZoneY() { return FLOOR_Y - 10; }
        @Override public String getLadderName() { return "load"; }
    }
}
