package dev.nandi0813.zppbots.smoke;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotPlatform;
import dev.nandi0813.zppbots.probe.BotMovementScript;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class BotMovementSmoke implements Listener {

    private static final List<String> SCENARIOS = List.of("idle", "walk", "sprint", "strafe", "diagonal", "release",
            "jump", "sprint-jump", "sneak", "air-turn", "step", "wall", "fall", "knockback-ground",
            "knockback-air", "knockback-wall");
    private static final Location ORIGIN = new Location(null, 0.5, 100.0, 0.5, 0.0f, 0.0f);

    private final JavaPlugin plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private final List<BotActor> actors = new ArrayList<>();
    private final List<BodyToken> tokens = new ArrayList<>();
    private final List<BotInput> frameSnapshots = new ArrayList<>();
    private final List<BotPlatform.Snapshot> observations = new ArrayList<>();
    private BotPlatform platform;
    private Object probe;
    private Method startManaged;
    private Method stopManaged;
    private Method craftGetHandle;
    private Method nativeAuthority;
    private Method attack;
    private Class<?> nativeEntity;
    private World world;
    private BotActor target;
    private BotActor attacker;
    private BotMovementScript script;
    private BukkitTask task;
    private int humanOnlineCount;
    private int scenarioIndex;
    private int frameIndex;
    private boolean frameExecuted;
    private boolean selfCheckPending;
    private boolean finished;
    private double maxY;
    private double minY;
    private double maxZ;
    private double maxSpeed;
    private double hitZ;
    private double initialHealth;
    private Location selfCheckOrigin;
    private UUID recording;

    public BotMovementSmoke(JavaPlugin plugin, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.success = success;
        this.failure = failure;
    }

    public void start() {
        try {
            Object diagnostics = ZPPBots.getInstance().getDiagnostics();
            platform = (BotPlatform) findField(diagnostics.getClass(), BotPlatform.class).get(diagnostics);
            check(platform != null, "diagnostics platform is unavailable");
            probe = ZPPBots.getInstance().getProbe();
            startManaged = findMethod(probe.getClass(), UUID.class, BotActor.class, String.class, int.class, String.class);
            stopManaged = findMethod(probe.getClass(), void.class, BotActor.class, UUID.class, String.class);
            startManaged.setAccessible(true);
            stopManaged.setAccessible(true);

            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            craftGetHandle = craftPlayer.getMethod("getHandle");
            nativeEntity = Class.forName("net.minecraft.world.entity.Entity");
            attack = Class.forName("net.minecraft.world.entity.player.Player").getMethod("attack", nativeEntity);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            humanOnlineCount = Bukkit.getOnlinePlayers().size();
            world = Bukkit.getWorlds().getFirst();
            scenarioIndex = -1;
            prepareTerrain();
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void tick() {
        if (finished) return;
        try {
            check(Bukkit.getOnlinePlayers().size() == humanOnlineCount, "human online-player count changed");
            if (selfCheckPending) return;
            if (scenarioIndex < 0) {
                beginScenario();
                return;
            }
            if (target == null) {
                prepareScenario();
                return;
            }
            if (frameIndex >= script.frames().size()) {
                finishScenario();
                return;
            }

            BotInput input = toInput(script.frames().get(frameIndex));
            frameSnapshots.add(input);
            if (isKnockback() && frameIndex < 30) LegacyDrive.tick(platform, attacker, BotInput.neutral());
            if (isKnockback() && frameIndex == 30) hitTarget();
            LegacyDrive.tick(platform, target, input);
            if (isKnockback() && frameIndex == 30) {
                hitZ = target.player().getLocation().getZ();
                platform.pause(attacker);
            }
            frameIndex++;
            frameExecuted = true;
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void beginScenario() throws Exception {
        resetTerrain();
        target = spawnActor(new Location(world, ORIGIN.getX(), ORIGIN.getY(), ORIGIN.getZ(), 0.0f, 0.0f), Map.of());
        checkNativeActor(target);
        check(Bukkit.getEntity(target.entityId()) == target.player(), "managed body missing from Bukkit entity lookup");
        check(Bukkit.getPlayer(target.entityId()) == null && Bukkit.getOnlinePlayers().size() == humanOnlineCount,
                "managed body entered human online lookup");
        selfChecks();
        platform.reset(target, new Location(world, ORIGIN.getX(), ORIGIN.getY(), ORIGIN.getZ(), 0.0f, 0.0f), Map.of());
        selfCheckOrigin = target.player().getLocation().clone();
        LegacyDrive.tick(platform, target, new BotInput(true, true, true, true, false, false, false, 0.0f, 0.0f));
        selfCheckPending = true;
    }

    private void selfChecks() {
        reject(() -> new BotInput(false, false, false, false, false, false, false, Float.NaN, 0.0f));
        reject(() -> new BotInput(false, false, false, false, false, false, false, 180.01f, 0.0f));
        reject(() -> new BotInput(false, false, false, false, false, false, false, 0.0f, 90.01f));
        BodyToken stale = new BodyToken(target.identity().sessionId(), target.entityId(), target.identity().generation() + 1);
        reject(() -> LegacyDrive.tick(platform, new BotActor(stale, target.player()), BotInput.neutral()));

        target.player().setVelocity(new Vector(0.1, 0.2, 0.3));
        platform.pause(target);
        BotPlatform.Snapshot paused = platform.snapshot(target);
        check(close(paused.velocityX(), 0.1) && close(paused.velocityY(), 0.2) && close(paused.velocityZ(), 0.3),
                "pause changed momentum");
        platform.reset(target, new Location(world, ORIGIN.getX(), ORIGIN.getY(), ORIGIN.getZ(), 0.0f, 0.0f), Map.of());
        BotPlatform.Snapshot reset = platform.snapshot(target);
        check(close(reset.velocityX(), 0.0) && close(reset.velocityY(), 0.0) && close(reset.velocityZ(), 0.0),
                "reset did not clear velocity");

        LegacyDrive.tick(platform, target, BotInput.neutral());
        BotPlatform.Snapshot afterFirst = platform.snapshot(target);
        try {
            LegacyDrive.tick(platform, target, BotInput.neutral());
            throw new IllegalStateException("duplicate same-tick movement was accepted");
        } catch (IllegalStateException expected) {
            check("Bot movement already ran this Bukkit tick".equals(expected.getMessage()), "unexpected duplicate-tick failure");
        }
        BotPlatform.Snapshot afterDuplicate = platform.snapshot(target);
        check(sameMotion(afterFirst, afterDuplicate), "duplicate tick changed body state");
        platform.reset(target, new Location(world, ORIGIN.getX(), ORIGIN.getY(), ORIGIN.getZ(), 0.0f, 0.0f), Map.of());
    }

    private void prepareScenario() throws Exception {
        String scenario = SCENARIOS.get(scenarioIndex);
        resetTerrain();
        double y = scenario.equals("fall") ? 104.0 : ORIGIN.getY();
        target = spawnActor(new Location(world, ORIGIN.getX(), y, ORIGIN.getZ(), 0.0f, 0.0f), Map.of());
        checkNativeActor(target);
        check(Bukkit.getEntity(target.entityId()) == target.player(), "managed body missing from Bukkit entity lookup");
        check(Bukkit.getPlayer(target.entityId()) == null && Bukkit.getOnlinePlayers().size() == humanOnlineCount,
                "managed body entered human online lookup");
        if (scenario.equals("step")) {
            for (int z = 2; z <= 40; z++) for (int x = -2; x <= 2; x++) world.getBlockAt(x, 100, z).setType(Material.STONE_SLAB, false);
        }
        if (scenario.equals("wall") || scenario.equals("knockback-wall")) setWall();
        if (isKnockback()) {
            Map<EquipmentSlot, ItemStack> sword = Map.of(EquipmentSlot.HAND, new ItemStack(Material.DIAMOND_SWORD));
            attacker = spawnActor(new Location(world, target.player().getLocation().getX(), 100.0,
                    target.player().getLocation().getZ() - 3.0, 0.0f, 0.0f), sword);
            checkNativeActor(attacker);
        }
        JSONObject fixture = new JSONObject().put("world", world.getUID().toString())
                .put("origin", new JSONObject().put("x", ORIGIN.getX()).put("y", y).put("z", ORIGIN.getZ())
                        .put("yaw", 0).put("pitch", 0))
                .put("terrain", scenario.equals("step") ? "stone-floor-with-slab-strip" :
                        (scenario.equals("wall") || scenario.equals("knockback-wall") ? "stone-floor-and-wall" : "stone-floor"))
                .put("impulseTick", isKnockback() ? 30 : JSONObject.NULL);
        script = BotMovementScript.create(scenario, fixture.toString());
        recording = (UUID) startManaged.invoke(probe, target, scenario, 160, script.fixture());
        check(recording != null, "managed movement probe refused scenario " + scenario);
        frameIndex = 0;
        frameSnapshots.clear();
        observations.clear();
        maxY = Double.NEGATIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        maxZ = Double.NEGATIVE_INFINITY;
        maxSpeed = 0.0;
        hitZ = Double.NaN;
        initialHealth = target.player().getHealth();
    }

    private void hitTarget() throws Exception {
        Location targetLocation = target.player().getLocation();
        Location behind = targetLocation.clone().subtract(0.0, 0.0, 2.0);
        platform.reset(attacker, behind, Map.of(EquipmentSlot.HAND, new ItemStack(Material.DIAMOND_SWORD)));
        attacker.player().setSprinting(true);
        Object targetHandle = craftGetHandle.invoke(target.player());
        attack.invoke(craftGetHandle.invoke(attacker.player()), targetHandle);
        check(target.player().getHealth() < initialHealth, "native melee attack did not damage target");
    }

    private void finishScenario() throws Exception {
        check(observations.size() == 160 && frameSnapshots.size() == 160,
                "scenario did not record 160 tick-end observations: " + observations.size());
        assertScenario();
        String name = script.scenario();
        double displacement = horizontalDistance(target.player().getLocation(), ORIGIN);
        plugin.getLogger().info("BOT_MOVEMENT_SMOKE SCENARIO PASS " + name + " displacement=" + displacement
                + " maxY=" + maxY + " maxSpeed=" + maxSpeed);
        removeActors();
        recording = null;
        target = null;
        attacker = null;
        script = null;
        scenarioIndex++;
        if (scenarioIndex == SCENARIOS.size()) {
            succeed();
        }
    }

    private void assertScenario() {
        BotPlatform.Snapshot last = observations.getLast();
        double displacement = Math.hypot(last.x() - ORIGIN.getX(), last.z() - ORIGIN.getZ());
        switch (script.scenario()) {
            case "idle" -> check(displacement < 1.0e-6, "idle moved horizontally");
            case "walk", "release" -> check(displacement > 5.0, script.scenario() + " displacement is too small");
            case "sprint" -> check(displacement > lastWalkDistance, "sprint did not exceed walk distance");
            case "strafe" -> check(last.x() - ORIGIN.getX() > 5.0 && Math.abs(last.z() - ORIGIN.getZ()) < 1.0e-5,
                    "strafe did not move along positive x only");
            case "diagonal" -> check(Math.abs(displacement - lastWalkDistance) / lastWalkDistance <= 0.05,
                    "diagonal movement was not normalized");
            case "sneak" -> check(displacement > 0.0 && displacement < lastWalkDistance, "sneak distance is invalid");
            case "jump", "sprint-jump" -> check(maxY > 100.5 && close(last.y(), 100.0) && last.onGround(),
                    "jump did not rise and land");
            case "air-turn" -> check(close(last.yaw(), 45.0f) && Math.abs(last.x() - ORIGIN.getX()) > 0.0,
                    "air turn did not rotate and move along x");
            case "step" -> check(maxY >= 100.49 && close(last.y(), 100.5), "step did not climb onto the slab");
            case "wall" -> check(maxZ <= 1.701, "wall collision exceeded its bound");
            case "fall" -> check(minY <= 100.001 && close(last.y(), 100.0) && last.onGround(), "fall did not land");
            case "knockback-ground", "knockback-air" -> check(afterHitMoved() && last.y() <= 100.001 && last.onGround(),
                    "melee knockback did not move and land");
            case "knockback-wall" -> check(maxZ <= 1.701 && last.y() <= 100.001 && last.onGround(),
                    "melee knockback wall fixture did not stop and land");
            default -> throw new IllegalStateException("Unexpected movement scenario " + script.scenario());
        }
        if (script.scenario().equals("walk")) lastWalkDistance = displacement;
        check(Bukkit.getOnlinePlayers().size() == humanOnlineCount, "scenario changed human online count");
    }

    private double lastWalkDistance;

    private boolean afterHitMoved() {
        if (!Double.isFinite(hitZ)) return false;
        return observations.stream().skip(31).anyMatch(sample -> sample.z() > hitZ + 1.0e-4);
    }

    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        if (finished) return;
        try {
            if (selfCheckPending) {
                Location now = target.player().getLocation();
                check(Math.abs(now.getX() - selfCheckOrigin.getX()) < 1.0e-6
                                && Math.abs(now.getZ() - selfCheckOrigin.getZ()) < 1.0e-6,
                        "contradictory direction frame moved the body");
                selfCheckPending = false;
                BotActor preflight = target;
                BodyToken token = preflight.identity();
                target = null;
                platform.remove(preflight);
                SmokeIdentity.release(token);
                actors.clear();
                tokens.clear();
                scenarioIndex = 0;
                return;
            }
            if (!frameExecuted) return;
            frameExecuted = false;
            BotPlatform.Snapshot snapshot = platform.snapshot(target);
            assertFiniteValid(snapshot, target);
            if (!observations.isEmpty()) {
                BotPlatform.Snapshot previous = observations.getLast();
                maxSpeed = Math.max(maxSpeed, Math.hypot(snapshot.x() - previous.x(), snapshot.z() - previous.z()));
            }
            observations.add(snapshot);
            maxY = Math.max(maxY, snapshot.y());
            minY = Math.min(minY, snapshot.y());
            maxZ = Math.max(maxZ, snapshot.z());
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void assertFiniteValid(BotPlatform.Snapshot sample, BotActor actor) {
        check(actor.player().isValid() && !actor.player().isDead(), "sampled body is invalid");
        check(sample.worldId().equals(world.getUID()) && finite(sample.x(), sample.y(), sample.z(), sample.yaw(), sample.pitch(),
                sample.velocityX(), sample.velocityY(), sample.velocityZ(), sample.health(), sample.saturation(), sample.attackCooldown()),
                "movement sample contains invalid state");
        check(Bukkit.getOnlinePlayers().size() == humanOnlineCount, "sample changed human online count");
    }

    private BotActor spawnActor(Location location, Map<EquipmentSlot, ItemStack> equipment) {
        BodyToken token = SmokeIdentity.reserve(plugin, UUID.randomUUID(), 1);
        tokens.add(token);
        BotActor actor = platform.spawn(token, location, equipment);
        actors.add(actor);
        return actor;
    }

    private void checkNativeActor(BotActor actor) throws Exception {
        Object handle = craftGetHandle.invoke(actor.player());
        if (nativeAuthority == null) {
            nativeAuthority = handle.getClass().getDeclaredMethod("isClientAuthoritative");
            nativeAuthority.setAccessible(true);
        }
        check(nativeEntity.isInstance(handle) && !(boolean) nativeAuthority.invoke(handle),
                "bot native handle is not server authoritative");
    }

    private void prepareTerrain() {
        for (int chunkX = -3; chunkX <= 3; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 3; chunkZ++) world.getChunkAt(chunkX, chunkZ).setForceLoaded(true);
        }
    }

    private void resetTerrain() {
        for (int x = -32; x <= 32; x++) {
            for (int z = -8; z <= 40; z++) {
                world.getBlockAt(x, 99, z).setType(Material.STONE, false);
                for (int y = 100; y <= 110; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        }
    }

    private void setWall() {
        for (int x = -3; x <= 3; x++) for (int y = 100; y <= 102; y++) {
            world.getBlockAt(x, y, 2).setType(Material.STONE, false);
        }
    }

    private boolean isKnockback() {
        return script != null && script.scenario().startsWith("knockback-")
                || script == null && scenarioIndex >= 13;
    }

    private void removeActors() {
        for (int i = actors.size() - 1; i >= 0; i--) {
            BotActor actor = actors.get(i);
            platform.remove(actor);
        }
        for (BodyToken token : tokens) SmokeIdentity.release(token);
        actors.clear();
        tokens.clear();
    }

    private void succeed() {
        if (finished) return;
        finished = true;
        cleanup();
        plugin.getLogger().info("BOT_MOVEMENT_SMOKE PASS scenarios=16");
        success.run();
    }

    private void fail(Throwable error) {
        if (finished) return;
        finished = true;
        cleanup();
        failure.accept(error);
    }

    private void cleanup() {
        if (task != null) { task.cancel(); task = null; }
        HandlerList.unregisterAll(this);
        if (recording != null && target != null && probe != null && stopManaged != null) {
            try { stopManaged.invoke(probe, target, recording, "ERROR"); } catch (Throwable ignored) { }
            recording = null;
        }
        if (platform != null) {
            for (int i = actors.size() - 1; i >= 0; i--) {
                try { platform.remove(actors.get(i)); } catch (Throwable ignored) { }
            }
        }
        for (BodyToken token : tokens) {
            try { SmokeIdentity.release(token); } catch (Throwable ignored) { }
        }
        actors.clear();
        tokens.clear();
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

    private static Method findMethod(Class<?> type, Class<?> returnType, Class<?>... parameters) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (java.util.Arrays.equals(method.getParameterTypes(), parameters)
                    && method.getReturnType() == returnType) return method;
        }
        throw new NoSuchMethodException(type.getName() + " has no matching managed-probe method");
    }

    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new IllegalStateException("Expected IllegalArgumentException");
    }

    private static boolean sameMotion(BotPlatform.Snapshot left, BotPlatform.Snapshot right) {
        return close(left.x(), right.x()) && close(left.y(), right.y()) && close(left.z(), right.z())
                && close(left.velocityX(), right.velocityX()) && close(left.velocityY(), right.velocityY())
                && close(left.velocityZ(), right.velocityZ());
    }

    private static double horizontalDistance(Location location, Location origin) {
        return Math.hypot(location.getX() - origin.getX(), location.getZ() - origin.getZ());
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) <= 1.0e-5;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static BotInput toInput(BotMovementScript.Step step) {
        var frame = step.frame();
        return new BotInput(frame.forward() > 0, false, frame.strafe() > 0, false, frame.jump(), frame.sneak(),
                frame.sprint(), step.yawDelta(), 0.0f);
    }
}
