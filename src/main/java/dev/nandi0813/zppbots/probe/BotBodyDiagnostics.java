package dev.nandi0813.zppbots.probe;

import dev.nandi0813.api.Enum.PlayerState;
import dev.nandi0813.api.ZonePracticeApi;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotGuards;
import dev.nandi0813.zppbots.platform.BotPlatform;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.json.JSONObject;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class BotBodyDiagnostics implements CommandExecutor, TabCompleter, Listener, AutoCloseable {

    private final ZPPBots plugin;
    private final BotParityProbe probe;
    private final BotPlatform platform;
    private final String unavailableReason;
    private final Map<UUID, Diagnostic> active = new HashMap<>();
    private final Set<UUID> diagnosticIds = new HashSet<>();
    private long generation;
    private boolean closed;
    private BukkitTask motionTicker;

    public BotBodyDiagnostics(ZPPBots plugin, BotParityProbe probe) {
        this.plugin = plugin;
        this.probe = probe;
        this.platform = plugin.getPlatform();
        this.unavailableReason = plugin.getUnavailableReason();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if (!sender.hasPermission("zppbots.body")) {
            message(sender, "NO-PERMISSION");
            return true;
        }
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Bot body diagnostics must run on the main thread");
        if ((args.length != 1 && args.length != 2) || (args.length == 2 && !args[0].equalsIgnoreCase("run"))) {
            message(sender, "HELP");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> message(sender, "STATUS", "%count%", Integer.toString(active.size()), "%backend%",
                    platform == null ? unavailableReason : platform.getClass().getName());
            case "spawn", "remove", "run", "stop" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessages().send(sender, "PLAYER-ONLY");
                    return true;
                }
                if (closed || platform == null || !ZonePracticeApi.isAvailable()) {
                    message(player, "UNAVAILABLE");
                    return true;
                }
                if (args[0].equalsIgnoreCase("run")) {
                    if (args.length != 2) { message(player, "HELP"); return true; }
                    run(player, args[1]);
                    return true;
                }
                if (args[0].equalsIgnoreCase("stop")) {
                    if (args.length != 1) { message(player, "HELP"); return true; }
                    Diagnostic diagnostic = active.get(player.getUniqueId());
                    if (diagnostic == null) message(player, "NOT-ACTIVE");
                    else if (diagnostic.script == null) message(player, "NO-SCRIPT");
                    else { stopScript(player.getUniqueId(), diagnostic, "STOPPED"); message(player, "SCRIPT-STOPPED"); }
                    return true;
                }
                if (args[0].equalsIgnoreCase("remove")) {
                    Diagnostic diagnostic = active.get(player.getUniqueId());
                    if (diagnostic == null) message(player, "NOT-ACTIVE");
                    else {
                        finish(player.getUniqueId(), diagnostic);
                        message(player, "REMOVED");
                    }
                    return true;
                }
                spawn(player);
            }
            default -> message(sender, "HELP");
        }
        return true;
    }

    private void run(Player owner, String scenario) {
        if (BotGuards.isBot(owner.getUniqueId())) { message(owner, "UNAVAILABLE"); return; }
        if (!BotMovementScript.scenarios().contains(scenario)) { message(owner, "INVALID-SCENARIO"); return; }
        Diagnostic diagnostic = active.get(owner.getUniqueId());
        if (diagnostic == null) { message(owner, "NOT-ACTIVE"); return; }
        if (diagnostic.script != null) { message(owner, "SCRIPT-BUSY"); return; }
        Player body = diagnostic.actor.player();
        JSONObject fixture = new JSONObject().put("world", body.getWorld().getUID().toString())
                .put("position", new JSONObject().put("x", body.getLocation().getX()).put("y", body.getLocation().getY()).put("z", body.getLocation().getZ()))
                .put("yaw", body.getLocation().getYaw()).put("pitch", body.getLocation().getPitch())
                .put("note", "Terrain and impulses are prepared externally.");
        BotMovementScript script = BotMovementScript.create(scenario, fixture.toString());
        UUID recording = probe.startManaged(diagnostic.actor, scenario, script.frames().size(), script.fixture());
        if (recording == null) { message(owner, "RECORDING-BUSY"); return; }
        diagnostic.scriptStartTick = Bukkit.getCurrentTick();
        diagnostic.script = script;
        diagnostic.recordingSession = recording;
        diagnostic.frameIndex = 0;
        if (motionTicker == null) {
            try {
                motionTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickScripts, 1L, 1L);
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not schedule diagnostic movement for " + owner.getUniqueId(), exception);
                finish(owner.getUniqueId(), diagnostic, "ERROR");
                message(owner, "MOTION-FAILED");
                return;
            }
        }
        message(owner, "SCRIPT-STARTED", "%scenario%", scenario);
        if (scenario.startsWith("knockback-")) message(owner, "KNOCKBACK-FIXTURE");
    }

    private void tickScripts() {
        for (Map.Entry<UUID, Diagnostic> entry : new ArrayList<>(active.entrySet())) {
            UUID ownerId = entry.getKey();
            Diagnostic diagnostic = entry.getValue();
            if (diagnostic.script == null) continue;
            Player owner = Bukkit.getPlayer(ownerId);
            Player body = Bukkit.getEntity(diagnostic.token.playerId()) instanceof Player player ? player : null;
            if (owner == null || !owner.isOnline() || BotGuards.isBot(ownerId)
                    || owner.getWorld() != (body == null ? null : body.getWorld())
                    || !BotGuards.isBot(diagnostic.token.playerId()) || body != diagnostic.actor.player() || !body.isValid() || body.isDead()) {
                finish(ownerId, diagnostic, "ERROR");
                continue;
            }
            if (Bukkit.getCurrentTick() <= diagnostic.scriptStartTick) continue;
            if (diagnostic.frameIndex >= diagnostic.script.frames().size()) {
                stopScript(ownerId, diagnostic, "COMPLETED");
                continue;
            }
            try {
                BotMovementScript.Step step = diagnostic.script.frames().get(diagnostic.frameIndex++);
                platform.hands(diagnostic.actor).look(body.getYaw() + step.yawDelta(), body.getPitch());
                platform.tick(diagnostic.actor, step.frame());
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not run diagnostic movement for " + ownerId, exception);
                finish(ownerId, diagnostic, "ERROR");
                Player online = Bukkit.getPlayer(ownerId);
                if (online != null && online.isOnline()) message(online, "MOTION-FAILED");
            }
        }
        cancelIdleMotionTicker();
    }

    private void stopScript(UUID owner, Diagnostic diagnostic, String reason) {
        if (diagnostic.script == null) return;
        probe.stopManaged(diagnostic.actor, diagnostic.recordingSession, reason);
        try {
            platform.pause(diagnostic.actor);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not pause diagnostic body for " + owner, exception);
        }
        diagnostic.script = null;
        diagnostic.recordingSession = null;
        diagnostic.frameIndex = 0;
        cancelIdleMotionTicker();
    }

    private void cancelIdleMotionTicker() {
        if (motionTicker != null && active.values().stream().noneMatch(diagnostic -> diagnostic.script != null)) {
            motionTicker.cancel();
            motionTicker = null;
        }
    }

    private void spawn(Player owner) {
        UUID ownerId = owner.getUniqueId();
        if (active.containsKey(ownerId)) {
            message(owner, "ALREADY-ACTIVE");
            return;
        }
        if (active.size() >= 4) {
            message(owner, "BUSY");
            return;
        }
        World world = owner.getWorld();
        if (!owner.isOnline() || BotGuards.isBot(ownerId)
                || ZonePracticeApi.getInstance().getPlayerState(owner) != PlayerState.LOBBY
                || !plugin.getConfig().getStringList("DIAGNOSTICS.WORLDS").contains(world.getName())) {
            message(owner, "UNSAFE-AREA");
            return;
        }
        Location target = owner.getLocation().clone();
        Vector direction = target.getDirection().setY(0);
        if (direction.lengthSquared() < 0.01) direction = new Vector(0, 0, 1);
        target.add(direction.normalize().multiply(3));
        if (!safe(target)) {
            message(owner, "UNSAFE-AREA");
            return;
        }

        BodyToken token = null;
        BotActor actor = null;
        BukkitTask expiry = null;
        try {
            token = new BodyToken(UUID.randomUUID(), ZonePracticeApi.getInstance().getBotRegistry().reserveEntityId(plugin), ++generation);
            diagnosticIds.add(token.playerId());
            actor = platform.spawn(token, target, equipment(owner.getInventory()));
            Diagnostic diagnostic = new Diagnostic(token, actor);
            active.put(ownerId, diagnostic);
            platform.show(actor, owner);
            expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Diagnostic current = active.get(ownerId);
                if (current == diagnostic) finish(ownerId, current);
            }, 1200L);
            diagnostic.expiry = expiry;
            message(owner, "STARTED");
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not spawn diagnostic body for " + ownerId, exception);
            if (expiry != null) expiry.cancel();
            if (actor != null) {
                try {
                    platform.remove(actor);
                } catch (RuntimeException | LinkageError cleanupException) {
                    plugin.getLogger().log(Level.SEVERE, "Could not clean up failed diagnostic body", cleanupException);
                }
            }
            active.remove(ownerId);
            if (token != null) {
                diagnosticIds.remove(token.playerId());
                release(token);
            }
            message(owner, "FAILED");
        }
    }

    private boolean safe(Location location) {
        World world = location.getWorld();
        if (world == null || location.getY() < world.getMinHeight() || location.getY() + 1.8 >= world.getMaxHeight()
                || !world.getWorldBorder().isInside(location) || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return false;
        return location.getBlock().isPassable() && location.clone().add(0, 1, 0).getBlock().isPassable()
                && location.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }

    private static Map<EquipmentSlot, ItemStack> equipment(PlayerInventory inventory) {
        Map<EquipmentSlot, ItemStack> items = new HashMap<>();
        items.put(EquipmentSlot.HAND, copy(inventory.getItemInMainHand()));
        items.put(EquipmentSlot.OFF_HAND, copy(inventory.getItemInOffHand()));
        items.put(EquipmentSlot.HEAD, copy(inventory.getHelmet()));
        items.put(EquipmentSlot.CHEST, copy(inventory.getChestplate()));
        items.put(EquipmentSlot.LEGS, copy(inventory.getLeggings()));
        items.put(EquipmentSlot.FEET, copy(inventory.getBoots()));
        return items;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item.clone();
    }

    private void finish(UUID owner, Diagnostic expected) { finish(owner, expected, "REMOVED"); }

    private void finish(UUID owner, Diagnostic expected, String reason) {
        if (active.get(owner) != expected) return;
        if (expected.script != null) stopScript(owner, expected, reason);
        active.remove(owner);
        if (expected.expiry() != null) expected.expiry().cancel();
        try {
            platform.remove(expected.actor());
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not remove diagnostic body for " + owner, exception);
        } finally {
            diagnosticIds.remove(expected.token().playerId());
            release(expected.token());
        }
    }

    private static void release(BodyToken token) {
        if (ZonePracticeApi.isAvailable()) ZonePracticeApi.getInstance().getBotRegistry().releaseEntityId(token.playerId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Diagnostic diagnostic = active.get(event.getPlayer().getUniqueId());
        if (diagnostic != null) finish(event.getPlayer().getUniqueId(), diagnostic, "QUIT");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (Map.Entry<UUID, Diagnostic> entry : new ArrayList<>(active.entrySet())) {
            if (entry.getValue().actor().player().getWorld().equals(event.getWorld())) finish(entry.getKey(), entry.getValue(), "WORLD_UNLOAD");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Map.Entry<UUID, Diagnostic> entry : new ArrayList<>(active.entrySet())) {
            Location location = entry.getValue().actor().player().getLocation();
            if (location.getWorld().equals(event.getWorld()) && location.getBlockX() >> 4 == event.getChunk().getX()
                    && location.getBlockZ() >> 4 == event.getChunk().getZ()) finish(entry.getKey(), entry.getValue(), "CHUNK_UNLOAD");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (diagnosticIds.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && isDiagnostic(byEntity.getDamager())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (diagnosticIds.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (diagnosticIds.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    private boolean isDiagnostic(Entity entity) {
        if (diagnosticIds.contains(entity.getUniqueId())) return true;
        return entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                && diagnosticIds.contains(shooter.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("zppbots.body")) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("spawn", "remove", "status", "run", "stop").stream().filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("run")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return BotMovementScript.scenarios().stream().filter(option -> option.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private void message(CommandSender sender, String key, String... replacements) {
        TagResolver.Builder resolvers = TagResolver.builder();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            resolvers.resolver(Placeholder.unparsed(replacements[i].replace("%", ""), replacements[i + 1]));
        }
        plugin.getMessages().send(sender, "BOT-BODY." + key, resolvers.build());
    }

    @Override
    public void close() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Bot body diagnostics must close on the main thread");
        if (closed) return;
        closed = true;
        if (motionTicker != null) { motionTicker.cancel(); motionTicker = null; }
        for (Map.Entry<UUID, Diagnostic> entry : new ArrayList<>(active.entrySet())) finish(entry.getKey(), entry.getValue(), "DISABLE");
        diagnosticIds.clear();
        HandlerList.unregisterAll(this);
    }

    private static final class Diagnostic {
        private final BodyToken token;
        private final BotActor actor;
        private BukkitTask expiry;
        private BotMovementScript script;
        private UUID recordingSession;
        private int scriptStartTick;
        private int frameIndex;

        private Diagnostic(BodyToken token, BotActor actor) {
            this.token = token;
            this.actor = actor;
        }

        private BodyToken token() { return token; }
        private BotActor actor() { return actor; }
        private BukkitTask expiry() { return expiry; }
    }
}
