package dev.nandi0813.zppbots.smoke;

import dev.nandi0813.api.Interface.BotFightView;
import dev.nandi0813.api.Interface.BotOpponent;
import dev.nandi0813.api.Interface.BotProvider;
import dev.nandi0813.api.Interface.BotRegistry;
import dev.nandi0813.api.Interface.BotOptions;
import dev.nandi0813.api.ZonePracticeApi;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotPlatform;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.UUID;
import java.util.function.Consumer;

/** Bot controller versus a scripted circling body: chase, aim and legal hits only. */
public final class BotControllerSmoke implements Listener {

    private static final int REQUIRED_HITS = 4;
    private static final int TIMEOUT_TICKS = 600;
    private static final int MIN_HIT_INTERVAL = 11;
    private static final double REACH_TOLERANCE = 0.25;
    private static final boolean PREP = Boolean.getBoolean("bot.smoke.prep");
    private static final int COUNTDOWN_TICKS = 60;
    private static final int PREP_DEADLINE_TICKS = 200;
    private static final boolean POTS = Boolean.getBoolean("bot.smoke.pots");
    private static final int POTS_DEADLINE_TICKS = 400;
    private static final boolean ARCHER = Boolean.getBoolean("bot.smoke.archer");
    private static final int ARCHER_DEADLINE_TICKS = 400;
    private static final int ARCHER_REQUIRED_HITS = 3;
    private static final boolean PEARLS = Boolean.getBoolean("bot.smoke.pearls");
    private static final int PEARL_DEADLINE_TICKS = 200;
    private static final boolean NAVIGATION = Boolean.getBoolean("bot.smoke.navigation");
    private static final boolean CLOSE_WALL = Boolean.getBoolean("bot.smoke.navigation.close");
    private static final boolean CRYSTAL_WALL = Boolean.getBoolean("bot.smoke.navigation.crystal");

    private final JavaPlugin plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private World world;
    private BotPlatform platform;
    private BotRegistry registry;
    private BodyToken targetToken;
    private BotActor target;
    private UUID botId;
    private BotOpponent bot;
    private FixtureView view;
    private BukkitTask task;
    private int ticks;
    private int hits;
    private int swings;
    private int lastHitTick = Integer.MIN_VALUE;
    private Location prepStart;
    private double prepMoved;
    private boolean finished;
    private int startInventoryHeals;
    private int potThrows;
    private int healedCycles;
    private double maxHealth;
    private int arrowShots;
    private int arrowHits;
    private Location pearlStart;
    private int pearlShots;
    private boolean navigationDetour;
    private int navigationMining;
    private int navigationCrystalsSpawned;
    private int navigationCrystalExplosions;
    private int pearlTeleports;
    private int pearlTeleportTick = -1;
    private boolean pearlTeleportBoundsValid;
    private boolean pearlDamageSeen;
    private boolean pearlMeleeAfterTeleport;
    private final Map<PotionType, Integer> prepConsumeCounts = new EnumMap<>(PotionType.class);

    public BotControllerSmoke(JavaPlugin plugin, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.success = success;
        this.failure = failure;
    }

