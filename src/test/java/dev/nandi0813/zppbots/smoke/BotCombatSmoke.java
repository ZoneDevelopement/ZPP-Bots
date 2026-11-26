package dev.nandi0813.zppbots.smoke;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotPlatform;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class BotCombatSmoke implements Listener {

    private static final List<String> SCENARIOS = List.of("ready", "early", "repeated", "fast-speed", "miss", "slot",
            "slot-cancel", "reach-in", "reach-out", "wall", "aim-miss", "occluded", "critical", "sweep", "sprint-hit",
            "shield-early", "shield-front", "shield-rear", "shield-side", "shield-release", "shield-axe", "walk",
            "shield-walk", "food", "food-interrupt", "food-cancel", "golden-apple", "exhaustion", "regeneration",
            "armor", "enchant-effects", "kb-resistance", "damage-cancel", "damage-knockback-modify", "survival-death");
    private static final double EPSILON = 1.0e-5;

    private final JavaPlugin plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private final List<BotActor> actors = new ArrayList<>();
    private final List<BodyToken> tokens = new ArrayList<>();
    private final List<String> records = new ArrayList<>();
    private final List<UUID> recordings = new ArrayList<>();
    private final Map<String, Double> metrics = new HashMap<>();
    private BotPlatform platform;
    private Object probe;
    private Method startManaged;
    private Method stopManaged;
    private World world;
    private BotActor attacker;
    private BotActor target;
    private BotActor third;
    private BukkitTask task;
    private int humanOnlineCount;
    private int initialIdentityCount;
    private int initialBodyCount;
    private int scenarioIndex = -1;
    private int frame;
    private int actualConsumeFrame = -1;
    private int damageEvents;
    private int cancelledDamageEvents;
    private int regainEvents;
    private int heldEvents;
    private int swingEvents;
    private int preAttackEvents;
    private int deathEvents;
    private int consumeEvents;
    private boolean modifiedDamage;
    private boolean modifiedKnockback;
    private boolean finished;
    private boolean writingCase;
    private double maxTargetDamage;
    private double damageBeforeCancel;
    private double damageAfterCancel;
    private double maxAbsorption;
    private double minHealth;
    private double maxDisplacement;
    private double readyDamage;
    private double readyEndingHealth;
    private double readyDisplacement;
    private double shieldCooldown;
    private Location origin;
    private Location targetOrigin;

    public BotCombatSmoke(JavaPlugin plugin, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.success = success;
        this.failure = failure;
    }

    public void start() {
        try {
            check(Boolean.getBoolean("bot.smoke.combat"), "bot.smoke.combat must be true");
            check(Bukkit.getOnlinePlayers().isEmpty(), "combat smoke requires zero online players");
            Object diagnostics = ZPPBots.getInstance().getDiagnostics();
            platform = (BotPlatform) findField(diagnostics.getClass(), BotPlatform.class).get(diagnostics);
            probe = ZPPBots.getInstance().getProbe();
            startManaged = findMethod(probe.getClass(), UUID.class, BotActor.class, String.class, int.class, String.class);
            stopManaged = findMethod(probe.getClass(), void.class, BotActor.class, UUID.class, String.class);
            startManaged.setAccessible(true);
            stopManaged.setAccessible(true);
            check(platform != null, "diagnostics platform is unavailable");
            world = Bukkit.getWorlds().getFirst();
            humanOnlineCount = Bukkit.getOnlinePlayers().size();
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
    }

    private void tick() {
        if (finished) return;
        try {
            check(Bukkit.getOnlinePlayers().size() == humanOnlineCount, "human online count changed");
            if (scenarioIndex < 0) {
                selfChecks();
                scenarioIndex = 0;
                return;
            }
            if (scenarioIndex >= SCENARIOS.size()) return;
            if (writingCase) return;
            if (attacker == null) {
                prepareCase();
                return;
            }
            if (frame >= (scenario().equals("regeneration") ? 200 : 80)
                    || (scenario().equals("survival-death") && deathEvents > 0 && frame > 31)) {
                finishCase();
                return;
            }
            BotInput targetRequest = targetInput();
            BotInput attackerRequest = attackerInput();
            if (attackerRequest.attack()) {
                BotPlatform.Snapshot beforeHit = platform.snapshot(attacker);
                metrics.put("preHitCooldown", (double) beforeHit.attackCooldown());
                if (scenario().equals("critical")) {
                    metrics.put("preHitOnGround", beforeHit.onGround() ? 1.0 : 0.0);
                    metrics.put("preHitFallDistance", (double) attacker.player().getFallDistance());
                }
            }
            if (target.player().isValid() && !target.player().isDead()) LegacyDrive.tick(platform, target, targetRequest);
            if (attacker.player().isValid() && !attacker.player().isDead()) LegacyDrive.tick(platform, attacker, attackerRequest);
            if (third != null && third.player().isValid() && !third.player().isDead()) LegacyDrive.tick(platform, third, BotInput.neutral());
            captureFrame(targetRequest, attackerRequest);
            frame++;
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void selfChecks() throws Exception {
        reject(() -> input(false, -2, null, BotInput.UseAction.KEEP));
        reject(() -> input(false, 9, null, BotInput.UseAction.KEEP));
        reject(() -> new BotInput(false, false, false, false, false, false, false, 0, 0, -1, false, null, null));
        reject(() -> input(false, -1, UUID.randomUUID(), BotInput.UseAction.KEEP));
        reject(() -> input(true, -1, null, BotInput.UseAction.START_MAIN));
        reject(() -> new BotInput(false, false, false, false, false, false, false, Float.NaN, 0));
        BotInput legacy = new BotInput(false, false, false, false, false, false, false, 0, 0);
        check(legacy.heldSlot() == -1 && !legacy.attack() && legacy.attackTarget() == null
                && legacy.useAction() == BotInput.UseAction.KEEP, "legacy BotInput constructor is not neutral");
        attacker = spawn(new Location(world, 0.5, 100, 0.5), Map.of());
        BotInput neutral = BotInput.neutral();
        LegacyDrive.tick(platform, attacker, neutral);
        try {
            LegacyDrive.tick(platform, attacker, neutral);
            check(false, "duplicate tick was accepted");
        } catch (IllegalStateException expected) {
            check("Bot movement already ran this Bukkit tick".equals(expected.getMessage()), "unexpected duplicate tick error");
        }
        ItemStack original = new ItemStack(Material.DIAMOND_SWORD);
        platform.reset(attacker, new Location(world, 0.5, 100, 0.5), Map.of(EquipmentSlot.HAND, original));
        BotPlatform.Snapshot snapshot = platform.snapshot(attacker);
        snapshot.equipment().get(EquipmentSlot.HAND).setType(Material.AIR);
        check(snapshot.equipment().get(EquipmentSlot.HAND).getType() == Material.DIAMOND_SWORD
                && attacker.player().getInventory().getItemInMainHand().getType() == Material.DIAMOND_SWORD,
                "snapshot equipment is not copied");
        attacker.player().setHealth(10);
        attacker.player().setFoodLevel(4);
        attacker.player().setSaturation(0);
        attacker.player().setExhaustion(3);
        attacker.player().setAbsorptionAmount(3);
        attacker.player().addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
        attacker.player().setVelocity(new Vector(0.2, 0.3, 0.4));
        platform.reset(attacker, new Location(world, 0.5, 100, 0.5), Map.of());
        BotPlatform.Snapshot reset = platform.snapshot(attacker);
        check(reset.health() == 20 && reset.food() == 20 && close(reset.saturation(), 5) && close(reset.exhaustion(), 0)
                && close(reset.absorption(), 0) && reset.effects().isEmpty() && reset.heldSlot() == 0
                && close(reset.velocityX(), 0) && close(reset.velocityY(), 0) && close(reset.velocityZ(), 0),
                "alive reset did not clear changed state");
        disposeActors();
        check(SmokeIdentity.size() == initialIdentityCount && bodies().size() == initialBodyCount,
                "self-check actor cleanup failed");
    }

    private void prepareCase() throws Exception {
        resetSmallWall();
        String name = scenario();
        double targetZ = name.equals("reach-in") ? 3.7 : name.equals("reach-out") ? 3.9
                : name.equals("walk") || name.equals("shield-walk") || name.equals("food") || name.equals("food-interrupt")
                || name.equals("food-cancel") || name.equals("golden-apple") || name.equals("exhaustion")
                || name.equals("regeneration") ? 12.0 : 2.5;
        origin = new Location(world, 0.5, 100, 0.5);
        attacker = spawn(origin.clone(), Map.of(EquipmentSlot.HAND, new ItemStack(Material.DIAMOND_SWORD)));
        target = spawn(new Location(world, 0.5, 100, targetZ, 180, 0), Map.of());
        targetOrigin = target.player().getLocation().clone();
        target.player().setFoodLevel(4);
        target.player().setSaturation(0);
        if (name.equals("slot") || name.equals("slot-cancel")) attacker.player().getInventory().setItem(1, new ItemStack(Material.WOODEN_SWORD));
        if (name.equals("fast-speed")) attacker.player().getAttribute(Attribute.ATTACK_SPEED).setBaseValue(10);
        if (name.equals("wall")) for (int y = 100; y <= 102; y++) world.getBlockAt(0, y, 1).setType(Material.STONE, false);
        if (name.equals("occluded")) third = spawn(new Location(world, 0.5, 100, 1.5), Map.of());
        if (name.equals("sweep")) {
            third = spawn(new Location(world, 1.5, 100, 2.5), Map.of());
            third.player().setFoodLevel(4);
            third.player().setSaturation(0);
        }
        if (name.startsWith("shield-") && !name.equals("shield-walk")) target.player().getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        if (name.equals("shield-walk")) attacker.player().getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        if (name.equals("shield-axe")) attacker.player().getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_AXE));
        if (name.equals("shield-rear")) target.player().setRotation(0, 0);
        if (name.equals("shield-side")) target.player().setRotation(90, 0);
        if (name.equals("armor")) target.player().getInventory().setArmorContents(new ItemStack[]{
                new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.DIAMOND_LEGGINGS),
                new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_HELMET)});
        if (name.equals("enchant-effects")) {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            attacker.player().getInventory().setItemInMainHand(sword);
            ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
            chestplate.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
            target.player().getInventory().setChestplate(chestplate);
            attacker.player().addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 240, 0));
            target.player().addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 0));
        }
        if (name.equals("kb-resistance")) target.player().getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
        if (name.equals("food") || name.equals("food-interrupt") || name.equals("food-cancel")) {
            attacker.player().getInventory().setItemInMainHand(new ItemStack(Material.COOKED_BEEF, 2));
            attacker.player().setFoodLevel(4);
            attacker.player().setSaturation(0);
        }
        if (name.equals("golden-apple")) {
            attacker.player().getInventory().setItemInMainHand(new ItemStack(Material.GOLDEN_APPLE, 2));
            attacker.player().setFoodLevel(4);
            attacker.player().setSaturation(0);
        }
        if (name.equals("exhaustion")) {
            attacker.player().setFoodLevel(10);
            attacker.player().setSaturation(0);
            attacker.player().setExhaustion(4.1f);
        }
        if (name.equals("regeneration")) {
            attacker.player().setHealth(10);
            attacker.player().setFoodLevel(20);
            attacker.player().setSaturation(5);
        }
        if (name.equals("survival-death")) {
            attacker.player().teleport(new Location(world, 0.5, 106, 0.5));
            attacker.player().setFireTicks(60);
            target.player().setHealth(1);
        }
        frame = 0;
        clearCaseData();
        JSONObject fixture = new JSONObject().put("scenario", name).put("world", world.getUID().toString())
                .put("attacker", state(attacker)).put("target", state(target))
                .put("humanReference", JSONObject.NULL).put("stackText", "isolated native combat smoke; no human reference");
        String recordingCase = String.format(java.util.Locale.ROOT, "%02d", scenarioIndex + 1);
        beginRecording(attacker, "combat-" + recordingCase + "-attacker", fixture.toString());
        beginRecording(target, "combat-" + recordingCase + "-target", fixture.toString());
        if (third != null) beginRecording(third, "combat-" + recordingCase + "-third", fixture.toString());
        records.add(new JSONObject().put("kind", "header").put("scenario", name)
                .put("version", Bukkit.getMinecraftVersion()).put("stack", Bukkit.getVersion())
                .put("role", "managed-candidate").put("reference", "no-human-reference").toString());
        minHealth = Math.min(attacker.player().getHealth(), target.player().getHealth());
    }

    private void beginRecording(BotActor actor, String label, String fixture) throws Exception {
        UUID recording = (UUID) startManaged.invoke(probe, actor, label, scenario().equals("regeneration") ? 200 : 80, fixture);
        check(recording != null, "managed probe refused " + label);
        recordings.add(recording);
    }

    private BotInput targetInput() {
        String name = scenario();
        BotInput.UseAction use = BotInput.UseAction.KEEP;
        if (name.startsWith("shield-") && !name.equals("shield-walk")
                && frame == (name.equals("shield-early") ? 30 : 20)) use = BotInput.UseAction.START_OFF;
        if (name.equals("shield-release") && frame == 28) use = BotInput.UseAction.RELEASE;
        return new BotInput(false, false, false, false, false, false, false, 0, 0, -1, false, null, use);
    }

    private BotInput attackerInput() {
        String name = scenario();
        int slot = name.equals("slot") || name.equals("slot-cancel") ? frame == 20 ? 1 : -1 : -1;
        BotInput.UseAction use = BotInput.UseAction.KEEP;
        if ((name.equals("food") || name.equals("food-interrupt") || name.equals("food-cancel") || name.equals("golden-apple"))
                && frame == 20) use = BotInput.UseAction.START_MAIN;
        if (name.equals("shield-walk") && frame == 0) use = BotInput.UseAction.START_OFF;
        if (name.equals("food-interrupt") && frame == 25) use = BotInput.UseAction.RELEASE;
        // Attacks run before the movement tick, so sprint must already be on when the attack frame starts.
        boolean forward = (name.equals("walk") && frame < 20) || (name.equals("shield-walk") && frame < 20)
                || name.equals("sprint-hit") && frame >= 28 && frame <= 30;
        boolean sprint = name.equals("shield-walk") && frame < 20 || name.equals("sprint-hit") && (frame == 28 || frame == 29);
        boolean jump = name.equals("critical") && frame == 24;
        boolean attack = switch (name) {
            case "early" -> frame == 1;
            case "repeated" -> frame == 30 || frame == 31;
            case "critical" -> frame == 31;
            case "shield-early" -> frame == 32;
            case "survival-death" -> frame == 30;
            case "walk", "shield-walk", "food", "food-interrupt", "food-cancel", "golden-apple", "exhaustion", "regeneration" -> false;
            default -> frame == 30;
        };
        float yawDelta = 0;
        float pitchDelta = 0;
        if (name.equals("aim-miss")) {
            yawDelta = wrapYaw(90 - attacker.player().getYaw());
        } else if (!name.equals("walk") && !name.equals("shield-walk") && target != null && target.player().isValid()) {
            Location from = attacker.player().getEyeLocation();
            Vector direction = target.player().getEyeLocation().toVector().subtract(from.toVector()).normalize();
            float desiredYaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            float desiredPitch = (float) Math.toDegrees(-Math.asin(direction.getY()));
            yawDelta = wrapYaw(desiredYaw - attacker.player().getYaw());
            pitchDelta = Math.max(-90, Math.min(90, desiredPitch - attacker.player().getPitch()));
        }
        return new BotInput(forward, false, false, false, jump, false, sprint, yawDelta, pitchDelta, slot, attack,
                attack && !name.equals("miss") ? target.entityId() : null, use);
    }

    private static float wrapYaw(float value) {
        float wrapped = value % 360;
        if (wrapped > 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    private void captureFrame(BotInput targetRequest, BotInput attackerRequest) {
        if (attacker == null) return;
        BotPlatform.Snapshot attackerState = platform.snapshot(attacker);
        BotPlatform.Snapshot targetState = target == null || !target.player().isValid() ? null : platform.snapshot(target);
        minHealth = Math.min(minHealth, attackerState.health());
        maxAbsorption = Math.max(maxAbsorption, attackerState.absorption());
        if (targetState != null) {
            minHealth = Math.min(minHealth, targetState.health());
            if (!List.of("walk", "shield-walk").contains(scenario())) maxDisplacement = Math.max(maxDisplacement,
                    horizontalDistance(targetOrigin, target.player().getLocation()));
            metrics.put("targetMaxUseTicks", Math.max(metrics.getOrDefault("targetMaxUseTicks", 0.0), (double) targetState.useTicks()));
        }
        if (List.of("walk", "shield-walk").contains(scenario())) maxDisplacement = Math.max(maxDisplacement, horizontalDistance(origin, attacker.player().getLocation()));
        metrics.put("maxUseTicks", Math.max(metrics.getOrDefault("maxUseTicks", 0.0), (double) attackerState.useTicks()));
        records.add(new JSONObject().put("kind", "frame").put("scenario", scenario()).put("frame", frame)
                .put("attackerRequest", inputJson(attackerRequest)).put("targetRequest", inputJson(targetRequest))
                .put("thirdRequest", inputJson(BotInput.neutral())).put("attacker", snapshotJson(attackerState))
                .put("target", targetState == null ? JSONObject.NULL : snapshotJson(targetState))
                .put("third", third == null || !third.player().isValid() ? JSONObject.NULL : snapshotJson(platform.snapshot(third))).toString());
        if (attackerState.attackCooldown() >= 1.0f) metrics.putIfAbsent("firstReadyFrame", (double) frame);
        if (scenario().equals("fast-speed") && frame == 10) metrics.put("frame10Cooldown", (double) attackerState.attackCooldown());
        if (scenario().equals("sprint-hit") && frame == 30) metrics.put("sprintingAfterHit", attacker.player().isSprinting() ? 1.0 : 0.0);
        if (scenario().equals("shield-walk") && attacker.player().isSprinting()) metrics.put("sprintedWithShield", 1.0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageModify(EntityDamageByEntityEvent event) {
        if (!owned(event.getEntity())) return;
        if (scenario().equals("damage-cancel") && event.getEntity().equals(target.player())) event.setCancelled(true);
        if (scenario().equals("damage-knockback-modify") && event.getEntity().equals(target.player())) {
            event.setDamage(2.0);
            modifiedDamage = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!owned(event.getEntity())) return;
        damageEvents++;
        if (event.isCancelled()) cancelledDamageEvents++;
        if (target != null && event.getEntity().equals(target.player()) && event.getDamager().equals(attacker.player())) {
            maxTargetDamage = Math.max(maxTargetDamage, event.getFinalDamage());
            damageBeforeCancel = event.getDamage();
            damageAfterCancel = event.getFinalDamage();
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) metrics.put("attackDamageEvents", 1.0);
        }
        event("damage", new JSONObject().put("base", event.getDamage()).put("final", event.getFinalDamage())
                .put("cancelled", event.isCancelled()).put("cause", event.getCause().name())
                .put("damager", event.getDamager().getUniqueId().toString()).put("recipient", event.getEntity().getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOtherDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !owned(event.getEntity())) return;
        event("environment-damage", new JSONObject().put("damage", event.getDamage()).put("final", event.getFinalDamage())
                .put("cause", event.getCause().name()).put("cancelled", event.isCancelled()));
        if (event.getEntity().equals(attacker.player())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FIRE || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                    || event.getCause() == EntityDamageEvent.DamageCause.LAVA) metrics.put("fireDamage", 1.0);
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) metrics.put("fallDamage", 1.0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (owned(event.getEntity()) && scenario().equals("damage-knockback-modify") && event.getEntity().equals(target.player())) {
            event.setFinalKnockback(new Vector(0, 0, 0));
            modifiedKnockback = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKnockbackObserved(EntityKnockbackEvent event) {
        if (!owned(event.getEntity())) return;
        Vector finalVelocity = event.getFinalKnockback();
        event("knockback", new JSONObject().put("entity", event.getEntity().getUniqueId().toString())
                .put("finalX", finalVelocity.getX()).put("finalY", finalVelocity.getY()).put("finalZ", finalVelocity.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (owned(event.getPlayer())) { preAttackEvents++; event("pre-attack", new JSONObject().put("attacker", event.getPlayer().getUniqueId().toString()).put("target", event.getAttacked().getUniqueId().toString())); }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerArmSwingEvent event) {
        if (owned(event.getPlayer())) { swingEvents++; event("swing", new JSONObject().put("player", event.getPlayer().getUniqueId().toString())); }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (owned(event.getPlayer())) event("interact", new JSONObject().put("player", event.getPlayer().getUniqueId().toString())
                .put("action", event.getAction().name()).put("hand", event.getHand() == null ? "none" : event.getHand().name()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeldModify(PlayerItemHeldEvent event) {
        if (owned(event.getPlayer()) && scenario().equals("slot-cancel")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        if (owned(event.getPlayer())) { heldEvents++; event("held-slot", new JSONObject().put("player", event.getPlayer().getUniqueId().toString())
                .put("previous", event.getPreviousSlot()).put("new", event.getNewSlot()).put("cancelled", event.isCancelled())); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsumeCancel(PlayerItemConsumeEvent event) {
        if (owned(event.getPlayer()) && scenario().equals("food-cancel")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (owned(event.getPlayer())) { consumeEvents++; actualConsumeFrame = frame; event("consume", new JSONObject()
                .put("player", event.getPlayer().getUniqueId().toString()).put("item", event.getItem().getType().name()).put("cancelled", event.isCancelled())); }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFood(FoodLevelChangeEvent event) {
        if (owned(event.getEntity())) event("food", new JSONObject().put("from", event.getEntity() instanceof Player player ? player.getFoodLevel() : JSONObject.NULL)
                .put("to", event.getFoodLevel()).put("cancelled", event.isCancelled()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegain(EntityRegainHealthEvent event) {
        if (owned(event.getEntity())) { regainEvents++; event("regain", new JSONObject().put("amount", event.getAmount()).put("reason", event.getRegainReason().name())); }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (owned(event.getEntity())) { deathEvents++; event("death", new JSONObject().put("player", event.getEntity().getUniqueId().toString())); }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent event) {
        if ((event.getPlugin() == plugin || event.getPlugin().getName().equals("ZonePracticePro") || event.getPlugin().getName().equals("ZPPBots")) && !finished) fail(new IllegalStateException("Plugin disabled during combat smoke"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        if (finished || attacker == null) return;
        if (attacker.player().isValid()) {
            BotPlatform.Snapshot snapshot = platform.snapshot(attacker);
            metrics.put("maxAbsorption", Math.max(metrics.getOrDefault("maxAbsorption", 0.0), snapshot.absorption()));
            metrics.put("maxUseTicks", Math.max(metrics.getOrDefault("maxUseTicks", 0.0), (double) snapshot.useTicks()));
        }
        if (target != null && target.player().isValid() && !target.player().isDead()) {
            shieldCooldown = Math.max(shieldCooldown, target.player().getCooldown(Material.SHIELD));
        }
    }

    private void finishCase() throws Exception {
        assertCase();
        if (scenario().equals("ready")) {
            readyDamage = maxTargetDamage;
            readyDisplacement = maxDisplacement;
            readyEndingHealth = target.player().getHealth();
        }
        String caseName = scenario();
        JSONObject result = new JSONObject(metrics).put("scenario", caseName).put("damage", maxTargetDamage).put("cancelledDamageEvents", cancelledDamageEvents)
                .put("damageEvents", damageEvents).put("damageBeforeCancel", damageBeforeCancel).put("damageAfterCancel", damageAfterCancel)
                .put("endingHealth", scenario().equals("survival-death") ? metrics.getOrDefault("deadTargetHealth", 0.0)
                        : target != null && target.player().isValid() ? target.player().getHealth() : 0)
                .put("minimumHealth", minHealth).put("maxAbsorption", maxAbsorption).put("remainingHand", attacker.player().getInventory().getItemInMainHand().getAmount())
                .put("remainingDurability", attacker.player().getInventory().getItemInMainHand().getDurability())
                .put("maxDisplacement", maxDisplacement).put("actualConsumeFrame", actualConsumeFrame).put("shieldCooldown", shieldCooldown);
        records.add(new JSONObject().put("kind", "outcome").put("scenario", caseName).put("passed", true).put("metrics", result).toString());
        plugin.getLogger().info("BOT_COMBAT_CASE PASS " + caseName + " " + result + " recordings=" + recordings);
        for (UUID recording : List.copyOf(recordings)) stopRecording(recording, "COMPLETED");
        recordings.clear();
        disposeActors();
        check(SmokeIdentity.size() == initialIdentityCount, "identity count changed after " + caseName);
        check(bodies().size() == initialBodyCount, "native body count changed after " + caseName);
        check(Bukkit.getOnlinePlayers().size() == humanOnlineCount, "online count changed after " + caseName);
        Path output = plugin.getDataFolder().toPath().resolve("combat").resolve(Bukkit.getMinecraftVersion()).resolve(caseName + ".jsonl");
        List<String> immutableRecords = List.copyOf(records);
        writingCase = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.createDirectories(output.getParent());
                Files.write(output, immutableRecords, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (finished) return;
                    writingCase = false;
                    scenarioIndex++;
                    if (scenarioIndex == SCENARIOS.size()) succeed();
                });
            } catch (IOException error) { Bukkit.getScheduler().runTask(plugin, () -> fail(error)); }
        });
        attacker = null;
        target = null;
        third = null;
    }

    private void assertCase() {
        String name = scenario();
        double targetHealth = target != null && target.player().isValid() ? target.player().getHealth() : 0;
        switch (name) {
            case "ready" -> check(maxTargetDamage > 0 && close(20 - targetHealth, maxTargetDamage)
                    && close(metrics.getOrDefault("preHitCooldown", 0.0), 1) && preAttackEvents > 0 && damageEvents > 0,
                    "ready attack evidence missing");
            case "early" -> check(metrics.getOrDefault("preHitCooldown", 1.0) < 1
                    && maxTargetDamage > 0 && maxTargetDamage < readyDamage, "early attack was not weaker than charged baseline");
            case "repeated" -> check(close(targetHealth, readyEndingHealth), "repeat hit changed ending health from ready baseline");
            case "fast-speed" -> check(close(metrics.getOrDefault("frame10Cooldown", 0.0), 1)
                    && maxTargetDamage > 0 && maxTargetDamage <= readyDamage + EPSILON,
                    "fast-speed cooldown/damage differed from native baseline");
            case "miss" -> check(close(targetHealth, 20) && swingEvents > 0, "miss hit target or did not swing");
            case "slot" -> check(heldEvents > 0 && attacker.player().getInventory().getHeldItemSlot() == 1
                    && maxTargetDamage > 0 && maxTargetDamage < readyDamage, "slot switch did not affect weapon");
            case "slot-cancel" -> check(attacker.player().getInventory().getHeldItemSlot() == 0 && close(maxTargetDamage, readyDamage), "cancelled slot change altered hit");
            case "reach-in" -> check(maxTargetDamage > 0, "near reach target was not hit");
            case "reach-out" -> check(close(targetHealth, 20) && swingEvents > 0, "out-of-reach target was hit or not swung at");
            case "wall", "aim-miss" -> check(close(targetHealth, 20), "blocked aim reached target");
            case "occluded" -> check(close(targetHealth, 20) && (third == null || close(third.player().getHealth(), 20)), "occlusion selected a body");
            case "critical" -> check(metrics.getOrDefault("preHitOnGround", 1.0) == 0.0
                    && metrics.getOrDefault("preHitFallDistance", 0.0) > 0 && maxTargetDamage > readyDamage,
                    "critical attack state or damage missing");
            case "sweep" -> check(third != null && third.player().getHealth() < 20, "sweep did not damage adjacent native body");
            case "sprint-hit" -> check(maxTargetDamage > 0 && metrics.getOrDefault("sprintingAfterHit", 1.0) == 0,
                    "sprint attack did not reset sprint");
            case "shield-early" -> check(targetHealth < 20 && target.player().isHandRaised(), "early shield raised in time or prevented damage");
            case "shield-front" -> check(close(targetHealth, 20) && target.player().getInventory().getItemInOffHand().getDurability() > 0,
                    "front shield did not block or lose durability");
            case "shield-rear" -> check(targetHealth < 20, "rear shield blocked the attack");
            case "shield-side" -> check(metrics.getOrDefault("targetMaxUseTicks", 0.0) >= 5 && swingEvents > 0 && preAttackEvents > 0,
                    "side shield observation window missing");
            case "shield-release" -> check(!target.player().isHandRaised() && targetHealth < 20, "released shield remained active");
            case "shield-axe" -> check(shieldCooldown > 0, "axe did not apply native shield cooldown");
            case "walk" -> check(maxDisplacement > 0, "walk did not move");
            case "shield-walk" -> check(maxDisplacement > 0 && maxDisplacement < walkDistance
                    && metrics.getOrDefault("sprintedWithShield", 0.0) == 0 && !attacker.player().isSprinting()
                    && attacker.player().isHandRaised(), "shield walk movement constraints failed");
            case "food" -> check(consumeEvents == 1 && attacker.player().getInventory().getItemInMainHand().getAmount() == 1
                    && attacker.player().getFoodLevel() > 4 && actualConsumeFrame >= 51 && !attacker.player().isHandRaised(), "food consume was not native/timed");
            case "food-interrupt" -> check(consumeEvents == 0 && attacker.player().getInventory().getItemInMainHand().getAmount() == 2
                    && attacker.player().getFoodLevel() == 4, "interrupted food was consumed");
            case "food-cancel" -> check(consumeEvents == 1 && attacker.player().getInventory().getItemInMainHand().getAmount() == 2
                    && attacker.player().getFoodLevel() == 4, "cancelled consume changed state");
            case "golden-apple" -> check(consumeEvents == 1 && attacker.player().getInventory().getItemInMainHand().getAmount() == 1
                    && attacker.player().getAbsorptionAmount() > 0 && !attacker.player().getActivePotionEffects().isEmpty(), "golden apple native effects missing");
            case "exhaustion" -> check(attacker.player().getExhaustion() < 4.1f && attacker.player().getFoodLevel() < 10, "natural exhaustion did not reduce food");
            case "regeneration" -> check(attacker.player().getHealth() > 10 && regainEvents > 0
                    && Float.isFinite(attacker.player().getSaturation()) && Float.isFinite(attacker.player().getExhaustion()), "native regeneration missing");
            case "armor" -> check(maxTargetDamage > 0 && maxTargetDamage < readyDamage
                    && platform.snapshot(target).equipment().get(EquipmentSlot.HEAD).getDurability() > 0
                    && platform.snapshot(attacker).equipment().get(EquipmentSlot.HAND).getDurability() > 0,
                    "armor or weapon durability outcome missing");
            case "enchant-effects" -> check(Double.isFinite(maxTargetDamage) && maxTargetDamage > 0
                    && platform.snapshot(attacker).equipment().get(EquipmentSlot.HAND).getEnchantmentLevel(Enchantment.SHARPNESS) == 5
                    && platform.snapshot(target).equipment().get(EquipmentSlot.CHEST).getEnchantmentLevel(Enchantment.PROTECTION) == 4
                    && !attacker.player().getActivePotionEffects().isEmpty() && !target.player().getActivePotionEffects().isEmpty(),
                    "enchant/effect native outcome missing");
            case "kb-resistance" -> check(maxTargetDamage > 0 && maxDisplacement < readyDisplacement, "knockback resistance did not reduce displacement");
            case "damage-cancel" -> check(close(targetHealth, 20) && cancelledDamageEvents > 0, "damage cancellation was not retained");
            case "damage-knockback-modify" -> check(close(targetHealth, 18) && modifiedDamage && modifiedKnockback && close(maxDisplacement, 0),
                    "modified damage/knockback did not match fixture");
            case "survival-death" -> check(deathEvents > 0 && metrics.containsKey("fireDamage") && metrics.containsKey("fallDamage") && damageEvents > 0, "fall/fire/death events were incomplete");
            default -> throw new IllegalStateException("Unexpected combat case " + name);
        }
        if (name.equals("walk")) walkDistance = maxDisplacement;
        if (name.equals("survival-death") && target.player().isDead()) replaceDeadTarget();
    }

    private double walkDistance;

    private void replaceDeadTarget() {
        try {
            BodyToken old = target.identity();
            reject(() -> platform.reset(target, new Location(world, 0.5, 100, 2.5), Map.of()));
            metrics.put("deadTargetHealth", target.player().getHealth());
            platform.remove(target);
            actors.remove(target);
            SmokeIdentity.release(old);
            tokens.remove(old);
            BodyToken token = SmokeIdentity.reserve(plugin, old.sessionId(), 2);
            tokens.add(token);
            target = platform.spawn(token, new Location(world, 0.5, 100, 2.5, 180, 0), Map.of());
            targetOrigin = target.player().getLocation().clone();
            actors.add(target);
            BotPlatform.Snapshot fresh = platform.snapshot(target);
            check(fresh.health() == 20 && fresh.food() == 20 && close(fresh.velocityX(), 0) && close(fresh.velocityY(), 0)
                    && close(fresh.velocityZ(), 0) && !fresh.usingItem() && fresh.effects().isEmpty()
                    && target.player().getCooldown(Material.SHIELD) == 0, "fresh generation inherited dead state");
            event("dead-reset", new JSONObject().put("rejected", true).put("generation", 2).put("freshUuid", target.entityId().toString()));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private void clearCaseData() {
        records.clear(); recordings.clear(); metrics.clear(); maxTargetDamage = 0; damageBeforeCancel = 0; damageAfterCancel = 0;
        maxAbsorption = 0; minHealth = 20; maxDisplacement = 0; shieldCooldown = 0; actualConsumeFrame = -1;
        damageEvents = 0; cancelledDamageEvents = 0; regainEvents = 0; heldEvents = 0; swingEvents = 0; preAttackEvents = 0;
        deathEvents = 0; consumeEvents = 0; modifiedDamage = false; modifiedKnockback = false;
    }

    private void event(String type, JSONObject value) {
        records.add(new JSONObject().put("kind", "event").put("scenario", scenario()).put("frame", frame)
                .put("event", type).put("data", value).toString());
    }

    private static JSONObject inputJson(BotInput input) {
        return new JSONObject().put("forward", input.forward()).put("backward", input.backward()).put("left", input.left())
                .put("right", input.right()).put("jump", input.jump()).put("sneak", input.sneak()).put("sprint", input.sprint())
                .put("yawDelta", input.yawDelta()).put("pitchDelta", input.pitchDelta()).put("heldSlot", input.heldSlot())
                .put("attack", input.attack()).put("attackTarget", input.attackTarget() == null ? JSONObject.NULL : input.attackTarget().toString())
                .put("useAction", input.useAction().name());
    }

    private static JSONObject snapshotJson(BotPlatform.Snapshot snapshot) {
        JSONArray equipment = new JSONArray();
        snapshot.equipment().forEach((slot, item) -> equipment.put(new JSONObject().put("slot", slot.name()).put("item", item.getType().name())
                .put("amount", item.getAmount()).put("durability", item.getDurability()).put("enchantments", item.getEnchantments().toString())));
        JSONArray effects = new JSONArray();
        for (PotionEffect effect : snapshot.effects()) effects.put(new JSONObject().put("type", effect.getType().getKey().toString())
                .put("amplifier", effect.getAmplifier()).put("duration", effect.getDuration()));
        return new JSONObject().put("uuid", snapshot.entityId().toString()).put("x", snapshot.x()).put("y", snapshot.y()).put("z", snapshot.z())
                .put("yaw", snapshot.yaw()).put("pitch", snapshot.pitch()).put("vx", snapshot.velocityX()).put("vy", snapshot.velocityY())
                .put("vz", snapshot.velocityZ()).put("onGround", snapshot.onGround()).put("sprinting", snapshot.sprinting())
                .put("health", snapshot.health()).put("food", snapshot.food()).put("saturation", snapshot.saturation())
                .put("exhaustion", snapshot.exhaustion()).put("absorption", snapshot.absorption()).put("cooldown", snapshot.attackCooldown())
                .put("slot", snapshot.heldSlot()).put("using", snapshot.usingItem()).put("hand", snapshot.activeHand() == null ? "none" : snapshot.activeHand().name())
                .put("useTicks", snapshot.useTicks()).put("useRemaining", snapshot.useRemainingTicks()).put("blocking", snapshot.blocking())
                .put("equipment", equipment).put("effects", effects);
    }

    private static JSONObject state(BotActor actor) {
        return new JSONObject().put("uuid", actor.entityId().toString()).put("location", actor.player().getLocation().toString())
                .put("generation", actor.identity().generation());
    }

    private static BotInput input(boolean attack, int slot, UUID target, BotInput.UseAction use) {
        return new BotInput(false, false, false, false, false, false, false, 0, 0, slot, attack, target, use);
    }

    private boolean owned(Entity entity) { return entity != null && SmokeIdentity.isManaged(entity.getUniqueId()); }

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

    private void stopRecording(UUID recording, String reason) throws Exception {
        for (BotActor actor : List.copyOf(actors)) {
            try {
                stopManaged.invoke(probe, actor, recording, reason);
            } catch (java.lang.reflect.InvocationTargetException ignored) { }
        }
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
        attacker = null;
        target = null;
        third = null;
        if (failure != null) throw new IllegalStateException("Could not dispose combat actors", failure);
    }

    private void succeed() {
        if (finished) return;
        finished = true;
        try {
            cleanup();
            plugin.getLogger().info("BOT_COMBAT_SMOKE PASS scenarios=" + SCENARIOS.size());
            success.run();
        } catch (Throwable error) { failure.accept(error); }
    }

    private void fail(Throwable error) {
        if (finished) return;
        plugin.getLogger().severe("BOT_COMBAT_SMOKE FAIL scenario=" + (scenarioIndex < 0 ? "self-check" : scenario())
                + " frame=" + frame + " error=" + error.getMessage());
        finished = true;
        try { cleanup(); } catch (Throwable cleanupError) { error.addSuppressed(cleanupError); }
        failure.accept(error);
    }

    private void cleanup() {
        if (task != null) { task.cancel(); task = null; }
        HandlerList.unregisterAll(this);
        for (UUID recording : List.copyOf(recordings)) {
            try { stopRecording(recording, "ERROR"); } catch (Throwable ignored) { }
        }
        recordings.clear();
        if (platform != null) disposeActors();
    }

    private void resetSmallWall() {
        for (int x = -2; x <= 2; x++) for (int y = 100; y <= 102; y++) world.getBlockAt(x, y, 1).setType(Material.AIR, false);
    }

    private String scenario() { return SCENARIOS.get(scenarioIndex); }

    private static double horizontalDistance(Location from, Location to) { return Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()); }
    private static boolean close(double left, double right) { return Math.abs(left - right) <= EPSILON; }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new IllegalStateException("Expected IllegalArgumentException"); }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static Field findField(Class<?> type, Class<?> fieldType) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) for (Field field : current.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && fieldType.isAssignableFrom(field.getType())) { field.setAccessible(true); return field; }
        }
        throw new NoSuchFieldException(type.getName() + " has no " + fieldType.getSimpleName() + " field");
    }

    private static Method findMethod(Class<?> type, Class<?> returnType, Class<?>... parameters) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) if (java.util.Arrays.equals(method.getParameterTypes(), parameters)
                && method.getReturnType() == returnType) return method;
        throw new NoSuchMethodException(type.getName() + " has no matching managed-probe method");
    }
}
