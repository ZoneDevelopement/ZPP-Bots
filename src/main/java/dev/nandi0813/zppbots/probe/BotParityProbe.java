package dev.nandi0813.zppbots.probe;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotGuards;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BotParityProbe implements CommandExecutor, TabCompleter, Listener, AutoCloseable {

    private final ZPPBots plugin;
    private final Logger logger;
    private final Path output;
    private final HashMap<UUID, Capture> active = new HashMap<>();
    private final Semaphore permits = new Semaphore(4);
    private final ExecutorService writer;
    private boolean closed;

    public BotParityProbe(ZPPBots plugin) {
        this.plugin = plugin;
        logger = plugin.getLogger();
        output = plugin.getDataFolder().toPath().resolve("bot-probes");
        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "ZPPBots-probe-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if (!sender.hasPermission("zppbots.probe")) {
            message(sender, "NO-PERMISSION");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "PLAYER-ONLY");
            return true;
        }
        if (closed) {
            message(player, "UNAVAILABLE");
            return true;
        }
        if (args.length == 0) {
            message(player, "HELP");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (args.length > 3) {
                    message(player, "HELP");
                    return true;
                }
                String label = args.length >= 2 ? args[1] : "reference";
                if (!label.matches("[A-Za-z0-9_-]{1,32}")) {
                    message(player, "INVALID-LABEL");
                    return true;
                }
                int ticks = 200;
                if (args.length == 3) {
                    try {
                        if (!args[2].matches("[0-9]+")) throw new NumberFormatException();
                        ticks = Integer.parseInt(args[2]);
                        if (ticks < 20 || ticks > 600) throw new NumberFormatException();
                    } catch (NumberFormatException exception) {
                        message(player, "INVALID-TICKS");
                        return true;
                    }
                }
                start(player, label, ticks);
            }
            case "stop", "status" -> {
                if (args.length != 1) {
                    message(player, "HELP");
                    return true;
                }
                Capture capture = active.get(player.getUniqueId());
                if (capture == null) {
                    message(player, "NOT-RECORDING");
                } else if (args[0].equalsIgnoreCase("stop")) {
                    finish(capture.subject, "STOPPED", true);
                } else {
                    message(player, "STATUS", "%label%", capture.label, "%samples%", Integer.toString(capture.trace.sampleCount()),
                            "%ticks%", Integer.toString(capture.requestedSamples), "%events%", Integer.toString(capture.trace.eventCount()));
                }
            }
            default -> message(player, "HELP");
        }
        return true;
    }

    private void start(Player player, String label, int requestedSamples) {
        if (BotGuards.isBot(player.getUniqueId())) {
            message(player, "UNAVAILABLE");
            return;
        }
        UUID subject = player.getUniqueId();
        if (active.containsKey(subject)) {
            message(player, "ALREADY-RECORDING");
            return;
        }
        if (!permits.tryAcquire()) {
            message(player, "BUSY");
            return;
        }
        try {
            prepare(subject, null, label, requestedSamples, null);
        } catch (RuntimeException exception) {
            permits.release();
            logger.log(Level.SEVERE, "Could not prepare bot parity recording for " + subject, exception);
            message(player, "FAILED");
            return;
        }
        message(player, "STARTED", "%label%", label, "%ticks%", Integer.toString(requestedSamples));
    }

    UUID startManaged(BotActor actor, String label, int samples, String fixture) {
        requireMainThread();
        if (actor == null || !BotGuards.isBot(actor.entityId()) || Bukkit.getEntity(actor.entityId()) != actor.player()
                || !actor.player().isValid() || actor.player().isDead()) throw new IllegalArgumentException("Invalid managed actor");
        if (label == null || !label.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid label");
        if (samples < 20 || samples > 600) throw new IllegalArgumentException("Invalid sample count");
        if (fixture == null || fixture.isEmpty() || fixture.length() > 4096) throw new IllegalArgumentException("Invalid fixture");
        if (closed || active.containsKey(actor.entityId()) || !permits.tryAcquire()) return null;
        try {
            return prepare(actor.entityId(), actor.identity(), label, samples, fixture);
        } catch (RuntimeException exception) {
            permits.release();
            logger.log(Level.SEVERE, "Could not prepare managed bot parity recording", exception);
            return null;
        }
    }

    private UUID prepare(UUID subject, BodyToken identity, String label, int requestedSamples, String fixture) {
        UUID session = UUID.randomUUID();
        int startTick = Bukkit.getCurrentTick();
        JSONArray plugins = new JSONArray();
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            plugins.put(new JSONObject().put("name", installed.getName()).put("version", installed.getDescription().getVersion()));
        }
        JSONObject metadata = new JSONObject().put("type", "metadata").put("schema", 2)
                .put("kind", identity == null ? "human-reference" : "managed-candidate").put("samplePhase", "server-tick-end")
                .put("session", session.toString()).put("subject", subject.toString()).put("label", label)
                .put("startedAt", Instant.now().toString()).put("requestedSamples", requestedSamples).put("startTick", startTick)
                .put("serverVersion", Bukkit.getVersion()).put("minecraftVersion", Bukkit.getMinecraftVersion())
                .put("javaVersion", System.getProperty("java.version")).put("pluginVersion", plugin.getDescription().getVersion())
                .put("plugins", plugins);
        if (fixture != null) metadata.put("fixture", fixture);
        if (identity != null) metadata.put("identity", new JSONObject().put("session", identity.sessionId().toString())
                .put("generation", identity.generation()));
        Capture capture = new Capture(subject, session, identity, label, requestedSamples, startTick,
                new BotParityTrace(metadata.toString()));
        active.put(subject, capture);
        return session;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        for (UUID subject : new ArrayList<>(active.keySet())) {
            Capture capture = active.get(subject);
            if (capture == null || Bukkit.getCurrentTick() <= capture.startTick) continue;
            try {
                Player player = resolve(capture);
                if (player == null) { finish(subject, "OFFLINE", capture.identity == null); continue; }
                if (!capture.trace.sample(sample(player, capture, event).toString())) {
                    finish(subject, "LIMIT", capture.identity == null);
                    continue;
                }
                if (capture.trace.sampleCount() >= capture.requestedSamples) {
                    finish(subject, "COMPLETED", capture.identity == null);
                }
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Could not sample bot parity recording for " + subject, exception);
                finish(subject, "ERROR", capture.identity == null);
            }
        }
    }

    private static Player resolve(Capture capture) {
        if (capture.identity == null) {
            Player player = Bukkit.getPlayer(capture.subject);
            return player != null && player.isOnline() && !BotGuards.isBot(capture.subject) ? player : null;
        }
        Entity entity = Bukkit.getEntity(capture.subject);
        return entity instanceof Player player && player.isValid() && !player.isDead()
                && BotGuards.isBot(capture.identity.playerId()) && player.getUniqueId().equals(capture.identity.playerId()) ? player : null;
    }

    private static JSONObject sample(Player player, Capture capture, ServerTickEndEvent event) {
        int tick = Bukkit.getCurrentTick();
        Location position = player.getLocation();
        org.bukkit.util.Vector velocity = player.getVelocity();
        Input input = player.getCurrentInput();
        PlayerInventory inventory = player.getInventory();
        JSONArray armor = new JSONArray();
        for (ItemStack item : inventory.getArmorContents()) armor.put(item(item));
        JSONArray effects = new JSONArray();
        player.getActivePotionEffects().forEach(effect -> effects.put(new JSONObject().put("type", effect.getType().getKey().toString())
                .put("amplifier", effect.getAmplifier()).put("duration", effect.getDuration())));
        JSONArray tps = new JSONArray();
        for (double value : Bukkit.getTPS()) tps.put(value);
        return new JSONObject().put("type", "sample").put("sample", capture.trace.sampleCount()).put("tick", tick)
                .put("elapsedTicks", tick - capture.startTick).put("nanoTime", System.nanoTime())
                .put("tickDuration", event.getTickDuration()).put("tps", tps)
                .put("world", player.getWorld().getUID().toString())
                .put("position", new JSONObject().put("x", position.getX()).put("y", position.getY()).put("z", position.getZ()))
                .put("velocity", new JSONObject().put("x", velocity.getX()).put("y", velocity.getY()).put("z", velocity.getZ()))
                .put("yaw", position.getYaw()).put("pitch", position.getPitch()).put("onGround", player.isOnGround())
                .put("sprinting", player.isSprinting()).put("sneaking", player.isSneaking()).put("blocking", player.isBlocking())
                .put("handRaised", player.isHandRaised()).put("dead", player.isDead()).put("gameMode", player.getGameMode().name())
                .put("attackCooldown", player.getAttackCooldown()).put("health", player.getHealth()).put("absorption", player.getAbsorptionAmount())
                .put("food", player.getFoodLevel()).put("saturation", player.getSaturation()).put("exhaustion", player.getExhaustion())
                .put("heldSlot", inventory.getHeldItemSlot()).put("ping", player.getPing())
                .put("input", new JSONObject().put("forward", input.isForward()).put("backward", input.isBackward())
                        .put("left", input.isLeft()).put("right", input.isRight()).put("jump", input.isJump())
                        .put("sneak", input.isSneak()).put("sprint", input.isSprint()))
                .put("equipment", new JSONObject().put("mainHand", item(inventory.getItemInMainHand()))
                        .put("offHand", item(inventory.getItemInOffHand())).put("armor", armor))
                .put("attributes", new JSONObject().put("movementSpeed", attribute(player, Attribute.MOVEMENT_SPEED))
                        .put("attackSpeed", attribute(player, Attribute.ATTACK_SPEED)).put("maxHealth", attribute(player, Attribute.MAX_HEALTH))
                        .put("armor", attribute(player, Attribute.ARMOR)).put("armorToughness", attribute(player, Attribute.ARMOR_TOUGHNESS))
                        .put("knockbackResistance", attribute(player, Attribute.KNOCKBACK_RESISTANCE))).put("effects", effects);
    }

    private static Object attribute(Player player, Attribute attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? JSONObject.NULL : instance.getValue();
    }

    private static JSONObject item(ItemStack item) {
        boolean empty = item == null || item.getType().isAir();
        JSONObject enchantments = new JSONObject();
        int damage = 0;
        if (!empty) {
            if (item.getItemMeta() instanceof Damageable meta) damage = meta.getDamage();
            item.getEnchantments().forEach((enchantment, level) -> enchantments.put(enchantment.getKey().toString(), level));
        }
        return new JSONObject().put("material", empty ? "AIR" : item.getType().name()).put("amount", empty ? 0 : item.getAmount())
                .put("damage", damage).put("enchantments", enchantments);
    }

    void stopManaged(BotActor actor, UUID session, String reason) {
        requireMainThread();
        Capture capture = actor == null ? null : active.get(actor.entityId());
        if (capture == null || capture.identity == null || !capture.identity.equals(actor.identity()) || !capture.session.equals(session)) return;
        finish(capture.subject, reason, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        UUID victim = event.getEntity().getUniqueId();
        UUID damager = null;
        UUID attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity entity = byEntity.getDamager();
            damager = entity.getUniqueId();
            attacker = damager;
            if (entity instanceof Projectile projectile) {
                attacker = projectile.getShooter() instanceof Entity shooter ? shooter.getUniqueId() : null;
            }
        }
        LinkedHashSet<UUID> subjects = new LinkedHashSet<>();
        subjects.add(victim);
        if (damager != null) subjects.add(damager);
        for (UUID subject : subjects) {
            Capture capture = active.get(subject);
            if (capture == null) continue;
            try {
                int tick = Bukkit.getCurrentTick();
                recordEvent(capture, new JSONObject().put("type", "damage").put("tick", tick)
                        .put("elapsedTicks", tick - capture.startTick).put("victim", victim.toString())
                        .put("damager", damager == null ? JSONObject.NULL : damager.toString())
                        .put("attacker", attacker == null ? JSONObject.NULL : attacker.toString())
                        .put("cause", event.getCause().name()).put("damage", event.getDamage()).put("finalDamage", event.getFinalDamage()));
            } catch (RuntimeException exception) {
                eventError(subject, exception);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        UUID subject = event.getPlayer().getUniqueId();
        Capture capture = active.get(subject);
        if (capture == null) return;
        try {
            int tick = Bukkit.getCurrentTick();
            recordEvent(capture, new JSONObject().put("type", "swing").put("tick", tick)
                    .put("elapsedTicks", tick - capture.startTick).put("animation", event.getAnimationType().name()));
        } catch (RuntimeException exception) {
            eventError(subject, exception);
        }
    }

    private void recordEvent(Capture capture, JSONObject event) {
        if (!capture.trace.event(event.toString())) finish(capture.subject, "LIMIT", capture.identity == null);
    }

    private void eventError(UUID subject, RuntimeException exception) {
        logger.log(Level.SEVERE, "Could not record bot parity event for " + subject, exception);
        Capture capture = active.get(subject);
        if (capture != null) finish(subject, "ERROR", capture.identity == null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        finish(event.getPlayer().getUniqueId(), "QUIT", false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (Capture capture : new ArrayList<>(active.values())) {
            Player player = resolve(capture);
            if (player != null && player.getWorld().equals(event.getWorld())) {
                finish(capture.subject, "WORLD_UNLOAD", false);
            }
        }
    }

    private void finish(UUID subject, String reason, boolean notify) {
        Capture capture = active.remove(subject);
        if (capture == null) return;
        String payload = capture.trace.finish(new JSONObject().put("type", "summary").put("reason", reason)
                .put("samples", capture.trace.sampleCount()).put("events", capture.trace.eventCount())
                .put("truncated", capture.trace.isTruncated()).put("endedAt", Instant.now().toString())
                .put("endTick", Bukkit.getCurrentTick()).toString());
        Path file = output.resolve(capture.filename);
        Semaphore reservedPermits = permits;
        Logger writeLogger = logger;
        try {
            writer.execute(() -> write(file, payload, reservedPermits, writeLogger));
        } catch (RejectedExecutionException exception) {
            permits.release();
            logger.log(Level.SEVERE, "Could not queue bot parity recording " + capture.filename, exception);
            Player player = notify && capture.identity == null ? Bukkit.getPlayer(subject) : null;
            if (player != null && player.isOnline()) message(player, "FAILED");
            return;
        }
        Player player = notify && capture.identity == null ? Bukkit.getPlayer(subject) : null;
        if (player != null && player.isOnline()) {
            message(player, "QUEUED", "%file%", capture.filename,
                    "%samples%", Integer.toString(capture.trace.sampleCount()), "%reason%", reason);
        }
    }

    private static void write(Path file, String payload, Semaphore permits, Logger logger) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, payload, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            logger.info("Saved bot parity recording " + file.getFileName());
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not save bot parity recording " + file.getFileName(), exception);
        } finally {
            permits.release();
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Bot parity recording must run on the main thread");
    }

    @Override
    public void close() {
        requireMainThread();
        if (closed) return;
        for (UUID subject : new ArrayList<>(active.keySet())) finish(subject, "DISABLE", false);
        closed = true;
        writer.shutdown();
        HandlerList.unregisterAll(this);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("zppbots.probe") || closed) return List.of();
        List<String> choices;
        if (args.length == 1) choices = List.of("start", "stop", "status");
        else if (args.length == 3 && args[0].equalsIgnoreCase("start")) choices = List.of("200", "400", "600");
        else return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.startsWith(prefix)).toList();
    }

    private static void message(CommandSender sender, String key, String... replacements) {
        TagResolver.Builder resolvers = TagResolver.builder();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            resolvers.resolver(Placeholder.unparsed(replacements[i].replace("%", ""), replacements[i + 1]));
        }
        ZPPBots.getInstance().getMessages().send(sender, "BOT-PROBE." + key, resolvers.build());
    }

    private static final class Capture {
        private final UUID subject;
        private final UUID session;
        private final BodyToken identity;
        private final String label;
        private final String filename;
        private final int requestedSamples;
        private final int startTick;
        private final BotParityTrace trace;

        private Capture(UUID subject, UUID session, BodyToken identity, String label, int requestedSamples, int startTick, BotParityTrace trace) {
            this.subject = subject;
            this.session = session;
            this.identity = identity;
            this.label = label;
            this.filename = session + ".jsonl";
            this.requestedSamples = requestedSamples;
            this.startTick = startTick;
            this.trace = trace;
        }
    }
}
