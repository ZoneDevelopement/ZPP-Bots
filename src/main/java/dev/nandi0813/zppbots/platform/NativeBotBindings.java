package dev.nandi0813.zppbots.platform;

import org.bukkit.inventory.EquipmentSlot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/** Cached reflective access to the exact Paper native API used by bot bodies. */
final class NativeBotBindings {

    final Constructor<?> playerConstructor;
    final Constructor<?> profileConstructor;
    final Constructor<?> channelConstructor;
    final Constructor<?> removeInfoConstructor;
    final Constructor<?> inputConstructor;
    final Constructor<?> friendlyBufferConstructor;
    final Constructor<?> carriedItemConstructor;
    final Constructor<?> useItemConstructor;
    final Constructor<?> useItemStampedConstructor;
    final Constructor<?> releaseUseConstructor;
    final Constructor<?> attackConstructor;
    final Constructor<?> swingConstructor;
    final Constructor<?> vec3Constructor;
    final Constructor<?> blockPosConstructor;
    final Constructor<?> blockHitConstructor;
    final Constructor<?> useOnConstructor;
    // Null on 1.21.x, which uses createInteractionPacket instead.
    final Constructor<?> interactConstructor;

    final Method server;
    final Method level;
    final Method playerHandle;
    final Method bukkitPlayer;
    final Method gameProfile;
    final Method clientInformation;
    final Method initialCookie;
    final Method addPlayer;
    final Method removePlayer;
    final Method snapTo;
    final Method headRotation;
    final Method velocity;
    final Method jumping;
    final Method sprinting;
    final Method stopUsingItem;
    final Method itemSlot;
    final Method itemCopy;
    final Method health;
    final Method maxHealth;
    final Method food;
    final Method foodLevel;
    final Method saturation;
    final Method resetPosition;
    final Method advancements;
    final Method stopListening;
    final Method textFilter;
    final Method leave;
    final Method disconnect;
    final Method send;
    final Method updateListed;
    final Method finishChannel;
    final Method succeededFuture;
    final Method completeListener;
    final Method setLastClientInput;
    final Method doTick;
    final Method shiftKeyDown;
    final Method yRot;
    final Method xRot;
    final Method speed;
    final Method crouching;
    final Method visuallyCrawling;
    final Method updatePlayerPose;
    final Method motionId;
    final Method motionMovement;
    final Method entityId;
    final Method streamEncode;
    final Method streamDecode;
    final Method allocateBuffer;
    final Method releaseBuffer;
    final Method handleCarriedItem;
    final Method handleUseItem;
    final Method handlePlayerAction;
    final Method handleAttack;
    final Method attackFactory;
    final Method handleSwing;
    final Method entityHandle;
    final Method pickable;
    final Method interactionRange;
    final Method useItem;
    final Method componentOrDefault;
    final Method useCanSprint;
    final Method useSpeedMultiplier;
    final Method isUsingItem;
    final Method usedItemHand;
    final Method useTicks;
    final Method useRemainingTicks;
    final Method handleUseItemOn;
    final Method handleAcceptTeleport;
    final Constructor<?> acceptTeleportConstructor;
    final boolean acceptTeleportPositioned;
    final Method handleInteract;
    final Method interactFactory;
    // 1.21.x sends interact-at and interact as two packets, like the client.
    final Method interactPlainFactory;
    final Method tryToStartFallFlying;

    final Field playerConnection;
    final Field realPlayer;
    final Field channel;
    final Field address;
    final Field preparing;
    final Field xxa;
    final Field yya;
    final Field zza;
    final Field horizontalCollision;
    final Field minorHorizontalCollision;
    final Field motionCodec;
    final Field vecX;
    final Field vecY;
    final Field vecZ;
    // 1.21.x only: Paper's spam limiter reads it; absent on the 26.x record (null).
    final Field useItemTimestamp;
    // 1.21.x only: the use-on packet's timestamp is a public field, not a constructor argument.
    final Field useOnTimestamp;
    // private on ServerGamePacketListenerImpl; set while a server teleport waits for the client's ack
    final Field awaitingPosition;
    final Field awaitingTeleport;

