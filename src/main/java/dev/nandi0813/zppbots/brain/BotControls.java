package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.platform.BotFrame;
import dev.nandi0813.zppbots.util.ArenaGuard;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The bot's keyboard and mouse: movement state, look interpolation and the USE/ATTACK/JUMP action machinery.
 * Player action pack and bot commands. {@link #update()} runs once per tick and returns the
 * {@link BotFrame} for the physics step.
 */
public final class BotControls {

    private static final ActionType[] ACTION_TYPES = ActionType.values();
    private static final EquipmentSlot[] HANDS = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
    private static final float MOVE_ACCEL = 0.22F;
    private static final float MOVE_DECEL = 0.8F;

    private final BotContext ctx;
    private final Map<ActionType, Action> actions = new EnumMap<>(ActionType.class);
    private final List<DelayedAction> pendingActions = new ArrayList<>();
    private BlockPos currentBlock;
    private int blockHitDelay;
    private float curBlockDamageMP;
    public boolean autoJump = false;
    private int autoJumpTime;
    private boolean sneaking;
    private boolean sprinting;
    private float forward;
    private float strafing;
    private float appliedForward;
    private float appliedStrafing;
    private boolean jumping;
    private boolean oneShotJump;
    private int vehicleJumpOverrideTicks = 0;
    private boolean clearJumpNextTick = false;
    private int itemUseCooldown;
    private LookInterpolation lookInterpolation;
    private float speedScale = 1.0F;

    public BotControls(BotContext ctx) {
        this.ctx = ctx;
    }

    private Player player() {
        return ctx.player();
    }

    public boolean frozen() {
        return !ctx.match().canMove();
    }

    public BotControls start(ActionType type, Action action) {
        if (action.isContinuous) {
            Action current = actions.get(type);
            if (current != null) {
                return this;
            }
        }

        Action previous = actions.remove(type);
        if (previous != null) {
            type.stop(this, previous);
        }

        actions.put(type, action);
        return this;
    }

    public BotControls stop(ActionType type) {
        Action action = actions.remove(type);
        if (action != null) {
            action.ticksRemaining = -1;
            type.stop(this, action);
        }

        if (type == ActionType.USE) {
            itemUseCooldown = 0;
            ctx.hands().releaseUseItem();
        }

        return this;
    }

    public BotControls setSneaking(boolean doSneak) {
        sneaking = doSneak;
        return this;
    }

    /** Ignores true while the round is not live. */
    public BotControls setSprinting(boolean doSprint) {
        if (doSprint && frozen()) {
            return this;
        }
        sprinting = doSprint;
        return this;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public BotControls setForward(float value) {
        forward = value;
        return this;
    }

    public BotControls setSpeedScale(float value) {
        speedScale = Math.max(0.1F, Math.min(1.0F, value));
        return this;
    }

    public BotControls setStrafing(float value) {
        strafing = value;
        return this;
    }

    public BotControls setJumping(boolean value) {
        if (value) {
            start(ActionType.JUMP, Action.continuous());
        } else {
            stop(ActionType.JUMP);
        }
        return this;
    }

    public float getForward() {
        return forward;
    }

    public float getStrafing() {
        return strafing;
    }

    private void guardArena() {
        if (appliedForward == 0.0F && appliedStrafing == 0.0F) return;
        double rad = Math.toRadians(yaw());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        // Mojang moveRelative: forward = (-sin, cos), strafe = (cos, sin)
        if (appliedForward != 0.0F && ArenaGuard.blocksStep(ctx, -sin * appliedForward, cos * appliedForward)) appliedForward = 0.0F;
        if (appliedStrafing != 0.0F && ArenaGuard.blocksStep(ctx, cos * appliedStrafing, sin * appliedStrafing)) appliedStrafing = 0.0F;
        if ((appliedForward != 0.0F || appliedStrafing != 0.0F) && ArenaGuard.blocksStep(ctx,
                cos * appliedStrafing - sin * appliedForward, cos * appliedForward + sin * appliedStrafing)) {
            appliedForward = 0.0F;
            appliedStrafing = 0.0F;
        }
    }

    private float yaw() {
        return player().getLocation().getYaw();
    }

    private float pitch() {
        return player().getLocation().getPitch();
    }

    public BotControls look(Direction direction) {
        return switch (direction) {
            case NORTH -> look(180.0F, 0.0F);
            case SOUTH -> look(0.0F, 0.0F);
            case EAST -> look(-90.0F, 0.0F);
            case WEST -> look(90.0F, 0.0F);
            case UP -> look(yaw(), -90.0F);
            case DOWN -> look(yaw(), 90.0F);
        };
    }

    public BotControls look(float yaw, float pitch) {
        ctx.hands().look(yaw % 360.0F, Mth.clamp(pitch, -90.0F, 90.0F));
        return this;
    }

    public BotControls lookAt(Vec3 position) {
        Vec3 eye = Ent.eyePos(player());
        double dx = position.x() - eye.x();
        double dy = position.y() - eye.y();
        double dz = position.z() - eye.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, dist) * (180.0 / Math.PI))));
        return look(yaw, pitch);
    }

    public BotControls turn(float yaw, float pitch) {
        return look(yaw() + yaw, pitch() + pitch);
    }

    public BotControls lookInterpolated(float targetYaw, float targetPitch, int ticks) {
        if (ticks <= 0) {
            return look(targetYaw, targetPitch);
        }
        float clampedPitch = Mth.clamp(targetPitch, -90.0F, 90.0F);
        lookInterpolation = new LookInterpolation(
                targetYaw, clampedPitch, Mth.wrapDegrees(targetYaw - yaw()) / ticks, (clampedPitch - pitch()) / ticks, ticks);
        return this;
    }

    public BotControls look(Direction direction, int ticks) {
        float targetYaw;
        float targetPitch;
        switch (direction) {
            case NORTH -> {
                targetYaw = 180.0F;
                targetPitch = 0.0F;
            }
            case SOUTH -> {
                targetYaw = 0.0F;
                targetPitch = 0.0F;
            }
            case EAST -> {
                targetYaw = -90.0F;
                targetPitch = 0.0F;
            }
            case WEST -> {
                targetYaw = 90.0F;
                targetPitch = 0.0F;
            }
            case UP -> {
                targetYaw = yaw();
                targetPitch = -90.0F;
            }
            case DOWN -> {
                targetYaw = yaw();
                targetPitch = 90.0F;
            }
            default -> {
                return this;
            }
        }
        return lookInterpolated(targetYaw, targetPitch, ticks);
    }

    public BotControls turn(float yaw, float pitch, int ticks) {
        return lookInterpolated(yaw() + yaw, pitch() + pitch, ticks);
    }

    public BotControls lookAt(Vec3 position, int ticks) {
        if (ticks <= 0) {
            return lookAt(position);
        }
        Vec3 eye = Ent.eyePos(player());
        double dx = position.x() - eye.x();
        double dy = position.y() - eye.y();
        double dz = position.z() - eye.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, dist) * (180.0 / Math.PI))));
        return lookInterpolated(yaw, pitch, ticks);
    }

    public void stopInterpolation() {
        lookInterpolation = null;
    }

    public BotControls stopMovement() {
        setSneaking(false);
        setSprinting(false);
        forward = 0.0F;
        strafing = 0.0F;
        return this;
    }

    public BotControls startOrExtender(ActionType type, int ticks) {
        Action current = actions.get(type);
        if (current != null && current.isContinuous) {
            current.ticksRemaining = ticks;
            return this;
        }
        Action previous = actions.remove(type);
        if (previous != null) {
            type.stop(this, previous);
        }
        actions.put(type, Action.continuous(ticks));
        return this;
    }

    public BotControls stopAll() {
        for (Map.Entry<ActionType, Action> entry : actions.entrySet()) {
            entry.getKey().stop(this, entry.getValue());
        }
        actions.clear();
        stopInterpolation();
        appliedForward = 0.0F;
        appliedStrafing = 0.0F;
        return stopMovement();
    }

    /** Runs the action after the given number of game ticks, on the first update at or past that time. */
    public void later(int ticks, Runnable action) {
        pendingActions.add(new DelayedAction(ctx.world().gameTime() + ticks, action));
    }

    // BotCommands shims

    public void moveForward() {
        if (!frozen()) setForward(1.0F);
    }

    public void moveBackward() {
        if (!frozen()) setForward(-1.0F);
    }

    public void moveLeft() {
        if (!frozen()) setStrafing(1.0F);
    }

    public void moveRight() {
        if (!frozen()) setStrafing(-1.0F);
    }

    public void setMovement(float forward, float strafe) {
        if (!frozen()) {
            setForward(forward);
            setStrafing(strafe);
        }
    }

    public void stopMoving() {
        stopMovement();
    }

    public void stopLookInterpolation() {
        stopInterpolation();
    }

    public void jumpOnce() {
        if (!frozen()) start(ActionType.JUMP, Action.once());
    }

    public void attackOnce() {
        start(ActionType.ATTACK, Action.once());
    }

    public void mineContinuous() {
        start(ActionType.ATTACK, Action.continuous());
    }

    public void stopMining() {
        stop(ActionType.ATTACK);
    }

    public void startUsingItem() {
        start(ActionType.USE, Action.continuous());
    }

    public void stopUsingItem() {
        stop(ActionType.USE);
    }

    public BotFrame update() {
        if (autoJumpTime > 0) {
            autoJumpTime--;
            start(ActionType.JUMP, Action.once());
        }

        if (lookInterpolation != null) {
            lookInterpolation.ticksRemaining--;
            if (lookInterpolation.ticksRemaining == 0) {
                look(lookInterpolation.targetYaw, lookInterpolation.targetPitch);
                lookInterpolation = null;
            } else {
                look(Mth.wrapDegrees(yaw() + lookInterpolation.deltaYaw),
                        Mth.clamp(pitch() + lookInterpolation.deltaPitch, -90.0F, 90.0F));
            }
        }

        if (!pendingActions.isEmpty()) {
            long currentTick = ctx.world().gameTime();
            List<DelayedAction> due = new ArrayList<>();
            for (DelayedAction pending : pendingActions) {
                if (currentTick >= pending.tick()) due.add(pending);
            }
            pendingActions.removeAll(due);
            for (DelayedAction pending : due) pending.action().run();
        }

        for (ActionType type : ACTION_TYPES) {
            Action action = actions.get(type);
            if (action != null && action.done) {
                actions.remove(type);
            }
        }

        Boolean useAttempt = null;
        Boolean attackAttempt = null;

        for (ActionType type : ACTION_TYPES) {
            Action action = actions.get(type);
            if (action != null) {
                if (!Boolean.TRUE.equals(useAttempt) || type != ActionType.ATTACK) {
                    Boolean actionStatus = action.tick(this, type);
                    if (actionStatus != null) {
                        if (type == ActionType.USE) {
                            useAttempt = actionStatus;
                        } else if (type == ActionType.ATTACK) {
                            attackAttempt = actionStatus;
                        }
                    }
                }

                if (type == ActionType.ATTACK && Boolean.TRUE.equals(attackAttempt) && Boolean.FALSE.equals(useAttempt)) {
                    Action using = actions.get(ActionType.USE);
                    if (using != null) {
                        using.retry(this, ActionType.USE);
                    }
                }
            }
        }

        if (vehicleJumpOverrideTicks > 0) {
            jumping = true;
            vehicleJumpOverrideTicks--;
            if (vehicleJumpOverrideTicks == 0) {
                clearJumpNextTick = true;
            }
        } else if (clearJumpNextTick) {
            jumping = false;
            clearJumpNextTick = false;
        }

        Player player = player();
        boolean sprintDecision;
        boolean slowUse = player.isHandRaised() && !ItemKinds.isSpear(player.getActiveItem());
        if (slowUse) {
            sprinting = false;
            sprintDecision = false;
        } else if ((forward != 0.0F || strafing != 0.0F) && player.getFoodLevel() <= 6 && !player.isInsideVehicle()) {
            sprintDecision = false;
        } else {
            sprintDecision = sprinting;
        }

        appliedForward = approachInput(appliedForward, forward);
        appliedStrafing = approachInput(appliedStrafing, strafing);
        guardArena();
        boolean jump = jumping || oneShotJump;
        oneShotJump = false;
        // the native step applies the sneak (0.3) and use (0.2) factors itself
        return new BotFrame(
                Mth.clamp(appliedForward * speedScale, -1.0F, 1.0F),
                Mth.clamp(appliedStrafing * speedScale, -1.0F, 1.0F),
                jump, sneaking, sprintDecision);
    }

    private static float approachInput(float cur, float target) {
        if (cur == target) {
            return target;
        }
        float rate = Math.abs(target) > Math.abs(cur) ? MOVE_ACCEL : MOVE_DECEL;
        return cur < target ? Math.min(cur + rate, target) : Math.max(cur - rate, target);
    }

    private void updateAutoJump() {
        if (!canAutoJump()) {
            return;
        }
        Player player = player();
        BotWorld world = ctx.world();
        Vec3 startPos = Ent.pos(player);
        float speed = (float) Ent.attr(player, Attribute.MOVEMENT_SPEED);
        float xInput = speed * strafing;
        float zInput = speed * forward;
        float yawRad = player.getLocation().getYaw() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yawRad);
        float cos = Mth.cos(yawRad);
        Vec3 movement = new Vec3(xInput * cos - zInput * sin, 0.0, zInput * cos + xInput * sin);
        float movementLenSqr = (float) movement.lengthSqr();
        if (movementLenSqr <= 0.001F) {
            return;
        }

        float invLen = Mth.invSqrt(movementLenSqr);
        Vec3 movementDir = movement.scale(invLen);
        Vec3 playerForward = Ent.look(player);
        float forwardDot = (float) (playerForward.x() * movementDir.x() + playerForward.z() * movementDir.z());
        if (forwardDot < -0.15F) {
            return;
        }

        BlockPos blockPos = BlockPos.containing(startPos.x(), player.getBoundingBox().getMaxY(), startPos.z());
        if (!world.collisionBoxes(blockPos).isEmpty()) {
            return;
        }
        blockPos = blockPos.above();
        if (!world.collisionBoxes(blockPos).isEmpty()) {
            return;
        }

        float maxStepHeight = 1.2F;
        PotionEffect jumpBoost = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jumpBoost != null) {
            maxStepHeight += (jumpBoost.getAmplifier() + 1) * 0.75F;
        }

        float probeDistance = Math.max(speed * 7.0F, 1.0F / invLen);
        Vec3 probeEnd = startPos.add(movementDir.scale(probeDistance));
        float bbWidth = (float) player.getWidth();
        float bbHeight = (float) player.getHeight();
        AABB searchBox = new AABB(startPos, probeEnd.add(0.0, bbHeight, 0.0)).inflate(bbWidth, 0.0, bbWidth);
        Vec3 raisedStart = startPos.add(0.0, 0.51, 0.0);
        Vec3 raisedEnd = probeEnd.add(0.0, 0.51, 0.0);
        Vec3 side = movementDir.cross(new Vec3(0.0, 1.0, 0.0));
        Vec3 halfWidth = side.scale(bbWidth * 0.5F);
        Vec3 leftStart = raisedStart.subtract(halfWidth);
        Vec3 leftEnd = raisedEnd.subtract(halfWidth);
        Vec3 rightStart = raisedStart.add(halfWidth);
        Vec3 rightEnd = raisedEnd.add(halfWidth);
        float obstacleTopY = Float.MIN_VALUE;

        BlockPos low = BlockPos.containing(searchBox.minX(), searchBox.minY(), searchBox.minZ());
        BlockPos high = BlockPos.containing(searchBox.maxX(), searchBox.maxY(), searchBox.maxZ());
        search:
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            for (AABB hitBox : world.collisionBoxes(pos)) {
                if (!hitBox.intersects(searchBox)) {
                    continue;
                }
                if (hitBox.intersects(leftStart, leftEnd) || hitBox.intersects(rightStart, rightEnd)) {
                    obstacleTopY = (float) hitBox.maxY();
                    BlockPos obstaclePos = BlockPos.containing(hitBox.getCenter());

                    for (int u = 1; u < maxStepHeight; u++) {
                        BlockPos abovePos = obstaclePos.above(u);
                        double aboveTop = world.collisionTop(abovePos);
                        if (!Double.isNaN(aboveTop)) {
                            obstacleTopY = (float) aboveTop;
                            if (obstacleTopY - startPos.y() > maxStepHeight) {
                                return;
                            }
                        }

                        if (u > 1) {
                            blockPos = blockPos.above();
                            if (!world.collisionBoxes(blockPos).isEmpty()) {
                                return;
                            }
                        }
                    }
                    break search;
                }
            }
        }

        if (obstacleTopY != Float.MIN_VALUE) {
            float stepHeight = (float) (obstacleTopY - startPos.y());
            if (stepHeight > 0.5F && stepHeight <= maxStepHeight) {
                autoJumpTime = 1;
            }
        }
    }

    public void attemptAutoJump() {
        boolean wasAutoJump = autoJump;
        autoJump = true;
        updateAutoJump();
        autoJump = wasAutoJump;
    }

    private boolean canAutoJump() {
        return autoJump && autoJumpTime <= 0 && player().isOnGround() && !player().isInsideVehicle()
                && (forward != 0.0F || strafing != 0.0F);
    }

    /** What the crosshair points at: a block, an entity, or nothing. */
    private record Target(BlockHit block, Entity entity, Vec3 entityHit) {
    }

    private Target blockFirst() {
        BlockHit block = blockTarget();
        return block.hit() ? new Target(block, null, null) : entityTarget();
    }

    private Target entityFirst() {
        Target entity = entityTarget();
        return entity.entity() != null ? entity : new Target(blockTarget(), null, null);
    }

    private BlockHit blockTarget() {
        Player player = player();
        Vec3 eye = Ent.eyePos(player);
        Vec3 end = eye.add(Ent.look(player).scale(Ent.attr(player, Attribute.BLOCK_INTERACTION_RANGE)));
        return ctx.world().clip(eye, end, true, false);
    }

    private Target entityTarget() {
        Player player = player();
        BlockHit block = blockTarget();
        Location eye = player.getEyeLocation();
        double range = Ent.attr(player, Attribute.ENTITY_INTERACTION_RANGE);
        if (block.hit()) {
            range = Math.min(range, Ent.eyePos(player).distanceTo(block.location()));
        }
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(),
                range, 0.0,
                e -> e != player && (e instanceof LivingEntity || e instanceof Vehicle));
        if (result == null || result.getHitEntity() == null) {
            return new Target(BlockHit.MISS, null, null);
        }
        return new Target(BlockHit.MISS, result.getHitEntity(), Vec3.of(result.getHitPosition()));
    }

    private boolean executeUse(Target target) {
        for (EquipmentSlot hand : HANDS) {
            if (target.entity() != null) {
                Entity entity = target.entity();
                if (ctx.hands().interact(entity, hand, target.entityHit().subtract(Ent.pos(entity)))) {
                    itemUseCooldown = 3;
                    return true;
                }
            } else if (target.block().hit()) {
                BlockPos pos = target.block().pos();
                Direction side = target.block().face();
                if (pos.getY() < ctx.world().maxY() - (side == Direction.UP ? 1 : 0)
                        && ctx.hands().useItemOn(hand, pos, side, target.block().location())) {
                    itemUseCooldown = 3;
                    return true;
                }
            }

            if (ctx.hands().useItem(hand)) {
                itemUseCooldown = 3;
                return true;
            }
        }
        return false;
    }

    private boolean executeAttack(Action action) {
        Player player = player();
        if (ItemKinds.isSpear(player.getInventory().getItemInMainHand())) {
            if (player.getAttackCooldown() < 1.0F) {
                return false;
            }
            ctx.hands().stab();
            return true;
        }

        Target target = entityFirst();
        if (target.entity() != null) {
            if (!action.isContinuous) {
                ctx.hands().attack(target.entity());
                ctx.hands().swing(EquipmentSlot.HAND);
            }
            player.resetCooldown();
            return true;
        }

        if (target.block().hit()) {
            if (blockHitDelay > 0) {
                blockHitDelay--;
                return false;
            }
            BlockPos pos = target.block().pos();
            Direction side = target.block().face();
            if (currentBlock != null && ctx.world().isAir(currentBlock)) {
                currentBlock = null;
                return false;
            }

            boolean blockBroken = false;
            if (currentBlock != null && currentBlock.equals(pos)) {
                curBlockDamageMP += ctx.world().breakSpeed(pos);
                if (curBlockDamageMP >= 1.0F) {
                    ctx.hands().stopDestroy(pos, side);
                    currentBlock = null;
                    blockHitDelay = 5;
                    blockBroken = true;
                }
            } else {
                if (currentBlock != null) {
                    ctx.hands().abortDestroy(currentBlock, side);
                }

                ctx.hands().startDestroy(pos, side);
                boolean notAir = !ctx.world().isAir(pos);
                if (notAir && ctx.world().breakSpeed(pos) >= 1.0F) {
                    currentBlock = null;
                    blockBroken = true;
                } else {
                    currentBlock = pos;
                    curBlockDamageMP = 0.0F;
                }
            }

            ctx.hands().swing(EquipmentSlot.HAND);
            if (action.resetMineCheck() && currentBlock != null) {
                ctx.hands().abortDestroy(currentBlock, side);
                currentBlock = null;
                curBlockDamageMP = 0.0F;
            }
            return blockBroken;
        }

        if (!action.isContinuous) {
            ctx.hands().swing(EquipmentSlot.HAND);
        }
        player.resetCooldown();
        return false;
    }

    private void abortCurrentBlock() {
        if (currentBlock != null) {
            ctx.hands().abortDestroy(currentBlock, Direction.DOWN);
            currentBlock = null;
            curBlockDamageMP = 0.0F;
        }
    }

    public static class Action {
        public boolean done = false;
        public final int limit;
        public final int interval;
        public final int offset;
        public final EquipmentSlot hand;
        private int count;
        private int next;
        private final boolean isContinuous;
        private int ticksRemaining;

        private Action(int limit, int interval, int offset, boolean continuous, EquipmentSlot hand, int ticksRemaining) {
            this.limit = limit;
            this.interval = interval;
            this.offset = offset;
            this.hand = hand;
            this.next = interval + offset;
            this.isContinuous = continuous;
            this.ticksRemaining = ticksRemaining;
        }

        public static Action once() {
            return new Action(1, 1, 0, false, null, -1);
        }

        public static Action continuous() {
            return new Action(-1, 1, 0, true, null, -1);
        }

        public static Action continuous(int ticks) {
            return new Action(-1, 1, 0, true, null, ticks);
        }

        public static Action interval(int interval) {
            return new Action(-1, interval, 0, false, null, -1);
        }

        public static Action interval(int interval, int ticks) {
            return new Action(-1, interval, 0, false, null, ticks);
        }

        public boolean resetMineCheck() {
            return !isContinuous || ticksRemaining > 0;
        }

        Boolean tick(BotControls controls, ActionType type) {
            if (ticksRemaining > 0) {
                ticksRemaining--;
                if (ticksRemaining <= 0) {
                    type.stop(controls, this);
                    done = true;
                    return null;
                }
            }

            boolean active = !type.preventSpectator || controls.player().getGameMode() != GameMode.SPECTATOR;
            next--;
            Boolean cancel = null;
            if (next <= 0) {
                if (interval == 1 && !isContinuous && active) {
                    type.inactiveTick(controls, this);
                }

                if (active) {
                    cancel = type.execute(controls, this);
                }

                count++;
                if (count == limit) {
                    type.stop(controls, null);
                    done = true;
                    return cancel;
                }

                next = interval;
            } else if (active) {
                type.inactiveTick(controls, this);
            }

            return cancel;
        }

        void retry(BotControls controls, ActionType type) {
            if (!type.preventSpectator || controls.player().getGameMode() != GameMode.SPECTATOR) {
                type.execute(controls, this);
            }

            count++;
            if (count == limit) {
                type.stop(controls, null);
                done = true;
            }
        }
    }

    public enum ActionType {
        USE(true) {
            @Override
            boolean execute(BotControls c, Action action) {
                if (c.itemUseCooldown > 0) {
                    c.itemUseCooldown--;
                    return true;
                } else if (c.player().isHandRaised()) {
                    return true;
                }
                return c.executeUse(c.blockFirst());
            }

            @Override
            void inactiveTick(BotControls c, Action action) {
                c.itemUseCooldown = 0;
                c.ctx.hands().releaseUseItem();
            }
        },
        ATTACK(true) {
            @Override
            boolean execute(BotControls c, Action action) {
                return c.executeAttack(action);
            }

            @Override
            void stop(BotControls c, Action action) {
                super.stop(c, action);
                c.abortCurrentBlock();
            }
        },
        JUMP(true) {
            @Override
            boolean execute(BotControls c, Action action) {
                if (action.limit == 1) {
                    if (c.player().isInsideVehicle()) {
                        c.vehicleJumpOverrideTicks = 2;
                        c.jumping = true;
                    } else if (c.player().isOnGround()) {
                        c.oneShotJump = true;
                    } else if (!c.player().isClimbing()) {
                        c.ctx.hands().startGliding();
                    }
                } else {
                    c.jumping = true;
                }
                return false;
            }

            @Override
            void inactiveTick(BotControls c, Action action) {
                if (c.vehicleJumpOverrideTicks <= 0) {
                    c.jumping = false;
                }
            }
        },
        DROP_ITEM(true) {
            @Override
            boolean execute(BotControls c, Action action) {
                c.ctx.hands().drop(false);
                return false;
            }
        },
        DROP_STACK(true) {
            @Override
            boolean execute(BotControls c, Action action) {
                c.ctx.hands().drop(true);
                return false;
            }
        },
        SWAP_HANDS(true) {
            @Override
            boolean execute(BotControls c, Action action) {
                c.ctx.hands().swapHands();
                return false;
            }
        },
        SWING(false) {
            @Override
            boolean execute(BotControls c, Action action) {
                c.ctx.hands().swing(action.hand == null ? EquipmentSlot.HAND : action.hand);
                return false;
            }
        };

        public final boolean preventSpectator;

        ActionType(boolean preventSpectator) {
            this.preventSpectator = preventSpectator;
        }

        abstract boolean execute(BotControls controls, Action action);

        void inactiveTick(BotControls controls, Action action) {
        }

        void stop(BotControls controls, Action action) {
            inactiveTick(controls, action);
        }
    }

    private record DelayedAction(long tick, Runnable action) {
    }

    private static class LookInterpolation {
        final float targetYaw;
        final float targetPitch;
        final float deltaYaw;
        final float deltaPitch;
        int ticksRemaining;

        LookInterpolation(float targetYaw, float targetPitch, float deltaYaw, float deltaPitch, int ticks) {
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            this.ticksRemaining = ticks;
        }
    }
}