    public void start() {
        try {
            check(Bukkit.getOnlinePlayers().isEmpty(), "controller smoke requires zero online players");
            platform = ZPPBots.getInstance().getPlatform();
            check(platform != null, "native platform is unavailable");
            registry = ZonePracticeApi.getInstance().getBotRegistry();
            BotProvider provider = registry.getProvider();
            check(provider != null && "zpp-bots".equals(provider.getId()) && provider.isAvailable(), "zpp-bots provider is unavailable");
            world = Bukkit.getWorlds().getFirst();
            prepareTerrain();

            targetToken = SmokeIdentity.reserve(plugin, UUID.randomUUID(), 1);
            target = platform.spawn(targetToken, new Location(world, ARCHER || PEARLS ? 14.5 : POTS && !PREP ? -2.5 : POTS ? 15.5 : NAVIGATION && CLOSE_WALL ? 1.5 : 4.5, 100.0, 0.5, 90.0f, 0.0f), Map.of());
            Player targetPlayer = target.player();
            targetPlayer.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000.0);
            targetPlayer.setHealth(1000.0);
            if (PEARLS) targetPlayer.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

            botId = registry.reserveEntityId(plugin);
            bot = provider.spawn(botId, new Location(world, NAVIGATION && CLOSE_WALL ? -0.5 : -4.5, 100.0, 0.5, -90.0f, 0.0f),
                    PREP || POTS ? new BotOptions("Expert", Map.of("difficulty", "5", "playstyle", "DEFAULT")) : provider.getDefaultOptions());
            if (!ARCHER) bot.getPlayer().getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
            bot.getPlayer().getInventory().setHeldItemSlot(0);
            if (NAVIGATION) {
                bot.getPlayer().getInventory().setItem(1, new ItemStack(Material.DIAMOND_PICKAXE));
                bot.getPlayer().getInventory().setItem(2, new ItemStack(Material.ENDER_PEARL, 2));
                if (CRYSTAL_WALL) {
                    bot.getPlayer().getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000.0);
                    bot.getPlayer().setHealth(1000.0);
                    bot.getPlayer().getInventory().setItem(3, new ItemStack(Material.END_CRYSTAL, 64));
                    bot.getPlayer().getInventory().setItem(4, new ItemStack(Material.OBSIDIAN, 64));
                    bot.getPlayer().getInventory().setItem(5, new ItemStack(Material.TOTEM_OF_UNDYING, 64));
                    bot.getPlayer().getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                    check(CLOSE_WALL && horizontalDistance(bot.getPlayer().getLocation(), targetPlayer.getLocation()) <= 3.0, "crystal close-wall target must start within three blocks");
                    check(!bot.getPlayer().hasLineOfSight(targetPlayer), "crystal close-wall target must start without line of sight");
                }
            }
            if (PEARLS) {
                bot.getPlayer().getInventory().setItem(1, new ItemStack(Material.ENDER_PEARL, 2));
                bot.getPlayer().showEntity(plugin, targetPlayer);
                pearlStart = bot.getPlayer().getLocation().clone();
            }
            if (ARCHER) {
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addUnsafeEnchantment(Enchantment.INFINITY, 1);
                bot.getPlayer().getInventory().setItem(0, bow);
                bot.getPlayer().getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
                bot.getPlayer().showEntity(plugin, targetPlayer);
            }
            if (PREP) {
                bot.getPlayer().getInventory().setItem(9, potion(PotionType.STRONG_SWIFTNESS));
                bot.getPlayer().getInventory().setItem(10, potion(PotionType.STRONG_STRENGTH));
                bot.getPlayer().getInventory().setItem(11, potion(PotionType.LONG_FIRE_RESISTANCE));
                bot.getPlayer().getInventory().setItem(2, new ItemStack(Material.GOLDEN_APPLE, 3));
                for (int slot : new int[] {1, 3, 4, 5, 6, 7, 8}) {
                    bot.getPlayer().getInventory().setItem(slot, new ItemStack(Material.COOKED_BEEF));
                }
                prepStart = bot.getPlayer().getLocation().clone();
            }
            if (POTS && !PREP) {
                for (int slot = 20; slot <= 25; slot++) bot.getPlayer().getInventory().setItem(slot, heal());
                bot.getPlayer().setHealth(5.0);
                startInventoryHeals = countInventoryHeals();
            }
            bot.pause();

