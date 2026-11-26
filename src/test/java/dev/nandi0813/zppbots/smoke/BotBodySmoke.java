package dev.nandi0813.zppbots.smoke;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class BotBodySmoke extends JavaPlugin implements Listener {

    private final List<BotActor> actors = new ArrayList<>();
    private final List<BodyToken> tokens = new ArrayList<>();
    private BotPlatform platform;
    private UUID failRegistration;
    private int humanOnlineCount;
    private int loadAttempts;
    private BukkitTask loadTask;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        loadTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (ZPPBots.getInstance() != null && ZPPBots.getInstance().isEnabled()) {
                loadTask.cancel();
                String feature = System.getProperty("bot.smoke.feature");
                if (feature != null) {
                    new BotFeatureSmoke(this, feature, this::runChecks, this::fail).start();
                } else if (Boolean.getBoolean("bot.smoke.team")) {
                    new BotTeamSmoke(this, this::runChecks, this::fail).start();
                } else if (Boolean.getBoolean("bot.smoke.calibrate")) {
                    new BotCalibrationSmoke(this, this::runChecks, this::fail).start();
                } else if (Integer.getInteger("bot.smoke.load.pairs", 0) > 0) {
                    new BotLoadSmoke(this, this::runChecks, this::fail).start();
                } else if (Boolean.getBoolean("bot.smoke.actions")) {
                    new BotActionSmoke(this, this::runChecks, this::fail).start();
                } else if (Boolean.getBoolean("bot.smoke.items")) {
                    new BotItemSmoke(this, this::runChecks, this::fail).start();
                } else if (Boolean.getBoolean("bot.smoke.combat")) {
                    new BotCombatSmoke(this, this::runChecks, this::fail).start();
                } else if (Boolean.getBoolean("bot.smoke.controller")) {
                    new BotControllerSmoke(this, this::runChecks, this::fail).start();
                } else if (Boolean.getBoolean("bot.smoke.movement")) {
                    new BotMovementSmoke(this, this::runChecks, this::fail).start();
                } else {
                    runChecks();
                }
            } else if (++loadAttempts >= 60) {
                loadTask.cancel();
                fail(new IllegalStateException("ZPPBots did not enable within 60 attempts"));
            }
        }, 20L, 20L);
    }

    private void runChecks() {
        try {
            ZPPBots bots = ZPPBots.getInstance();
            Object diagnostics = bots.getDiagnostics();
            platform = (BotPlatform) findField(diagnostics.getClass(), BotPlatform.class).get(diagnostics);
            check(platform != null, "diagnostics platform is unavailable");

            String version = Bukkit.getMinecraftVersion();
            check(version.equals("1.21.11") || version.matches("26\\.[123](?:\\.[0-9]+)?"), "Unexpected Minecraft version " + version);
            check(platform.getClass().getName().equals("dev.nandi0813.zppbots.platform.PaperBotPlatform"),
                    "wrong backend: " + platform.getClass().getName());

            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation().clone();
            world.getChunkAt(spawn).addPluginChunkTicket(this);
            spawn.setY(world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ()) + 1);

            int entityCount = world.getEntities().size();
            int identityCount = SmokeIdentity.size();
            int bodyCount = mapField(platform).size();
            UUID sessionId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();
            BodyToken rejectionToken = trackToken(SmokeIdentity.reserve(this, sessionId, 1));
            BodyToken unowned = new BodyToken(UUID.randomUUID(), UUID.randomUUID(), 1);
            try {
                reject(() -> platform.spawn(unowned, spawn, Map.of()));
                reject(() -> platform.spawn(rejectionToken, spawn, Map.of(EquipmentSlot.BODY, new ItemStack(Material.IRON_CHESTPLATE))));
                reject(() -> platform.spawn(rejectionToken, spawn, null));
                reject(() -> platform.spawn(rejectionToken, new Location(world, 1_000_000, spawn.getY(), 1_000_000), Map.of()));
            } finally {
                SmokeIdentity.release(rejectionToken);
                tokens.remove(rejectionToken);
            }
            check(SmokeIdentity.size() == identityCount, "identity leaked after rejected spawns");
            check(world.getEntities().size() == entityCount, "entity leaked after rejected spawns");
            check(mapField(platform).size() == bodyCount, "native body leaked after rejected spawns");

            BodyToken failedRegistrationToken = trackToken(SmokeIdentity.reserve(this, UUID.randomUUID(), 1));
            failRegistration = failedRegistrationToken.playerId();
            boolean registrationFailed = false;
            BotActor unexpectedActor = null;
            try {
                unexpectedActor = platform.spawn(failedRegistrationToken, spawn, Map.of());
            } catch (IllegalStateException expected) {
                registrationFailed = true;
            } finally {
                failRegistration = null;
                if (unexpectedActor != null) trackActor(unexpectedActor);
            }
            check(registrationFailed, "post-registration validity check did not fail");
            check(Bukkit.getEntity(failedRegistrationToken.playerId()) == null, "partial registration left a Bukkit entity");
            check(mapField(platform).size() == bodyCount, "partial registration left a native body");
            check(SmokeIdentity.owns(failedRegistrationToken), "partial registration released the identity");
            SmokeIdentity.release(failedRegistrationToken);
            tokens.remove(failedRegistrationToken);
            check(SmokeIdentity.size() == identityCount, "identity leaked after partial registration failure");
            check(world.getEntities().size() == entityCount, "entity leaked after partial registration failure");

            Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            equipment.put(EquipmentSlot.HAND, sword);
            equipment.put(EquipmentSlot.OFF_HAND, new ItemStack(Material.SHIELD));
            humanOnlineCount = Bukkit.getOnlinePlayers().size();
            BodyToken successfulToken = trackToken(SmokeIdentity.reserve(this, UUID.randomUUID(), 1));
            BotActor actor = trackActor(platform.spawn(successfulToken, spawn, equipment));
            Player body = actor.player();
            check(body.isValid(), "spawned body is invalid");
            check(body.getName().matches("bot_\\d+"), "bot body name " + body.getName());
            check(Bukkit.getEntity(body.getUniqueId()) == body, "Bukkit entity lookup missed body");
            check(world.getPlayers().contains(body), "world player list missed body");
            check(Bukkit.getPlayer(body.getUniqueId()) == null, "body entered human player lookup");
            check(Bukkit.getOnlinePlayers().size() == humanOnlineCount, "online player count changed");
            check(!body.isPersistent(), "body is persistent");
            check(!body.isVisibleByDefault(), "body is visible by default");
            checkAdapters(body);
            check(close(body.getWidth(), 0.6) && close(body.getHeight(), 1.8), "body hitbox dimensions differ");
            check(body.getFoodLevel() == 20 && close(body.getHealth(), 20), "body health or food differs");
            sword.setAmount(2);
            check(body.getInventory().getItemInMainHand().getAmount() == 1, "spawn did not clone supplied equipment");

            if (!Boolean.getBoolean("bot.smoke.obfuscated")) {
                Class<?> profileManager = Class.forName("dev.nandi0813.practice.manager.profile.ProfileManager");
                Object profiles = profileManager.getMethod("getInstance").invoke(null);
                check(profileManager.getMethod("getProfile", UUID.class).invoke(profiles, body.getUniqueId()) == null,
                        "UUID profile lookup returned a profile");
                check(profileManager.getMethod("getProfile", Player.class).invoke(profiles, body) == null,
                        "Player profile lookup returned a profile");
                check(profileManager.getMethod("getProfile", OfflinePlayer.class).invoke(profiles, (OfflinePlayer) body) == null,
                        "OfflinePlayer profile lookup returned a profile");
                check(profileManager.getMethod("getProfile", Entity.class).invoke(profiles, (Entity) body) == null,
                        "Entity profile lookup returned a profile");
                try {
                    profileManager.getMethod("newProfile", Player.class, UUID.class).invoke(profiles, body, body.getUniqueId());
                    check(false, "Expected IllegalArgumentException");
                } catch (InvocationTargetException exception) {
                    if (!(exception.getCause() instanceof IllegalArgumentException)) throw exception;
                }
                Map<?, ?> profileCache = (Map<?, ?>) profileManager.getMethod("getProfiles").invoke(profiles);
                Map<?, ?> uuidCache = (Map<?, ?>) profileManager.getMethod("getUuids").invoke(profiles);
                check(!profileCache.containsKey(body.getUniqueId()), "profile cache contains bot UUID");
                check(!uuidCache.containsKey(body), "player UUID cache contains bot body");
            }
            File profileFile = new File(new File(Bukkit.getPluginManager().getPlugin("ZonePracticePro").getDataFolder(), "profiles"), body.getUniqueId().toString().toLowerCase() + ".yml");
            check(!profileFile.exists(), "bot profile file exists");

            check(SmokeIdentity.owns(successfulToken), "spawned body identity is not reserved");

            BodyToken stale = new BodyToken(successfulToken.sessionId(), successfulToken.playerId(), 2);
            reject(() -> platform.pause(new BotActor(stale, body)));
            Vector velocity = new Vector(0.1, 0.2, 0.3);
            body.setVelocity(velocity);
            platform.pause(actor);
            BotPlatform.Snapshot paused = platform.snapshot(actor);
            check(close(paused.velocityX(), velocity.getX()) && close(paused.velocityY(), velocity.getY())
                    && close(paused.velocityZ(), velocity.getZ()), "pause changed velocity");

            Location beforeReset = body.getLocation().clone();
            reject(() -> platform.reset(actor, spawn, Map.of(EquipmentSlot.BODY, new ItemStack(Material.IRON_CHESTPLATE))));
            check(body.getLocation().equals(beforeReset), "invalid reset moved the body");
            Location resetLocation = spawn.clone().add(1, 0, 0);
            platform.reset(actor, resetLocation, equipment);
            BotPlatform.Snapshot reset = platform.snapshot(actor);
            check(close(reset.x(), resetLocation.getX()) && close(reset.y(), resetLocation.getY())
                    && close(reset.z(), resetLocation.getZ()), "reset position differs from target");
            check(close(reset.velocityX(), 0) && close(reset.velocityY(), 0) && close(reset.velocityZ(), 0), "reset velocity is nonzero");

            Bukkit.getScheduler().runTaskLater(this, () -> completeFirstActor(bots, world, actor, spawn), 20L);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void completeFirstActor(ZPPBots bots, World world, BotActor actor, Location spawn) {
        try {
            check(actor.player().isValid(), "body did not remain valid");
            check(Bukkit.getOnlinePlayers().size() == humanOnlineCount, "body changed online player count");
            removeAndRelease(actor);
            check(!world.getPlayers().contains(actor.player()), "removed body remains in world player list");
            check(Bukkit.getEntity(actor.entityId()) == null, "removed body remains in Bukkit entity lookup");
            check(!SmokeIdentity.isManaged(actor.entityId()), "removed body identity remains reserved");
            check(mapField(platform).isEmpty(), "native body map is not empty after remove");

            BodyToken repeatToken = trackToken(SmokeIdentity.reserve(this, UUID.randomUUID(), 1));
            BotActor repeat = trackActor(platform.spawn(repeatToken, spawn, Map.of()));
            platform.remove(repeat);
            SmokeIdentity.release(repeatToken);
            actors.remove(repeat);
            tokens.remove(repeatToken);
            check(Bukkit.getEntity(repeat.entityId()) == null && !SmokeIdentity.isManaged(repeat.entityId()), "repeat body cleanup failed");

            Object diagnostics = bots.getDiagnostics();
            Map<UUID, Object> active = diagnosticsMap(diagnostics);
            Set<UUID> diagnosticIds = diagnosticsSet(diagnostics);
            Map<?, ?> nativeBodies = mapField(platform);

            Injected first = injectDiagnostic(diagnostics, active, diagnosticIds, world, spawn, nativeBodies);
            Bukkit.getPluginManager().callEvent(new WorldUnloadEvent(world));
            check(!first.actor().player().isValid() && Bukkit.getEntity(first.actor().entityId()) == null, "synthetic world unload kept body");
            check(!SmokeIdentity.isManaged(first.actor().entityId()), "synthetic world unload kept identity");
            check(active.isEmpty() && diagnosticIds.isEmpty(), "synthetic world unload kept diagnostic tracking");
            check(first.expiry().isCancelled(), "synthetic world unload did not cancel expiry task");
            check(nativeBodies.isEmpty(), "synthetic world unload kept native body");
            forget(first.actor(), first.token());

            Injected second = injectDiagnostic(diagnostics, active, diagnosticIds, world, spawn, nativeBodies);
            Bukkit.getPluginManager().disablePlugin(bots);
            check(!bots.isEnabled(), "ZPPBots remained enabled after disable call");
            check(!second.actor().player().isValid() && Bukkit.getEntity(second.actor().entityId()) == null, "plugin disable kept body");
            check(!SmokeIdentity.isManaged(second.actor().entityId()), "plugin disable kept identity");
            check(active.isEmpty() && diagnosticIds.isEmpty(), "plugin disable kept diagnostic tracking");
            check(second.expiry().isCancelled(), "plugin disable did not cancel expiry task");
            check(nativeBodies.isEmpty(), "plugin disable kept native body");
            forget(second.actor(), second.token());

            getLogger().info("BOT_BODY_SMOKE PASS");
            Bukkit.shutdown();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private Injected injectDiagnostic(Object diagnostics, Map<UUID, Object> active, Set<UUID> diagnosticIds,
                                      World world, Location location, Map<?, ?> nativeBodies) throws Exception {
        BodyToken token = trackToken(SmokeIdentity.reserve(this, UUID.randomUUID(), 1));
        BotActor actor = trackActor(platform.spawn(token, location, Map.of()));
        Class<?> diagnosticType = null;
        Constructor<?> constructor = null;
        for (Class<?> nested : diagnostics.getClass().getDeclaredClasses()) {
            try {
                constructor = nested.getDeclaredConstructor(BodyToken.class, BotActor.class);
                diagnosticType = nested;
                break;
            } catch (NoSuchMethodException ignored) {
            }
        }
        check(constructor != null, "diagnostic record constructor not found");
        constructor.setAccessible(true);
        Object diagnostic = constructor.newInstance(token, actor);
        BukkitTask expiry = Bukkit.getScheduler().runTaskLater(ZPPBots.getInstance(), () -> {}, 1200L);
        Field expiryField = findField(diagnosticType, BukkitTask.class);
        expiryField.set(diagnostic, expiry);
        UUID ownerId = UUID.randomUUID();
        active.put(ownerId, diagnostic);
        diagnosticIds.add(token.playerId());
        check(nativeBodies.size() == 1, "diagnostic body was not tracked natively");
        return new Injected(token, actor, expiry);
    }

    private static void checkAdapters(Player body) throws ReflectiveOperationException {
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        Object handle = craftPlayer.getMethod("getHandle").invoke(body);
        check(!handle.getClass().getField("isRealPlayer").getBoolean(handle), "native handle is a real player");

        Object loadedListener = handle.getClass().getField("connection").get(handle);
        Class<?> listenerType = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        check((boolean) listenerType.getMethod("hasClientLoaded").invoke(loadedListener), "loaded listener is not client loaded");
        listenerType.getMethod("tickClientLoadTimeout").invoke(loadedListener);

        Class<?> commonListenerType = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
        Object connection = commonListenerType.getField("connection").get(loadedListener);
        Class<?> connectionType = Class.forName("net.minecraft.network.Connection");
        Class<?> packetType = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> listenerCallbackType = Class.forName("io.netty.channel.ChannelFutureListener");
        Class<?> futureType = Class.forName("io.netty.util.concurrent.Future");
        Class<?> embeddedChannelType = Class.forName("io.netty.channel.embedded.EmbeddedChannel");

        AtomicInteger callbacks = new AtomicInteger();
        Object callback = Proxy.newProxyInstance(listenerCallbackType.getClassLoader(), new Class<?>[]{listenerCallbackType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "operationComplete" -> {
                        check((boolean) futureType.getMethod("isSuccess").invoke(args[0]), "sink callback future failed");
                        callbacks.incrementAndGet();
                        yield null;
                    }
                    case "toString" -> "BotBodySmoke listener";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });

        connectionType.getMethod("send", packetType).invoke(connection, new Object[]{null});
        connectionType.getMethod("send", packetType, listenerCallbackType).invoke(connection, null, callback);
        connectionType.getMethod("send", packetType, listenerCallbackType, boolean.class).invoke(connection, null, callback, false);
        connectionType.getMethod("send", packetType, listenerCallbackType).invoke(connection, null, null);
        connectionType.getMethod("send", packetType, listenerCallbackType, boolean.class).invoke(connection, null, null, true);
        check(callbacks.get() == 2, "sink callback count differs: " + callbacks.get());

        Object channel = connectionType.getField("channel").get(connection);
        check(embeddedChannelType.isInstance(channel), "connection channel is not embedded");
        check(embeddedChannelType.getMethod("readOutbound").invoke(channel) == null, "sink wrote an outbound packet");
        check((boolean) embeddedChannelType.getMethod("isActive").invoke(channel), "sink channel is inactive");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> diagnosticsMap(Object diagnostics) throws ReflectiveOperationException {
        return (Map<UUID, Object>) findField(diagnostics.getClass(), Map.class).get(diagnostics);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> diagnosticsSet(Object diagnostics) throws ReflectiveOperationException {
        return (Set<UUID>) findField(diagnostics.getClass(), Set.class).get(diagnostics);
    }

    private static Map<?, ?> mapField(Object instance) throws ReflectiveOperationException {
        return (Map<?, ?>) findField(instance.getClass(), Map.class).get(instance);
    }

    private static Field findField(Class<?> type, Class<?> fieldType) throws NoSuchFieldException {
        Field found = null;
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && fieldType.isAssignableFrom(field.getType())) {
                if (found != null) throw new IllegalStateException("Expected one " + fieldType.getSimpleName() + " field in " + type.getName());
                found = field;
            }
        }
        if (found == null) throw new NoSuchFieldException(type.getName() + " has no " + fieldType.getSimpleName() + " field");
        found.setAccessible(true);
        return found;
    }

    private BodyToken trackToken(BodyToken token) {
        tokens.add(token);
        return token;
    }

    private BotActor trackActor(BotActor actor) {
        actors.add(actor);
        return actor;
    }

    private void removeAndRelease(BotActor actor) {
        platform.remove(actor);
        platform.remove(actor);
        SmokeIdentity.release(actor.identity());
        SmokeIdentity.release(actor.identity());
        forget(actor, actor.identity());
    }

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        UUID expected = failRegistration;
        if (expected != null && expected.equals(event.getEntity().getUniqueId())) {
            try {
                Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                Object handle = craftPlayer.getMethod("getHandle").invoke(event.getEntity());
                Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
                entity.getField("valid").setBoolean(handle, false);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to inject invalid registration state", exception);
            }
        }
    }

    private void forget(BotActor actor, BodyToken token) {
        actors.remove(actor);
        tokens.remove(token);
    }

    private void fail(Throwable failure) {
        getLogger().severe("BOT_BODY_SMOKE FAIL");
        failure.printStackTrace();
        for (int i = actors.size() - 1; i >= 0; i--) {
            try {
                if (platform != null) platform.remove(actors.get(i));
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        for (BodyToken token : tokens) {
            try {
                SmokeIdentity.release(token);
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        Bukkit.shutdown();
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("Expected IllegalArgumentException");
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) <= 1.0e-5;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Injected(BodyToken token, BotActor actor, BukkitTask expiry) {
    }
}
