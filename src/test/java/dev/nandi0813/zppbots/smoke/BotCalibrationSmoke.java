package dev.nandi0813.zppbots.smoke;

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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Headless bot-vs-bot fights between difficulty levels. Proves level ordering, not how a level feels. */
public final class BotCalibrationSmoke implements Listener {

    private static final int[][] PAIRS = {{1, 2}, {2, 3}, {3, 4}, {4, 5}, {1, 1}, {2, 2}, {3, 3}, {4, 4}, {5, 5}};
    private static final int ARENA_HALF = 15;
    private static final int ARENA_SPACING = 64;
    private static final int FLOOR_Y = 150;
    private static final int SPRINT_TICKS = Integer.MAX_VALUE;
    // Shield blockhits and gapple eats cap honest melee near 0.4-0.8/s; this floor only catches stalls.
    private static final double MIN_SWINGS_PER_SECOND = 0.3;
    private static final String KIT = System.getProperty("bot.smoke.calibrate.kit", "sword");

    private final JavaPlugin plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private final int fightsPerPair = Integer.getInteger("bot.smoke.calibrate.fights", 30);
    private final int fightTimeoutTicks = Integer.getInteger("bot.smoke.calibrate.timeout", 6000);
    private final Slot[] slots = new Slot[PAIRS.length];
    private final Map<UUID, Side> sides = new HashMap<>();
    private BotRegistry registry;
    private NativeBotProvider provider;
    private World world;
    private BukkitTask task;
    private boolean finished;

    public BotCalibrationSmoke(JavaPlugin plugin, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.success = success;
        this.failure = failure;
    }