            view = new FixtureView(targetPlayer, new BoundingBox(-24, 99, -24, 25, 110, 25));
            if (PREP || PEARLS) view.live = false;
            Bukkit.getPluginManager().registerEvents(this, plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void prepareTerrain() {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.getChunkAt(x, z).setForceLoaded(true);
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            world.getBlockAt(x, 99, z).setType(Material.STONE, false);
            for (int y = 100; y <= 110; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        world.setSpawnFlags(false, false);
        world.setTime(6000L);
        world.setDifficulty(Difficulty.NORMAL);
        if (NAVIGATION) for (int z = -1; z <= 1; z++) for (int y = 100; y <= 102; y++) world.getBlockAt(0, y, z).setType(CRYSTAL_WALL ? Material.OBSIDIAN : Material.STONE, false);
        if (PEARLS || POTS) world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false);
    }

    private void tick() {
        if (finished) return;
        try {
            ticks++;
            boolean live = !(PREP || PEARLS) || ticks > COUNTDOWN_TICKS;
            view.live = live;
            view.roundTicks = PREP || PEARLS ? (live ? ticks - COUNTDOWN_TICKS : 0) : ticks;
            check(target.player().isValid() && !target.player().isDead(), "target body is gone");
            check(target.player().getLocation().getY() >= 99.0, "target left the platform");
            boolean prepTargetMoves = !PREP || ticks > PREP_DEADLINE_TICKS;
            BotInput targetInput = BotInput.neutral();
            if (live && prepTargetMoves && !PEARLS && !NAVIGATION) {
                if (POTS) targetInput = potsTargetInput();
                else if (!POTS) targetInput = new BotInput(true, false, false, false, false, false, false, 3.0f, 0.0f);
            }
            LegacyDrive.tick(platform, target, targetInput);
            bot.tick(view);
            if (NAVIGATION) {
                Player self = bot.getPlayer();
                navigationDetour |= Math.abs(self.getLocation().getZ() - 0.5) >= (CLOSE_WALL ? 1.5 : 1.8);
                Location at = self.getLocation();
                check(navigationMining == 0 && pearlShots == 0, String.format("navigation bot mined or threw a pearl close=%s crystal=%s ticks=%d at %.2f,%.2f,%.2f mining=%d pearls=%d crystals=%d explosions=%d", CLOSE_WALL, CRYSTAL_WALL, ticks, at.getX(), at.getY(), at.getZ(), navigationMining, pearlShots, navigationCrystalsSpawned, navigationCrystalExplosions));
                for (int z = -1; z <= 1; z++) for (int y = 100; y <= 102; y++) check(world.getBlockAt(0, y, z).getType() == (CRYSTAL_WALL ? Material.OBSIDIAN : Material.STONE), "navigation wall changed");
            }
            if (PREP) checkPrep();
            if (PEARLS) checkPearls();
            if (POTS) {
                checkPots();
                if (finished) return;
            }
            if (ticks % 40 == 0) {
                Player self = bot.getPlayer();
                Location botLocation = self.getLocation();
                Location targetLocation = target.player().getLocation();
                plugin.getLogger().info(String.format("BOT_CONTROLLER_TRACE tick=%d bot=%.2f,%.2f,%.2f yaw=%.1f pitch=%.1f target=%.2f,%.2f,%.2f distance=%.2f charge=%.2f swings=%d hits=%d",
                        ticks, botLocation.getX(), botLocation.getY(), botLocation.getZ(), botLocation.getYaw(), botLocation.getPitch(),
                        targetLocation.getX(), targetLocation.getY(), targetLocation.getZ(),
                        distanceToBox(self.getEyeLocation().toVector(), target.player().getBoundingBox()),
                        self.getAttackCooldown(), swings, hits));
            }
            int timeout = PREP || PEARLS ? TIMEOUT_TICKS + COUNTDOWN_TICKS : TIMEOUT_TICKS;
            boolean prepDone = !PREP || ticks >= PREP_DEADLINE_TICKS;
            if (NAVIGATION && CRYSTAL_WALL && navigationDetour && bot.getPlayer().hasLineOfSight(target.player())
                    && horizontalDistance(bot.getPlayer().getLocation(), target.player().getLocation()) <= 3.0) {
                plugin.getLogger().info(String.format("BOT_NAVIGATION PASS close=%s crystal=%s ticks=%d detour=%s mining=%d pearls=%d crystals=%d explosions=%d", CLOSE_WALL, CRYSTAL_WALL, ticks, navigationDetour, navigationMining, pearlShots, navigationCrystalsSpawned, navigationCrystalExplosions));
                succeed();
            } else if (NAVIGATION && !CRYSTAL_WALL && hits >= 1) {
                check(navigationDetour && (CLOSE_WALL || bot.getPlayer().getLocation().getX() > 1.0), "navigation bot hit without a wall detour");
                plugin.getLogger().info(String.format("BOT_NAVIGATION PASS close=%s crystal=%s ticks=%d detour=%s mining=%d pearls=%d", CLOSE_WALL, CRYSTAL_WALL, ticks, navigationDetour, navigationMining, pearlShots));
                succeed();
            } else if (NAVIGATION && ticks >= 200) {
                Location at = bot.getPlayer().getLocation();
                throw new IllegalStateException(String.format("navigation timeout close=%s crystal=%s ticks=%d at %.2f,%.2f,%.2f detour=%s hits=%d mining=%d pearls=%d crystals=%d explosions=%d", CLOSE_WALL, CRYSTAL_WALL, ticks, at.getX(), at.getY(), at.getZ(), navigationDetour, hits, navigationMining, pearlShots, navigationCrystalsSpawned, navigationCrystalExplosions));
            } else if (ARCHER) {
                if (arrowHits >= ARCHER_REQUIRED_HITS) {
                    plugin.getLogger().info(String.format("BOT_ARCHER PASS shots=%d hits=%d ticks=%d", arrowShots, arrowHits, ticks));
                    succeed();
                } else if (ticks >= ARCHER_DEADLINE_TICKS) {
                    throw new IllegalStateException("archer bot hit " + arrowHits + " of " + arrowShots + " shots in " + ticks + " ticks");
                }
            } else if (PEARLS && hits >= REQUIRED_HITS && pearlShots >= 1 && pearlTeleports >= 1 && pearlDamageSeen
                    && pearlMeleeAfterTeleport && bot.getPlayer().getInventory().getHeldItemSlot() == 0) {
                plugin.getLogger().info(String.format("BOT_PEARLS PASS shots=%d teleports=%d health=%.1f teleportTick=%d hits=%d ticks=%d",
                        pearlShots, pearlTeleports, bot.getPlayer().getHealth(), pearlTeleportTick, hits, ticks));
                succeed();
            } else if (PEARLS && pearlTeleports == 0 && ticks >= PEARL_DEADLINE_TICKS) {
                throw new IllegalStateException("pearl controller did not teleport after " + ticks + " ticks; shots=" + pearlShots);
            } else if (!PEARLS && !POTS && hits >= REQUIRED_HITS && prepDone) {
                succeed();
            } else if (ticks >= timeout) {
                throw new IllegalStateException("bot landed " + hits + " hits from " + swings + " swings, need " + REQUIRED_HITS + " hits in " + timeout + " ticks");
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamage(BlockDamageEvent event) {
        if (NAVIGATION && botId != null && event.getPlayer().getUniqueId().equals(botId)) { navigationMining++; event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (NAVIGATION && botId != null && event.getPlayer().getUniqueId().equals(botId)) { navigationMining++; event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCrystalWallExplosionDamage(EntityDamageEvent event) {
        if (NAVIGATION && CRYSTAL_WALL && !finished && navigationFixtureActor(event.getEntity().getUniqueId())
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCrystalWallKnockback(EntityKnockbackEvent event) {
        if (NAVIGATION && CRYSTAL_WALL && !finished && navigationFixtureActor(event.getEntity().getUniqueId())
                && event.getCause() == EntityKnockbackEvent.Cause.EXPLOSION) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNavigationCrystalSpawn(EntitySpawnEvent event) {
        if (NAVIGATION && CRYSTAL_WALL && !finished && event.getEntityType() == EntityType.END_CRYSTAL) navigationCrystalsSpawned++;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNavigationCrystalExplosion(EntityExplodeEvent event) {
        if (NAVIGATION && CRYSTAL_WALL && !finished && event.getEntityType() == EntityType.END_CRYSTAL) navigationCrystalExplosions++;
    }

    private boolean navigationFixtureActor(UUID entityId) {
        return botId != null && (entityId.equals(botId) || target != null && entityId.equals(target.entityId()));
    }

    private void checkPrep() {
        Player self = bot.getPlayer();
        if (ticks == COUNTDOWN_TICKS) {
            double moved = horizontalDistance(self.getLocation(), prepStart);
            check(moved < 0.1, "bot moved during the countdown: " + moved);
            prepMoved = moved;
        }
        if (ticks == PREP_DEADLINE_TICKS) {
            check(self.hasPotionEffect(PotionEffectType.SPEED), "no speed after prep");
            check(self.hasPotionEffect(PotionEffectType.STRENGTH), "no strength after prep");
            check(self.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE), "no fire resistance after prep");
            check(self.getInventory().getItem(0) != null && self.getInventory().getItem(0).getType() == Material.DIAMOND_SWORD,
                    "sword left slot 0 during prep");
            check(countPrepPotions() == 0, "drink potions remain after prep");
            check(prepConsumeCounts.getOrDefault(PotionType.STRONG_SWIFTNESS, 0) == 1, "speed consume event count was not one");
            check(prepConsumeCounts.getOrDefault(PotionType.STRONG_STRENGTH, 0) == 1, "strength consume event count was not one");
            check(prepConsumeCounts.getOrDefault(PotionType.LONG_FIRE_RESISTANCE, 0) == 1, "fire resistance consume event count was not one");
            check(countCookedBeef() == 7, "cooked beef stacks were lost or duplicated: " + countCookedBeef());
            PotionEffect speed = self.getPotionEffect(PotionEffectType.SPEED);
            PotionEffect strength = self.getPotionEffect(PotionEffectType.STRENGTH);
            PotionEffect fireResistance = self.getPotionEffect(PotionEffectType.FIRE_RESISTANCE);
            plugin.getLogger().info(String.format("BOT_PREP PASS speed=%d/%d strength=%d/%d fireResistance=%d/%d consumes=%s absorption=%.1f beef=%d moved=%.3f",
                    speed.getDuration(), prepConsumeCounts.get(PotionType.STRONG_SWIFTNESS),
                    strength.getDuration(), prepConsumeCounts.get(PotionType.STRONG_STRENGTH),
                    fireResistance.getDuration(), prepConsumeCounts.get(PotionType.LONG_FIRE_RESISTANCE),
                    prepConsumeCounts, self.getAbsorptionAmount(), countCookedBeef(), prepMoved));
        }
    }

    private void checkPearls() {
        Player self = bot.getPlayer();
        if (ticks == COUNTDOWN_TICKS) {
            double moved = horizontalDistance(self.getLocation(), pearlStart);
            check(pearlShots == 0 && pearlTeleports == 0 && moved < 0.1,
                    "pearl bot acted during countdown: shots=" + pearlShots + " teleports=" + pearlTeleports + " moved=" + moved);
        }
        if (pearlTeleportTick >= 0 && ticks > pearlTeleportTick && ticks <= pearlTeleportTick + 20
                && self.getHealth() < 20.0) pearlDamageSeen = true;
        if (pearlTeleportTick >= 0 && ticks > pearlTeleportTick + 20) {
            check(pearlDamageSeen, "ender pearl did not damage the bot within 20 ticks of teleport");
        }
        if (pearlTeleports > 0) {
            check(pearlTeleportBoundsValid, "pearl teleport destination is outside arena bounds or wrong height");
            check(horizontalDistance(self.getLocation(), pearlStart) >= 8.0,
                    "pearl teleport horizontal travel was less than 8 blocks");
        }
    }

    private void checkPots() {
        Player self = bot.getPlayer();
        maxHealth = Math.max(maxHealth, self.getHealth());
        int heals = countInventoryHeals();
        if (self.getHealth() >= 14.0 && potThrows > healedCycles) {
            healedCycles++;
            plugin.getLogger().info(String.format("BOT_POTS_CYCLE cycle=%d throws=%d health=%.1f inventoryHeals=%d",
                    healedCycles, potThrows, self.getHealth(), heals));
            if (healedCycles < 3) self.setHealth(5.0);
        }
        if (healedCycles >= 3 && potThrows >= 3 && heals <= startInventoryHeals - 3) {
            plugin.getLogger().info(String.format("BOT_POTS PASS throws=%d cycles=%d maxHealth=%.1f inventoryHeals=%d->%d",
                    potThrows, healedCycles, maxHealth, startInventoryHeals, heals));
            succeed();
        } else if (ticks >= POTS_DEADLINE_TICKS) {
            throw new IllegalStateException(String.format("healing refill smoke incomplete: throws=%d cycles=%d maxHealth=%.1f health=%.1f inventoryHeals=%d->%d",
                    potThrows, healedCycles, maxHealth, self.getHealth(), startInventoryHeals, heals));
        }
    }

    private BotInput potsTargetInput() {
        Location targetLocation = target.player().getLocation();
        Location botLocation = bot.getPlayer().getLocation();
        double dx = botLocation.getX() - targetLocation.getX();
        double dz = botLocation.getZ() - targetLocation.getZ();
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float delta = desiredYaw - targetLocation.getYaw();
        while (delta > 180.0f) delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        float turn = Math.max(-30.0f, Math.min(30.0f, delta));
        float remaining = desiredYaw - (targetLocation.getYaw() + turn);
        while (remaining > 180.0f) remaining -= 360.0f;
        while (remaining < -180.0f) remaining += 360.0f;
        boolean approach = Math.hypot(dx, dz) > 2.0 && Math.abs(remaining) <= 20.0f;
        return new BotInput(approach, false, false, false, false, false, approach, turn, 0.0f);
    }

    private int countInventoryHeals() {
        int count = 0;
        for (int slot = 9; slot <= 35; slot++) {
            ItemStack item = bot.getPlayer().getInventory().getItem(slot);
            if (item != null && item.getType() == Material.SPLASH_POTION) count++;
        }
        return count;
    }

    private static ItemStack heal() {
        ItemStack splash = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) splash.getItemMeta();
        meta.setBasePotionType(PotionType.STRONG_HEALING);
        splash.setItemMeta(meta);
        return splash;
    }

    private static ItemStack potion(PotionType type) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(type);
        potion.setItemMeta(meta);
        return potion;
    }

    private int countPrepPotions() {
        int count = 0;
        for (int slot = 0; slot <= 35; slot++) {
            ItemStack item = bot.getPlayer().getInventory().getItem(slot);
            if (item == null || item.getType() != Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)) continue;
            if (meta.getBasePotionType() == PotionType.STRONG_SWIFTNESS
                    || meta.getBasePotionType() == PotionType.STRONG_STRENGTH
                    || meta.getBasePotionType() == PotionType.LONG_FIRE_RESISTANCE) count += item.getAmount();
        }
        return count;
    }

    private int countCookedBeef() {
        int count = 0;
        for (int slot = 0; slot <= 35; slot++) {
            ItemStack item = bot.getPlayer().getInventory().getItem(slot);
            if (item != null && item.getType() == Material.COOKED_BEEF) count += item.getAmount();
        }
        return count;
    }

    private static double horizontalDistance(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (ARCHER || finished || bot == null || target == null) return;
        if (!event.getDamager().getUniqueId().equals(botId)) return;
        if (NAVIGATION && CRYSTAL_WALL && event.getEntity() instanceof EnderCrystal) return;
        try {
            check(event.getEntity().getUniqueId().equals(target.entityId()), "bot hit an entity other than its opponent");
            int now = Bukkit.getCurrentTick();
            check(lastHitTick == Integer.MIN_VALUE || now - lastHitTick >= MIN_HIT_INTERVAL,
                    "bot hit again after " + (now - lastHitTick) + " ticks");
            Player self = bot.getPlayer();
            double range = self.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).getValue();
            double distance = distanceToBox(self.getEyeLocation().toVector(), target.player().getBoundingBox());
            check(distance <= range + REACH_TOLERANCE, "bot hit from " + distance + " blocks (range " + range + ")");
            lastHitTick = now;
            hits++;
            if (PEARLS && pearlTeleportTick >= 0 && ticks > pearlTeleportTick) pearlMeleeAfterTeleport = true;
            plugin.getLogger().info("BOT_CONTROLLER_HIT " + hits + " tick=" + ticks + " distance=" + String.format("%.3f", distance));
        } catch (Throwable error) {
            fail(error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        if (botId != null && event.getAnimationType() == PlayerAnimationType.ARM_SWING
                && event.getPlayer().getUniqueId().equals(botId)) swings++;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!PREP || botId == null || !event.getPlayer().getUniqueId().equals(botId)) return;
        if (!(event.getItem().getItemMeta() instanceof PotionMeta meta)) return;
        PotionType type = meta.getBasePotionType();
        if (type == PotionType.STRONG_SWIFTNESS || type == PotionType.STRONG_STRENGTH
                || type == PotionType.LONG_FIRE_RESISTANCE) prepConsumeCounts.merge(type, 1, Integer::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (finished || botId == null) return;
        if (event.getEntity() instanceof Arrow arrow) {
            if (ARCHER && arrow.getShooter() instanceof Player shooter && shooter.getUniqueId().equals(botId)) arrowShots++;
            return;
        }
        if (!POTS) return;
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;
        if (potion.getShooter() instanceof Player shooter && shooter.getUniqueId().equals(botId)) potThrows++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if ((!PEARLS && !NAVIGATION) || finished || botId == null || !(event.getEntity() instanceof EnderPearl pearl)) return;
        if (pearl.getShooter() instanceof Player shooter && shooter.getUniqueId().equals(botId)) pearlShots++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (!PEARLS || finished || botId == null || !event.getPlayer().getUniqueId().equals(botId)
                || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        pearlTeleports++;
        pearlTeleportTick = ticks;
        Location from = event.getFrom();
        Location to = event.getTo();
        pearlTeleportBoundsValid = view.bounds.contains(to.getX(), to.getY(), to.getZ())
                && view.bounds.contains(to.getX(), to.getY() + 1.8, to.getZ())
                && to.getY() >= 99.5 && to.getY() <= 102.0
                && horizontalDistance(from, to) >= 8.0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!ARCHER || finished || botId == null || target == null) return;
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter) || !shooter.getUniqueId().equals(botId)) return;
        if (event.getHitEntity() != null && event.getHitEntity().getUniqueId().equals(target.entityId())) arrowHits++;
    }

    private static double distanceToBox(Vector point, BoundingBox box) {
        double dx = Math.max(Math.max(box.getMinX() - point.getX(), 0.0), point.getX() - box.getMaxX());
        double dy = Math.max(Math.max(box.getMinY() - point.getY(), 0.0), point.getY() - box.getMaxY());
        double dz = Math.max(Math.max(box.getMinZ() - point.getZ(), 0.0), point.getZ() - box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void succeed() {
        if (finished) return;
        finished = true;
        cleanup();
        plugin.getLogger().info("BOT_CONTROLLER_SMOKE PASS hits=" + hits + " ticks=" + ticks);
        success.run();
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
        if (bot != null) { bot.remove(); bot = null; }
        if (botId != null) { registry.releaseEntityId(botId); botId = null; }
        if (target != null) { platform.remove(target); target = null; }
        if (targetToken != null) { SmokeIdentity.release(targetToken); targetToken = null; }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class FixtureView implements BotFightView {
        private final Player opponent;
        private final BoundingBox bounds;
        private int roundTicks;
        private boolean live = true;

        private FixtureView(Player opponent, BoundingBox bounds) {
            this.opponent = opponent;
            this.bounds = bounds;
        }

        @Override public int getTeam() { return 1; }
        @Override public List<Player> getEnemies() { return List.of(opponent); }
        @Override public List<Player> getAllies() { return List.of(); }
        @Override public String getMatchId() { return "controller-smoke"; }
        @Override public int getRoundNumber() { return 1; }
        @Override public int getRoundTicks() { return roundTicks; }
        @Override public boolean isLive() { return live; }
        @Override public BoundingBox getArenaBounds() { return bounds; }
        @Override public int getDeadZoneY() { return 90; }
        @Override public String getLadderName() { return "controller-smoke"; }
    }
}
