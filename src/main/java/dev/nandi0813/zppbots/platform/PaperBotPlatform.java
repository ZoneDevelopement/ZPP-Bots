package dev.nandi0813.zppbots.platform;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Vec3;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class PaperBotPlatform implements BotPlatform {

    private final Plugin plugin;
    private final NativeBotBindings b;
    private final Map<BodyToken, Body> bodies = new HashMap<>();
    private final BotNames names;

    public PaperBotPlatform(Plugin plugin) throws ReflectiveOperationException {
        BotGuards.requireMainThread();
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        b = new NativeBotBindings(Bukkit.getServer().getClass().getClassLoader());
        String prefix = plugin.getConfig().getString("NAMES.PREFIX", BotNames.DEFAULT_PREFIX);
        if (!BotNames.validPrefix(prefix)) {
            plugin.getLogger().warning("NAMES.PREFIX must be 1-12 letters, digits or _; using bot_");
            prefix = BotNames.DEFAULT_PREFIX;
        }
        names = new BotNames(prefix);
    }

    /** Builds the reflection accessors off the main thread; see NativeBotBindings.primeAccessors. */
    public void primeNativeAccessors() {
        b.primeAccessors();
    }

    private static final class Body {
        private final BotActor actor;
        private final Object handle;
        private final Object connection;
        private final Object channel;
        private final Object level;
        private final String name;
        private final Set<UUID> viewers = new HashSet<>();
        private Object[] pendingMotion;
        private int lastMotionTick = Integer.MIN_VALUE;

        private Body(BotActor actor, Object handle, Object connection, Object channel, Object level, String name) {
            this.actor = actor;
            this.handle = handle;
            this.connection = connection;
            this.channel = channel;
            this.level = level;
            this.name = name;
        }
    }

    @Override
    public BotActor spawn(BodyToken identity, Location location, Map<EquipmentSlot, ItemStack> equipment) {
        BotGuards.requireMainThread();
        if (identity == null || !BotGuards.isBot(identity.playerId())) throw new IllegalArgumentException("Bot identity is not owned");
        if (bodies.containsKey(identity)) throw new IllegalArgumentException("Bot body already exists for identity");
        if (Bukkit.getEntity(identity.playerId()) != null || Bukkit.getPlayer(identity.playerId()) != null) {
            throw new IllegalArgumentException("Bot player UUID is already present");
        }
        Location target = validateLocation(location);
        EnumMap<EquipmentSlot, ItemStack> copiedEquipment = copyEquipment(equipment);
        Object server = b.call(b.server, Bukkit.getServer());
        Object level = b.call(b.level, target.getWorld());
        Object handle = null;
        Object connection = null;
        Object channel = null;
        Body body = null;
        String name = null;
        try {
            name = names.acquire(candidate -> Bukkit.getPlayerExact(candidate) != null);
            Object profile = b.construct(b.profileConstructor, identity.playerId(), name);
            handle = b.construct(b.playerConstructor, server, level, profile, b.call(b.clientInformation, null));
            b.set(b.realPlayer, handle, false);
            channel = b.construct(b.channelConstructor);
            Object capturedChannel = channel;
            Consumer<Object> completion = callback -> {
                if (callback != null) b.call(b.completeListener, callback, b.call(b.succeededFuture, capturedChannel));
            };
            Body[] capturedBody = new Body[1];
            Consumer<Object> packets = packet -> {
                Body current = capturedBody[0];
                if (current == null || bodies.get(identity) != current || packet == null
                        || !b.motionPacketClass.isInstance(packet)) return;
                BotGuards.requireMainThread();
                if ((int) b.call(b.motionId, packet) != (int) b.call(b.entityId, current.handle)) return;
                Object rawBuffer = b.call(b.allocateBuffer, null);
                try {
                    Object friendlyBuffer = b.construct(b.friendlyBufferConstructor, rawBuffer);
                    Object codec = b.get(b.motionCodec, null);
                    b.call(b.streamEncode, codec, friendlyBuffer, packet);
                    Object decoded = b.call(b.streamDecode, codec, friendlyBuffer);
                    Object movement = b.call(b.motionMovement, decoded);
                    current.pendingMotion = new Object[]{b.get(b.vecX, movement), b.get(b.vecY, movement),
                            b.get(b.vecZ, movement)};
                } finally {
                    b.call(b.releaseBuffer, rawBuffer);
                }
            };
            connection = b.construct(b.sinkConstructor, b.serverbound, completion, packets);
            b.set(b.channel, connection, channel);
            b.set(b.address, connection, new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            b.set(b.preparing, connection, false);
            Object cookie = b.call(b.initialCookie, null, b.call(b.gameProfile, handle), false);
            Object listener = b.construct(b.loadedConstructor, server, connection, handle, cookie);
            b.set(b.playerConnection, handle, listener);
            Player player = (Player) b.call(b.bukkitPlayer, handle);
            BotActor actor = new BotActor(identity, player);
            body = new Body(actor, handle, connection, channel, level, name);
            capturedBody[0] = body;
            bodies.put(identity, body);
            player.setPersistent(false);
            player.setVisibleByDefault(false);
            player.setCanPickupItems(false);
            setup(body, target, copiedEquipment);
            b.call(b.addPlayer, level, handle);
            if (Bukkit.getEntity(identity.playerId()) != player || !player.isValid()) {
                throw new IllegalStateException("Native bot was not registered as a valid world entity");
            }
            return actor;
        } catch (RuntimeException | Error failure) {
            if (body != null) {
                try { dispose(body); } catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            } else {
                cleanupPartial(handle, channel, failure);
                if (name != null) names.release(name);
            }
            throw failure;
        }
    }

    @Override
    public void reset(BotActor actor, Location location, Map<EquipmentSlot, ItemStack> equipment) {
        BotGuards.requireMainThread();
        Body body = requireBody(actor);
        Location target = validateLocation(location);
        EnumMap<EquipmentSlot, ItemStack> copiedEquipment = copyEquipment(equipment);
        Player player = body.actor.player();
        if (!player.isValid() || player.isDead()) throw new IllegalArgumentException("Bot player is not alive");
        if (target.getWorld() != player.getWorld()) throw new IllegalArgumentException("Bot reset must stay in the same world");
        setup(body, target, copiedEquipment);
    }

    @Override
    public void tick(BotActor actor, BotFrame frame) {
        BotGuards.requireMainThread();
        Body body = requireBody(actor);
        java.util.Objects.requireNonNull(frame, "frame");
        World world = checkTick(body);
        Player player = body.actor.player();
        b.ackTeleport(b.get(b.playerConnection, body.handle), player.getLocation().getYaw(), player.getLocation().getPitch());
        applyKeys(body, frame);
        step(body, frame, world);
    }

    /** Main-thread, alive, loaded-neighborhood and once-per-tick checks; returns the body's world. */
    private World checkTick(Body body) {
        Player player = body.actor.player();
        if (!player.isValid() || player.isDead()) throw new IllegalArgumentException("Bot player is not alive");
        Location before = player.getLocation();
        World world = before.getWorld();
        if (world == null || Bukkit.getWorld(world.getUID()) != world || !loadedNeighborhood(world, before)
                || before.getY() < world.getMinHeight() || before.getY() + 1.8 >= world.getMaxHeight()
                || !world.getWorldBorder().isInside(before)) {
            throw new IllegalArgumentException("Bot movement requires a loaded location inside the world border");
        }
        if (player.isFlying()) throw new IllegalArgumentException("Bot movement state is unsupported");
        int tick = Bukkit.getCurrentTick();
        if (body.lastMotionTick == tick) throw new IllegalStateException("Bot movement already ran this Bukkit tick");
        body.lastMotionTick = tick;
        return world;
    }

    private void applyKeys(Body body, BotFrame frame) {
        Object nativeInput = b.construct(b.inputConstructor,
                frame.forward() > 0.0f, frame.forward() < 0.0f, frame.strafe() > 0.0f, frame.strafe() < 0.0f,
                frame.jump(), frame.sneak(), frame.sprint());
        b.call(b.setLastClientInput, body.handle, nativeInput);
        b.call(b.shiftKeyDown, body.handle, frame.sneak());
        b.call(b.updatePlayerPose, body.handle);
    }

    /** Runs one physics step. */
    private void step(Body body, BotFrame frame, World world) {
        Player player = body.actor.player();
        Object activeUseEffects = null;
        if (player.isHandRaised()) {
            Object nativeUseItem = b.call(b.useItem, body.handle);
            activeUseEffects = b.call(b.componentOrDefault, nativeUseItem, b.useEffectsComponent, b.defaultUseEffects);
        }

        boolean crouching = (boolean) b.call(b.crouching, body.handle);
        boolean crawling = (boolean) b.call(b.visuallyCrawling, body.handle);
        boolean sprintEligible = frame.forward() > 0.0f && !frame.sneak() && !crouching
                && player.getFoodLevel() > 6 && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        boolean collisionStopsSprint = (boolean) b.get(b.horizontalCollision, body.handle)
                && !(boolean) b.get(b.minorHorizontalCollision, body.handle);
        boolean useCanSprint = activeUseEffects == null || (boolean) b.call(b.useCanSprint, activeUseEffects);
        b.call(b.sprinting, body.handle, sprintEligible && (frame.sprint() || player.isSprinting())
                && !collisionStopsSprint && useCanSprint);
        var movementAttribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        var sneakAttribute = player.getAttribute(Attribute.SNEAKING_SPEED);
        if (movementAttribute == null || sneakAttribute == null) {
            throw new IllegalStateException("Bot movement attributes are unavailable");
        }
        b.call(b.speed, body.handle, (float) movementAttribute.getValue());
        float slowdown = crouching || crawling ? (float) sneakAttribute.getValue() : 1.0f;
        if (activeUseEffects != null) slowdown *= (float) b.call(b.useSpeedMultiplier, activeUseEffects);
        b.set(b.xxa, body.handle, frame.strafe() * slowdown);
        b.set(b.yya, body.handle, 0.0f);
        b.set(b.zza, body.handle, frame.forward() * slowdown);
        b.call(b.jumping, body.handle, frame.jump());
        Object[] motion = body.pendingMotion;
        if (motion != null) {
            b.call(b.velocity, body.handle, (double) motion[0], (double) motion[1], (double) motion[2]);
            body.pendingMotion = null;
        }
        b.call(b.doTick, body.handle);
        Location after = player.getLocation();
        Vector velocity = player.getVelocity();
        try {
            validateLocation(after);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Native bot movement produced an invalid location", invalid);
        }
        if (after.getWorld() != world || !Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY())
                || !Double.isFinite(velocity.getZ())) {
            throw new IllegalStateException("Native bot movement produced invalid state");
        }
    }

    @Override
    public BotHands hands(BotActor actor) {
        BotGuards.requireMainThread();
        return new Hands(requireBody(actor));
    }

    private final class Hands implements BotHands {
        private final Body body;

        private Hands(Body body) {
            this.body = body;
        }

        private Player player() {
            BotGuards.requireMainThread();
            requireBody(body.actor);
            return body.actor.player();
        }

        private Object listener() {
            return b.get(b.playerConnection, body.handle);
        }

        private Object nativeHand(EquipmentSlot hand) {
            return switch (hand) {
                case HAND -> b.mainHand;
                case OFF_HAND -> b.offHand;
                default -> throw new IllegalArgumentException("Hand must be HAND or OFF_HAND");
            };
        }

        private ItemStack held(Player player, EquipmentSlot hand) {
            ItemStack item = player.getInventory().getItem(hand);
            return item == null ? new ItemStack(Material.AIR) : item.clone();
        }

        private boolean changed(ItemStack before, ItemStack after) {
            return before.getType() != after.getType() || before.getAmount() != after.getAmount();
        }

        private int cooldown(Player player, ItemStack item) {
            return item.getType().isAir() ? 0 : player.getCooldown(item.getType());
        }

        private Object nativePos(BlockPos pos) {
            return b.construct(b.blockPosConstructor, pos.x(), pos.y(), pos.z());
        }

        private Object nativeVec(Vec3 vec) {
            return b.construct(b.vec3Constructor, vec.x(), vec.y(), vec.z());
        }

        private String blockState(Player player, BlockPos pos) {
            org.bukkit.block.Block block = player.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
            return block.getType() + block.getBlockData().getAsString();
        }

        @Override
        public void look(float yaw, float pitch) {
            player();
            float wrapped = wrapYaw(yaw);
            b.call(b.yRot, body.handle, wrapped);
            b.call(b.xRot, body.handle, Math.max(-90.0f, Math.min(90.0f, pitch)));
            b.call(b.headRotation, body.handle, wrapped);
        }

        @Override
        public boolean useItem(EquipmentSlot hand) {
            Player player = player();
            Object nativeHand = nativeHand(hand);
            ItemStack before = held(player, hand);
            boolean raisedBefore = player.isHandRaised();
            int cooldownBefore = cooldown(player, before);
            b.handleUseItem(listener(), nativeHand, player.getYaw(), player.getPitch(), Bukkit.getCurrentTick() * 50L);
            ItemStack after = held(player, hand);
            return (player.isHandRaised() && !raisedBefore) || changed(before, after)
                    || cooldown(player, before) > cooldownBefore;
        }

        @Override
        public boolean useItemOn(EquipmentSlot hand, BlockPos pos, Direction face, Vec3 hit) {
            Player player = player();
            Object nativeHand = nativeHand(hand);
            BlockPos neighbor = pos.relative(face);
            ItemStack before = held(player, hand);
            String posBefore = blockState(player, pos);
            String neighborBefore = blockState(player, neighbor);
            boolean raisedBefore = player.isHandRaised();
            Object hitResult = b.construct(b.blockHitConstructor, nativeVec(hit), b.directions.get(face), nativePos(pos), false);
            b.handleUseItemOn(listener(), nativeHand, hitResult, Bukkit.getCurrentTick() * 50L);
            return changed(before, held(player, hand)) || !posBefore.equals(blockState(player, pos))
                    || !neighborBefore.equals(blockState(player, neighbor)) || player.isHandRaised() != raisedBefore;
        }

        @Override
        public void releaseUseItem() {
            if (player().isHandRaised()) b.handlePlayerAction(listener());
        }

        @Override
        public boolean attack(org.bukkit.entity.Entity target) {
            Player player = player();
            if (target == null || !target.isValid() || target.getWorld() != player.getWorld() || target == player) return false;
            Object targetHandle = b.call(b.entityHandle, target);
            b.handleAttack(listener(), targetHandle, (int) b.call(b.entityId, targetHandle), player.isSneaking());
            return true;
        }

        @Override
        public void swing(EquipmentSlot hand) {
            player();
            b.handleSwing(listener(), nativeHand(hand));
        }

        @Override
        public boolean interact(org.bukkit.entity.Entity target, EquipmentSlot hand, Vec3 relativeHit) {
            Player player = player();
            Object nativeHand = nativeHand(hand);
            if (target == null || !target.isValid() || target.getWorld() != player.getWorld() || target == player) return false;
            org.bukkit.entity.Entity vehicleBefore = player.getVehicle();
            ItemStack before = held(player, hand);
            Object targetHandle = b.call(b.entityHandle, target);
            b.handleInteract(listener(), targetHandle, (int) b.call(b.entityId, targetHandle), nativeHand,
                    nativeVec(relativeHit), player.isSneaking());
            return player.getVehicle() != vehicleBefore || changed(before, held(player, hand));
        }

        @Override
        public void drop(boolean wholeStack) {
            player();
            b.handlePlayerAction(listener(), wholeStack ? b.dropAllAction : b.dropAction, b.zeroBlockPos, b.downDirection);
        }

        @Override
        public void swapHands() {
            player();
            b.handlePlayerAction(listener(), b.swapAction, b.zeroBlockPos, b.downDirection);
        }

        @Override
        public void startDestroy(BlockPos pos, Direction face) {
            player();
            b.handlePlayerAction(listener(), b.startDestroyAction, nativePos(pos), b.directions.get(face));
        }

        @Override
        public void stopDestroy(BlockPos pos, Direction face) {
            player();
            b.handlePlayerAction(listener(), b.stopDestroyAction, nativePos(pos), b.directions.get(face));
        }

        @Override
        public void abortDestroy(BlockPos pos, Direction face) {
            player();
            b.handlePlayerAction(listener(), b.abortDestroyAction, nativePos(pos), b.directions.get(face));
        }

        @Override
        public void selectSlot(int slot) {
            Player player = player();
            if (slot < 0 || slot > 8) throw new IllegalArgumentException("Hotbar slot must be 0-8");
            if (slot != player.getInventory().getHeldItemSlot()) b.handleCarriedItem(listener(), slot);
        }

        @Override
        public void stab() {
            player();
            if (b.stabAction != null) b.handlePlayerAction(listener(), b.stabAction, b.zeroBlockPos, b.downDirection);
        }

        @Override
        public void dismount() {
            player().leaveVehicle();
        }

        @Override
        public boolean startGliding() {
            player();
            return (boolean) b.call(b.tryToStartFallFlying, body.handle);
        }
    }

    @Override
    public void pause(BotActor actor) {
        BotGuards.requireMainThread();
        Body body = requireBody(actor);
        b.set(b.xxa, body.handle, 0.0f);
        b.set(b.yya, body.handle, 0.0f);
        b.set(b.zza, body.handle, 0.0f);
        b.call(b.jumping, body.handle, false);
        b.call(b.sprinting, body.handle, false);
        b.call(b.setLastClientInput, body.handle, b.construct(b.inputConstructor,
                false, false, false, false, false, false, false));
        b.call(b.shiftKeyDown, body.handle, false);
        b.call(b.stopUsingItem, body.handle);
    }

    @Override
    public Snapshot snapshot(BotActor actor) {
        BotGuards.requireMainThread();
        requireBody(actor);
        Player player = actor.player();
        Location location = player.getLocation();
        Vector velocity = player.getVelocity();
        boolean usingItem = (boolean) b.call(b.isUsingItem, b.call(b.playerHandle, player));
        Object handle = b.call(b.playerHandle, player);
        EquipmentSlot activeHand = usingItem
                ? (b.call(b.usedItemHand, handle) == b.mainHand ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND) : null;
        return new Snapshot(actor.entityId(), location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), velocity.getX(), velocity.getY(), velocity.getZ(),
                player.isOnGround(), player.isSprinting(), player.getHealth(), player.getFoodLevel(),
                player.getSaturation(), player.getAttackCooldown(), player.getAbsorptionAmount(), player.getExhaustion(),
                player.getInventory().getHeldItemSlot(), usingItem, activeHand,
                (int) b.call(b.useTicks, handle), (int) b.call(b.useRemainingTicks, handle), player.isBlocking(),
                player.getActivePotionEffects().stream().toList(), snapshotEquipment(player));
    }

    @Override
    public void show(BotActor actor, Player viewer) {
        BotGuards.requireMainThread();
        Body body = requireBody(actor);
        validateViewer(actor, viewer);
        body.viewers.add(viewer.getUniqueId());
        viewer.showEntity(plugin, actor.player());
        Object viewerHandle = b.call(b.playerHandle, viewer);
        Object listener = b.get(b.playerConnection, viewerHandle);
        b.call(b.send, listener, b.call(b.updateListed, null, actor.entityId(), false));
    }

    @Override
    public void hide(BotActor actor, Player viewer) {
        BotGuards.requireMainThread();
        Body body = requireBody(actor);
        if (viewer != null) hideBody(body, viewer);
    }

    @Override
    public void refresh(BotActor actor) {
        BotGuards.requireMainThread();
        Body body = requireBody(actor);
        for (UUID id : Set.copyOf(body.viewers)) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null) {
                body.viewers.remove(id);
                continue;
            }
            if (!viewer.isOnline() || !viewer.getWorld().equals(actor.player().getWorld())) {
                hideBody(body, viewer);
                continue;
            }
            hideBody(body, viewer);
            show(actor, viewer);
        }
    }

    @Override
    public void remove(BotActor actor) {
        BotGuards.requireMainThread();
        if (actor == null) throw new IllegalArgumentException("Bot actor cannot be null");
        Body body = bodies.get(actor.identity());
        if (body == null) return;
        if (body.actor.player() != actor.player()) throw new IllegalArgumentException("Bot actor does not match the registered body");
        dispose(body);
    }

    private Body requireBody(BotActor actor) {
        if (actor == null) throw new IllegalArgumentException("Bot actor cannot be null");
        Body body = bodies.get(actor.identity());
        if (body == null || body.actor.player() != actor.player() || !BotGuards.isBot(actor.entityId())) {
            throw new IllegalArgumentException("Bot actor is not owned by this platform");
        }
        return body;
    }

    private static Location validateLocation(Location location) {
        if (location == null) throw new IllegalArgumentException("Bot location cannot be null");
        Location target = location.clone();
        target.checkFinite();
        World world = target.getWorld();
        if (world == null || Bukkit.getWorld(world.getUID()) != world
                || !world.isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)
                || target.getY() < world.getMinHeight() || target.getY() + 1.8 >= world.getMaxHeight()
                || !world.getWorldBorder().isInside(target)) {
            throw new IllegalArgumentException("Bot location must be inside a loaded world chunk and border");
        }
        return target;
    }

    private static boolean loadedNeighborhood(World world, Location location) {
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                if (!world.isChunkLoaded(x, z)) return false;
            }
        }
        return true;
    }

    private static float wrapYaw(float yaw) {
        return yaw - (float) Math.floor((yaw + 180.0f) / 360.0f) * 360.0f;
    }

    private static EnumMap<EquipmentSlot, ItemStack> copyEquipment(Map<EquipmentSlot, ItemStack> equipment) {
        if (equipment == null) throw new IllegalArgumentException("Bot equipment cannot be null");
        EnumMap<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ItemStack item = entry.getValue();
            if (slot == null || item == null || switch (slot) {
                case HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET -> false;
                default -> true;
            }) throw new IllegalArgumentException("Unsupported bot equipment entry");
            copy.put(slot, item.clone());
        }
        return copy;
    }

    private void setup(Body body, Location location, Map<EquipmentSlot, ItemStack> equipment) {
        pause(body.actor);
        body.pendingMotion = null;
        body.lastMotionTick = Integer.MIN_VALUE;
        b.call(b.snapTo, body.handle, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        b.call(b.headRotation, body.handle, location.getYaw());
        b.call(b.velocity, body.handle, 0.0d, 0.0d, 0.0d);
        Player player = body.actor.player();
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        for (EquipmentSlot slot : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            b.call(b.itemSlot, body.handle, b.equipmentSlots.get(slot), b.emptyItem);
        }
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
            b.call(b.itemSlot, body.handle, b.equipmentSlots.get(entry.getKey()), b.call(b.itemCopy, null, entry.getValue().clone()));
        }
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        b.call(b.health, body.handle, (float) ((Number) b.call(b.maxHealth, body.handle)).doubleValue());
        Object food = b.call(b.food, body.handle);
        b.call(b.foodLevel, food, 20);
        b.call(b.saturation, food, 5.0f);
        player.setAbsorptionAmount(0.0d);
        player.setExhaustion(0.0f);
        player.setNoDamageTicks(0);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setExp(0);
        player.setLevel(0);
        b.call(b.resetPosition, b.get(b.playerConnection, body.handle));
    }

    private static Map<EquipmentSlot, ItemStack> snapshotEquipment(Player player) {
        EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        var inventory = player.getInventory();
        equipment.put(EquipmentSlot.HAND, snapshotItem(inventory.getItemInMainHand()));
        equipment.put(EquipmentSlot.OFF_HAND, snapshotItem(inventory.getItemInOffHand()));
        equipment.put(EquipmentSlot.HEAD, snapshotItem(inventory.getHelmet()));
        equipment.put(EquipmentSlot.CHEST, snapshotItem(inventory.getChestplate()));
        equipment.put(EquipmentSlot.LEGS, snapshotItem(inventory.getLeggings()));
        equipment.put(EquipmentSlot.FEET, snapshotItem(inventory.getBoots()));
        return equipment;
    }

    private static ItemStack snapshotItem(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private void validateViewer(BotActor actor, Player viewer) {
        if (viewer == null || !viewer.isOnline() || BotGuards.isBot(viewer.getUniqueId())
                || viewer.getUniqueId().equals(actor.entityId()) || !viewer.getWorld().equals(actor.player().getWorld())) {
            throw new IllegalArgumentException("Viewer must be an online human in the bot world");
        }
    }

    private void hideBody(Body body, Player viewer) {
        viewer.hideEntity(plugin, body.actor.player());
        if (viewer.isOnline()) {
            Object viewerHandle = b.call(b.playerHandle, viewer);
            Object listener = b.get(b.playerConnection, viewerHandle);
            if (listener != null) b.call(b.send, listener, b.construct(b.removeInfoConstructor, List.of(body.actor.entityId())));
        }
        body.viewers.remove(viewer.getUniqueId());
    }

    private void dispose(Body body) {
        bodies.remove(body.actor.identity(), body);
        names.release(body.name);
        body.pendingMotion = null;
        body.lastMotionTick = Integer.MIN_VALUE;
        Throwable failure = null;
        for (UUID viewerId : Set.copyOf(body.viewers)) {
            try {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) hideBody(body, viewer);
                else body.viewers.remove(viewerId);
            } catch (RuntimeException | Error exception) { failure = collect(failure, exception); }
        }
        failure = attempt(failure, () -> b.call(b.removePlayer, body.level, body.handle, b.discarded));
        failure = attempt(failure, () -> b.call(b.stopListening, b.call(b.advancements, body.handle)));
        failure = attempt(failure, () -> b.call(b.leave, b.call(b.textFilter, body.handle)));
        failure = attempt(failure, () -> b.call(b.disconnect, body.handle));
        failure = attempt(failure, () -> b.call(b.finishChannel, body.channel));
        body.viewers.clear();
        rethrow(failure);
    }

    private void cleanupPartial(Object handle, Object channel, Throwable failure) {
        if (handle != null) {
            failure = attempt(failure, () -> b.call(b.stopListening, b.call(b.advancements, handle)));
            failure = attempt(failure, () -> b.call(b.leave, b.call(b.textFilter, handle)));
            failure = attempt(failure, () -> b.call(b.disconnect, handle));
        }
        if (channel != null) failure = attempt(failure, () -> b.call(b.finishChannel, channel));
        // Cleanup exceptions are suppressed onto the original spawn failure.
    }

    private static Throwable attempt(Throwable failure, Runnable action) {
        try { action.run(); } catch (RuntimeException | Error exception) { return collect(failure, exception); }
        return failure;
    }

    private static Throwable collect(Throwable failure, Throwable next) {
        if (failure == null) return next;
        if (failure != next) failure.addSuppressed(next);
        return failure;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        if (failure instanceof Error error) throw error;
    }
}
