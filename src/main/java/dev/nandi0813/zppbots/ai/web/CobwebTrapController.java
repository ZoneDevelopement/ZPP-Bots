package dev.nandi0813.zppbots.ai.web;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.BotSettings;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

public final class CobwebTrapController {

    private static final int HIT_WAIT_TICKS = 2;
    private static final int HIT_PLACE_TIMEOUT = 26;
    private static final int PLACE_WINDUP_TICKS = 3;

    private final BotContext ctx;
    private final Player bot;
    private HitWebPhase hitPhase = HitWebPhase.NONE;
    private int hitTicks = 0;
    private LivingEntity hitTarget = null;
    private BlockPos hitLanding = null;
    private int hitWebSlot = -1;
    private int hitPrevSlot = -1;
    private int cooldown = 0;
    private BlockPos placedPos = null;
    private long placedAtTick = 0L;
    private int combatDelay;
    private int webBlockPearl = 0;
    private int placeWindup = 0;
    private BlockPos pendingTrapPos = null;
    private int pendingSlot = -1;
    private int prevSlot = -1;

    private double hitWebRange() {
        return ctx.settings().attackRange();
    }

    public boolean isPlacing() {
        return placeWindup > 0 || hitPhase != HitWebPhase.NONE;
    }

    public CobwebTrapController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.combatDelay = ctx.tuning().integer(TuningKey.WEB_TRAP_COMBAT_START_DELAY);
    }

    public boolean tick(LivingEntity target, boolean wantCrit, boolean pearlSuppressesWeb) {
        if (hitPhase != HitWebPhase.NONE) {
            return tickHitWeb();
        } else if (!ctx.settings().useCobwebs()) {
            return false;
        } else {
            if (cooldown > 0) {
                cooldown--;
            }

            if (webBlockPearl > 0) {
                webBlockPearl--;
            }

            if (placeWindup > 0) {
                if (pendingSlot >= 0
                        && pendingSlot < 9
                        && ItemKinds.is(bot.getInventory().getItem(pendingSlot), Material.COBWEB)
                        && pendingTrapPos != null
                        && ctx.world().isAir(pendingTrapPos)) {
                    InventoryUtils.select(ctx, pendingSlot);
                    lookAt(pendingTrapPos);
                    placeWindup--;
                    if (placeWindup == 0) {
                        placeCobwebNow();
                    }

                    return true;
                } else {
                    abortPending();
                    return false;
                }
            } else if (pearlSuppressesWeb) {
                return false;
            } else {
                expirePlacedWeb();
                if (target == null) {
                    return false;
                } else if (ctx.settings().hitWeb()) {
                    if (cooldown == 0 && Ent.dist(bot, target) <= hitWebRange() && bot.hasLineOfSight(target)) {
                        int webSlot = findCobweb();
                        if (webSlot >= 0) {
                            return beginHitWeb(target, moveCobwebToHotbar(webSlot));
                        }
                    }

                    return false;
                } else if (combatDelay > 0) {
                    combatDelay--;
                    return false;
                } else if (Ent.dist(bot, target) > ctx.tuning().number(TuningKey.WEB_TRAP_RANGE).doubleValue()) {
                    return false;
                } else if (!bot.hasLineOfSight(target)) {
                    return false;
                } else if (!shouldTrap(wantCrit)) {
                    return false;
                } else if (cooldown <= 0 && placedPos == null) {
                    int slot = findCobweb();
                    if (slot < 0) {
                        return false;
                    } else {
                        BlockPos trapPos = predictPosition(target);
                        if (trapPos == null) {
                            return false;
                        } else if (!ctx.world().isAir(trapPos)) {
                            return false;
                        } else if (Ent.pos(bot).distanceTo(Vec3.atCenterOf(trapPos))
                                > ctx.tuning().number(TuningKey.WEB_TRAP_RANGE).doubleValue()) {
                            return false;
                        } else {
                            slot = moveCobwebToHotbar(slot);
                            prevSlot = InventoryUtils.selected(ctx);
                            pendingSlot = slot;
                            pendingTrapPos = trapPos;
                            InventoryUtils.select(ctx, slot);
                            lookAt(trapPos);
                            placeWindup = PLACE_WINDUP_TICKS;
                            return true;
                        }
                    }
                } else {
                    return false;
                }
            }
        }
    }

    private void placeCobwebNow() {
        PlayerInventory inv = bot.getInventory();
        boolean placed = false;
        if (pendingTrapPos != null && pendingSlot >= 0) {
            if (ItemKinds.is(inv.getItem(pendingSlot), Material.COBWEB) && ctx.world().isAir(pendingTrapPos)) {
                lookAt(pendingTrapPos);
                Vec3 hitVec = new Vec3(pendingTrapPos.getX() + 0.5, pendingTrapPos.getY(), pendingTrapPos.getZ() + 0.5);
                ctx.hands().useItemOn(EquipmentSlot.HAND, pendingTrapPos.below(), Direction.UP, hitVec);
                ctx.hands().swing(EquipmentSlot.HAND);
                placed = ctx.world().type(pendingTrapPos) == Material.COBWEB;
            }
        }

        if (placed) {
            placedPos = pendingTrapPos;
            placedAtTick = ctx.world().gameTime();
            cooldown = rollWebCooldown();
            webBlockPearl = ctx.tuning().integer(TuningKey.WEB_TRAP_PEARL_BLOCK_TICKS);
        } else {
            cooldown = ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN);
        }

        if (prevSlot >= 0) {
            InventoryUtils.select(ctx, prevSlot);
        }

        pendingTrapPos = null;
        pendingSlot = -1;
        prevSlot = -1;
    }

    private float webCooldownScale() {
        return cooldownScale(ctx.tuning(), ctx.settings().playStyle());
    }

    static float cooldownScale(BotTuning.Profile profile, BotSettings.PlayStyle style) {
        return switch (style) {
            case AGGRESSIVE -> profile.number(TuningKey.WEB_TRAP_SCALE_AGGRESSIVE).floatValue();
            case DEFENSIVE -> profile.number(TuningKey.WEB_TRAP_SCALE_DEFENSIVE).floatValue();
            case SAFE -> profile.number(TuningKey.WEB_TRAP_SCALE_SAFE).floatValue();
            case BALANCED -> profile.number(TuningKey.WEB_TRAP_SCALE_BALANCED).floatValue();
            default -> profile.number(TuningKey.WEB_TRAP_SCALE_DEFAULT).floatValue();
        };
    }

    private int rollWebCooldown() {
        int base = ctx.tuning().integer(TuningKey.WEB_TRAP_COOLDOWN_MIN)
                + ctx.random().nextInt(ctx.tuning().integer(TuningKey.WEB_TRAP_COOLDOWN_SPAN));
        return Math.max(ctx.tuning().integer(TuningKey.WEB_TRAP_COOLDOWN_FLOOR), Math.round(base * webCooldownScale()));
    }

    private void expirePlacedWeb() {
        if (placedPos != null) {
            if (ctx.world().type(placedPos) != Material.COBWEB
                    || ctx.world().gameTime() - placedAtTick
                            >= ctx.tuning().integer(TuningKey.WEB_TRAP_PLACED_TRACK_TICKS)) {
                placedPos = null;
            }
        }
    }

    private void abortPending() {
        cooldown = ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN);
        if (prevSlot >= 0) {
            InventoryUtils.select(ctx, prevSlot);
        }

        placeWindup = 0;
        pendingTrapPos = null;
        pendingSlot = -1;
        prevSlot = -1;
    }

    private void lookAt(BlockPos pos) {
        if (pos != null) {
            Vec3 eye = Ent.eyePos(bot);
            double dx = pos.getX() + 0.5 - eye.x();
            double dy = pos.getY() + 0.5 - eye.y();
            double dz = pos.getZ() + 0.5 - eye.z();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, horiz))));
        }
    }

    public boolean tryEmergencyWeb(LivingEntity target) {
        if (target == null) {
            return false;
        } else if (!ctx.settings().useCobwebs()) {
            return false;
        } else {
            expirePlacedWeb();
            if (cooldown <= 0 && placedPos == null && hitPhase == HitWebPhase.NONE) {
                if (Ent.dist(bot, target) > ctx.tuning().number(TuningKey.WEB_TRAP_RANGE).doubleValue()) {
                    return false;
                } else if (!bot.hasLineOfSight(target)) {
                    return false;
                } else {
                    int slot = findCobweb();
                    if (slot < 0) {
                        return false;
                    } else {
                        BlockPos trapPos = predictPosition(target);
                        if (trapPos != null && ctx.world().isAir(trapPos)) {
                            if (Ent.pos(bot).distanceTo(Vec3.atCenterOf(trapPos))
                                    > ctx.tuning().number(TuningKey.WEB_TRAP_RANGE).doubleValue()) {
                                return false;
                            } else {
                                slot = moveCobwebToHotbar(slot);
                                int prev = InventoryUtils.selected(ctx);
                                hitWebSlot = slot;
                                boolean placed = placeCobwebAt(trapPos);
                                hitWebSlot = -1;
                                InventoryUtils.select(ctx, prev);
                                if (placed) {
                                    placedPos = trapPos;
                                    placedAtTick = ctx.world().gameTime();
                                    webBlockPearl = ctx.tuning().integer(TuningKey.WEB_TRAP_PEARL_BLOCK_TICKS);
                                    cooldown = rollWebCooldown();
                                } else {
                                    cooldown = ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN);
                                }

                                return placed;
                            }
                        } else {
                            return false;
                        }
                    }
                }
            } else {
                return false;
            }
        }
    }

    public boolean placedWebRecently() {
        return webBlockPearl > 0;
    }

    public void reset() {
        cooldown = 0;
        combatDelay = ctx.tuning().integer(TuningKey.WEB_TRAP_COMBAT_START_DELAY);
        placeWindup = 0;
        pendingTrapPos = null;
        pendingSlot = -1;
        prevSlot = -1;
        hitPhase = HitWebPhase.NONE;
        hitTicks = 0;
        hitTarget = null;
        hitLanding = null;
        hitWebSlot = -1;
        hitPrevSlot = -1;
    }

    private boolean beginHitWeb(LivingEntity target, int webSlot) {
        hitTarget = target;
        hitWebSlot = webSlot;
        hitPrevSlot = InventoryUtils.selected(ctx);
        lookAtEntity(target);
        ctx.controls().setSprinting(true);
        ctx.hands().attack(target);
        ctx.hands().swing(EquipmentSlot.HAND);
        hitPhase = HitWebPhase.WAIT;
        hitTicks = 0;
        return true;
    }

    private boolean tickHitWeb() {
        if (cooldown > 0) {
            cooldown--;
        }

        if (webBlockPearl > 0) {
            webBlockPearl--;
        }

        hitTicks++;
        if (hitTarget != null && Ent.alive(hitTarget)) {
            switch (hitPhase) {
                case WAIT:
                    ctx.controls().stopMoving();
                    ctx.controls().setSprinting(false);
                    if (hitTicks >= HIT_WAIT_TICKS) {
                        BlockPos landing = predictLanding(hitTarget);
                        if (landing == null) {
                            endHitWeb(ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN));
                            return false;
                        }

                        hitLanding = landing;
                        hitPhase = HitWebPhase.PLACE;
                        hitTicks = 0;
                    }

                    return true;
                case PLACE:
                    return tickHitWebPlace();
                default:
                    endHitWeb(ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN));
                    return false;
            }
        } else {
            endHitWeb(ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN));
            return false;
        }
    }

    private boolean tickHitWebPlace() {
        if (hitLanding != null && ctx.world().isAir(hitLanding)) {
            PlayerInventory inv = bot.getInventory();
            if (hitWebSlot < 0 || hitWebSlot >= 9 || !ItemKinds.is(inv.getItem(hitWebSlot), Material.COBWEB)) {
                int s = findCobweb();
                if (s < 0) {
                    endHitWeb(ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN));
                    return false;
                }

                hitWebSlot = moveCobwebToHotbar(s);
            }

            InventoryUtils.select(ctx, hitWebSlot);
            lookAt(hitLanding);
            double reach = Ent.pos(bot).distanceTo(Vec3.atCenterOf(hitLanding));
            if (reach > ctx.tuning().number(TuningKey.WEB_TRAP_RANGE).doubleValue()) {
                moveTowardLanding();
                if (hitTicks >= HIT_PLACE_TIMEOUT) {
                    endHitWeb(ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN));
                    return false;
                } else {
                    return true;
                }
            } else {
                ctx.controls().stopMoving();
                if (placeCobwebAt(hitLanding)) {
                    webBlockPearl = ctx.tuning().integer(TuningKey.WEB_TRAP_PEARL_BLOCK_TICKS);
                    int hitMin = ctx.tuning().integer(TuningKey.WEB_TRAP_HIT_COOLDOWN_MIN);
                    int hitMax = ctx.tuning().integer(TuningKey.WEB_TRAP_HIT_COOLDOWN_MAX);
                    endHitWeb(hitMin + ctx.random().nextInt(hitMax - hitMin + 1));
                } else {
                    endHitWeb(ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN));
                }

                return true;
            }
        } else {
            endHitWeb(ctx.tuning().integer(TuningKey.WEB_TRAP_FAIL_RETRY_COOLDOWN));
            return false;
        }
    }

    private void moveTowardLanding() {
        Vec3 pos = Ent.pos(bot);
        double dx = hitLanding.getX() + 0.5 - pos.x();
        double dz = hitLanding.getZ() + 0.5 - pos.z();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        ctx.controls().look(yaw, bot.getLocation().getPitch());
        ctx.controls().setSprinting(true);
        ctx.controls().moveForward();
    }

    private BlockPos predictLanding(LivingEntity target) {
        Vec3 pos = Ent.pos(target);
        double px = pos.x();
        double py = pos.y();
        double pz = pos.z();
        Vec3 v = ctx.match().velocityOf(target);
        double vx = v.x();
        double vy = v.y();
        double vz = v.z();

        for (int t = 0; t < 40; t++) {
            vy = (vy - 0.08) * 0.98;
            px += vx;
            py += vy;
            pz += vz;
            vx *= 0.91;
            vz *= 0.91;
            if (!(vy >= 0.0)) {
                BlockPos foot = BlockPos.containing(px, py, pz);
                BlockPos below = foot.below();
                boolean groundBelow = !ctx.world().collisionBoxes(below).isEmpty();
                if (groundBelow && ctx.world().isAir(foot) && !overlapsBot(foot)) {
                    return foot;
                }
            }
        }

        return null;
    }

    private boolean placeCobwebAt(BlockPos pos) {
        PlayerInventory inv = bot.getInventory();
        if (hitWebSlot >= 0 && hitWebSlot < 9) {
            if (ItemKinds.is(inv.getItem(hitWebSlot), Material.COBWEB) && ctx.world().isAir(pos)) {
                lookAt(pos);
                Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                ctx.hands().useItemOn(EquipmentSlot.HAND, pos.below(), Direction.UP, hitVec);
                ctx.hands().swing(EquipmentSlot.HAND);
                return ctx.world().type(pos) == Material.COBWEB;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void lookAtEntity(LivingEntity target) {
        Vec3 pos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double dx = targetPos.x() - pos.x();
        double dy = targetPos.y() + target.getEyeHeight() - Ent.eyePos(bot).y();
        double dz = targetPos.z() - pos.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, horiz))));
    }

    private void endHitWeb(int cooldownTicks) {
        ctx.controls().stopMoving();
        ctx.controls().setSprinting(false);
        if (hitPrevSlot >= 0 && hitPrevSlot < 9) {
            InventoryUtils.select(ctx, hitPrevSlot);
        }

        hitPhase = HitWebPhase.NONE;
        hitTicks = 0;
        hitTarget = null;
        hitLanding = null;
        hitWebSlot = -1;
        hitPrevSlot = -1;
        cooldown = Math.max(cooldown, cooldownTicks);
    }

    private int moveCobwebToHotbar(int slot) {
        return InventoryUtils.moveToHotbarSafe(ctx, slot);
    }

    private boolean shouldTrap(boolean wantCrit) {
        return bot.getHealth() <= ctx.tuning().number(TuningKey.WEB_TRAP_LOW_HP).floatValue() || wantCrit;
    }

    private BlockPos predictPosition(LivingEntity target) {
        Vec3 vel = ctx.match().velocityOf(target);
        Vec3 pos = Ent.pos(target);
        double fx = pos.x() + vel.x() * ctx.tuning().integer(TuningKey.WEB_TRAP_PREDICT_TICKS);
        double fz = pos.z() + vel.z() * ctx.tuning().integer(TuningKey.WEB_TRAP_PREDICT_TICKS);
        BlockPos start = BlockPos.containing(fx, pos.y(), fz);
        int maxY = bot.getLocation().getBlockY() + 1;

        for (int dy = 0; dy <= 4; dy++) {
            BlockPos candidate = start.below(dy);
            if (candidate.getY() <= maxY) {
                BlockPos below = candidate.below();
                boolean groundBelow = !ctx.world().collisionBoxes(below).isEmpty();
                if (groundBelow) {
                    return overlapsBot(candidate) ? null : candidate;
                }
            }
        }

        return null;
    }

    private boolean overlapsBot(BlockPos pos) {
        return AABB.ofBlock(pos).intersects(Ent.box(bot).inflate(0.3));
    }

    private int findCobweb() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.COBWEB)) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.COBWEB)) {
                return i;
            }
        }

        return -1;
    }

    private enum HitWebPhase {
        NONE,
        WAIT,
        PLACE
    }
}