    public void start() {
        try {
            check(Bukkit.getOnlinePlayers().isEmpty(), "calibration requires zero online players");
            check(fightsPerPair >= 1 && fightsPerPair <= 200, "bot.smoke.calibrate.fights must be 1-200");
            check(List.of("sword", "gapple", "nodebuff", "archer", "builduhc").contains(KIT), "unknown calibration kit: " + KIT);
            plugin.getLogger().info("BOT_CALIBRATION_KIT " + KIT + " timeout=" + fightTimeoutTicks);
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
            for (int i = 0; i < PAIRS.length; i++) {
                slots[i] = new Slot(PAIRS[i][0], PAIRS[i][1], i * ARENA_SPACING);
                buildArena(slots[i].centerX);
            }
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getServerTickManager().requestGameToSprint(SPRINT_TICKS);
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

    private void tick() {
        if (finished) return;
        try {
            boolean running = false;
            for (Slot slot : slots) running |= slot.step();
            if (!running) complete();
        } catch (Throwable error) {
            fail(error);
        }
    }

    /** Sprinted ticks pass through nights; mobs would attack bots and creepers would break the arena floor. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getLocation().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        Side side = sides.get(event.getEntity().getUniqueId());
        if (side == null || !(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() < victim.getHealth()) return;
        event.setCancelled(true);
        if (side.fight().over) return;
        if (event instanceof EntityDamageByEntityEvent byEntity) countHit(side, byEntity, victim);
        side.fight().winner = side.isA() ? 2 : 1;
        side.fight().end = event.getCause() + " " + (side.isA() ? "A" : "B") + "@" + where(victim);
        side.fight().over = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Side side = sides.get(event.getEntity().getUniqueId());
        if (side == null || side.fight().over || !(event.getEntity() instanceof Player victim)) return;
        countHit(side, event, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Side side = sides.get(event.getPlayer().getUniqueId());
        if (side != null && !side.fight().over) side.stats().swings++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Side side = sides.get(event.getPlayer().getUniqueId());
        if (side != null && !side.fight().over) side.stats().food++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        Side side = sides.get(shooter.getUniqueId());
        if (side == null || side.fight().over) return;
        if (event.getEntity() instanceof ThrownPotion) side.stats().pots++;
        else if (event.getEntity() instanceof AbstractArrow) side.stats().shots++;
    }

    private void countHit(Side victimSide, EntityDamageByEntityEvent event, Player victim) {
        Entity damager = event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter : event.getDamager();
        Side attackerSide = sides.get(damager.getUniqueId());
        if (attackerSide == null || attackerSide.fight() != victimSide.fight()
                || !(damager instanceof Player attacker)) return;
        Stats stats = attackerSide.stats();
        if (victim.isBlocking()) {
            stats.blocked++;
            return;
        }
        stats.hits++;
        stats.damage += event.getFinalDamage();
        if (event.getDamager() instanceof Projectile) {
            if (event.getDamager() instanceof AbstractArrow) stats.arrowHits++;
            return;
        }
        if (attacker.isSprinting()) stats.sprintHits++;
        if (attacker.getFallDistance() > 0.0f && !attacker.isOnGround() && !attacker.isSprinting()) stats.crits++;
    }

    private void complete() {
        finished = true;
        List<String> problems = new ArrayList<>();
        for (Slot slot : slots) {
            int decisive = slot.winsA + slot.winsB;
            double rateA = decisive == 0 ? 0.5 : slot.winsA / (double) decisive;
            double seconds = Math.max(1.0, slot.ticks / 20.0);
            double swingsA = slot.totalA.swings / seconds;
            double swingsB = slot.totalB.swings / seconds;
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "BOT_CALIBRATION_PAIR kit=%s %dv%d fights=%d winsA=%d winsB=%d draws=%d rateA=%.2f swingsPerSecA=%.2f swingsPerSecB=%.2f A{%s} B{%s}",
                    KIT, slot.levelA, slot.levelB, fightsPerPair, slot.winsA, slot.winsB, slot.draws, rateA, swingsA, swingsB,
                    slot.totalA.format(), slot.totalB.format()));
            if (!"archer".equals(KIT) && (swingsA < MIN_SWINGS_PER_SECOND || swingsB < MIN_SWINGS_PER_SECOND)) {
                problems.add(String.format(Locale.ROOT, "%dv%d swings per second %.2f/%.2f below %.1f",
                        slot.levelA, slot.levelB, swingsA, swingsB, MIN_SWINGS_PER_SECOND));
            }
            if (slot.draws * 2 > fightsPerPair) {
                problems.add(slot.levelA + "v" + slot.levelB + " drew " + slot.draws + " of " + fightsPerPair);
            } else if (slot.levelA == slot.levelB) {
                if (rateA < 0.35 || rateA > 0.65) {
                    problems.add(String.format(Locale.ROOT, "mirror %d side A won %.2f", slot.levelA, rateA));
                }
            } else if (1.0 - rateA < 0.65) {
                problems.add(String.format(Locale.ROOT, "level %d won only %.2f against level %d",
                        slot.levelB, 1.0 - rateA, slot.levelA));
            }
        }
        cleanup();
        if (problems.isEmpty()) {
            plugin.getLogger().info("BOT_CALIBRATION PASS");
            success.run();
        } else {
            failure.accept(new IllegalStateException("BOT_CALIBRATION FAIL: " + String.join("; ", problems)));
        }
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
        if (Bukkit.getServerTickManager().isSprinting()) Bukkit.getServerTickManager().stopSprinting();
        for (Slot slot : slots) {
            if (slot != null && slot.fight != null) {
                slot.fight.dispose();
                slot.fight = null;
            }
        }
        sides.clear();
    }

    private static void equip(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        switch (KIT) {
            case "gapple" -> {
                inventory.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inventory.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 8));
                inventory.setArmorContents(new ItemStack[] {new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.DIAMOND_LEGGINGS),
                        new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_HELMET)});
            }
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
            case "archer" -> {
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addUnsafeEnchantment(Enchantment.INFINITY, 1);
                inventory.setItem(0, bow);
                inventory.setItem(9, new ItemStack(Material.ARROW));
                inventory.setArmorContents(new ItemStack[] {new ItemStack(Material.IRON_BOOTS), new ItemStack(Material.IRON_LEGGINGS),
                        new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.IRON_HELMET)});
            }
            case "builduhc" -> {
                inventory.setItem(0, new ItemStack(Material.NETHERITE_SWORD));
                inventory.setItem(1, new ItemStack(Material.BOW));
                inventory.setItem(2, new ItemStack(Material.WATER_BUCKET, 2));
                inventory.setItem(3, new ItemStack(Material.LAVA_BUCKET, 2));
                inventory.setItem(4, new ItemStack(Material.COBBLESTONE, 64));
                inventory.setItem(5, new ItemStack(Material.GOLDEN_APPLE, 6));
                inventory.setItem(6, new ItemStack(Material.BIRCH_PLANKS, 64));
                inventory.setItem(9, new ItemStack(Material.ARROW, 32));
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

    private static String where(Player player) {
        Location location = player.getLocation();
        PlayerInventory inventory = player.getInventory();
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f ground=%s below=%s held=%d hand=%s raised=%s slot9=%s bowDamage=%d arrowsInv=%d health=%.1f",
                location.getX(), location.getY(), location.getZ(), player.isOnGround(),
                location.clone().add(0, -0.5, 0).getBlock().getType(), inventory.getHeldItemSlot(),
                inventory.getItemInMainHand().getType(), player.isHandRaised(), slotText(inventory.getItem(9)),
                bowDamage(inventory), arrowsInInventory(inventory), player.getHealth());
    }

    private static String slotText(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "EMPTY";
        return item.getType() + "x" + item.getAmount();
    }

    private static int bowDamage(PlayerInventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.BOW && item.getItemMeta() instanceof Damageable damageable) {
                return damageable.getDamage();
            }
        }
        return -1;
    }

    private static int arrowsInInventory(PlayerInventory inventory) {
        int arrows = 0;
        for (int slot = 0; slot <= 35; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.ARROW) arrows += item.getAmount();
        }
        return arrows;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** One arena running the fights of one level pair, one after another. */
    private final class Slot {
        private final int levelA;
        private final int levelB;
        private final int centerX;
        private final BoundingBox bounds;
        private final Stats totalA = new Stats();
        private final Stats totalB = new Stats();
        private int started;
        private int winsA;
        private int winsB;
        private int draws;
        private long ticks;
        private Fight fight;

        private Slot(int levelA, int levelB, int centerX) {
            this.levelA = levelA;
            this.levelB = levelB;
            this.centerX = centerX;
            this.bounds = new BoundingBox(centerX - ARENA_HALF, FLOOR_Y - 1, -ARENA_HALF,
                    centerX + ARENA_HALF + 1, FLOOR_Y + 6, ARENA_HALF + 1);
        }

        /** Advances this arena by one tick; false once all its fights are recorded. */
        private boolean step() {
            if (fight != null && fight.over) {
                fight.dispose();
                record(fight);
                fight = null;
            }
            if (fight == null) {
                if (started >= fightsPerPair) return false;
                fight = new Fight(this, started++);
                return true;
            }
            fight.step();
            return true;
        }

        private void record(Fight finished) {
            ticks += finished.ticks;
            if (finished.winner == 1) winsA++;
            else if (finished.winner == 2) winsB++;
            else draws++;
            totalA.add(finished.statsA);
            totalB.add(finished.statsB);
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "BOT_CALIBRATION_FIGHT %s winner=%s ticks=%d A(level %d){%s} B(level %d){%s} end=%s",
                    finished.matchId, finished.winner == 1 ? "A" : finished.winner == 2 ? "B" : "draw", finished.ticks,
                    levelA, finished.statsA.format(), levelB, finished.statsB.format(), finished.end));
        }
    }

