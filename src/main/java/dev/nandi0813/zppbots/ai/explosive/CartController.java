package dev.nandi0813.zppbots.ai.explosive;

import dev.nandi0813.zppbots.ai.gear.OffhandManager;
import dev.nandi0813.zppbots.ai.ranged.CrossbowHelper;
import dev.nandi0813.zppbots.brain.BlockHit;
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
import dev.nandi0813.zppbots.util.BotMath;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

public final class CartController {

    private static final String CART_SKIPPED = "CART_SKIPPED";
    private static final String CART_START = "CART_START";
    private static final String CROSSBOW_MISSING = "CROSSBOW_MISSING";
    private static final String CROSSBOW_WRONG_ITEM = "CROSSBOW_WRONG_ITEM";
    private static final String CROSSBOW_FLAME_MISSING = "CROSSBOW_FLAME_MISSING";
    private static final String BOW_WRONG_ITEM = "BOW_WRONG_ITEM";
    private static final String BOW_DRAW_START = "BOW_DRAW_START";
    private static final String BOW_RELEASE = "BOW_RELEASE";

    private static final double VANILLA_PLACE_REACH = 4.5;
    private static final int MAX_EXECUTE_TICKS = 102;
    private static final int XBOW_LOAD_TICKS = 30;
    private static final int XBOW_POST_FIRE_WAIT = 8;
    private static final int XBOW_TICK_RAIL_EQUIP = 1;
    private static final int XBOW_TICK_RAIL = 2;
    private static final int XBOW_TICK_CART = 4;
    private static final int XBOW_TICK_FLINT_EQUIP = 8;
    private static final int XBOW_TICK_FIRE = 9;
    private static final int XBOW_TICK_SHOOT = 12;

    private final BotContext ctx;
    private final Player bot;
    private final OffhandManager offhand;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldownLeft = 0;
    private int lastBowCartTick = Integer.MIN_VALUE / 2;
    private BotSettings.CartMode activeType = null;
    private BlockPos railPos = null;
    private BlockPos firePos = null;
    private int prevSlot = 0;
    private int railSlot = -1;
    private int cartSlot = -1;
    private int flameBowSlot = -1;
    private int crossbowSlot = -1;
    private int sincePearl = 9999;
    private int sinceCartMiss = 9999;
    private CartDoctrine.Reason lastReason = CartDoctrine.Reason.NONE;
    private Vec3 lastTargetPos = null;
    private int lastTargetId = -1;
    private boolean sawPrimedEnemyCart = false;
    private final ArrayDeque<UUID> ownCarts = new ArrayDeque<>();
    private boolean offhandCart = false;
    private int cartMissScanTick = 0;
    private boolean instaRetried = false;
    private boolean instaRecovery = false;
    private int lastFiredCrossbowSlot = -1;
    private int flintSlot = -1;
    private boolean isDrawing = false;
    private ExplosiveMinecart spawnedCart = null;
    private boolean xbowFired = false;
    private int xbowPostFireWait = 0;
    private boolean xbowFireRetried = false;
    private boolean xbowCharging = false;
    private int xbowChargeTicks = 0;
    private String lastBail = "";
    private int defenceCooldown = 0;
    private int pendingGrabDelay = 0;

