package dev.nandi0813.zppbots.smoke;

import dev.nandi0813.api.Interface.BotFightView;
import dev.nandi0813.api.Interface.BotOpponent;
import dev.nandi0813.api.Interface.BotOptions;
import dev.nandi0813.api.Interface.BotProvider;
import dev.nandi0813.api.Interface.BotRegistry;
import dev.nandi0813.api.ZonePracticeApi;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.platform.BodyToken;
import dev.nandi0813.zppbots.platform.BotActor;
import dev.nandi0813.zppbots.platform.BotPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** One bot against a sturdy dummy per AI feature (-Dbot.smoke.feature=name); asserts one observable event. */
final class BotFeatureSmoke implements Listener {

    private final JavaPlugin plugin;
    private final String name;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private World world;
    private BotPlatform platform;
    private BotRegistry registry;
    private BodyToken dummyToken;
    private BotActor dummy;
    private UUID botId;
    private BotOpponent bot;
    private FeatureView view;
    private BukkitTask task;
    private boolean finished;
    private int ticks;
    private int timeout;
    private double dummyX = 6.5;

    private final List<Integer> hitTicks = new ArrayList<>();
    private boolean tooClose;
    private boolean shieldCooldown;
    private boolean splashSelf;
    private boolean ate;
    private boolean arrowHit;
    private boolean pearlTeleport;
    private boolean webNearDummy;
    private int webs;
    private boolean bucketEmpty;
    private boolean crystalBlast;
    private int crystalsSpawned;
    private boolean anchorPlaced;
    private boolean anchorBlast;
    private boolean cartBlast;
    private boolean maceHit;
    private double maceDamage;
    private boolean glided;
    private boolean rockets;
    private boolean boxPlaced;
    private boolean appleAfter;
    private boolean boxBroken;
    private boolean plankBroken;
    private int placed;
    private int rocketsStart = -1;
    private boolean passedWall;
    private int wallStuck;
    private double wallX = Double.NaN;
    private double wallZ = Double.NaN;
    private int shots;
    private int waterDumps;
    private int lavaDumps;
    private int refills;
    private double minDist = Double.MAX_VALUE;

    BotFeatureSmoke(JavaPlugin plugin, String name, Runnable success, Consumer<Throwable> failure) {
        this.plugin = plugin;
        this.name = name;
        this.success = success;
        this.failure = failure;
    }