    /** Two bots of one pair; side A always has the pair's first level. Sides swap spawns every other fight. */
    private final class Fight {
        private final String matchId;
        private final Stats statsA = new Stats();
        private final Stats statsB = new Stats();
        private UUID idA;
        private UUID idB;
        private BotOpponent botA;
        private BotOpponent botB;
        private View viewA;
        private View viewB;
        private final java.util.Random order;
        private int ticks;
        private int winner;
        private boolean over;
        private String end = "none";

        private Fight(Slot slot, int number) {
            matchId = "calib-" + slot.levelA + "v" + slot.levelB + "-" + number;
            order = new java.util.Random(matchId.hashCode());
            boolean swap = number % 2 == 1;
            buildArena(slot.centerX);
            Location west = new Location(world, slot.centerX - 4.5, FLOOR_Y, 0.5, -90.0f, 0.0f);
            Location east = new Location(world, slot.centerX + 5.5, FLOOR_Y, 0.5, 90.0f, 0.0f);
            try {
                idA = registry.reserveEntityId(plugin);
                botA = provider.spawn(idA, swap ? east : west, provider.options(slot.levelA, PlayStyleSetting.DEFAULT));
                idB = registry.reserveEntityId(plugin);
                botB = provider.spawn(idB, swap ? west : east, provider.options(slot.levelB, PlayStyleSetting.DEFAULT));
                equip(botA.getPlayer());
                equip(botB.getPlayer());
                if ("archer".equals(KIT)) {
                    botA.getPlayer().showEntity(plugin, botB.getPlayer());
                    botB.getPlayer().showEntity(plugin, botA.getPlayer());
                }
                botA.pause();
                botB.pause();
                viewA = new View(botB.getPlayer(), slot.bounds, matchId + "-A");
                viewB = new View(botA.getPlayer(), slot.bounds, matchId + "-B");
                sides.put(idA, new Side(this, true));
                sides.put(idB, new Side(this, false));
            } catch (RuntimeException error) {
                dispose();
                throw error;
            }
        }

