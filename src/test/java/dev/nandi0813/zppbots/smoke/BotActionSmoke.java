package dev.nandi0813.zppbots.smoke;

import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotFrame;
import dev.nandi0813.zppbots.platform.BotHands;
import dev.nandi0813.zppbots.platform.BotPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Hands actions on bare bodies: each scenario uses only {@link BotHands} and {@code tick(actor, BotFrame)}. */
public final class BotActionSmoke {

    private static final List<String> SCENARIOS = List.of("place", "mine", "drop", "swap", "water", "glide", "ride", "use");
    private static final int MINE_STOP_FRAME = 20;

    private final JavaPlugin plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private BotPlatform platform;
    private World world;
    private BukkitTask task;
    private BotActor actor;
    private BotHands hands;
    private BodyToken token;
    private int initialIdentityCount;
    private int initialBodyCount;
    private int scenarioIndex;
    private int frame;
    private boolean finished;

    private int startAmount;
    private Location startLocation;
    private Boat boat;
    private int glideStart = -1;

    public BotActionSmoke(JavaPlugin plugin, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.success = success;
        this.failure = failure;
    }

    public void start() {
        try {
            check(Boolean.getBoolean("bot.smoke.actions"), "bot.smoke.actions must be true");
            check(Bukkit.getOnlinePlayers().isEmpty(), "action smoke requires zero online players");
            Object diagnostics = ZPPBots.getInstance().getDiagnostics();
            platform = (BotPlatform) findField(diagnostics.getClass(), BotPlatform.class).get(diagnostics);
            check(platform != null, "diagnostics platform is unavailable");
            world = Bukkit.getWorlds().getFirst();
            initialIdentityCount = SmokeIdentity.size();
            initialBodyCount = bodies().size();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.getChunkAt(x, z).setForceLoaded(true);
            world.setSpawnFlags(false, false);
            world.setTime(6000L);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void tick() {
        if (finished) return;
        try {
            if (scenarioIndex == SCENARIOS.size()) {
                succeed();
                return;
            }
            if (actor == null) {
                prepare(scenario());
                frame = 0;
                return;
            }
            BotFrame next = step(scenario());
            if (next == null) {
                plugin.getLogger().info("BOT_ACTION_SMOKE scenario=" + scenario() + " ok");
                disposeActor();
                scenarioIndex++;
                return;
            }
            platform.tick(actor, next);
            frame++;
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void prepareTerrain(int top) {
        for (Entity entity : world.getEntities()) if (!(entity instanceof Player)) entity.remove();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            world.getBlockAt(x, 99, z).setType(Material.STONE, false);
            for (int y = 100; y <= top; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
    }

    private void prepare(String name) {
        prepareTerrain(name.equals("glide") ? 136 : 110);
        Location at = new Location(world, 0.5, 100, 0.5, 0, 0);
        Map<EquipmentSlot, ItemStack> equipment = Map.of();
        switch (name) {
            case "place" -> equipment = Map.of(EquipmentSlot.HAND, new ItemStack(Material.COBBLESTONE, 4));
            case "mine" -> {
                equipment = Map.of(EquipmentSlot.HAND, new ItemStack(Material.IRON_SHOVEL));
                world.getBlockAt(1, 100, 0).setType(Material.DIRT, false);
            }
            case "drop" -> equipment = Map.of(EquipmentSlot.HAND, new ItemStack(Material.STONE, 2));
            case "swap" -> equipment = Map.of(EquipmentSlot.HAND, new ItemStack(Material.IRON_SWORD),
                    EquipmentSlot.OFF_HAND, new ItemStack(Material.SHIELD));
            case "water" -> {
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 100; y <= 102; y++) {
                    boolean inside = Math.abs(x) <= 1 && Math.abs(z) <= 1 && y <= 101;
                    world.getBlockAt(x, y, z).setType(inside ? Material.WATER : Math.max(Math.abs(x), Math.abs(z)) == 2
                            ? Material.STONE : Material.AIR, false);
                }
                at = new Location(world, 0.5, 100, -0.5, 0, 0);
            }
            case "glide" -> {
                equipment = Map.of(EquipmentSlot.CHEST, new ItemStack(Material.ELYTRA));
                at = new Location(world, 0.5, 130, 0.5, 0, 0);
            }
            case "ride" -> boat = (Boat) world.spawnEntity(new Location(world, 2.5, 100, 0.5), EntityType.OAK_BOAT);
            case "use" -> equipment = Map.of(EquipmentSlot.HAND, new ItemStack(Material.BOW));
            default -> throw new IllegalStateException("Unexpected action case " + name);
        }
        token = SmokeIdentity.reserve(plugin, UUID.randomUUID(), 1);
        actor = platform.spawn(token, at, equipment);
        hands = platform.hands(actor);
        if (name.equals("use")) actor.player().getInventory().setItem(1, new ItemStack(Material.ARROW, 16));
        startAmount = actor.player().getInventory().getItemInMainHand().getAmount();
        startLocation = actor.player().getLocation();
        glideStart = -1;
    }

    /** Returns the movement keys for this tick, or null when the scenario is finished (assertions done). */
    private BotFrame step(String name) {
        Player player = actor.player();
        switch (name) {
            case "place" -> {
                if (frame == 0) {
                    hands.look(0, 90);
                    boolean placed = hands.useItemOn(EquipmentSlot.HAND, new BlockPos(0, 99, 2), Direction.UP,
                            new Vec3(0.5, 100.0, 2.5));
                    check(placed, "place: useItemOn returned false");
                } else if (frame == 2) {
                    check(world.getBlockAt(0, 100, 2).getType() == Material.COBBLESTONE, "place: block is "
                            + world.getBlockAt(0, 100, 2).getType());
                    check(player.getInventory().getItemInMainHand().getAmount() == startAmount - 1, "place: amount is "
                            + player.getInventory().getItemInMainHand().getAmount());
                    return null;
                }
            }
            case "mine" -> {
                BlockPos pos = new BlockPos(1, 100, 0);
                if (frame == 0) hands.startDestroy(pos, Direction.WEST);
                else if (frame == MINE_STOP_FRAME) hands.stopDestroy(pos, Direction.WEST);
                else if (frame == MINE_STOP_FRAME + 2) {
                    plugin.getLogger().info("BOT_ACTION_SMOKE mine stopFrame=" + MINE_STOP_FRAME);
                    check(world.getBlockAt(1, 100, 0).getType() == Material.AIR, "mine: block is "
                            + world.getBlockAt(1, 100, 0).getType());
                    return null;
                }
            }
            case "drop" -> {
                if (frame == 0) hands.drop(false);
                else if (frame == 5) {
                    List<Item> items = world.getEntitiesByClass(Item.class).stream()
                            .filter(item -> item.getLocation().distanceSquared(player.getLocation()) <= 25.0).toList();
                    check(items.size() == 1 && items.getFirst().getItemStack().getType() == Material.STONE
                            && items.getFirst().getItemStack().getAmount() == 1, "drop: items=" + items);
                    check(player.getInventory().getItemInMainHand().getAmount() == 1, "drop: hand amount is "
                            + player.getInventory().getItemInMainHand().getAmount());
                    return null;
                }
            }
            case "swap" -> {
                if (frame == 0) hands.swapHands();
                else if (frame == 2) {
                    check(player.getInventory().getItemInMainHand().getType() == Material.SHIELD
                            && player.getInventory().getItemInOffHand().getType() == Material.IRON_SWORD,
                            "swap: main=" + player.getInventory().getItemInMainHand().getType() + " off="
                                    + player.getInventory().getItemInOffHand().getType());
                    return null;
                }
            }
            case "water" -> {
                if (frame == 40) {
                    double moved = player.getLocation().distance(startLocation);
                    check(moved > 0.5, "water: moved " + moved);
                    return null;
                }
                return new BotFrame(1f, 0f, false, false, false);
            }
            case "glide" -> {
                if (glideStart < 0) {
                    check(frame <= 10 || !player.isOnGround(), "glide: never left the ground");
                    if (!player.isOnGround() && player.getVelocity().getY() < 0) {
                        check(hands.startGliding(), "glide: startGliding returned false");
                        check(player.isGliding(), "glide: not gliding after startGliding");
                        glideStart = frame;
                    } else {
                        check(frame <= 10, "glide: not falling within 10 ticks");
                    }
                } else if (frame == glideStart + 21) {
                    check(player.isValid(), "glide: body invalid");
                    return null;
                } else {
                    return new BotFrame(1f, 0f, false, false, false);
                }
            }
            case "ride" -> {
                if (frame == 0) {
                    check(hands.interact(boat, EquipmentSlot.HAND, Vec3.ZERO), "ride: interact returned false");
                    check(player.isInsideVehicle(), "ride: not inside a vehicle");
                } else if (frame == 3) {
                    check(player.isInsideVehicle(), "ride: left the vehicle early");
                    hands.dismount();
                    check(!player.isInsideVehicle(), "ride: still inside after dismount");
                } else if (frame == 5) {
                    return null;
                }
            }
            case "use" -> {
                if (frame == 0) {
                    check(hands.useItem(EquipmentSlot.HAND), "use: useItem returned false");
                    check(player.isHandRaised(), "use: hand not raised");
                } else if (frame == 20) {
                    hands.releaseUseItem();
                    check(!player.isHandRaised(), "use: hand still raised");
                } else if (frame == 23) {
                    int arrows = world.getEntitiesByClass(AbstractArrow.class).size();
                    check(arrows == 1, "use: arrows=" + arrows);
                    return null;
                }
            }
            default -> throw new IllegalStateException("Unexpected action case " + name);
        }
        return BotFrame.neutral();
    }

    private void succeed() {
        if (finished) return;
        finished = true;
        try {
            cleanup();
            check(SmokeIdentity.size() == initialIdentityCount && bodies().size() == initialBodyCount,
                    "action smoke actor cleanup changed identity or body counts");
            plugin.getLogger().info("BOT_ACTION_SMOKE PASS scenarios=" + SCENARIOS.size());
            success.run();
        } catch (Throwable error) { failure.accept(error); }
    }

    private void fail(Throwable error) {
        if (finished) return;
        plugin.getLogger().severe("BOT_ACTION_SMOKE FAIL scenario=" + (scenarioIndex < SCENARIOS.size() ? scenario() : "end")
                + " frame=" + frame + " error=" + error);
        finished = true;
        try { cleanup(); } catch (Throwable cleanupError) { error.addSuppressed(cleanupError); }
        failure.accept(error);
    }

    private void cleanup() {
        if (task != null) { task.cancel(); task = null; }
        if (platform != null) disposeActor();
        if (world != null) for (Entity entity : world.getEntities()) if (!(entity instanceof Player)) entity.remove();
    }

    private void disposeActor() {
        Throwable failure = null;
        if (actor != null) {
            try { platform.remove(actor); } catch (Throwable error) { failure = error; }
        }
        if (token != null) {
            try { SmokeIdentity.release(token); } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        actor = null; hands = null; token = null; boat = null;
        for (Entity entity : world.getEntities()) if (!(entity instanceof Player)) entity.remove();
        if (failure != null) throw new IllegalStateException("Could not dispose action actor", failure);
    }

    private Map<?, ?> bodies() throws Exception {
        return (Map<?, ?>) findField(platform.getClass(), Map.class).get(platform);
    }

    private String scenario() { return SCENARIOS.get(scenarioIndex); }

    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static Field findField(Class<?> type, Class<?> fieldType) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) for (Field field : current.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && fieldType.isAssignableFrom(field.getType())) { field.setAccessible(true); return field; }
        }
        throw new NoSuchFieldException(type.getName() + " has no " + fieldType.getSimpleName() + " field");
    }
}