    void start() {
        try {
            check(Bukkit.getOnlinePlayers().isEmpty(), "feature smoke requires zero online players");
            check(List.of("sword", "axe-shield", "pots", "gapple", "bow", "crossbow", "pearl", "cobweb",
                    "water-clutch", "crystal", "anchor", "cart", "mace", "elytra", "elytra-wall", "restock", "scaffold", "builduhc", "boxed").contains(name),
                    "unknown feature " + name);
            platform = ZPPBots.getInstance().getPlatform();
            check(platform != null, "native platform is unavailable");
            registry = ZonePracticeApi.getInstance().getBotRegistry();
            BotProvider provider = registry.getProvider();
            check(provider != null && "zpp-bots".equals(provider.getId()) && provider.isAvailable(), "zpp-bots provider is unavailable");
            world = Bukkit.getWorlds().getFirst();
            prepareTerrain();
            if (name.equals("elytra-wall")) {
                for (int y = 100; y <= 118; y++) for (int z = -20; z <= 20; z++) world.getBlockAt(18, y, z).setType(Material.BARRIER, false);
            }

            timeout = switch (name) {
                case "axe-shield", "cobweb" -> 400;
                case "pots", "gapple" -> 300;
                case "water-clutch" -> 200;
                case "restock", "scaffold" -> 800;
                case "boxed" -> 1200;
                default -> 600;
            };
            dummyX = switch (name) {
                case "bow", "crossbow" -> 14.5;
                case "pearl" -> 24.5;
                case "cobweb" -> 1.5;
                case "crystal", "anchor" -> 3.5;
                case "cart" -> 4.5;
                case "elytra", "elytra-wall" -> 35.5;
                case "scaffold" -> 6.5;
                case "builduhc" -> 5.5;
                case "boxed" -> 20.5;
                default -> 6.5;
            };

            if (name.equals("scaffold")) {
                for (int y = 100; y <= 104; y++) world.getBlockAt(6, y, 0).setType(Material.STONE, false);
            }
            if (name.equals("boxed")) {
                // a plank box around the dummy: walls at y 100..101 except the centre column, roof at y 102
                for (int x = 19; x <= 21; x++) for (int z = -1; z <= 1; z++) {
                    for (int y = 100; y <= 101; y++) if (x != 20 || z != 0) world.getBlockAt(x, y, z).setType(Material.BIRCH_PLANKS, false);
                    world.getBlockAt(x, 102, z).setType(Material.BIRCH_PLANKS, false);
                }
            }
            dummyToken = SmokeIdentity.reserve(plugin, UUID.randomUUID(), 1);
            dummy = platform.spawn(dummyToken, new Location(world, dummyX, name.equals("scaffold") ? 105.0 : 100.0, 0.5, 90.0f, 0.0f), Map.of());
            Player dummyPlayer = dummy.player();
            dummyPlayer.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000.0);
            dummyPlayer.setHealth(1000.0);
            dummyPlayer.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            var armor = dummyPlayer.getInventory();
            armor.setHelmet(new ItemStack(Material.IRON_HELMET));
            armor.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            armor.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            armor.setBoots(new ItemStack(Material.IRON_BOOTS));
            if (name.equals("restock")) dummyPlayer.setGameMode(org.bukkit.GameMode.CREATIVE);
            if (name.equals("axe-shield")) armor.setItemInOffHand(new ItemStack(Material.SHIELD));

            botId = registry.reserveEntityId(plugin);
            bot = provider.spawn(botId, new Location(world, -6.5, name.equals("water-clutch") ? 120.0 : 100.0, 0.5, -90.0f, 0.0f),
                    new BotOptions("Expert", Map.of("difficulty", "5", "playstyle", "DEFAULT")));
            Player self = bot.getPlayer();
            self.getInventory().clear();
            giveKit(self);
            self.getInventory().setHeldItemSlot(0);
            // a ZPP match teleports the bot into the arena; a pending teleport blocks use-item-on until acked
            self.teleport(self.getLocation());
            self.showEntity(plugin, dummyPlayer);
            bot.pause();

            view = new FeatureView(dummyPlayer, new BoundingBox(-40, 99, -40, 41, 131, 41));
            Bukkit.getPluginManager().registerEvents(this, plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void giveKit(Player self) {
        var inv = self.getInventory();
        switch (name) {
            case "sword" -> inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
            case "axe-shield" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_AXE));
                inv.setItem(1, new ItemStack(Material.DIAMOND_SWORD));
            }
            case "pots" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                for (int slot = 1; slot <= 6; slot++) inv.setItem(slot, healing());
                self.setHealth(5.0);
            }
            case "gapple" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 4));
                self.setHealth(5.0);
            }
            case "bow" -> {
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addUnsafeEnchantment(Enchantment.INFINITY, 1);
                inv.setItem(0, bow);
                inv.setItem(9, new ItemStack(Material.ARROW, 1));
            }
            case "crossbow" -> {
                inv.setItem(0, new ItemStack(Material.CROSSBOW));
                inv.setItem(9, new ItemStack(Material.ARROW, 16));
            }
            case "pearl" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.ENDER_PEARL, 4));
            }
            case "cobweb" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.COBWEB, 16));
            }
            case "water-clutch" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.WATER_BUCKET));
            }
            // the crystal and anchor doctrines only arm with a totem in the inventory (SpecialsPhase.inCrystalPvpMode)
            case "crystal" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.OBSIDIAN, 16));
                inv.setItem(2, new ItemStack(Material.END_CRYSTAL, 16));
                inv.setItem(3, new ItemStack(Material.TOTEM_OF_UNDYING));
            }
            case "anchor" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.RESPAWN_ANCHOR, 8));
                inv.setItem(2, new ItemStack(Material.GLOWSTONE, 16));
                inv.setItem(3, new ItemStack(Material.TOTEM_OF_UNDYING));
            }
            case "cart" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.RAIL, 16));
                inv.setItem(2, new ItemStack(Material.TNT_MINECART, 4));
                inv.setItem(3, new ItemStack(Material.FLINT_AND_STEEL));
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addUnsafeEnchantment(Enchantment.FLAME, 1);
                inv.setItem(4, bow);
                inv.setItem(5, new ItemStack(Material.ARROW, 16));
                inv.setHelmet(blastProof(Material.NETHERITE_HELMET));
                inv.setChestplate(blastProof(Material.NETHERITE_CHESTPLATE));
                inv.setLeggings(blastProof(Material.NETHERITE_LEGGINGS));
                inv.setBoots(blastProof(Material.NETHERITE_BOOTS));
            }
            case "mace" -> {
                inv.setItem(0, new ItemStack(Material.MACE));
                inv.setItem(1, new ItemStack(Material.WIND_CHARGE, 16));
            }
            case "elytra", "elytra-wall" -> {
                inv.setChestplate(new ItemStack(Material.ELYTRA));
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.FIREWORK_ROCKET, 32));
                // every ported elytra combo needs a mace (ElytraMaceController.tickIdle) or spear in the kit
                inv.setItem(2, new ItemStack(Material.MACE));
            }
            case "restock" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                ItemStack box = new ItemStack(Material.SHULKER_BOX);
                BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
                ShulkerBox state = (ShulkerBox) meta.getBlockState();
                state.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 8));
                meta.setBlockState(state);
                box.setItemMeta(meta);
                inv.setItem(1, box);
                // RestockController only runs with no target (NoTargetPhase) and only for a supply that was stocked once:
                // the dummy is creative (ignored by targeting) and loose apples are removed and health set to 6 at tick 15
                inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 4));
            }
            case "scaffold" -> {
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.COBBLESTONE, 64));
            }
            // the BuildUHC kit of ladders/builduhc.yml without the fishing rod
            case "builduhc", "boxed" -> {
                inv.setItem(0, new ItemStack(Material.NETHERITE_SWORD));
                inv.setItem(1, new ItemStack(Material.BOW));
                inv.setItem(2, new ItemStack(Material.WATER_BUCKET, 2));
                inv.setItem(3, new ItemStack(Material.LAVA_BUCKET, 2));
                inv.setItem(4, new ItemStack(Material.COBBLESTONE, 64));
                inv.setItem(5, new ItemStack(Material.GOLDEN_APPLE, 6));
                inv.setItem(6, new ItemStack(Material.BIRCH_PLANKS, 64));
                inv.setItem(9, new ItemStack(Material.ARROW, 32));
                inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                if (name.equals("boxed")) {
                    inv.setItem(7, new ItemStack(Material.DIAMOND_PICKAXE));
                    inv.setItem(8, new ItemStack(Material.DIAMOND_AXE));
                }
            }
            default -> throw new IllegalStateException("unknown feature " + name);
        }
    }

    private static ItemStack blastProof(Material type) {
        ItemStack item = new ItemStack(type);
        item.addUnsafeEnchantment(Enchantment.BLAST_PROTECTION, 4);
        return item;
    }

    private static ItemStack healing() {
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.HEALING);
        item.setItemMeta(meta);
        return item;
    }

    private void prepareTerrain() {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) world.getChunkAt(x, z).setForceLoaded(true);
        for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) {
            world.getBlockAt(x, 99, z).setType(Material.STONE, false);
            for (int y = 100; y <= 130; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        world.setSpawnFlags(false, false);
        world.setTime(6000L);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
    }

    private void tick() {
        if (finished) return;
        try {
            ticks++;
            view.roundTicks = ticks;
            Player self = bot.getPlayer();
            Player dummyPlayer = dummy.player();
            check(self.isValid() && !self.isDead(), "bot died at tick " + ticks + " " + state());
            check(dummyPlayer.isValid() && !dummyPlayer.isDead(), "dummy body is gone");
            BotInput input = BotInput.neutral();
            if (name.equals("axe-shield") && ticks == 1) {
                input = new BotInput(false, false, false, false, false, false, false, 0.0f, 0.0f, -1, false, null,
                        BotInput.UseAction.START_OFF);
            }
            LegacyDrive.tick(platform, dummy, input);
            bot.tick(view);
            if (name.equals("axe-shield") && dummyPlayer.getCooldown(Material.SHIELD) > 0) shieldCooldown = true;
            if (ticks % 40 == 0) {
                Location at = self.getLocation();
                Location to = dummyPlayer.getLocation();
                plugin.getLogger().info(String.format("FEATURE_TRACE %s tick=%d bot=%.2f,%.2f,%.2f hp=%.1f held=%s target=%.2f,%.2f,%.2f",
                        name, ticks, at.getX(), at.getY(), at.getZ(), self.getHealth(),
                        self.getInventory().getItemInMainHand().getType(), to.getX(), to.getY(), to.getZ()));
            }
            if (name.equals("restock") && ticks == 15) {
                self.getInventory().setItem(2, null);
                self.setHealth(6.0);
            }
            if (name.equals("elytra-wall")) {
                Location at = self.getLocation();
                double speed = Double.isNaN(wallX) ? 1.0 : Math.hypot(at.getX() - wallX, at.getZ() - wallZ);
                wallX = at.getX();
                wallZ = at.getZ();
                if (self.isGliding()) glided = true;
                if (at.getX() > 19.5) passedWall = true;
                boolean stuck = self.isGliding() && at.getX() >= 16.0 && at.getX() <= 18.0 && speed < 0.1;
                wallStuck = stuck ? wallStuck + 1 : 0;
                if (wallStuck >= 20) throw new IllegalStateException("stuck against the wall for 20 ticks");
            }
            if (name.equals("elytra")) {
                if (self.isGliding()) glided = true;
                int count = count(self, Material.FIREWORK_ROCKET);
                if (rocketsStart < 0) rocketsStart = count;
                if (count < rocketsStart) rockets = true;
            }
            if (name.equals("restock") && boxPlaced && count(self, Material.GOLDEN_APPLE) > 0) appleAfter = true;
            if (name.equals("builduhc")) minDist = Math.min(minDist, self.getLocation().distance(dummyPlayer.getLocation()));
            String detail = passDetail();
            if (detail != null) {
                plugin.getLogger().info("FEATURE " + name + " PASS " + detail);
                finished = true;
                cleanup();
                success.run();
            } else if (ticks >= timeout) {
                throw new IllegalStateException("feature " + name + " timed out: " + state());
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private String passDetail() {
        return switch (name) {
            case "sword" -> hitTicks.size() >= 5 && !tooClose ? "hits=" + hitTicks.size() + " at=" + hitTicks : null;
            case "axe-shield" -> shieldCooldown ? "shield cooldown at tick " + ticks : null;
            case "pots" -> splashSelf ? "splash on self at tick " + ticks : null;
            case "gapple" -> ate ? "golden apple eaten at tick " + ticks : null;
            case "bow", "crossbow" -> arrowHit ? "arrow hit at tick " + ticks : null;
            case "pearl" -> pearlTeleport ? "pearl teleport at tick " + ticks : null;
            case "cobweb" -> webNearDummy ? "cobweb near dummy at tick " + ticks + " webs=" + webs : null;
            case "water-clutch" -> bucketEmpty && self().isOnGround() && Math.abs(self().getLocation().getY() - 100.0) < 0.5 && self().getHealth() > 0
                    ? "clutched hp=" + self().getHealth() : null;
            case "crystal" -> crystalBlast ? "crystal exploded at tick " + ticks + " spawned=" + crystalsSpawned : null;
            case "anchor" -> anchorPlaced && anchorBlast ? "anchor blast at tick " + ticks : null;
            case "cart" -> cartBlast ? "cart exploded at tick " + ticks : null;
            case "mace" -> maceHit ? "mace hit damage=" + maceDamage : null;
            case "elytra" -> glided && rockets ? "glided and used rocket at tick " + ticks : null;
            case "elytra-wall" -> glided && passedWall ? "glided past the barrier wall at tick " + ticks : null;
            case "restock" -> boxPlaced && appleAfter && boxBroken ? "box placed, apple taken, box broken at tick " + ticks : null;
            case "scaffold" -> placed >= 1 && !hitTicks.isEmpty() ? "placed=" + placed + " hits=" + hitTicks.size() : null;
            case "boxed" -> plankBroken && !hitTicks.isEmpty() ? "box opened, hits=" + hitTicks.size() + " at tick " + ticks : null;
            case "builduhc" -> minDist <= 3.5 && hitTicks.size() >= 5 && waterDumps + lavaDumps >= 1 && refills >= 1
                    ? String.format("hits=%d shots=%d water=%d lava=%d refills=%d blocks=%d minDist=%.2f", hitTicks.size(), shots, waterDumps, lavaDumps, refills, placed, minDist)
                    : null;
            default -> null;
        };
    }

    private Player self() {
        return bot.getPlayer();
    }

    private String state() {
        return String.format("tick=%d hits=%s tooClose=%s shield=%s splash=%s ate=%s arrow=%s pearl=%s webs=%d/%s bucket=%s crystals=%d/%s anchor=%s/%s cart=%s mace=%s glide=%s rockets=%s box=%s/%s/%s plank=%s placed=%d wall=%s/%d shots=%d water=%d lava=%d minDist=%.2f bot=%s hp=%.1f",
                ticks, hitTicks, tooClose, shieldCooldown, splashSelf, ate, arrowHit, pearlTeleport, webs, webNearDummy,
                bucketEmpty, crystalsSpawned, crystalBlast, anchorPlaced, anchorBlast, cartBlast, maceHit, glided, rockets,
                boxPlaced, appleAfter, boxBroken, plankBroken, placed, passedWall, wallStuck, shots, waterDumps, lavaDumps, minDist,
                bot.getPlayer().getLocation(), bot.getPlayer().getHealth());
    }

    private static int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) if (item != null && item.getType() == material) total += item.getAmount();
        return total;
    }

    private boolean isBot(Entity entity) {
        return bot != null && entity != null && entity.getUniqueId().equals(bot.getPlayer().getUniqueId());
    }

    private boolean isDummy(Entity entity) {
        return dummy != null && entity != null && entity.getUniqueId().equals(dummy.player().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (finished || !isDummy(event.getEntity())) return;
        Entity damager = event.getDamager();
        if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Entity shooter && isBot(shooter)) {
            arrowHit = true;
        } else if (isBot(damager)) {
            if (!hitTicks.isEmpty() && ticks - hitTicks.getLast() < 10) tooClose = true;
            hitTicks.add(ticks);
            if (self().getInventory().getItemInMainHand().getType() == Material.MACE && event.getFinalDamage() > 10.0) {
                maceHit = true;
                maceDamage = event.getFinalDamage();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(EntityDamageEvent event) {
        if (!finished && isDummy(event.getEntity()) && anchorPlaced && event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) anchorBlast = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (finished || !isBot(event.getPlayer())) return;
        bucketEmpty = true;
        if (event.getBucket() == Material.WATER_BUCKET) waterDumps++;
        if (event.getBucket() == Material.LAVA_BUCKET) lavaDumps++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRefill(PlayerBucketFillEvent event) {
        if (!finished && isBot(event.getPlayer())) refills++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!finished && isBot(event.getEntity())) shots++;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(EntitySpawnEvent event) {
        if (!finished && event.getEntity() instanceof EnderCrystal) crystalsSpawned++;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent event) {
        if (finished) return;
        if (event.getEntity() instanceof EnderCrystal) crystalBlast = true;
        if (event.getEntity() instanceof ExplosiveMinecart) cartBlast = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!finished && anchorPlaced) anchorBlast = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!finished && isBot(event.getPlayer()) && event.getBlock().getType().name().endsWith("SHULKER_BOX")) boxBroken = true;
        if (name.equals("boxed") && isBot(event.getPlayer()) && event.getBlock().getType() == Material.BIRCH_PLANKS
                && event.getBlock().getX() >= 19 && event.getBlock().getX() <= 21 && event.getBlock().getZ() >= -1 && event.getBlock().getZ() <= 1
                && event.getBlock().getY() >= 100 && event.getBlock().getY() <= 102) plankBroken = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (finished) return;
        Projectile potion = event.getEntity();
        if (potion.getShooter() instanceof Entity shooter && isBot(shooter)
                && event.getAffectedEntities().stream().anyMatch(this::isBot)) splashSelf = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!finished && isBot(event.getPlayer()) && event.getItem().getType() == Material.GOLDEN_APPLE) ate = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!finished && isBot(event.getPlayer()) && event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) pearlTeleport = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (finished || !isBot(event.getPlayer())) return;
        Material type = event.getBlockPlaced().getType();
        placed++;
        if (type == Material.RESPAWN_ANCHOR) anchorPlaced = true;
        if (type.name().endsWith("SHULKER_BOX")) boxPlaced = true;
        if (type != Material.COBWEB) return;
        webs++;
        Location at = event.getBlockPlaced().getLocation().add(0.5, 0.0, 0.5);
        Location to = dummy.player().getLocation();
        if (Math.hypot(at.getX() - to.getX(), at.getZ() - to.getZ()) <= 2.0) webNearDummy = true;
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
        if (dummy != null) { platform.remove(dummy); dummy = null; }
        if (dummyToken != null) { SmokeIdentity.release(dummyToken); dummyToken = null; }
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Projectile || entity instanceof org.bukkit.entity.Item
                        || entity instanceof EnderCrystal || entity instanceof org.bukkit.entity.Minecart) entity.remove();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class FeatureView implements BotFightView {
        private final Player opponent;
        private final BoundingBox bounds;
        private int roundTicks;

        private FeatureView(Player opponent, BoundingBox bounds) {
            this.opponent = opponent;
            this.bounds = bounds;
        }

        @Override public int getTeam() { return 1; }
        @Override public List<Player> getEnemies() { return List.of(opponent); }
        @Override public List<Player> getAllies() { return List.of(); }
        @Override public String getMatchId() { return "feature-smoke"; }
        @Override public int getRoundNumber() { return 1; }
        @Override public int getRoundTicks() { return roundTicks; }
        @Override public boolean isLive() { return true; }
        @Override public BoundingBox getArenaBounds() { return bounds; }
        @Override public int getDeadZoneY() { return 60; }
        @Override public String getLadderName() { return "feature-smoke"; }
    }
}