        private void step() {
            ticks++;
            viewA.roundTicks = ticks;
            viewB.roundTicks = ticks;
            check(alive(botA) && alive(botB), matchId + ": a bot body is gone");
            if (ticks > fightTimeoutTicks) {
                end = "TIMEOUT A@" + where(botA.getPlayer()) + " B@" + where(botB.getPlayer());
                over = true;
                return;
            }
            // A fixed A-then-B order decides same-tick kills, which biased mirror fights toward one side.
            if (order.nextBoolean()) {
                botA.tick(viewA);
                botB.tick(viewB);
            } else {
                botB.tick(viewB);
                botA.tick(viewA);
            }
            Player a = botA.getPlayer();
            Player b = botB.getPlayer();
            double dx = a.getX() - b.getX();
            double dz = a.getZ() - b.getZ();
            if (Math.sqrt(dx * dx + dz * dz) <= 3.3) {
                statsA.rangeTicks++;
                statsB.rangeTicks++;
            }
            if (a.isBlocking()) statsA.blockTicks++;
            if (b.isBlocking()) statsB.blockTicks++;
            if (a.isHandRaised() && !a.isBlocking()) statsA.useTicks++;
            if (b.isHandRaised() && !b.isBlocking()) statsB.useTicks++;
            if (!a.isOnGround()) statsA.airTicks++;
            if (!b.isOnGround()) statsB.airTicks++;
        }

        private void dispose() {
            if (idA != null) sides.remove(idA);
            if (idB != null) sides.remove(idB);
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

    private record Side(Fight fight, boolean isA) {
        private Stats stats() {
            return isA ? fight.statsA : fight.statsB;
        }
    }

    private static final class Stats {
        private int swings;
        private int hits;
        private int blocked;
        private int crits;
        private int sprintHits;
        private int food;
        private double damage;
        private int rangeTicks;
        private int blockTicks;
        private int useTicks;
        private int airTicks;
        private int pots;
        private int shots;
        private int arrowHits;

        private void add(Stats other) {
            swings += other.swings;
            hits += other.hits;
            blocked += other.blocked;
            crits += other.crits;
            sprintHits += other.sprintHits;
            food += other.food;
            damage += other.damage;
            rangeTicks += other.rangeTicks;
            blockTicks += other.blockTicks;
            useTicks += other.useTicks;
            airTicks += other.airTicks;
            pots += other.pots;
            shots += other.shots;
            arrowHits += other.arrowHits;
        }

        private String format() {
            return String.format(Locale.ROOT,
                    "swings=%d hits=%d blocked=%d damage=%.1f crits=%d sprintHits=%d food=%d rangeTicks=%d blockTicks=%d useTicks=%d airTicks=%d pots=%d shots=%d arrowHits=%d",
                    swings, hits, blocked, damage, crits, sprintHits, food, rangeTicks, blockTicks, useTicks, airTicks,
                    pots, shots, arrowHits);
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
        @Override public String getLadderName() { return "calibration"; }
    }
}