    public CartController(BotContext ctx, OffhandManager offhand) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.offhand = offhand;
    }

    public boolean isExecuting() {
        return phase == Phase.EXECUTING;
    }

    public boolean tick(LivingEntity target, double dist) {
        if (cooldownLeft > 0) {
            cooldownLeft--;
        }

        if (defenceCooldown > 0) {
            defenceCooldown--;
        }

        observeTarget(target, dist);
        ensureCartHotbar();
        defendAgainstIncomingCart();

        return switch (phase) {
            case IDLE -> tickIdle(target, dist);
            case EXECUTING -> tickExecuting(target);
        };
    }

    private void observeTarget(LivingEntity target, double dist) {
        if (sincePearl < 9999) {
            sincePearl++;
        }

        if (sinceCartMiss < 9999) {
            sinceCartMiss++;
        }

        if (target == null) {
            lastTargetPos = null;
        } else {
            if (target.getEntityId() != lastTargetId) {
                lastTargetId = target.getEntityId();
                lastTargetPos = null;
                sincePearl = 9999;
                sinceCartMiss = 9999;
                sawPrimedEnemyCart = false;
            }

            if (lastTargetPos != null) {
                double jump = Ent.pos(target).distanceTo(lastTargetPos);
                if (jump > 8.0 && dist > 1.5) {
                    sincePearl = 0;
                }
            }

            lastTargetPos = Ent.pos(target);
            if (cartMissScanTick++ % 5 == 0) {
                boolean primedNear = !ctx.world()
                        .entities(
                                ExplosiveMinecart.class,
                                Ent.box(target).inflate(6.0),
                                m -> m.isIgnited() && !ownCarts.contains(m.getUniqueId()))
                        .isEmpty();
                if (primedNear) {
                    sawPrimedEnemyCart = true;
                } else if (sawPrimedEnemyCart) {
                    sawPrimedEnemyCart = false;
                    if (bot.getNoDamageTicks() == 0) {
                        sinceCartMiss = 0;
                    }
                }
            }
        }
    }

    public boolean wantsToChaseForCart(LivingEntity target, double dist) {
        if (phase != Phase.IDLE) {
            return false;
        } else if (cooldownLeft > 0) {
            return false;
        } else if (target != null && Ent.alive(target)) {
            if (bot.getHealth() < ctx.tuning().number(TuningKey.CART_MIN_HP).floatValue()) {
                return false;
            } else if (!(dist <= ctx.tuning().number(TuningKey.CART_CHASE_MIN_DIST).doubleValue())
                    && !(dist > ctx.tuning().number(TuningKey.CART_CHASE_MAX_DIST).doubleValue())) {
                return !ctx.settings().useCarting()
                        ? false
                        : findRailInInventory() >= 0
                                && findInInventory(Material.TNT_MINECART) >= 0
                                && (findFlameBowInHotbar() >= 0 || hasAnyCrossbow() && hasArrows());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void reset() {
        if (phase != Phase.IDLE) {
            InventoryUtils.select(ctx, prevSlot);
            if (isDrawing) {
                ctx.hands().releaseUseItem();
                isDrawing = false;
            }

            if (xbowCharging && bot.isHandRaised()) {
                ctx.hands().releaseUseItem();
            }
        }

        phase = Phase.IDLE;
        phaseTick = 0;
        instaRetried = false;
        instaRecovery = false;
        offhandCart = false;
        lastTargetPos = null;
        spawnedCart = null;
        clearXbowCartState();
    }

    private boolean aboutToLand() {
        return Ent.velocity(bot).y() > 0.1 ? false : !bot.collidesAt(bot.getLocation().subtract(0.0, 1.2, 0.0));
    }

    private boolean bail(String reason) {
        int sp = reason.indexOf(32);
        int kindLen = sp < 0 ? reason.length() : sp;
        if (kindLen != lastBail.length() || !reason.startsWith(lastBail)) {
            lastBail = reason.substring(0, kindLen);
            ctx.log().decision("CartController", CART_SKIPPED, "[Cart] no cart: " + reason);
        }

        return false;
    }

    private boolean repeatBail(String kind) {
        return lastBail.equals(kind);
    }

    private double cartSelfMin() {
        return !CartDoctrine.isGuideDoctrine(ctx) ? 3.0 : Math.max(3.0, CartDoctrine.selfMinDistance(ctx, lastReason));
    }

    private boolean tickIdle(LivingEntity target, double dist) {
        if (target == null || !Ent.alive(target)) {
            return bail("no target");
        } else if (cooldownLeft > 0) {
            return repeatBail("cooldown") ? false : bail("cooldown " + cooldownLeft);
        } else if (bot.getHealth() < ctx.tuning().number(TuningKey.CART_MIN_HP).floatValue()) {
            return repeatBail("hp") ? false : bail("hp " + bot.getHealth());
        } else if (!(dist < ctx.tuning().number(TuningKey.CART_MIN_DIST).doubleValue())
                && !(dist > ctx.tuning().number(TuningKey.CART_MAX_DIST).doubleValue())) {
            if (!bot.isOnGround() && !aboutToLand()) {
                return bail("airborne");
            } else {
                if (!ctx.settings().useCarting()) {
                    return bail("carting off");
                } else {
                    CartDoctrine.Reason why = CartDoctrine.shouldCart(ctx, target, dist, sincePearl, sinceCartMiss);
                    if (why == CartDoctrine.Reason.NONE) {
                        return bail("doctrine");
                    } else if (why != CartDoctrine.Reason.RELENTLESS && !CartDoctrine.selfDamageOk(ctx, target, why)) {
                        return bail("selfdamage");
                    } else {
                        lastReason = why;
                        if (why != CartDoctrine.Reason.FINISH && skipThisCart()) {
                            cooldownLeft = cartCooldown();
                            return false;
                        } else {
                            railSlot = findRailInInventory();
                            cartSlot = findInInventory(Material.TNT_MINECART);
                            if (railSlot >= 0 && cartSlot >= 0) {
                                flameBowSlot = findFlameBowInHotbar();
                                int chained = findChargedCrossbowExcluding(lastFiredCrossbowSlot);
                                if (chained < 0) {
                                    chained = findChargedCrossbowExcluding(-1);
                                }

                                crossbowSlot = chained >= 0 ? chained : findCrossbowForCart();
                                flintSlot = findInInventory(Material.FLINT_AND_STEEL);
                                BotSettings.CartMode mode = ctx.settings().cartMode();
                                if (mode == BotSettings.CartMode.SMART) {
                                    mode = pickSmartMode(target, dist);
                                }

                                if (!hasItemsForType(mode)) {
                                    return repeatBail("no")
                                            ? false
                                            : bail("no items for " + mode + " bow=" + flameBowSlot + " xbow="
                                                    + crossbowSlot + " flint=" + flintSlot);
                                } else if (bowCooling(mode, ctx.tick(), lastBowCartTick,
                                        ctx.tuning().integer(TuningKey.CART_BOW_COOLDOWN_TICKS))) {
                                    return repeatBail("bow") ? false : bail("bow cooldown");
                                } else {
                                    railPos = findPlacementPos(target);
                                    if (railPos == null) {
                                        return bail("no placement spot");
                                    } else {
                                        if (mode == BotSettings.CartMode.XBOW_CART) {
                                            firePos = findFirePosAlongShotPath(
                                                    Vec3.atCenterOf(railPos).add(0.0, 0.15, 0.0));
                                            if (firePos == null) {
                                                return bail("no fire spot");
                                            }
                                        }

                                        ctx.log().decision("CartController", CART_START, String.format(
                                                "[Cart] starting %s cart at dist=%.1f railPos=%s because=%s",
                                                mode, dist, railPos, lastReason));
                                        lastBail = "";
                                        activeType = mode;
                                        if (mode != BotSettings.CartMode.XBOW_CART) lastBowCartTick = ctx.tick();
                                        prevSlot = InventoryUtils.selected(ctx);
                                        phaseTick = 0;
                                        phase = Phase.EXECUTING;
                                        spawnedCart = null;
                                        isDrawing = false;
                                        offhandCart = false;
                                        clearXbowCartState();
                                        ctx.controls().stopMoving();
                                        ctx.controls().setSprinting(false);
                                        return true;
                                    }
                                }
                            } else {
                                return repeatBail("no")
                                        ? false
                                        : bail("no rail(" + railSlot + ") or cart(" + cartSlot + ")");
                            }
                        }
                    }
                }
            }
        } else {
            return repeatBail("range") ? false : bail("range " + String.format("%.1f", dist));
        }
    }

    private void backAwayOnceArmed() {
        if (spawnedCart != null) {
            if (phaseTick > lastPlacementTick(activeType)) {
                ctx.controls().moveBackward();
            }
        }
    }

    static int lastPlacementTick(BotSettings.CartMode mode) {
        return mode == BotSettings.CartMode.XBOW_CART ? 9 : 0;
    }

    /** Flame-bow carts (every mode but XBOW_CART) wait this long after the last one; a player does not chain bow shots. */
    static boolean bowCooling(BotSettings.CartMode mode, int now, int lastBowTick, int cooldown) {
        return mode != BotSettings.CartMode.XBOW_CART && now - lastBowTick < cooldown;
    }

    private int cartCooldown() {
        return Math.max(1, ctx.difficulty().cartCooldownTicks);
    }

    private boolean skipThisCart() {
        int skip = ctx.difficulty().cartSkipChance;
        return skip > 0 && ctx.random().nextInt(100) < skip;
    }

    private void defendAgainstIncomingCart() {
        if (defenceCooldown <= 0) {
            ExplosiveMinecart threat = null;
            double range = ctx.tuning().number(TuningKey.CART_INCOMING_RANGE).doubleValue();
            double closest = range;

            for (ExplosiveMinecart cart :
                    ctx.world().entities(ExplosiveMinecart.class, Ent.box(bot).inflate(range), e -> true)) {
                if (!cart.equals(spawnedCart) && Ent.alive(cart)) {
                    double d = Ent.dist(cart, bot);
                    if (!(d > closest)) {
                        Vec3 toBot = Ent.pos(bot).subtract(Ent.pos(cart));
                        Vec3 vel = Ent.velocity(cart);
                        if (!(vel.horizontalDistance() < 0.05) && !(vel.x() * toBot.x() + vel.z() * toBot.z() <= 0.0)) {
                            closest = d;
                            threat = cart;
                        }
                    }
                }
            }

            if (threat == null) {
                defenceCooldown = ctx.tuning().integer(TuningKey.CART_DEFENCE_IDLE_COOLDOWN);
            } else if (phase == Phase.EXECUTING) {
                defenceCooldown = ctx.tuning().integer(TuningKey.CART_DEFENCE_BUSY_COOLDOWN);
            } else {
                Vec3 dir = Ent.velocity(threat).normalize();
                int blockSlot = findSolidBlockInInventory();
                if (blockSlot >= 0) {
                    for (double ahead = 1.0; ahead <= 2.0; ahead++) {
                        Vec3 p = Ent.pos(threat).add(dir.scale(ahead));
                        BlockPos at = BlockPos.containing(p.x(), p.y(), p.z());
                        if (ctx.world().isAir(at) && !(Ent.pos(bot).distanceTo(Vec3.atCenterOf(at)) > 4.5)) {
                            boolean placed = placeSolidBlock(blockSlot, at);
                            if (placed) {
                                defenceCooldown = ctx.tuning().integer(TuningKey.CART_DEFENCE_PLACED_COOLDOWN);
                                return;
                            }
                        }
                    }
                }

                ctx.controls().moveBackward();
                defenceCooldown = ctx.tuning().integer(TuningKey.CART_DEFENCE_BACKOFF_COOLDOWN);
            }
        }
    }

    private int findSolidBlockInInventory() {
        for (int i = 0; i < 36; i++) {
            ItemStack st = bot.getInventory().getItem(i);
            if (!ItemKinds.isEmpty(st)
                    && !isRailStack(st)
                    && st.getType() != Material.TNT_MINECART
                    && st.getType().isBlock()
                    && st.getType().isOccluding()) {
                return i;
            }
        }

        return -1;
    }

    private boolean placeSolidBlock(int slot, BlockPos at) {
        slot = moveToHotbar(slot);
        InventoryUtils.select(ctx, slot);
        Vec3 hitVec = Vec3.atCenterOf(at);
        lookAt(hitVec);
        boolean result = ctx.hands().useItemOn(EquipmentSlot.HAND, at.below(), Direction.UP, hitVec);
        swingForPlacement(result);
        return result;
    }

    private boolean tickExecuting(LivingEntity target) {
        phaseTick++;
        if (phaseTick <= 102 && target != null && Ent.alive(target)) {
            backAwayOnceArmed();

            return switch (activeType) {
                case XBOW_CART -> execXbowCart(target);
                case PRE_CART -> execPreCart(target);
                case PRE_RAIL -> execPreRail(target);
                case INSTA_CART -> execInstaCart(target);
                case SMART -> {
                    abort();
                    yield false;
                }
            };
        } else {
            abort();
            return false;
        }
    }

    private boolean execPreCart(LivingEntity target) {
        int railPlace = 2;
        int cartEquip = 3;
        int cartPlace = 4;
        int switchTick = 5;
        int drawStart = 6;
        int release = drawStart + 7;
        int done = release + 5;
        if (phaseTick == 1) {
            equipRail();
            aimAtBlock(railPos);
        } else if (phaseTick == railPlace) {
            aimAtBlock(railPos);
            placeRail();
        } else if (phaseTick == cartEquip) {
            equipCart();
            aimAtBlock(railPos);
        } else if (phaseTick == cartPlace) {
            aimAtBlock(railPos);
            spawnCartEntity();
        } else if (phaseTick == switchTick) {
            InventoryUtils.select(ctx, flameBowSlot);
            aimAtCartOrRail(target);
        } else if (phaseTick == drawStart) {
            aimAtCartOrRail(target);
            startBowDraw();
        } else if (phaseTick > drawStart && phaseTick < release) {
            aimAtCartOrRail(target);
        } else if (phaseTick == release) {
            aimAtCartOrRail(target);
            releaseBow();
        } else if (phaseTick == done) {
            finish();
        }

        return true;
    }

    private boolean execPreRail(LivingEntity target) {
        int railPlace = 2;
        int switchTick = 3;
        int drawStart = 4;
        int release = drawStart + 7;
        int cartEquip = release + 1;
        int cartTick = release + 2;
        int done = release + 6;
        if (phaseTick == 1) {
            equipRail();
            aimAtBlock(railPos);
        } else if (phaseTick == railPlace) {
            aimAtBlock(railPos);
            placeRail();
        } else if (phaseTick == switchTick) {
            InventoryUtils.select(ctx, flameBowSlot);
            aimAtBlock(railPos);
        } else if (phaseTick == drawStart) {
            aimAtBlock(railPos);
            startBowDraw();
        } else if (phaseTick > drawStart && phaseTick < release) {
            aimAtBlock(railPos);
        } else if (phaseTick == release) {
            aimAtBlock(railPos);
            releaseBow();
        } else if (phaseTick == cartEquip) {
            equipCart();
            aimAtBlock(railPos);
        } else if (phaseTick == cartTick) {
            spawnCartEntity();
        } else if (phaseTick == done) {
            finish();
        }

        return true;
    }

    static int instaDrawTicks(double distance) {
        int ticks = (int) Math.round(4.0 + distance * 1.1);
        return Math.max(4, Math.min(12, ticks));
    }

    private int instaDrawTicks() {
        return instaDrawTicks(Ent.pos(bot).distanceTo(Vec3.atCenterOf(railPos)));
    }

    private boolean execInstaCart(LivingEntity target) {
        int drawStart = 2;
        int release = drawStart + instaDrawTicks();
        int railEquip = release + 1;
        int railPlace = release + 2;
        int cartEquip = release + 3;
        int cartPlace = release + 4;
        int done = release + 8;
        if (phaseTick == 1) {
            InventoryUtils.select(ctx, flameBowSlot);
            aimAtCartOrRail(target);
        } else if (phaseTick == drawStart) {
            aimAtCartOrRail(target);
            startBowDraw();
        } else if (phaseTick > drawStart && phaseTick < release) {
            aimAtCartOrRail(target);
        } else if (phaseTick == release) {
            aimAtCartOrRail(target);
            releaseBow();
        } else if (!instaRecovery && phaseTick == railEquip) {
            equipRail();
            aimAtBlock(railPos);
        } else if (!instaRecovery && phaseTick == railPlace) {
            aimAtBlock(railPos);
            placeRail();
        } else if (!instaRecovery && phaseTick == cartEquip) {
            equipCart();
            aimAtBlock(railPos);
        } else if (!instaRecovery && phaseTick == cartPlace) {
            aimAtBlock(railPos);
            spawnCartEntity();
        } else if (phaseTick == done) {
            if (!instaRetried
                    && spawnedCart != null
                    && Ent.alive(spawnedCart)
                    && !spawnedCart.isIgnited()
                    && flameBowSlot >= 0
                    && hasArrows()) {
                instaRetried = true;
                phaseTick = 0;
                instaRecovery = true;
                return true;
            }

            finish();
        } else if (instaRecovery && phaseTick == 1) {
            InventoryUtils.select(ctx, flameBowSlot);
            aimAtCartOrRail(target);
        }

        return true;
    }

    private boolean execXbowCart(LivingEntity target) {
        if (phaseTick == 1) {
            equipRail();
            aimAtBlock(railPos);
        } else if (phaseTick == 2) {
            aimAtBlock(railPos);
            placeRail();
        } else if (phaseTick == 3) {
            if (!isRailAt(railPos)) {
                placeRail();
            }
        } else if (phaseTick == 4) {
            equipCart();
            aimAtBlock(railPos);
        } else if (phaseTick == 5) {
            aimAtBlock(railPos);
            spawnCartEntity();
        } else if (phaseTick == 6) {
            refreshSpawnedCart();
        } else if (phaseTick == 8) {
            equipFlint();
            firePos = findFirePosAlongShotPath(cartLowerAimPoint());
            aimAtBlock(firePos != null ? firePos : railPos);
        } else if (phaseTick == 9) {
            firePos = findFirePosAlongShotPath(cartLowerAimPoint());
            aimAtBlock(firePos != null ? firePos : railPos);
            placeFire();
        } else if (phaseTick == 10) {
            if (!isFireAt(firePos)) {
                firePos = findFirePosAlongShotPath(cartLowerAimPoint());
                placeFire();
            }
        } else if (phaseTick >= 12) {
            tickXbowCartCrossbow(target);
        }

        return true;
    }

    private void tickXbowCartCrossbow(LivingEntity target) {
        if (crossbowSlot < 0) {
            ctx.log().decision("CartController", CROSSBOW_MISSING, "[Cart] xbow cart: no crossbow slot", true);
            abort();
        } else {
            crossbowSlot = moveToHotbar(crossbowSlot);
            InventoryUtils.select(ctx, crossbowSlot);
            aimAtCartLowerHalf();
            if (xbowFired) {
                xbowPostFireWait++;
                if (xbowPostFireWait >= 8) {
                    finish();
                }
            } else {
                ItemStack held = bot.getInventory().getItemInMainHand();
                if (!ItemKinds.is(held, Material.CROSSBOW)) {
                    ctx.log().decision("CartController", CROSSBOW_WRONG_ITEM, "[Cart] xbow cart: expected crossbow in hand, got " + held.getType(), true);
                    abort();
                } else if (ItemKinds.isCrossbowCharged(held)) {
                    if (isFireAt(firePos) && fireIsOnShotLine(Ent.eyePos(bot), cartLowerAimPoint(), firePos)) {
                        aimAtCartLowerHalf();
                        fireCrossbow();
                        xbowFired = true;
                        lastFiredCrossbowSlot = crossbowSlot;
                        xbowPostFireWait = 0;
                    } else {
                        if (!xbowFireRetried) {
                            xbowFireRetried = true;
                            firePos = findFirePosAlongShotPath(cartLowerAimPoint());
                            if (firePos != null) {
                                equipFlint();
                                aimAtBlock(firePos);
                                placeFire();
                                return;
                            }
                        }

                        ctx.log().decision("CartController", CROSSBOW_FLAME_MISSING, "[Cart] xbow cart: no flame on the shot line - holding fire "
                                + "(a plain arrow would destroy the cart)", true);
                        abort();
                    }
                } else {
                    if (xbowCharging && !bot.isHandRaised()) {
                        xbowCharging = false;
                        xbowChargeTicks = 0;
                    }

                    if (!xbowCharging) {
                        ctx.hands().useItem(EquipmentSlot.HAND);
                        xbowCharging = true;
                        xbowChargeTicks = 0;
                    }

                    xbowChargeTicks++;
                    if (xbowChargeTicks >= 30) {
                        ctx.hands().releaseUseItem();
                        xbowCharging = false;
                        xbowChargeTicks = 0;
                    }
                }
            }
        }
    }

    private void clearXbowCartState() {
        xbowFired = false;
        xbowPostFireWait = 0;
        xbowCharging = false;
        xbowChargeTicks = 0;
        xbowFireRetried = false;
    }

    private void equipRail() {
        if (railSlot >= 0) {
            railSlot = moveToHotbar(railSlot);
            InventoryUtils.select(ctx, railSlot);
        }
    }

    private void equipCart() {
        if (cartSlot >= 0) {
            if (countHotbar(Material.TNT_MINECART) == 0 && countInInventoryOf(Material.TOTEM_OF_UNDYING) == 0) {
                offhandCart = true;
            } else {
                cartSlot = moveToHotbar(cartSlot);
                InventoryUtils.select(ctx, cartSlot);
            }
        }
    }

    private void equipFlint() {
        if (flintSlot >= 0) {
            flintSlot = moveToHotbar(flintSlot);
            InventoryUtils.select(ctx, flintSlot);
        }
    }

    void ensureCartHotbar() {
        if (phase == Phase.IDLE) {
            if (ctx.settings().useCarting()) {
                if (countHotbar(Material.TNT_MINECART) < 2) {
                    hoistToHotbar(findInBag(Material.TNT_MINECART));
                }

                if (countHotbarRails() < 1) {
                    hoistToHotbar(findRailInInventory());
                }
            }
        }
    }

    private int countHotbar(Material item) {
        int n = 0;

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), item)) {
                n++;
            }
        }

        return n;
    }

    private int countHotbarRails() {
        int n = 0;

        for (int i = 0; i < 9; i++) {
            if (isRailStack(bot.getInventory().getItem(i))) {
                n++;
            }
        }

        return n;
    }

    private void hoistToHotbar(int slot) {
        if (slot >= 9) {
            int dest = findHotbarDest(true);
            if (dest >= 0) {
                swapIntoHotbar(slot, dest);
            }
        }
    }

    private int moveToHotbar(int slot) {
        if (slot < 9) {
            return slot;
        } else {
            if (CartDoctrine.isGuideDoctrine(ctx)) {
                pendingGrabDelay = ctx.tuning().integer(TuningKey.CART_GRAB_DELAY_TICKS);
            }

            return swapIntoHotbar(slot, findHotbarDest(false));
        }
    }

    private int swapIntoHotbar(int slot, int dest) {
        InventoryUtils.swap(ctx, slot, dest);
        return dest;
    }

    private int findHotbarDest(boolean strict) {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.isEmpty(inv.getItem(i))) {
                return i;
            }
        }

        for (int i = 8; i >= 0; i--) {
            if (!isSequenceSlot(i)) {
                ItemStack s = inv.getItem(i);
                if (!InventoryUtils.isProtected(s)
                        && !isRailStack(s)
                        && !ItemKinds.is(s, Material.TNT_MINECART)
                        && !ItemKinds.is(s, Material.FLINT_AND_STEEL)) {
                    return i;
                }
            }
        }

        if (strict) {
            return -1;
        } else {
            for (int i = 8; i >= 0; i--) {
                if (!isSequenceSlot(i)) {
                    return i;
                }
            }

            return 8;
        }
    }

    private boolean isSequenceSlot(int i) {
        return i == railSlot || i == cartSlot || i == flameBowSlot || i == crossbowSlot || i == flintSlot || i == prevSlot;
    }

    private void placeRail() {
        if (canPlaceRail(railPos)) {
            if (!(Ent.pos(bot).distanceTo(Vec3.atCenterOf(railPos)) > 4.5)) {
                railSlot = moveToHotbar(railSlot);
                InventoryUtils.select(ctx, railSlot);
                ItemStack railStack = bot.getInventory().getItem(railSlot);
                if (isRailStack(railStack)) {
                    Vec3 hitVec = new Vec3(railPos.getX() + 0.5, railPos.getY(), railPos.getZ() + 0.5);
                    lookAt(hitVec);
                    boolean result = ctx.hands().useItemOn(EquipmentSlot.HAND, railPos.below(), Direction.UP, hitVec);
                    swingForPlacement(result);
                }
            }
        }
    }

    private boolean tryOffhandCart(Vec3 hitVec) {
        if (ItemKinds.is(bot.getInventory().getItemInOffHand(), Material.TOTEM_OF_UNDYING)) {
            return false;
        } else if (countInInventoryOf(Material.TOTEM_OF_UNDYING) > 0) {
            return false;
        } else {
            int source = findInInventory(Material.TNT_MINECART);
            if (source < 0) {
                return false;
            } else if (!offhand.park(source, 20)) {
                return false;
            } else {
                ItemStack off = bot.getInventory().getItemInOffHand();
                if (!ItemKinds.is(off, Material.TNT_MINECART)) {
                    offhand.unpark();
                    return false;
                } else {
                    lookAt(hitVec);
                    boolean r = ctx.hands().useItemOn(EquipmentSlot.OFF_HAND, railPos, Direction.UP, hitVec);
                    swingForPlacement(r);
                    offhand.unpark();
                    List<ExplosiveMinecart> near = ctx.world()
                            .entities(ExplosiveMinecart.class, AABB.ofBlock(railPos).inflate(1.0), e -> true);
                    if (!near.isEmpty()) {
                        spawnedCart = near.get(0);
                    }

                    rememberOwnCart(spawnedCart);
                    return spawnedCart != null;
                }
            }
        }
    }

    private int countInInventoryOf(Material item) {
        int n = 0;

        for (int i = 0; i < 41; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), item)) {
                n++;
            }
        }

        return n;
    }

    private void spawnCartEntity() {
        if (isRailBlock(railPos)) {
            Vec3 hitVec = new Vec3(railPos.getX() + 0.5, railPos.getY() + 0.0625, railPos.getZ() + 0.5);
            if (!offhandCart || !tryOffhandCart(hitVec)) {
                cartSlot = moveToHotbar(cartSlot);
                InventoryUtils.select(ctx, cartSlot);
                ItemStack cartStack = bot.getInventory().getItem(cartSlot);
                if (ItemKinds.is(cartStack, Material.TNT_MINECART)) {
                    lookAt(hitVec);
                    boolean result = ctx.hands().useItemOn(EquipmentSlot.HAND, railPos, Direction.UP, hitVec);
                    swingForPlacement(result);
                    List<ExplosiveMinecart> nearby = ctx.world()
                            .entities(ExplosiveMinecart.class, AABB.ofBlock(railPos).inflate(1.0), e -> true);
                    if (!nearby.isEmpty()) {
                        spawnedCart = nearby.get(0);
                    }

                    rememberOwnCart(spawnedCart);
                }
            }
        }
    }

    private void rememberOwnCart(ExplosiveMinecart cart) {
        if (cart != null && !ownCarts.contains(cart.getUniqueId())) {
            ownCarts.addLast(cart.getUniqueId());
            if (ownCarts.size() > 8) {
                ownCarts.removeFirst();
            }
        }
    }

    private void placeFire() {
        if (firePos != null && flintSlot >= 0) {
            if (canPlaceFire(firePos)) {
                if (!(Ent.pos(bot).distanceTo(Vec3.atCenterOf(firePos)) > 4.5)) {
                    flintSlot = moveToHotbar(flintSlot);
                    InventoryUtils.select(ctx, flintSlot);
                    ItemStack flintStack = bot.getInventory().getItem(flintSlot);
                    if (ItemKinds.is(flintStack, Material.FLINT_AND_STEEL)) {
                        Vec3 hitVec = new Vec3(firePos.getX() + 0.5, firePos.getY(), firePos.getZ() + 0.5);
                        lookAt(hitVec);
                        boolean result = ctx.hands().useItemOn(EquipmentSlot.HAND, firePos.below(), Direction.UP, hitVec);
                        swingForPlacement(result);
                    }
                }
            }
        }
    }

    private void swingForPlacement(boolean consumed) {
        if (consumed) {
            ctx.hands().swing(EquipmentSlot.HAND);
        }
    }

    private void startBowDraw() {
        ItemStack held = bot.getInventory().getItemInMainHand();
        if (!ItemKinds.is(held, Material.BOW)) {
            ctx.log().decision("CartController", BOW_WRONG_ITEM, "[Cart] tried to draw but held item is " + held.getType() + " (slot "
                    + InventoryUtils.selected(ctx) + ")", true);
        } else {
            ctx.hands().useItem(EquipmentSlot.HAND);
            isDrawing = true;
            ctx.log().decision("CartController", BOW_DRAW_START, "[Cart] started bow draw (slot " + InventoryUtils.selected(ctx) + ")");
        }
    }

    private void releaseBow() {
        if (isDrawing) {
            int useTicks = bot.getActiveItemUsedTime();
            ctx.log().decision("CartController", BOW_RELEASE, "[Cart] releasing bow after " + useTicks + " use-ticks");
            ctx.hands().releaseUseItem();
            isDrawing = false;
        }
    }

    private void fireCrossbow() {
        CrossbowHelper.fireIfCharged(ctx);
    }

    private void aimAt(double x, double y, double z) {
        Vec3 eye = Ent.eyePos(bot);
        double dx = x - eye.x();
        double dy = y - eye.y();
        double dz = z - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        ctx.controls().look(BotMath.smoothBodyYaw(bot.getLocation().getYaw(), yaw), pitch);
    }

    private void aimAtBlock(BlockPos pos) {
        aimAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private void aimAtCartOrRail(LivingEntity target) {
        if (spawnedCart != null && Ent.alive(spawnedCart)) {
            Vec3 c = Ent.pos(spawnedCart);
            aimAt(c.x(), c.y() + 0.35, c.z());
        } else {
            aimAt(railPos.getX() + 0.5, railPos.getY() + 0.3, railPos.getZ() + 0.5);
        }
    }

    private void aimAtCartLowerHalf() {
        Vec3 p = cartLowerAimPoint();
        aimAt(p.x(), p.y(), p.z());
    }

    private Vec3 cartLowerAimPoint() {
        if (spawnedCart != null && Ent.alive(spawnedCart)) {
            double h = spawnedCart.getHeight();
            Vec3 c = Ent.pos(spawnedCart);
            return new Vec3(c.x(), c.y() + h * 0.25, c.z());
        } else {
            return Vec3.atCenterOf(railPos).add(0.0, 0.15, 0.0);
        }
    }

    private void refreshSpawnedCart() {
        if (railPos != null) {
            List<ExplosiveMinecart> nearby = ctx.world()
                    .entities(ExplosiveMinecart.class, AABB.ofBlock(railPos).inflate(1.5), e -> true);
            if (!nearby.isEmpty()) {
                spawnedCart = nearby.get(0);
            }

            rememberOwnCart(spawnedCart);
        }
    }

    private boolean isRailAt(BlockPos pos) {
        return pos != null && isRailBlock(pos);
    }

    private boolean isRailBlock(BlockPos pos) {
        return Tag.RAILS.isTagged(ctx.world().type(pos));
    }

    static boolean isRailMaterial(Material type) {
        return type == Material.RAIL
                || type == Material.POWERED_RAIL
                || type == Material.DETECTOR_RAIL
                || type == Material.ACTIVATOR_RAIL;
    }

    private static boolean isRailStack(ItemStack stack) {
        return !ItemKinds.isEmpty(stack) && isRailMaterial(stack.getType());
    }

    private boolean isFireAt(BlockPos pos) {
        if (pos == null) {
            return false;
        } else {
            Material type = ctx.world().type(pos);
            return type == Material.FIRE || type == Material.SOUL_FIRE;
        }
    }

    private void finish() {
        if (isDrawing) {
            ctx.hands().releaseUseItem();
            isDrawing = false;
        }

        if (xbowCharging && bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        clearXbowCartState();
        InventoryUtils.select(ctx, prevSlot);
        phase = Phase.IDLE;
        cooldownLeft = cartCooldown() + pendingGrabDelay;
        pendingGrabDelay = 0;
        spawnedCart = null;
        instaRetried = false;
        instaRecovery = false;
        offhandCart = false;
    }

    private void abort() {
        if (isDrawing) {
            ctx.hands().releaseUseItem();
            isDrawing = false;
        }

        if (xbowCharging && bot.isHandRaised()) {
            ctx.hands().releaseUseItem();
        }

        clearXbowCartState();
        InventoryUtils.select(ctx, prevSlot);
        phase = Phase.IDLE;
        cooldownLeft = cartCooldown() / 2;
        spawnedCart = null;
        instaRetried = false;
        instaRecovery = false;
        offhandCart = false;
    }

    private int findInInventory(Material item) {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), item)) {
                return i;
            }
        }

        return -1;
    }

    private int findInBag(Material item) {
        for (int i = 9; i < 36; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), item)) {
                return i;
            }
        }

        return -1;
    }

    private int findRailInInventory() {
        for (int i = 0; i < 36; i++) {
            if (isRailStack(bot.getInventory().getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private int findFlameBowInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (ItemKinds.is(s, Material.BOW) && hasEnchant(s, "flame")) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (ItemKinds.is(s, Material.BOW) && hasEnchant(s, "flame")) {
                return swapIntoHotbar(i, findHotbarDest(false));
            }
        }

        return -1;
    }

    private int findCrossbowForCart() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (ItemKinds.is(s, Material.CROSSBOW)) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (ItemKinds.is(s, Material.CROSSBOW)) {
                return swapIntoHotbar(i, findHotbarDest(false));
            }
        }

        return -1;
    }

    private boolean hasAnyCrossbow() {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), Material.CROSSBOW)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasArrows() {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isArrow(bot.getInventory().getItem(i))) {
                return true;
            }
        }

        return ItemKinds.isArrow(bot.getInventory().getItem(40));
    }

    private boolean hasEnchant(ItemStack stack, String enchantPath) {
        return EnchantmentUtils.getEnchantLevel(stack, enchantPath) > 0;
    }

    private boolean hasItemsForType(BotSettings.CartMode type) {
        return switch (type) {
            case XBOW_CART -> crossbowSlot >= 0 && flintSlot >= 0 && hasArrows();
            case PRE_CART, PRE_RAIL, INSTA_CART -> flameBowSlot >= 0 && hasArrows();
            case SMART -> false;
        };
    }

    /** Distance gate of a rail spot: near the target, outside the bot's blast radius, inside placement reach. */
    static boolean placementInRange(BotTuning.Profile profile, double toTarget, double toBot, double selfMin) {
        return !(toTarget > profile.number(TuningKey.CART_TARGET_MAX).doubleValue())
                && !(toBot < selfMin)
                && !(toBot > VANILLA_PLACE_REACH);
    }

    private BlockPos findPlacementPos(LivingEntity target) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        BlockPos centre = BlockPos.containing(targetPos.x(), targetPos.y(), targetPos.z());
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = centre.offset(dx, dy, dz);
                    if (canPlaceRail(p)) {
                        Vec3 c = Vec3.atCenterOf(p);
                        double toTarget = targetPos.distanceTo(c);
                        double toBot = botPos.distanceTo(c);
                        if (placementInRange(ctx.tuning(), toTarget, toBot, cartSelfMin())
                                && canSee(Ent.eyePos(bot), c)
                                && canSee(c, targetPos.add(0.0, target.getHeight() * 0.5, 0.0))
                                && toTarget < bestDist) {
                            bestDist = toTarget;
                            best = p;
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean canSee(Vec3 from, Vec3 to) {
        BlockHit hit = ctx.world().clip(from, to, false, false);
        return !hit.hit();
    }

    private BlockPos findFirePosAlongShotPath(Vec3 cartLower) {
        if (railPos != null && cartLower != null) {
            Vec3 eye = Ent.eyePos(bot);
            Vec3 delta = cartLower.subtract(eye);
            double len = delta.length();
            if (len < 1.0) {
                return null;
            } else {
                Vec3 dir = delta.scale(1.0 / len);
                BlockPos last = null;

                for (double dist = len - 0.6; dist >= 1.2; dist -= 0.25) {
                    Vec3 sample = eye.add(dir.scale(dist));
                    BlockPos pos = BlockPos.containing(sample.x(), sample.y(), sample.z());
                    if (!pos.equals(last)) {
                        last = pos;
                        if (!pos.equals(railPos)
                                && !pos.equals(railPos.above())
                                && !(Ent.pos(bot).distanceTo(Vec3.atCenterOf(pos)) > 4.25)
                                && canPlaceFire(pos)) {
                            return pos;
                        }
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    static boolean fireIsOnShotLine(Vec3 eye, Vec3 cartLower, BlockPos pos) {
        if (pos != null && cartLower != null) {
            Vec3 delta = cartLower.subtract(eye);
            double len = delta.length();
            if (len < 0.001) {
                return false;
            } else {
                Vec3 dir = delta.scale(1.0 / len);

                for (double dist = 0.5; dist <= len; dist += 0.2) {
                    Vec3 sample = eye.add(dir.scale(dist));
                    if (BlockPos.containing(sample.x(), sample.y(), sample.z()).equals(pos)) {
                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private BotSettings.CartMode pickSmartMode(LivingEntity target, double dist) {
        boolean expoReady = crossbowSlot >= 0 && flintSlot >= 0 && hasArrows() && hasChargedCrossbowInHotbar();
        boolean bowReady = flameBowSlot >= 0 && hasArrows();
        if (!expoReady) {
            return BotSettings.CartMode.INSTA_CART;
        } else if (!bowReady) {
            return BotSettings.CartMode.XBOW_CART;
        } else if (target.getHealth() <= ctx.tuning().number(TuningKey.CART_SMART_INSTA_HP).floatValue()) {
            return BotSettings.CartMode.INSTA_CART;
        } else {
            return !(dist >= 9.0) && !sprintingStraightIn(target)
                    ? BotSettings.CartMode.XBOW_CART
                    : BotSettings.CartMode.XBOW_CART;
        }
    }

    private int findChargedCrossbowExcluding(int exclude) {
        for (int i = 0; i < 9; i++) {
            if (i != exclude) {
                ItemStack st = bot.getInventory().getItem(i);
                if (ItemKinds.is(st, Material.CROSSBOW) && ItemKinds.isCrossbowCharged(st)) {
                    return i;
                }
            }
        }

        return -1;
    }

    private boolean hasChargedCrossbowInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (ItemKinds.is(s, Material.CROSSBOW) && ItemKinds.isCrossbowCharged(s)) {
                return true;
            }
        }

        return false;
    }

    private boolean sprintingStraightIn(LivingEntity target) {
        if (!(target instanceof Player tp) || !tp.isSprinting()) {
            return false;
        } else {
            Vec3 v = ctx.match().velocityOf(target);
            double dx = Ent.pos(bot).x() - Ent.pos(target).x();
            double dz = Ent.pos(bot).z() - Ent.pos(target).z();
            double h = Math.hypot(dx, dz);
            return h < 0.001 ? false : v.x() * dx / h + v.z() * dz / h > 0.15;
        }
    }

    private boolean canPlaceRail(BlockPos pos) {
        if (!ctx.world().isAir(pos)) {
            return false;
        } else {
            return ctx.world().isFullBlock(pos.below());
        }
    }

    private boolean canPlaceFire(BlockPos pos) {
        if (!ctx.world().isAir(pos)) {
            return false;
        } else {
            return ctx.world().isFullBlock(pos.below());
        }
    }

    private void lookAt(Vec3 pos) {
        aimAt(pos.x(), pos.y(), pos.z());
    }

    private enum Phase {
        IDLE,
        EXECUTING
    }
}
