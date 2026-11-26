package dev.nandi0813.zppbots.smoke;

import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class BotItemSmoke implements Listener {

    private static final List<String> SCENARIOS = List.of("drink", "splash-heal", "bow-full", "bow-partial", "bow-hit",
            "inventory-move", "ender-pearl");

    private final JavaPlugin plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private final List<BotActor> actors = new ArrayList<>();
    private final List<BodyToken> tokens = new ArrayList<>();
    private BotPlatform platform;
    private World world;
    private BotActor shooter;
    private BotActor target;
    private BukkitTask task;
    private int initialIdentityCount;
    private int initialBodyCount;
    private int scenarioIndex = -1;
    private int frame;
    private int consumeFrame = -1;
    private int splashes;
    private double arrowSpeed = -1;
    private int arrowHits;
    private int pearlLaunches;
    private int pearlTeleports;
    private int firstPearlTeleportFrame = -1;
    private double lastPearlZ;
    private boolean finished;

    public BotItemSmoke(JavaPlugin plugin, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.success = success;
        this.failure = failure;
    }

    public void start() {
        try {
            check(Boolean.getBoolean("bot.smoke.items"), "bot.smoke.items must be true");
            check(Bukkit.getOnlinePlayers().isEmpty(), "item smoke requires zero online players");
            Object diagnostics = ZPPBots.getInstance().getDiagnostics();
            platform = (BotPlatform) findField(diagnostics.getClass(), BotPlatform.class).get(diagnostics);
            check(platform != null, "diagnostics platform is unavailable");
            world = Bukkit.getWorlds().getFirst();
            initialIdentityCount = SmokeIdentity.size();
            initialBodyCount = bodies().size();
            prepareTerrain();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void prepareTerrain() {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.getChunkAt(x, z).setForceLoaded(true);
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
            world.getBlockAt(x, 99, z).setType(Material.STONE, false);
            for (int y = 100; y <= 110; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        world.setSpawnFlags(false, false);
        world.setTime(6000L);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false);
    }

    private void tick() {
        if (finished) return;
        try {
            if (scenarioIndex < 0) {
                selfChecks();
                scenarioIndex = 0;
                return;
            }
            if (shooter == null) {
                prepareScenario();
                frame = 0;
                consumeFrame = -1;
                splashes = 0;
                arrowSpeed = -1;
                arrowHits = 0;
                pearlLaunches = 0;
                pearlTeleports = 0;
                firstPearlTeleportFrame = -1;
                lastPearlZ = 0;
                return;
            }
            if (frame >= 60) {
                assertCase();
                disposeActors();
                scenarioIndex++;
                if (scenarioIndex == SCENARIOS.size()) succeed();
                return;
            }
            BotInput input = shooterInput();
            if (target != null && target.player().isValid()) LegacyDrive.tick(platform, target, BotInput.neutral());
            LegacyDrive.tick(platform, shooter, input);
            frame++;
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void selfChecks() throws Exception {
        reject(() -> move(36, 3));
        reject(() -> move(3, 3));
        reject(() -> move(-2, 3));
        reject(() -> move(20, -1));
        reject(() -> new BotInput(false, false, false, false, false, false, false, 0, 0, -1, true, UUID.randomUUID(),
                BotInput.UseAction.KEEP, 20, 3));
        reject(() -> new BotInput(false, false, false, false, false, false, false, 0, 0, -1, false, null,
                BotInput.UseAction.START_MAIN, 20, 3));
        check(BotInput.neutral().inventoryFrom() == -1 && BotInput.neutral().inventoryTo() == -1,
                "neutral input carries an inventory move");
    }

    private void prepareScenario() {
        String name = scenario();
        shooter = spawn(new Location(world, 0.5, 100, 0.5, 0, 0), Map.of());
        switch (name) {
            case "drink" -> {
                shooter.player().getInventory().setItem(0, potion(Material.POTION, PotionType.SWIFTNESS));
                shooter.player().getInventory().setHeldItemSlot(0);
            }
            case "splash-heal" -> {
                shooter.player().setHealth(10);
                shooter.player().getInventory().setItem(0, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
            }
            case "bow-full", "bow-partial" -> prepareBow();
            case "bow-hit" -> {
                prepareBow();
                target = spawn(new Location(world, 0.5, 100, 15.5, 180, 0), Map.of());
                shooter.player().showEntity(plugin, target.player());
                check(shooter.player().canSee(target.player()), "bow-hit shooter cannot see target");
            }
            case "inventory-move" -> shooter.player().getInventory().setItem(20, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
            case "ender-pearl" -> {
                shooter.player().getInventory().setItem(0, new ItemStack(Material.ENDER_PEARL, 2));
                shooter.player().getInventory().setHeldItemSlot(0);
                shooter.player().setHealth(20);
            }
            default -> throw new IllegalStateException("Unexpected item case " + name);
        }
    }

    private void prepareBow() {
        shooter.player().getInventory().setItem(0, new ItemStack(Material.BOW));
        shooter.player().getInventory().setItem(1, new ItemStack(Material.ARROW, 16));
    }

    private BotInput shooterInput() {
        String name = scenario();
        if (name.equals("drink")) return use(frame == 0 ? BotInput.UseAction.START_MAIN : BotInput.UseAction.KEEP, 0);
        if (name.equals("splash-heal")) {
            if (frame == 0) return use(BotInput.UseAction.KEEP, Math.max(-90.0f, Math.min(90.0f, 90.0f - shooter.player().getPitch())));
            return use(frame == 1 ? BotInput.UseAction.START_MAIN : BotInput.UseAction.KEEP, 0);
        }
        if (name.equals("bow-full") || name.equals("bow-hit")) {
            return use(frame == 0 ? BotInput.UseAction.START_MAIN : frame == 22 ? BotInput.UseAction.RELEASE : BotInput.UseAction.KEEP, 0);
        }
        if (name.equals("bow-partial")) {
            return use(frame == 0 ? BotInput.UseAction.START_MAIN : frame == 10 ? BotInput.UseAction.RELEASE : BotInput.UseAction.KEEP, 0);
        }
        if (name.equals("inventory-move") && frame == 0) return move(20, 3);
        if (name.equals("ender-pearl")) {
            if (frame == 0) return use(BotInput.UseAction.KEEP, 45.0f - shooter.player().getPitch());
            if (frame == 1 || frame == 2 || frame == 31) return use(BotInput.UseAction.START_MAIN, 0);
            if (frame >= 46 && frame <= 59) return new BotInput(true, false, false, false, false, false, false, 0, 0);
            return use(BotInput.UseAction.KEEP, 0);
        }
        return BotInput.neutral();
    }

    private void assertCase() {
        String name = scenario();
        String measured;
        switch (name) {
            case "drink" -> {
                boolean speed = shooter.player().hasPotionEffect(PotionEffectType.SPEED);
                measured = "consumeFrame=" + consumeFrame + " speed=" + speed;
                check(consumeFrame >= 30 && consumeFrame <= 36 && speed, name + " failed: " + measured);
            }
            case "splash-heal" -> {
                double health = shooter.player().getHealth();
                ItemStack slot0 = shooter.player().getInventory().getItem(0);
                Material slot0Type = slot0 == null ? Material.AIR : slot0.getType();
                measured = "splashes=" + splashes + " health=" + health + " slot0=" + slot0Type;
                check(splashes >= 1 && health >= 16 && slot0Type.isAir(), name + " failed: " + measured);
            }
            case "bow-full" -> {
                measured = "arrowSpeed=" + arrowSpeed;
                check(arrowSpeed >= 2.85 && arrowSpeed <= 3.15, name + " failed: " + measured);
            }
            case "bow-partial" -> {
                measured = "arrowSpeed=" + arrowSpeed;
                check(arrowSpeed >= 1.0 && arrowSpeed <= 1.5, name + " failed: " + measured);
            }
            case "bow-hit" -> {
                measured = "arrowHits=" + arrowHits;
                check(arrowHits >= 1, name + " failed: " + measured);
            }
            case "inventory-move" -> {
                ItemStack from = shooter.player().getInventory().getItem(20);
                ItemStack to = shooter.player().getInventory().getItem(3);
                Material toType = to == null ? Material.AIR : to.getType();
                measured = "slot3=" + toType + " slot20=" + (from == null ? Material.AIR : from.getType());
                check(toType == Material.SPLASH_POTION && (from == null || from.getType().isAir()), name + " failed: " + measured);
            }
            case "ender-pearl" -> {
                ItemStack slot0 = shooter.player().getInventory().getItem(0);
                Material slot0Type = slot0 == null ? Material.AIR : slot0.getType();
                double health = shooter.player().getHealth();
                double finalZ = shooter.player().getLocation().getZ();
                measured = "launches=" + pearlLaunches + " teleports=" + pearlTeleports + " firstTeleportFrame="
                        + firstPearlTeleportFrame + " health=" + health + " slot0=" + slot0Type + " lastPearlZ="
                        + lastPearlZ + " finalZ=" + finalZ + " movement=" + (finalZ - lastPearlZ);
                check(pearlLaunches == 2 && pearlTeleports == 2 && slot0Type.isAir() && health > 0 && health <= 12
                                && firstPearlTeleportFrame > 1 && firstPearlTeleportFrame < 30 && finalZ > lastPearlZ + 0.5,
                        name + " failed: " + measured);
            }
            default -> throw new IllegalStateException("Unexpected item case " + name);
        }
        plugin.getLogger().info("BOT_ITEM_CASE " + name + " PASS " + measured);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (shooterIs(event.getPlayer())) consumeFrame = frame;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (shooter != null && shooter.player().equals(event.getEntity().getShooter())) splashes++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (shooterIs(event.getEntity())) {
            arrowSpeed = event.getProjectile().getVelocity().length();
            plugin.getLogger().info("BOT_ITEM_SHOT frame=" + frame + " shooterEye=" + shooter.player().getEyeLocation().toVector()
                    + " yaw=" + shooter.player().getYaw() + " pitch=" + shooter.player().getPitch()
                    + " velocity=" + event.getProjectile().getVelocity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow && arrow.getShooter() instanceof Player player && shooterIs(player)) {
            plugin.getLogger().info("BOT_ITEM_ARROW scenario=" + scenario() + " frame=" + frame
                    + " hitEntity=" + (event.getHitEntity() == null ? "none" : event.getHitEntity().getType() + "/" + event.getHitEntity().getUniqueId())
                    + " hitBlock=" + (event.getHitBlock() == null ? "none" : event.getHitBlock().getType() + "@" + event.getHitBlock().getLocation().toVector())
                    + " arrowAt=" + arrow.getLocation().toVector()
                    + " target=" + (target == null ? "none" : target.player().getUniqueId() + "@" + target.player().getLocation().toVector()
                    + " valid=" + target.player().isValid() + " dead=" + target.player().isDead()));
        }
        if (event.getEntity() instanceof Arrow arrow && arrow.getShooter() instanceof Player player
                && shooterIs(player) && targetIs(event.getHitEntity())) arrowHits++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (scenario().equals("ender-pearl") && event.getEntity() instanceof EnderPearl pearl
                && pearl.getShooter() instanceof Player player && shooterIs(player)) {
            pearlLaunches++;
            plugin.getLogger().info("BOT_ITEM_PEARL_LAUNCH frame=" + frame + " velocity=" + pearl.getVelocity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (scenario().equals("ender-pearl") && shooterIs(event.getPlayer())
                && event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            pearlTeleports++;
            if (firstPearlTeleportFrame < 0) firstPearlTeleportFrame = frame;
            lastPearlZ = event.getTo().getZ();
        }
    }

    private void succeed() {
        if (finished) return;
        finished = true;
        try {
            cleanup();
            check(SmokeIdentity.size() == initialIdentityCount && bodies().size() == initialBodyCount,
                    "item smoke actor cleanup changed identity or body counts");
            plugin.getLogger().info("BOT_ITEM_SMOKE PASS scenarios=" + SCENARIOS.size());
            success.run();
        } catch (Throwable error) { failure.accept(error); }
    }

    private void fail(Throwable error) {
        if (finished) return;
        plugin.getLogger().severe("BOT_ITEM_SMOKE FAIL scenario=" + (scenarioIndex < 0 ? "self-check" : scenario())
                + " frame=" + frame + " error=" + error.getMessage());
        finished = true;
        try { cleanup(); } catch (Throwable cleanupError) { error.addSuppressed(cleanupError); }
        failure.accept(error);
    }

    private void cleanup() {
        if (task != null) { task.cancel(); task = null; }
        HandlerList.unregisterAll(this);
        if (platform != null) disposeActors();
    }

    private boolean shooterIs(Entity entity) {
        return shooter != null && entity != null && entity.equals(shooter.player());
    }

    private boolean targetIs(Entity entity) {
        return target != null && entity != null && entity.equals(target.player());
    }

    private BotActor spawn(Location location, Map<EquipmentSlot, ItemStack> equipment) {
        BodyToken token = SmokeIdentity.reserve(plugin, UUID.randomUUID(), 1);
        tokens.add(token);
        BotActor actor = platform.spawn(token, location, equipment);
        actors.add(actor);
        return actor;
    }

    private Map<?, ?> bodies() throws Exception {
        Field field = findField(platform.getClass(), Map.class);
        return (Map<?, ?>) field.get(platform);
    }

    private void disposeActors() {
        Throwable failure = null;
        for (int i = actors.size() - 1; i >= 0; i--) {
            try { platform.remove(actors.get(i)); } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        for (BodyToken token : List.copyOf(tokens)) {
            try { SmokeIdentity.release(token); } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        actors.clear(); tokens.clear();
        shooter = null;
        target = null;
        if (failure != null) throw new IllegalStateException("Could not dispose item actors", failure);
    }

    private String scenario() { return SCENARIOS.get(scenarioIndex); }

    private static ItemStack potion(Material material, PotionType type) {
        ItemStack item = new ItemStack(material);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    private static BotInput use(BotInput.UseAction action, float pitchDelta) {
        return new BotInput(false, false, false, false, false, false, false, 0, pitchDelta, -1, false, null, action, -1, -1);
    }

    private static BotInput move(int from, int to) {
        return new BotInput(false, false, false, false, false, false, false, 0, 0, -1, false, null,
                BotInput.UseAction.KEEP, from, to);
    }

    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new IllegalStateException("Expected IllegalArgumentException"); }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static Field findField(Class<?> type, Class<?> fieldType) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) for (Field field : current.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && fieldType.isAssignableFrom(field.getType())) { field.setAccessible(true); return field; }
        }
        throw new NoSuchFieldException(type.getName() + " has no " + fieldType.getSimpleName() + " field");
    }
}