    final Object serverbound;
    final Object discarded;
    final Object emptyItem;
    final Object mainHand;
    final Object offHand;
    final Object releaseUseAction;
    final Object zeroBlockPos;
    final Object downDirection;
    final Object startDestroyAction;
    final Object abortDestroyAction;
    final Object stopDestroyAction;
    final Object dropAllAction;
    final Object dropAction;
    final Object swapAction;
    // Null when the server has no STAB action.
    final Object stabAction;
    final EnumMap<dev.nandi0813.zppbots.mc.Direction, Object> directions;
    final Object useEffectsComponent;
    final Object defaultUseEffects;
    final boolean punchPacket;
    final EnumMap<EquipmentSlot, Object> equipmentSlots;
    final Class<?> packetClass;
    final Class<?> motionPacketClass;
    final Class<?> listenerCallbackClass;
    final Class<?> connectionClass;
    final Constructor<?> sinkConstructor;
    final Constructor<?> loadedConstructor;

    NativeBotBindings(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> minecraftServerClass = load("net.minecraft.server.MinecraftServer", loader);
        Class<?> serverLevelClass = load("net.minecraft.server.level.ServerLevel", loader);
        Class<?> serverPlayerClass = load("net.minecraft.server.level.ServerPlayer", loader);
        Class<?> clientInformationClass = load("net.minecraft.server.level.ClientInformation", loader);
        Class<?> playerClass = load("net.minecraft.world.entity.player.Player", loader);
        Class<?> inputClass = load("net.minecraft.world.entity.player.Input", loader);
        Class<?> livingEntityClass = load("net.minecraft.world.entity.LivingEntity", loader);
        Class<?> entityRemovalReasonClass = load("net.minecraft.world.entity.Entity$RemovalReason", loader);
        Class<?> gameProfileClass = load("com.mojang.authlib.GameProfile", loader);
        Class<?> connectionClassLocal = load("net.minecraft.network.Connection", loader);
        Class<?> packetFlowClass = load("net.minecraft.network.protocol.PacketFlow", loader);
        Class<?> packetClassLocal = load("net.minecraft.network.protocol.Packet", loader);
        Class<?> listenerImplClass = load("net.minecraft.server.network.ServerGamePacketListenerImpl", loader);
        Class<?> commonCookieClass = load("net.minecraft.server.network.CommonListenerCookie", loader);
        Class<?> playerAdvancementsClass = load("net.minecraft.server.PlayerAdvancements", loader);
        Class<?> foodDataClass = load("net.minecraft.world.food.FoodData", loader);
        Class<?> equipmentSlotClass = load("net.minecraft.world.entity.EquipmentSlot", loader);
        Class<?> nmsItemStackClass = load("net.minecraft.world.item.ItemStack", loader);
        Class<?> craftServerClass = load("org.bukkit.craftbukkit.CraftServer", loader);
        Class<?> craftWorldClass = load("org.bukkit.craftbukkit.CraftWorld", loader);
        Class<?> craftPlayerClass = load("org.bukkit.craftbukkit.entity.CraftPlayer", loader);
        Class<?> craftItemStackClass = load("org.bukkit.craftbukkit.inventory.CraftItemStack", loader);
        Class<?> embeddedChannelClass = load("io.netty.channel.embedded.EmbeddedChannel", loader);
        Class<?> channelClass = load("io.netty.channel.Channel", loader);
        Class<?> futureClass = load("io.netty.util.concurrent.Future", loader);
        Class<?> futureListenerClass = load("io.netty.util.concurrent.GenericFutureListener", loader);
        Class<?> textFilterClass = load("net.minecraft.server.network.TextFilter", loader);
        Class<?> vec3Class = load("net.minecraft.world.phys.Vec3", loader);
        Class<?> motionPacketClass = load("net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket", loader);
        Class<?> streamCodecClass = load("net.minecraft.network.codec.StreamCodec", loader);
        Class<?> streamEncoderClass = load("net.minecraft.network.codec.StreamEncoder", loader);
        Class<?> streamDecoderClass = load("net.minecraft.network.codec.StreamDecoder", loader);
        Class<?> byteBufClass = load("io.netty.buffer.ByteBuf", loader);
        Class<?> friendlyBufferClass = load("net.minecraft.network.FriendlyByteBuf", loader);
        Class<?> unpooledClass = load("io.netty.buffer.Unpooled", loader);

        profileConstructor = constructor(gameProfileClass, UUID.class, String.class);
        channelConstructor = constructor(embeddedChannelClass);
        removeInfoConstructor = constructor(load("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket",
                loader), List.class);
        inputConstructor = constructor(inputClass, boolean.class, boolean.class, boolean.class, boolean.class,
                boolean.class, boolean.class, boolean.class);
        friendlyBufferConstructor = constructor(friendlyBufferClass, byteBufClass);

        Class<?> carriedPacketClass = load("net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket", loader);
        Class<?> usePacketClass = load("net.minecraft.network.protocol.game.ServerboundUseItemPacket", loader);
        Class<?> playerActionPacketClass = load("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket", loader);
        Class<?> interactionHandClass = load("net.minecraft.world.InteractionHand", loader);
        Class<?> blockPosClass = load("net.minecraft.core.BlockPos", loader);
        Class<?> directionClass = load("net.minecraft.core.Direction", loader);
        Class<?> entityClass = load("net.minecraft.world.entity.Entity", loader);
        Class<?> blockHitClass = load("net.minecraft.world.phys.BlockHitResult", loader);
        Class<?> useOnPacketClass = load("net.minecraft.network.protocol.game.ServerboundUseItemOnPacket", loader);
        Class<?> interactPacketClass = load("net.minecraft.network.protocol.game.ServerboundInteractPacket", loader);
        vec3Constructor = constructor(vec3Class, double.class, double.class, double.class);
        blockPosConstructor = constructor(blockPosClass, int.class, int.class, int.class);
        blockHitConstructor = constructor(blockHitClass, vec3Class, directionClass, blockPosClass, boolean.class);
        Constructor<?> timestampedUseOn;
        try {
            timestampedUseOn = constructor(useOnPacketClass, interactionHandClass, blockHitClass, int.class, long.class);
        } catch (NoSuchMethodException absent) {
            timestampedUseOn = null;
        }
        if (timestampedUseOn != null) {
            useOnConstructor = timestampedUseOn;
            useOnTimestamp = null;
        } else {
            useOnConstructor = constructor(useOnPacketClass, interactionHandClass, blockHitClass, int.class);
            useOnTimestamp = field(useOnPacketClass, "timestamp", long.class);
        }
        Constructor<?> recordInteract;
        try {
            recordInteract = constructor(interactPacketClass, int.class, interactionHandClass, vec3Class, boolean.class);
        } catch (NoSuchMethodException absent) {
            recordInteract = null;
        }
        interactConstructor = recordInteract;
        interactFactory = recordInteract != null ? null : method(interactPacketClass, "createInteractionPacket",
                interactPacketClass, true, entityClass, boolean.class, interactionHandClass, vec3Class);
        interactPlainFactory = recordInteract != null ? null : method(interactPacketClass, "createInteractionPacket",
                interactPacketClass, true, entityClass, boolean.class, interactionHandClass);
        handleUseItemOn = method(listenerImplClass, "handleUseItemOn", void.class, useOnPacketClass);
        Class<?> acceptTeleportClass = load("net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket", loader);
        Constructor<?> positionedAck;
        try {
            positionedAck = constructor(acceptTeleportClass, int.class, double.class, double.class, double.class, float.class, float.class);
        } catch (NoSuchMethodException absent) {
            positionedAck = null;
        }
        // 26.3 acks carry the client position and rotation; older versions take only the id
        acceptTeleportConstructor = positionedAck != null ? positionedAck : constructor(acceptTeleportClass, int.class);
        acceptTeleportPositioned = positionedAck != null;
        handleAcceptTeleport = method(listenerImplClass, "handleAcceptTeleportPacket", void.class, acceptTeleportClass);
        awaitingPosition = privateField(listenerImplClass, "awaitingPositionFromClient", vec3Class);
        awaitingTeleport = privateField(listenerImplClass, "awaitingTeleport", int.class);
        handleInteract = method(listenerImplClass, "handleInteract", void.class, interactPacketClass);
        tryToStartFallFlying = method(playerClass, "tryToStartFallFlying", boolean.class);
        Class<?> playerActionEnumClass = load("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action", loader);
        startDestroyAction = enumValue(playerActionEnumClass, "START_DESTROY_BLOCK");
        abortDestroyAction = enumValue(playerActionEnumClass, "ABORT_DESTROY_BLOCK");
        stopDestroyAction = enumValue(playerActionEnumClass, "STOP_DESTROY_BLOCK");
        dropAllAction = enumValue(playerActionEnumClass, "DROP_ALL_ITEMS");
        dropAction = enumValue(playerActionEnumClass, "DROP_ITEM");
        swapAction = enumValue(playerActionEnumClass, "SWAP_ITEM_WITH_OFFHAND");
        Object stab;
        try {
            stab = enumValue(playerActionEnumClass, "STAB");
        } catch (IllegalStateException absent) {
            stab = null;
        }
        stabAction = stab;
        directions = new EnumMap<>(dev.nandi0813.zppbots.mc.Direction.class);
        for (dev.nandi0813.zppbots.mc.Direction direction : dev.nandi0813.zppbots.mc.Direction.values()) {
            directions.put(direction, enumValue(directionClass, direction.name()));
        }
        carriedItemConstructor = constructor(carriedPacketClass, int.class);
        useItemConstructor = constructor(usePacketClass, interactionHandClass, int.class, float.class, float.class);
        Constructor<?> stamped;
        try {
            stamped = constructor(usePacketClass, interactionHandClass, int.class, float.class, float.class, long.class);
        } catch (NoSuchMethodException absent) {
            stamped = null;
        }
        useItemStampedConstructor = stamped;
        Field timestampField;
        try {
            timestampField = field(usePacketClass, "timestamp", long.class);
        } catch (NoSuchFieldException absent) {
            timestampField = null;
        }
        useItemTimestamp = timestampField;
        releaseUseConstructor = constructor(playerActionPacketClass, playerActionEnumClass, blockPosClass, directionClass,
                int.class);
        Class<?> attackPacketClass;
        boolean modernAttack;
        try {
            attackPacketClass = load("net.minecraft.network.protocol.game.ServerboundAttackPacket", loader);
            modernAttack = true;
        } catch (ClassNotFoundException missingAttackPacket) {
            attackPacketClass = load("net.minecraft.network.protocol.game.ServerboundInteractPacket", loader);
            modernAttack = false;
        }
        attackConstructor = modernAttack ? constructor(attackPacketClass, int.class) : null;
        attackFactory = modernAttack ? null : method(attackPacketClass, "createAttackPacket", attackPacketClass,
                true, entityClass, boolean.class);
        Class<?> swingPacketClass;
        boolean hasPunch;
        try {
            swingPacketClass = load("net.minecraft.network.protocol.game.ServerboundPunchPacket", loader);
            hasPunch = true;
        } catch (ClassNotFoundException missingPunchPacket) {
            swingPacketClass = load("net.minecraft.network.protocol.game.ServerboundSwingPacket", loader);
            hasPunch = false;
        }
        swingConstructor = hasPunch ? constructor(swingPacketClass) : constructor(swingPacketClass, interactionHandClass);
        punchPacket = hasPunch;

        server = method(craftServerClass, "getServer", minecraftServerClass, true);
        level = method(craftWorldClass, "getHandle", serverLevelClass);
        playerHandle = method(craftPlayerClass, "getHandle", serverPlayerClass);
        bukkitPlayer = method(serverPlayerClass, "getBukkitEntity", org.bukkit.entity.Player.class, true);
        gameProfile = method(playerClass, "getGameProfile", gameProfileClass);
        clientInformation = method(clientInformationClass, "createDefault", clientInformationClass);
        initialCookie = method(commonCookieClass, "createInitial", commonCookieClass, gameProfileClass,
                boolean.class);
        addPlayer = method(serverLevelClass, "addNewPlayer", void.class, serverPlayerClass);
        Class<?> nativeEntityClass = entityClass;
        removePlayer = method(serverLevelClass, "removePlayerImmediately", void.class, serverPlayerClass,
                entityRemovalReasonClass);
        snapTo = method(nativeEntityClass, "snapTo", void.class, double.class, double.class, double.class,
                float.class, float.class);
        headRotation = method(nativeEntityClass, "setYHeadRot", void.class, float.class);
        velocity = method(nativeEntityClass, "setDeltaMovement", void.class, double.class, double.class,
                double.class);
        jumping = method(livingEntityClass, "setJumping", void.class, boolean.class);
        sprinting = method(nativeEntityClass, "setSprinting", void.class, boolean.class);
        stopUsingItem = method(livingEntityClass, "stopUsingItem", void.class);
        itemSlot = method(livingEntityClass, "setItemSlot", void.class, equipmentSlotClass, nmsItemStackClass);
        itemCopy = method(craftItemStackClass, "asNMSCopy", nmsItemStackClass, org.bukkit.inventory.ItemStack.class);
        health = method(livingEntityClass, "setHealth", void.class, float.class);
        maxHealth = method(livingEntityClass, "getMaxHealth", float.class);
        food = method(serverPlayerClass, "getFoodData", foodDataClass);
        foodLevel = method(foodDataClass, "setFoodLevel", void.class, int.class);
        saturation = method(foodDataClass, "setSaturation", void.class, float.class);
        resetPosition = method(listenerImplClass, "resetPosition", void.class);
        advancements = method(serverPlayerClass, "getAdvancements", playerAdvancementsClass);
        Method advancementCleanup;
        try {
            advancementCleanup = method(playerAdvancementsClass, "stopListening", void.class);
        } catch (NoSuchMethodException ignored) {
            // 26.2+ renamed advancement trigger cleanup.
            advancementCleanup = method(playerAdvancementsClass, "clearTriggers", void.class);
        }
        stopListening = advancementCleanup;
        textFilter = method(serverPlayerClass, "getTextFilter", textFilterClass);
        leave = method(textFilterClass, "leave", void.class);
        disconnect = method(serverPlayerClass, "disconnect", void.class);
        send = method(listenerImplClass, "send", void.class, packetClassLocal);
        Class<?> playerInfoUpdatePacketClass = load(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket", loader);
        updateListed = method(playerInfoUpdatePacketClass, "updateListed", playerInfoUpdatePacketClass,
                UUID.class, boolean.class);
        finishChannel = method(embeddedChannelClass, "finishAndReleaseAll", boolean.class);
        succeededFuture = method(channelClass, "newSucceededFuture", load("io.netty.channel.ChannelFuture", loader));
        completeListener = method(futureListenerClass, "operationComplete", void.class, futureClass);

        setLastClientInput = method(serverPlayerClass, "setLastClientInput", void.class, inputClass);
        doTick = method(serverPlayerClass, "doTick", void.class);
        shiftKeyDown = method(nativeEntityClass, "setShiftKeyDown", void.class, boolean.class);
        yRot = method(nativeEntityClass, "setYRot", void.class, float.class);
        xRot = method(nativeEntityClass, "setXRot", void.class, float.class);
        speed = method(livingEntityClass, "setSpeed", void.class, float.class);
        crouching = method(nativeEntityClass, "isCrouching", boolean.class);
        visuallyCrawling = method(nativeEntityClass, "isVisuallyCrawling", boolean.class);
        updatePlayerPose = playerClass.getDeclaredMethod("updatePlayerPose");
        int updatePlayerPoseModifiers = updatePlayerPose.getModifiers();
        if (updatePlayerPose.getReturnType() != void.class || Modifier.isStatic(updatePlayerPoseModifiers)
                || (!Modifier.isProtected(updatePlayerPoseModifiers) && !Modifier.isPublic(updatePlayerPoseModifiers))) {
            throw new NoSuchMethodException("Unexpected Player.updatePlayerPose signature: " + updatePlayerPose);
        }
        updatePlayerPose.setAccessible(true);
        motionId = eitherMethod(motionPacketClass, "getId", "id", int.class);
        motionMovement = eitherMethod(motionPacketClass, "getMovement", "movement", vec3Class);
        entityId = method(nativeEntityClass, "getId", int.class);
        streamEncode = method(streamEncoderClass, "encode", void.class, Object.class, Object.class);
        streamDecode = method(streamDecoderClass, "decode", Object.class, Object.class);
        allocateBuffer = method(unpooledClass, "buffer", byteBufClass);
        releaseBuffer = method(byteBufClass, "release", boolean.class);
        handleCarriedItem = method(listenerImplClass, "handleSetCarriedItem", void.class, carriedPacketClass);
        handleUseItem = method(listenerImplClass, "handleUseItem", void.class, usePacketClass);
        handlePlayerAction = method(listenerImplClass, "handlePlayerAction", void.class, playerActionPacketClass);
        handleAttack = method(listenerImplClass, modernAttack ? "handleAttack" : "handleInteract", void.class,
                attackPacketClass);
        Class<?> swingHandlerPacket = swingPacketClass;
        handleSwing = method(listenerImplClass, hasPunch ? "handlePunch" : "handleAnimate", void.class,
                swingHandlerPacket);
        Class<?> craftEntityClass = load("org.bukkit.craftbukkit.entity.CraftEntity", loader);
        entityHandle = method(craftEntityClass, "getHandle", entityClass);
        pickable = method(entityClass, "isPickable", boolean.class);
        interactionRange = method(playerClass, "entityInteractionRange", double.class);
        useItem = method(livingEntityClass, "getUseItem", nmsItemStackClass);
        Class<?> componentHolderClass = load("net.minecraft.core.component.DataComponentHolder", loader);
        Class<?> componentTypeClass = load("net.minecraft.core.component.DataComponentType", loader);
        componentOrDefault = method(componentHolderClass, "getOrDefault", Object.class, componentTypeClass, Object.class);
        Class<?> useEffectsClass = load("net.minecraft.world.item.component.UseEffects", loader);
        useCanSprint = method(useEffectsClass, "canSprint", boolean.class);
        useSpeedMultiplier = method(useEffectsClass, "speedMultiplier", float.class);
        isUsingItem = method(livingEntityClass, "isUsingItem", boolean.class);
        usedItemHand = method(livingEntityClass, "getUsedItemHand", interactionHandClass);
        useTicks = method(livingEntityClass, "getTicksUsingItem", int.class);
        useRemainingTicks = method(livingEntityClass, "getUseItemRemainingTicks", int.class);

        playerConnection = field(serverPlayerClass, "connection", listenerImplClass);
        realPlayer = field(serverPlayerClass, "isRealPlayer", boolean.class);
        channel = field(connectionClassLocal, "channel", load("io.netty.channel.Channel", loader));
        address = field(connectionClassLocal, "address", java.net.SocketAddress.class);
        preparing = field(connectionClassLocal, "preparing", boolean.class);
        xxa = field(livingEntityClass, "xxa", float.class);
        yya = field(livingEntityClass, "yya", float.class);
        zza = field(livingEntityClass, "zza", float.class);
        horizontalCollision = field(nativeEntityClass, "horizontalCollision", boolean.class);
        minorHorizontalCollision = field(nativeEntityClass, "minorHorizontalCollision", boolean.class);
        motionCodec = field(motionPacketClass, "STREAM_CODEC", streamCodecClass);
        vecX = field(vec3Class, "x", double.class);
        vecY = field(vec3Class, "y", double.class);
        vecZ = field(vec3Class, "z", double.class);

        serverbound = enumValue(packetFlowClass, "SERVERBOUND");
        discarded = enumValue(entityRemovalReasonClass, "DISCARDED");
        emptyItem = field(nmsItemStackClass, "EMPTY", nmsItemStackClass).get(null);
        mainHand = enumValue(interactionHandClass, "MAIN_HAND");
        offHand = enumValue(interactionHandClass, "OFF_HAND");
        releaseUseAction = enumValue(playerActionEnumClass, "RELEASE_USE_ITEM");
        zeroBlockPos = field(blockPosClass, "ZERO", blockPosClass).get(null);
        downDirection = enumValue(directionClass, "DOWN");
        Class<?> dataComponentsClass = load("net.minecraft.core.component.DataComponents", loader);
        Class<?> useEffectsTypeClass = load("net.minecraft.world.item.component.UseEffects", loader);
        useEffectsComponent = field(dataComponentsClass, "USE_EFFECTS", componentTypeClass).get(null);
        defaultUseEffects = field(useEffectsTypeClass, "DEFAULT", useEffectsTypeClass).get(null);
        equipmentSlots = new EnumMap<>(EquipmentSlot.class);
        equipmentSlots.put(EquipmentSlot.HAND, enumValue(equipmentSlotClass, "MAINHAND"));
        equipmentSlots.put(EquipmentSlot.OFF_HAND, enumValue(equipmentSlotClass, "OFFHAND"));
        equipmentSlots.put(EquipmentSlot.HEAD, enumValue(equipmentSlotClass, "HEAD"));
        equipmentSlots.put(EquipmentSlot.CHEST, enumValue(equipmentSlotClass, "CHEST"));
        equipmentSlots.put(EquipmentSlot.LEGS, enumValue(equipmentSlotClass, "LEGS"));
        equipmentSlots.put(EquipmentSlot.FEET, enumValue(equipmentSlotClass, "FEET"));

        packetClass = packetClassLocal;
        this.motionPacketClass = motionPacketClass;
        listenerCallbackClass = load("io.netty.channel.ChannelFutureListener", loader);
        connectionClass = connectionClassLocal;
        NativeBotAdapters.Types adapters = NativeBotAdapters.create(connectionClass, packetFlowClass, packetClass,
                listenerCallbackClass, listenerImplClass, minecraftServerClass, serverPlayerClass, commonCookieClass,
                serverPlayerClass, serverLevelClass, gameProfileClass, clientInformationClass);
        playerConstructor = constructor(adapters.player(), minecraftServerClass, serverLevelClass, gameProfileClass,
                clientInformationClass);
        sinkConstructor = adapters.connection().getConstructor(packetFlowClass, java.util.function.Consumer.class,
                java.util.function.Consumer.class);
        loadedConstructor = adapters.listener().getConstructor(minecraftServerClass, connectionClass, serverPlayerClass,
                commonCookieClass);
    }

    void handleCarriedItem(Object listener, int slot) { call(handleCarriedItem, listener, construct(carriedItemConstructor, slot)); }

    void handleUseItem(Object listener, Object hand, float yaw, float pitch, long timestamp) {
        // 26.x keeps the timestamp private and stamps wall time itself, which trips the spam limiter under tick sprinting
        Object packet = useItemTimestamp == null && useItemStampedConstructor != null
                ? construct(useItemStampedConstructor, hand, 0, yaw, pitch, timestamp)
                : construct(useItemConstructor, hand, 0, yaw, pitch);
        if (useItemTimestamp != null) set(useItemTimestamp, packet, timestamp);
        call(handleUseItem, listener, packet);
    }

    void handlePlayerAction(Object listener) {
        call(handlePlayerAction, listener, construct(releaseUseConstructor, releaseUseAction, zeroBlockPos, downDirection, 0));
    }

    void handlePlayerAction(Object listener, Object action, Object nmsBlockPos, Object nmsDirection) {
        call(handlePlayerAction, listener, construct(releaseUseConstructor, action, nmsBlockPos, nmsDirection, 0));
    }

    /** Acks a pending server teleport, as the client does; vanilla ignores use-item-on until then. */
    void ackTeleport(Object listener, float yaw, float pitch) {
        Object target = get(awaitingPosition, listener);
        if (target == null) return;
        int id = (int) get(awaitingTeleport, listener);
        Object packet = acceptTeleportPositioned
                ? construct(acceptTeleportConstructor, id, get(vecX, target), get(vecY, target), get(vecZ, target), yaw, pitch)
                : construct(acceptTeleportConstructor, id);
        call(handleAcceptTeleport, listener, packet);
    }

    void handleUseItemOn(Object listener, Object hand, Object blockHitResult, long timestamp) {
        Object packet = useOnTimestamp == null ? construct(useOnConstructor, hand, blockHitResult, 0, timestamp)
                : construct(useOnConstructor, hand, blockHitResult, 0);
        if (useOnTimestamp != null) set(useOnTimestamp, packet, timestamp);
        call(handleUseItemOn, listener, packet);
    }

    void handleInteract(Object listener, Object targetHandle, int entityId, Object hand, Object nmsVec3, boolean sneaking) {
        if (interactConstructor != null) {
            call(handleInteract, listener, construct(interactConstructor, entityId, hand, nmsVec3, sneaking));
            return;
        }
        call(handleInteract, listener, call(interactFactory, null, targetHandle, sneaking, hand, nmsVec3));
        call(handleInteract, listener, call(interactPlainFactory, null, targetHandle, sneaking, hand));
    }

    void handleAttack(Object listener, Object target, int entityId, boolean sneaking) {
        Object packet = attackConstructor != null ? construct(attackConstructor, entityId)
                : call(attackFactory, null, target, sneaking);
        call(handleAttack, listener, packet);
    }

    void handleSwing(Object listener, Object hand) {
        call(handleSwing, listener, punchPacket ? construct(swingConstructor) : construct(swingConstructor, hand));
    }

    // Not an instance of any bound native class, so primed calls fail their receiver check.
    private static final Object PRIME_RECEIVER = new Object();

    /** Creates the JDK accessor of every bound member now, so the first match does not build them on the main thread. */
    void primeAccessors() {
        for (Field holder : NativeBotBindings.class.getDeclaredFields()) {
            if (Modifier.isStatic(holder.getModifiers())) continue;
            try {
                prime(holder.get(this));
            } catch (IllegalAccessException ignored) {
                // skipped: primed on first use instead
            }
        }
    }

    // The accessor is built before the receiver and argument checks reject the call, so nothing native runs.
    static void prime(Object member) {
        try {
            if (member instanceof Method method) {
                method.invoke(PRIME_RECEIVER, new Object[method.getParameterCount() + 1]);
            } else if (member instanceof Constructor<?> constructor) {
                constructor.newInstance(new Object[constructor.getParameterCount() + 1]);
            } else if (member instanceof Field field && !Modifier.isStatic(field.getModifiers())) {
                field.get(PRIME_RECEIVER);
            }
        } catch (ReflectiveOperationException | IllegalArgumentException | NullPointerException ignored) {
            // expected: the call is rejected after the accessor exists
        }
    }

    static Object call(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Native call failed: " + method, cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot access native method: " + method, exception);
        }
    }

    static Object construct(Constructor<?> constructor, Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Native construction failed: " + constructor, cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot invoke native constructor: " + constructor, exception);
        }
    }

    static Object get(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read native field: " + field, exception);
        }
    }

    static void set(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot write native field: " + field, exception);
        }
    }

    private static Class<?> load(String name, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private static Constructor<?> constructor(Class<?> type, Class<?>... parameters)
            throws NoSuchMethodException {
        Constructor<?> constructor = type.getConstructor(parameters);
        if (!Modifier.isPublic(constructor.getModifiers())) {
            throw new NoSuchMethodException("Expected public constructor: " + constructor);
        }
        return constructor;
    }

    private static Method method(Class<?> type, String name, Class<?> returnType, Class<?>... parameters)
            throws NoSuchMethodException {
        return method(type, name, returnType, false, parameters);
    }

    private static Method method(Class<?> type, String name, Class<?> returnType, boolean assignable,
                                 Class<?>... parameters) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameters);
        int modifiers = method.getModifiers();
        boolean returnMatches = assignable ? returnType.isAssignableFrom(method.getReturnType())
                : method.getReturnType() == returnType;
        if (!returnMatches || !Modifier.isPublic(modifiers)) {
            throw new NoSuchMethodException("Unexpected native method signature: " + method);
        }
        return method;
    }

    private static Method eitherMethod(Class<?> type, String first, String second, Class<?> returnType)
            throws NoSuchMethodException {
        try {
            return method(type, first, returnType);
        } catch (NoSuchMethodException ignored) {
            return method(type, second, returnType);
        }
    }

    private static Field privateField(Class<?> type, String name, Class<?> expectedType) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        if (field.getType() != expectedType || Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException("Unexpected native field type: " + field);
        }
        field.setAccessible(true);
        return field;
    }

    private static Field field(Class<?> type, String name, Class<?> expectedType) throws NoSuchFieldException {
        Field field = type.getField(name);
        if (field.getType() != expectedType) {
            throw new NoSuchFieldException("Unexpected native field type: " + field);
        }
        return field;
    }

    private static Object enumValue(Class<?> type, String name) {
        if (!type.isEnum()) {
            throw new IllegalStateException("Expected native enum: " + type.getName());
        }
        for (Object value : type.getEnumConstants()) {
            if (((Enum<?>) value).name().equals(name)) {
                return value;
            }
        }
        throw new IllegalStateException("Missing native enum value " + type.getName() + "." + name);
    }
}
