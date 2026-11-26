package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.ai.move.PathFollower;
import dev.nandi0813.zppbots.ai.ranged.EnderPearlController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.ElytraFlight;
import dev.nandi0813.zppbots.util.EnchantmentUtils;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.WebPlacer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public final class RestockController {

    private static final String RESUPPLY_NO_SOURCE = "RESUPPLY_NO_SOURCE";
    private static final String RESUPPLY_FAILED = "RESUPPLY_FAILED";
    private static final String RESUPPLY_PEARL_AWAY = "RESUPPLY_PEARL_AWAY";
    private static final String RESUPPLY_FLY_AWAY = "RESUPPLY_FLY_AWAY";
    private static final String RESUPPLY_HOLD_THREAT_CLOSE = "RESUPPLY_HOLD_THREAT_CLOSE";
    private static final String RESUPPLY_IN_PLACE = "RESUPPLY_IN_PLACE";
    private static final String PACK_SURPLUS_START = "PACK_SURPLUS_START";
    private static final String PACK_SURPLUS_DONE = "PACK_SURPLUS_DONE";
    private static final String DISCARD_JUNK = "DISCARD_JUNK";
    private static final String RESUPPLY_ABORT_THREAT = "RESUPPLY_ABORT_THREAT";
    private static final String RESUPPLY_PEARL_AGAIN = "RESUPPLY_PEARL_AGAIN";
    private static final String RESUPPLY_ESCAPE_FAILED = "RESUPPLY_ESCAPE_FAILED";
    private static final String RECOVER_SHULKER = "RECOVER_SHULKER";
    private static final String OPEN_NEXT_SHULKER = "OPEN_NEXT_SHULKER";
    private static final String RECOVER_ENDER_CHEST = "RECOVER_ENDER_CHEST";
    private static final String SHULKER_PLACE_BLOCKED = "SHULKER_PLACE_BLOCKED";
    private static final String PLACE_SHULKER = "PLACE_SHULKER";
    private static final String EXTRACT_SUMMARY = "EXTRACT_SUMMARY";
    private static final String ARMOR_UPGRADE_AVAILABLE = "ARMOR_UPGRADE_AVAILABLE";
    private static final String ENDER_CHEST_PLACE_BLOCKED = "ENDER_CHEST_PLACE_BLOCKED";
    private static final String PLACE_ENDER_CHEST = "PLACE_ENDER_CHEST";
    private static final String TAKE_SHULKER = "TAKE_SHULKER";
    private static final String TAKE_LOOSE_STACKS = "TAKE_LOOSE_STACKS";
    private static final String STOW_SHULKER = "STOW_SHULKER";

    private static final int CHECK_INTERVAL = 10;
    private static final int SHULKER_SLOTS = 27;
    private static final int PEARL_MAX_WAIT = 60;
    private static final double PEARL_LANDED_DIST = 4.0;
    private static final int OPEN_TICKS = 60;
    private static final int EXTRACT_EVERY = 5;
    private static final int MINE_TIMEOUT = 120;
    private static final int COLLECT_TICKS = 20;
    private static final int ELYTRA_LAUNCH_TICKS = 20;
    private static final int GROUND_SCAN_DEPTH = 5;
    private static final int ENDER_OPEN_TICKS = 8;
    private static final int OPEN_RETRY_TICKS = 4;
    private static final int OPEN_GIVE_UP_TICKS = 24;
    private static final int OPEN_RETRY_WINDOW = 20;

    private final BotContext ctx;
    private final Player bot;
    private final EnderPearlController pearl;
    private final InvisibleSuitUp suitUp;
    private final RestockPlan plan;
    private final PlacedBoxClaims claims;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int cooldown = 0;
    private int checkTick = 0;
    private Vec3 pearlOrigin = null;
    private BlockPos placedPos = null;
    private List<Supply> activeMissing = null;
    private int slotBeforeMine = -1;
    private boolean looted = false;
    private boolean extractDumped = false;
    private int extractedTotal = 0;
    private int missedTotal = 0;
    private int dumpedTotal = 0;
    private int equippedTotal = 0;
    private BlockPos minedPos = null;
    private BlockPos enderPos = null;
    private int enderSlotUsed = -1;
    private final boolean[] everStocked;
    private final Set<String> warnedNoSource = new HashSet<>();
    private final List<Supply> supplies;
    private int elytraStashSlot = -1;
    private int boxesOpened = 0;
    private boolean packMode = false;
    private Phase pendingFirst = Phase.PLACE;
    private int escapeHops = 0;
    private final ItemStack[] checkStacks = new ItemStack[36];
    private final Material[] tallyItems = new Material[37];
    private final int[] tallyCounts = new int[37];
    private int tallySize = -1;
    private double lastUpgradeWornScore = Double.NaN;
    private int returnedBoxSlot = -1;
    private final boolean[] boxSlotsBefore = new boolean[36];
    // ender chest content is only readable once opened, so remember which supplies it could not give
    private final Set<String> enderBarren = new HashSet<>();

    public RestockController(BotContext ctx, EnderPearlController pearl, InvisibleSuitUp suitUp, RestockPlan plan, PlacedBoxClaims claims) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.pearl = pearl;
        this.suitUp = suitUp;
        this.plan = plan;
        this.claims = claims;
        this.supplies = buildSupplies();
        this.everStocked = new boolean[supplies.size()];
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public boolean tick(LivingEntity threat) {
        try {
            if (phase != Phase.IDLE) {
                return tickRun(threat);
            } else {
                updateBaseline();
                if (cooldown > 0 && threat != null && Ent.alive(threat) && opponentDisengaged(threat)) {
                    cooldown = 0;
                }

                if (cooldown > 0) {
                    cooldown--;
                    return false;
                } else if (++checkTick < CHECK_INTERVAL) {
                    return false;
                } else {
                    checkTick = 0;
                    List<Supply> low = currentLow();
                    warnedNoSource.retainAll(keySet(low));
                    boolean boxRegear = ctx.settings().shulkerRegear();
                    if (low.isEmpty()) {
                        if (!boxRegear) {
                            return false;
                        } else {
                            List<Supply> upgrade = shulkerUpgrade();
                            if (upgrade.isEmpty()) {
                                return false;
                            } else {
                                activeMissing = upgrade;
                                boolean started = beginRun(threat, false);
                                if (started) {
                                    lastUpgradeWornScore = wornArmorScore();
                                }

                                return started;
                            }
                        }
                    } else if (boxRegear && findShulkerSlot(low) >= 0) {
                        activeMissing = low;
                        lastUpgradeWornScore = Double.NaN;
                        return beginRun(threat, false);
                    } else if (!boxRegear
                            || !enderRegearEnabled()
                            || findEnderChestSlot() < 0
                            || enderBarren.containsAll(keySet(low))) {
                        for (Supply s : low) {
                            if (warnedNoSource.add(s.key())) {
                                ctx.log().decision("RestockController", RESUPPLY_NO_SOURCE, "[Restock] " + ctx.name() + " is low on '" + s.key() + "' but no carried shulker contains it");
                            }
                        }

                        return false;
                    } else {
                        activeMissing = low;
                        lastUpgradeWornScore = Double.NaN;
                        return beginRun(threat, true);
                    }
                }
            }
        } catch (Exception e) {
            ctx.log().decision("RestockController", RESUPPLY_FAILED, "[Restock] " + ctx.name() + " regear failed - aborting run: " + e, true);

            try {
                reset();
            } catch (Exception ignored) {
            }

            return false;
        }
    }

    public boolean tick() {
        return tick(null);
    }

    /** Aborts the run. A box or ender chest that is still placed stays in the world: blocks are only edited through the body's hands. */
    public void reset() {
        ctx.controls().stopMining();
        closeView();
        restoreSlotAfterMining();
        boxesOpened = 0;
        packMode = false;
        phase = Phase.IDLE;
        phaseTick = 0;
        pearlOrigin = null;
        releasePlacedClaim();
        placedPos = null;
        enderPos = null;
        enderSlotUsed = -1;
        activeMissing = null;
        minedPos = null;
        looted = false;
        returnedBoxSlot = -1;
    }

    private boolean beginRun(LivingEntity threat, boolean viaEnderChest) {
        boolean canPearl = threat != null && ctx.settings().useEnderPearl() && hasEnderPearl();
        Phase first = viaEnderChest ? Phase.PLACE_ENDER : Phase.PLACE;
        if (canPearl) {
            escapeHops = 0;
            pearlOrigin = Ent.pos(bot);
            if (pearl.tryThrow(threat)) {
                phase = Phase.PEARL_WAIT;
                phaseTick = 0;
                pendingFirst = first;
                ctx.log().decision("RestockController", RESUPPLY_PEARL_AWAY, "[Restock] " + ctx.name() + " ran low - pearling away to resupply" + (viaEnderChest ? " from its ender chest" : ""));
                return true;
            }
        }

        if (threat != null && startElytraEscape()) {
            escapeHops = 0;
            pearlOrigin = Ent.pos(bot);
            phase = Phase.ELYTRA_LAUNCH;
            phaseTick = 0;
            pendingFirst = first;
            ctx.log().decision("RestockController", RESUPPLY_FLY_AWAY, "[Restock] " + ctx.name() + " ran low - flying away to resupply" + (viaEnderChest ? " from its ender chest" : ""));
            return true;
        } else if (!safeToOpenBox(threat)) {
            ctx.log().decision("RestockController", RESUPPLY_HOLD_THREAT_CLOSE, "[Restock] " + ctx.name() + " needs to resupply but " + threat.getName() + " is too close - holding");
            cooldown = ctx.tuning().integer(TuningKey.RESTOCK_HELD_BACK_COOLDOWN);
            return false;
        } else {
            ctx.log().decision("RestockController", RESUPPLY_IN_PLACE, "[Restock] " + ctx.name() + " ran low - resupplying where it stands" + (viaEnderChest ? " from its ender chest" : ""));
            phase = first;
            phaseTick = 0;
            return true;
        }
    }

    private boolean startElytraEscape() {
        if (countInInventory(Material.FIREWORK_ROCKET) <= 0) {
            return false;
        } else if (!equipElytraForEscape()) {
            return false;
        } else {
            faceAwayForEscape();
            return true;
        }
    }

    private void faceAwayForEscape() {
        LivingEntity chaser = escapeThreat();
        float yaw = bot.getLocation().getYaw();
        if (chaser != null) {
            Vec3 me = Ent.pos(bot);
            Vec3 them = Ent.pos(chaser);
            yaw = (float) Math.toDegrees(Math.atan2(-(me.x() - them.x()), me.z() - them.z()));
        }

        ctx.controls().look(yaw, -35.0F);
    }

    private void restoreChestAfterEscape() {
        int slot = elytraStashSlot;
        elytraStashSlot = -1;
        if (slot >= 0 && !bot.isGliding()) {
            PlayerInventory inv = bot.getInventory();
            ItemStack worn = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            ItemStack plate = inv.getItem(slot);
            if (ItemKinds.is(worn, Material.ELYTRA) && ArmorEvaluator.getSlot(plate) == EquipmentSlot.CHEST) {
                ItemStack back = plate.clone();
                inv.setItem(slot, worn.clone());
                bot.getEquipment().setItem(EquipmentSlot.CHEST, back);
            }
        }
    }

    private boolean equipElytraForEscape() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        elytraStashSlot = -1;
        if (ItemKinds.is(chest, Material.ELYTRA)) {
            return true;
        } else {
            PlayerInventory inv = bot.getInventory();
            int from = -1;

            for (int i = 0; i < InventoryUtils.SIZE; i++) {
                if (ItemKinds.is(inv.getItem(i), Material.ELYTRA) && isServiceable(inv.getItem(i))) {
                    from = i;
                    break;
                }
            }

            if (from < 0) {
                return false;
            } else {
                ItemStack elytra = inv.getItem(from).clone();
                inv.setItem(from, null);
                bot.getEquipment().setItem(EquipmentSlot.CHEST, elytra);
                if (!ItemKinds.isEmpty(chest)) {
                    inv.setItem(from, chest.clone());
                    elytraStashSlot = from;
                }

                return true;
            }
        }
    }

    private void fireEscapeRocket() {
        PlayerInventory inv = bot.getInventory();
        int slot = -1;

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.FIREWORK_ROCKET)) {
                slot = i;
                break;
            }
        }

        if (slot < 0) {
            for (int i = 9; i < 36; i++) {
                if (ItemKinds.is(inv.getItem(i), Material.FIREWORK_ROCKET)) {
                    slot = i;
                    break;
                }
            }

            if (slot < 0) {
                return;
            }

            slot = InventoryUtils.moveToHotbarSafe(ctx, slot);
        }

        int prev = InventoryUtils.selected(ctx);
        InventoryUtils.select(ctx, slot);
        ctx.hands().useItem(EquipmentSlot.HAND);
        InventoryUtils.select(ctx, prev);
    }

    public boolean tryPackLoot() {
        if (phase == Phase.IDLE && cooldown <= 0) {
            double radius = ctx.settings().lootPackRadius();
            if (radius <= 0.0) {
                return false;
            } else if (!ctx.settings().pickupLoot()) {
                return false;
            } else if (findEmptyShulkerSlot() < 0) {
                return false;
            } else if (!hasSurplusToPack()) {
                return false;
            } else if (enemyWithinRadius(radius)) {
                return false;
            } else {
                packMode = true;
                phase = Phase.PLACE;
                phaseTick = 0;
                looted = false;
                ctx.log().decision("RestockController", PACK_SURPLUS_START, "[Restock] " + ctx.name() + " is clear for " + (int) radius + " blocks - packing surplus into a shulker");
                return true;
            }
        } else {
            return false;
        }
    }

    private int findEmptyShulkerSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (isShulkerBox(st) && ItemKinds.containerContents(st).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private boolean isOpponent(Player p) {
        return Ent.alive(p)
                && p.getGameMode() != GameMode.SPECTATOR
                && p.getGameMode() != GameMode.CREATIVE
                && !p.equals(bot)
                && !ctx.match().isAlly(p);
    }

    private boolean enemyWithinRadius(double r) {
        for (Player p : ctx.world().players()) {
            if (isOpponent(p) && Ent.dist(bot, p) <= r) {
                return true;
            }
        }

        return false;
    }

    private boolean hasSurplusToPack() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (surplusBudget(inv.getItem(i)) > 0) {
                return true;
            }
        }

        return false;
    }

    private int surplusBudget(ItemStack st) {
        if (ItemKinds.isEmpty(st) || isShulkerBox(st) || st.getType() == Material.ENDER_CHEST) {
            return 0;
        } else if (!isRestockWorthy(st)) {
            return 0;
        } else {
            return !ArmorEvaluator.isArmor(st) && !isWeaponLike(st) ? Math.max(0, countHeldLike(st) - carryTarget(st)) : 0;
        }
    }

    /** Moves the surplus into the open shulker box. */
    private void doStuffSurplus() {
        Inventory box = openedBox();
        if (box == null) {
            return;
        }

        PlayerInventory inv = bot.getInventory();
        int packed = 0;
        Map<Material, Integer> budget = new HashMap<>();

        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!ItemKinds.isEmpty(st)) {
                budget.computeIfAbsent(st.getType(), k -> surplusBudget(st));
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!ItemKinds.isEmpty(st)) {
                Material kind = st.getType();
                int left = budget.getOrDefault(kind, 0);
                if (left > 0) {
                    int move = Math.min(left, st.getAmount());
                    int dest = firstRoomFor(box, st);
                    if (dest < 0) {
                        break;
                    }

                    ItemStack existing = box.getItem(dest);
                    if (ItemKinds.isEmpty(existing)) {
                        ItemStack put = st.clone();
                        put.setAmount(Math.min(move, st.getMaxStackSize()));
                        move = put.getAmount();
                        box.setItem(dest, put);
                    } else {
                        int room = Math.min(move, existing.getMaxStackSize() - existing.getAmount());
                        if (room <= 0) {
                            continue;
                        }

                        ItemStack grown = existing.clone();
                        grown.setAmount(existing.getAmount() + room);
                        box.setItem(dest, grown);
                        move = room;
                    }

                    setStackAmount(inv, i, st, st.getAmount() - move);
                    budget.put(kind, left - move);
                    packed += move;
                }
            }
        }

        ctx.log().decision("RestockController", PACK_SURPLUS_DONE, "[Restock] " + ctx.name() + " packed " + packed + " item(s) away");
    }

    /** Throws out the stacks that are not worth keeping through the hands: swap into the held slot, drop, swap back. */
    private void tossJunk() {
        PlayerInventory inv = bot.getInventory();
        int held = InventoryUtils.selected(ctx);
        int tossed = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!ItemKinds.isEmpty(st) && !isRestockWorthy(st)) {
                if (i != held) {
                    InventoryUtils.swap(ctx, i, held);
                }

                ctx.hands().drop(true);
                if (i != held) {
                    InventoryUtils.swap(ctx, i, held);
                }

                tossed++;
            }
        }

        ctx.log().decision("RestockController", DISCARD_JUNK, "[Restock] " + ctx.name() + " threw out " + tossed + " junk stack(s)");
    }

    private void setStackAmount(Inventory inv, int slot, ItemStack stack, int amount) {
        if (amount <= 0) {
            inv.setItem(slot, null);
        } else {
            ItemStack rest = stack.clone();
            rest.setAmount(amount);
            inv.setItem(slot, rest);
        }
    }

    private int firstRoomFor(Inventory box, ItemStack like) {
        for (int i = 0; i < box.getSize(); i++) {
            ItemStack st = box.getItem(i);
            if (ItemKinds.isEmpty(st)) {
                return i;
            }

            if (st.isSimilar(like) && st.getAmount() < st.getMaxStackSize()) {
                return i;
            }
        }

        return -1;
    }

    private LivingEntity escapeThreat() {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;

        for (Player p : ctx.world().players()) {
            if (isOpponent(p)) {
                double d = Ent.distSqr(bot, p);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }

        return best;
    }

    private boolean opponentDisengaged(LivingEntity threat) {
        if (threat != null && Ent.alive(threat)) {
            double dist = Ent.dist(bot, threat);
            if (dist >= ctx.tuning().number(TuningKey.RESTOCK_DISENGAGED_DIST).doubleValue()) {
                return true;
            } else {
                if (threat.isHandRaised()) {
                    ItemStack use = threat.getActiveItem();
                    if (ItemKinds.isFood(use) || ItemKinds.is(use, Material.POTION) || ItemKinds.is(use, Material.MILK_BUCKET)) {
                        return true;
                    }
                }

                return !bot.hasLineOfSight(threat)
                        && dist >= ctx.tuning().number(TuningKey.RESTOCK_COVER_OPEN_DIST).doubleValue();
            }
        } else {
            return true;
        }
    }

    private boolean safeToOpenBox(LivingEntity threat) {
        if (threat != null && Ent.alive(threat)) {
            double dist = Ent.dist(bot, threat);
            return dist >= ctx.tuning().number(TuningKey.RESTOCK_SAFE_OPEN_DIST).doubleValue()
                    ? true
                    : !bot.hasLineOfSight(threat)
                            && dist >= ctx.tuning().number(TuningKey.RESTOCK_COVER_OPEN_DIST).doubleValue();
        } else {
            return true;
        }
    }

    private void abortRun() {
        reset();
        cooldown = ctx.tuning().integer(TuningKey.RESTOCK_INTERRUPTED_COOLDOWN);
    }

    private boolean tickRun(LivingEntity threat) {
        phaseTick++;
        if (phase != Phase.PEARL_WAIT
                && phase != Phase.ELYTRA_LAUNCH
                && threat != null
                && Ent.alive(threat)
                && Ent.dist(bot, threat) <= ctx.tuning().number(TuningKey.RESTOCK_ABORT_DIST).doubleValue()
                && bot.hasLineOfSight(threat)) {
            ctx.log().decision("RestockController", RESUPPLY_ABORT_THREAT, "[Restock] " + ctx.name() + " broke off its resupply - " + threat.getName() + " closed in");
            abortRun();
            return false;
        } else {
            ctx.controls().stopMoving();
            switch (phase) {
                case PEARL_WAIT:
                    boolean landed = pearlOrigin != null && Ent.pos(bot).distanceTo(pearlOrigin) > PEARL_LANDED_DIST && bot.isOnGround();
                    if (landed || phaseTick >= PEARL_MAX_WAIT) {
                        LivingEntity chaser = escapeThreat();
                        if (!safeToOpenBox(chaser)) {
                            if (escapeHops < ctx.tuning().integer(TuningKey.RESTOCK_MAX_ESCAPE_HOPS)
                                    && chaser != null
                                    && hasEnderPearl()
                                    && ctx.settings().useEnderPearl()
                                    && pearl.tryThrow(chaser)) {
                                escapeHops++;
                                pearlOrigin = Ent.pos(bot);
                                phaseTick = 0;
                                ctx.log().decision(
                                        "RestockController",
                                        RESUPPLY_PEARL_AGAIN,
                                        "[Restock] " + ctx.name() + " did not get clear - pearling again (" + escapeHops + "/"
                                                + ctx.tuning().integer(TuningKey.RESTOCK_MAX_ESCAPE_HOPS) + ")");
                                return true;
                            }

                            ctx.log().decision("RestockController", RESUPPLY_ESCAPE_FAILED, "[Restock] " + ctx.name() + " could not break off to resupply - back to the fight");
                            finishRun();
                            cooldown = ctx.tuning().integer(TuningKey.RESTOCK_HELD_BACK_COOLDOWN);
                            return true;
                        }

                        phase = pendingFirst;
                        phaseTick = 0;
                    }

                    return true;
                case ELYTRA_LAUNCH:
                    faceAwayForEscape();
                    boolean gliding = bot.isGliding() || !bot.isOnGround() && ElytraFlight.beginGlide(ctx);
                    if (gliding) {
                        fireEscapeRocket();
                        elytraStashSlot = -1;
                        phase = Phase.PEARL_WAIT;
                        phaseTick = 0;
                        return true;
                    } else {
                        if (phaseTick >= ELYTRA_LAUNCH_TICKS) {
                            restoreChestAfterEscape();
                            finishRun();
                            cooldown = ctx.tuning().integer(TuningKey.RESTOCK_HELD_BACK_COOLDOWN);
                            return true;
                        }

                        if (bot.isOnGround()) {
                            ctx.controls().jumpOnce();
                        }

                        return true;
                    }
                case PLACE_ENDER:
                    if (!doPlaceEnderChest()) {
                        finishRun();
                        return true;
                    }

                    phase = Phase.TAKE_SHULKER;
                    phaseTick = 0;
                    return true;
                case TAKE_SHULKER:
                    if (phaseTick < ENDER_OPEN_TICKS) {
                        if (phaseTick == 1) {
                            openEnder();
                        }

                        return true;
                    } else {
                        Inventory ec = openedEnder();
                        if (ec == null) {
                            if (phaseTick >= ENDER_OPEN_TICKS + OPEN_GIVE_UP_TICKS) {
                                finishRun();
                            } else if (phaseTick % OPEN_RETRY_TICKS == 0) {
                                openEnder();
                            }

                            return true;
                        }

                        List<Supply> wanted = activeMissing != null ? activeMissing : currentLow();
                        int loose = takeLooseFromEnder(ec, wanted);
                        if (!takeShulkerFromEnder(ec, wanted)) {
                            if (loose <= 0) {
                                if (findEnderShulkerSlot(ec, wanted) < 0 && !enderHasLoose(ec, wanted)) {
                                    enderBarren.addAll(keySet(wanted));
                                }

                                finishRun();
                                return true;
                            }

                            phase = Phase.MINE_ENDER;
                            phaseTick = 0;
                            return true;
                        }

                        closeView();
                        phase = Phase.PLACE;
                        phaseTick = 0;
                        return true;
                    }
                case PLACE:
                    if (!doPlace()) {
                        finishRun();
                        return true;
                    }

                    boxesOpened++;
                    phase = Phase.OPEN;
                    phaseTick = 0;
                    looted = false;
                    resetExtractState();
                    return true;
                case OPEN:
                    lookAtPlaced();
                    if (openedBox() == null && phaseTick % EXTRACT_EVERY == 1 && phaseTick <= OPEN_RETRY_WINDOW) {
                        openBox(placedPos);
                    }

                    if (!packMode && phaseTick > 0 && phaseTick % EXTRACT_EVERY == 0) {
                        doExtract(1);
                    }

                    if (phaseTick >= OPEN_TICKS) {
                        boolean packedNow = false;
                        if (!looted) {
                            if (packMode) {
                                doStuffSurplus();
                                packedNow = true;
                            } else {
                                doExtract(Integer.MAX_VALUE);
                                logExtract();
                            }

                            looted = true;
                        }

                        closePlaced();
                        if (packedNow) {
                            tossJunk();
                        }

                        beginMining();
                        phase = Phase.MINE;
                        phaseTick = 0;
                    }

                    return true;
                case MINE:
                    lookAtPlaced();
                    if (placedPos != null) {
                        if (ctx.world().isAir(placedPos)) {
                            ctx.controls().stopMining();
                            minedPos = placedPos;
                            releasePlacedClaim();
                            placedPos = null;
                            phase = Phase.COLLECT;
                            phaseTick = 0;
                            return true;
                        }

                        if (phaseTick >= MINE_TIMEOUT) {
                            ctx.controls().stopMining();
                            releasePlacedClaim();
                            placedPos = null;
                            finishRun();
                        }

                        return true;
                    }

                    ctx.controls().stopMining();
                    finishRun();
                    return true;
                case COLLECT:
                    if (minedPos != null) {
                        collectDrop(minedPos, ItemKinds::isShulkerBox);
                    }

                    if (phaseTick >= COLLECT_TICKS) {
                        ctx.log().decision("RestockController", RECOVER_SHULKER, "[Restock] " + ctx.name() + " mined shulker back up at " + minedPos);
                        minedPos = null;
                        if (enderPos != null) {
                            findReturnedBox();
                            phase = Phase.STOW_SHULKER;
                            phaseTick = 0;
                            return true;
                        }

                        List<Supply> still = packMode ? List.of() : currentLow();
                        if (!still.isEmpty()
                                && boxesOpened < ctx.tuning().integer(TuningKey.RESTOCK_MAX_BOXES_PER_RUN)
                                && findShulkerSlot(still) >= 0) {
                            activeMissing = still;
                            phase = Phase.PLACE;
                            phaseTick = 0;
                            ctx.log().decision("RestockController", OPEN_NEXT_SHULKER, "[Restock] " + ctx.name() + " still short - opening box " + (boxesOpened + 1) + " of this run");
                            return true;
                        }

                        finishRun();
                    }

                    return true;
                case STOW_SHULKER:
                    if (phaseTick < ENDER_OPEN_TICKS) {
                        if (phaseTick == 1) {
                            openEnder();
                        }

                        return true;
                    } else {
                        Inventory ec = openedEnder();
                        if (ec == null) {
                            if (phaseTick >= ENDER_OPEN_TICKS + OPEN_GIVE_UP_TICKS) {
                                phase = Phase.MINE_ENDER;
                                phaseTick = 0;
                            } else if (phaseTick % OPEN_RETRY_TICKS == 0) {
                                openEnder();
                            }

                            return true;
                        }

                        stowShulkerInEnder(ec);
                        List<Supply> stillEnder = currentLow();
                        if (!stillEnder.isEmpty()
                                && boxesOpened < ctx.tuning().integer(TuningKey.RESTOCK_MAX_BOXES_PER_RUN)
                                && findEnderShulkerSlot(ec, stillEnder) >= 0) {
                            activeMissing = stillEnder;
                            phase = Phase.TAKE_SHULKER;
                            phaseTick = 0;
                            return true;
                        }

                        phase = Phase.MINE_ENDER;
                        phaseTick = 0;
                        return true;
                    }
                case MINE_ENDER:
                    if (enderPos != null && ctx.world().type(enderPos) == Material.ENDER_CHEST) {
                        if (phaseTick <= 1) {
                            closeView();
                            holdPickaxe();
                            ctx.controls().mineContinuous();
                        }

                        WebPlacer.lookAt(ctx, enderPos);
                        if (phaseTick < MINE_TIMEOUT) {
                            return true;
                        }

                        ctx.controls().stopMining();
                        enderPos = null;
                        finishRun();
                        return true;
                    }

                    ctx.controls().stopMining();
                    if (enderPos != null) {
                        minedPos = enderPos;
                        enderPos = null;
                        phase = Phase.COLLECT_ENDER;
                        phaseTick = 0;
                        return true;
                    }

                    finishRun();
                    return true;
                case COLLECT_ENDER:
                    if (minedPos != null) {
                        collectDrop(minedPos, s -> s.getType() == Material.ENDER_CHEST || s.getType() == Material.OBSIDIAN);
                    }

                    if (phaseTick >= COLLECT_TICKS) {
                        ctx.log().decision("RestockController", RECOVER_ENDER_CHEST, "[Restock] " + ctx.name() + " took its ender chest back up");
                        finishRun();
                    }

                    return true;
                default:
                    finishRun();
                    return false;
            }
        }
    }

    private void beginMining() {
        holdPickaxe();
        lookAtPlaced();
        ctx.controls().mineContinuous();
    }

    /** Walks over the dropped item; the server picks it up. */
    private void collectDrop(BlockPos pos, Predicate<ItemStack> wanted) {
        AABB area = AABB.ofBlock(pos).inflate(2.5).expandTowards(0.0, -8.0, 0.0);
        Item nearest = null;
        double best = Double.MAX_VALUE;

        for (Item ie : ctx.world().entities(Item.class, area, e -> wanted.test(e.getItemStack()))) {
            double d = Ent.distSqr(bot, ie);
            if (d < best) {
                best = d;
                nearest = ie;
            }
        }

        if (nearest != null) {
            Vec3 me = Ent.pos(bot);
            Vec3 at = Ent.pos(nearest);
            double dx = at.x() - me.x();
            double dz = at.z() - me.z();
            if (dx * dx + dz * dz > 0.04) {
                float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float[] input = PathFollower.yawToInput(bot.getLocation().getYaw(), wantYaw);
                ctx.controls().setMovement(input[0], input[1]);
            }
        }
    }

    /** The shulker that came back is the box slot that was not occupied when the run's box was placed. */
    private void findReturnedBox() {
        returnedBoxSlot = -1;
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (!boxSlotsBefore[i] && isShulkerBox(inv.getItem(i))) {
                returnedBoxSlot = i;
                return;
            }
        }
    }

    private void closePlaced() {
        if (openedBox() != null) {
            bot.closeInventory();
        }
    }

    private void closeView() {
        InventoryType type = bot.getOpenInventory().getTopInventory().getType();
        if (type == InventoryType.SHULKER_BOX || type == InventoryType.ENDER_CHEST) {
            bot.closeInventory();
        }
    }

    private void restoreSlotAfterMining() {
        if (slotBeforeMine >= 0 && slotBeforeMine < 9) {
            InventoryUtils.select(ctx, slotBeforeMine);
        }

        slotBeforeMine = -1;
    }

    private void holdPickaxe() {
        if (slotBeforeMine < 0) {
            slotBeforeMine = InventoryUtils.selected(ctx);
        }

        int pick = findPickaxeSlot(bot.getInventory());
        if (pick >= 0) {
            InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, pick));
        }
    }

    private int holdInHotbar(int slot) {
        if (slotBeforeMine < 0) {
            slotBeforeMine = InventoryUtils.selected(ctx);
        }

        int hot = InventoryUtils.moveToHotbarSafe(ctx, slot);
        InventoryUtils.select(ctx, hot);
        return hot;
    }

    private int findPickaxeSlot(PlayerInventory inv) {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isPickaxe(inv.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private void finishRun() {
        ctx.controls().stopMining();
        closeView();
        if (enderPos != null) {
            phase = Phase.MINE_ENDER;
            phaseTick = 0;
            return;
        }

        restoreSlotAfterMining();
        boxesOpened = 0;
        packMode = false;
        phase = Phase.IDLE;
        phaseTick = 0;
        cooldown = ctx.tuning().integer(TuningKey.RESTOCK_COOLDOWN_TICKS);
        pearlOrigin = null;
        releasePlacedClaim();
        placedPos = null;
        activeMissing = null;
        minedPos = null;
        looted = false;
        pendingFirst = Phase.PLACE;
        enderSlotUsed = -1;
        returnedBoxSlot = -1;
    }

    private void releasePlacedClaim() {
        if (placedPos != null) {
            claims.release(placedPos, bot.getUniqueId());
        }
    }

    // ---- containers: placed and opened through the hands, items moved only through the open view ----

    private boolean placeBlockFromSlot(int slot, BlockPos pos, Material expected) {
        holdInHotbar(slot);
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ctx.controls().lookAt(hit);
        boolean used = ctx.hands().useItemOn(EquipmentSlot.HAND, pos.below(), Direction.UP, hit);
        if (used) {
            ctx.hands().swing(EquipmentSlot.HAND);
        }

        Material placed = ctx.world().type(pos);
        return expected == Material.SHULKER_BOX ? ItemKinds.isShulkerBox(placed) : placed == expected;
    }

    private boolean openBox(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        ctx.controls().setSneaking(false);
        WebPlacer.lookAt(ctx, pos);
        return ctx.hands().useItemOn(EquipmentSlot.HAND, pos, Direction.UP, new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
    }

    private boolean openEnder() {
        return openBox(enderPos);
    }

    private Inventory openedBox() {
        if (placedPos == null) {
            return null;
        }

        Inventory top = bot.getOpenInventory().getTopInventory();
        Location at = top.getLocation();
        if (top.getType() == InventoryType.SHULKER_BOX
                && at != null
                && at.getBlockX() == placedPos.getX()
                && at.getBlockY() == placedPos.getY()
                && at.getBlockZ() == placedPos.getZ()) {
            return top;
        }

        return null;
    }

    private Inventory openedEnder() {
        if (enderPos == null) {
            return null;
        }

        Inventory top = bot.getOpenInventory().getTopInventory();
        Location at = top.getLocation();
        boolean here = at == null
                || at.getBlockX() == enderPos.getX() && at.getBlockY() == enderPos.getY() && at.getBlockZ() == enderPos.getZ();
        return top.getType() == InventoryType.ENDER_CHEST && here ? top : null;
    }

    private boolean doPlace() {
        int boxSlot = packMode ? findEmptyShulkerSlot() : findShulkerSlot(activeMissing != null ? activeMissing : currentLow());
        if (boxSlot < 0) {
            return false;
        } else {
            BlockPos placePos = findPlacementPos();
            if (placePos == null) {
                ctx.log().decision("RestockController", SHULKER_PLACE_BLOCKED, "[Restock] " + ctx.name() + " - no valid position to place shulker", true);
                return false;
            } else if (!placeBlockFromSlot(boxSlot, placePos, Material.SHULKER_BOX)) {
                return false;
            } else {
                PlayerInventory inv = bot.getInventory();
                for (int i = 0; i < 36; i++) {
                    boxSlotsBefore[i] = isShulkerBox(inv.getItem(i));
                }

                placedPos = placePos;
                claims.claim(placePos, bot.getUniqueId());
                ctx.log().decision("RestockController", PLACE_SHULKER, "[Restock] " + ctx.name() + " placed shulker at " + placePos);
                return true;
            }
        }
    }

    private void resetExtractState() {
        extractDumped = false;
        extractedTotal = 0;
        missedTotal = 0;
        dumpedTotal = 0;
        equippedTotal = 0;
    }

    private void logExtract() {
        ctx.log().decision("RestockController", EXTRACT_SUMMARY, "[Restock] " + ctx.name() + " extracted " + extractedTotal + " stacks (" + missedTotal + " didn't fit), dumped "
                + dumpedTotal + ", equipped " + equippedTotal + " armour");
    }

    private int dumpJunk(Inventory box, PlayerInventory inv) {
        int dumped = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!ItemKinds.isEmpty(stack) && !isUsefulItem(stack)) {
                for (int s = 0; s < Math.min(SHULKER_SLOTS, box.getSize()); s++) {
                    if (ItemKinds.isEmpty(box.getItem(s))) {
                        box.setItem(s, stack.clone());
                        inv.setItem(i, null);
                        dumped++;
                        break;
                    }
                }
            }
        }

        return dumped;
    }

    /** Pulls up to {@code maxPulls} stacks from the open shulker box into the inventory. */
    private void doExtract(int maxPulls) {
        Inventory box = openedBox();
        if (box == null) {
            return;
        }

        PlayerInventory inv = bot.getInventory();
        if (!extractDumped) {
            dumpedTotal += dumpJunk(box, inv);
            extractDumped = true;
        }

        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < Math.min(SHULKER_SLOTS, box.getSize()); i++) {
            ItemStack st = box.getItem(i);
            if (!ItemKinds.isEmpty(st) && isNeededItem(st)) {
                candidates.add(i);
            }
        }

        candidates.sort(Comparator.<Integer>comparingDouble(ix -> -needScore(box.getItem(ix)))
                .thenComparingDouble(ix -> -ArmorEvaluator.score(box.getItem(ix))));
        int extracted = 0;
        int missed = 0;
        int pulls = 0;

        pass:
        for (int pass = 0; pass < 2; pass++) {
            for (int ix : candidates) {
                ItemStack fromBox = box.getItem(ix);
                if (!ItemKinds.isEmpty(fromBox)) {
                    int want = wantFor(fromBox);
                    if (want > 0) {
                        int room = roomKeepingReserve(fromBox);
                        if (room <= 0) {
                            if (pass == 1) {
                                missed++;
                            }
                        } else {
                            int takeCount = Math.min(want, fromBox.getAmount());
                            if (pass == 0) {
                                takeCount = Math.min(takeCount, fromBox.getMaxStackSize());
                            }

                            takeCount = Math.min(takeCount, room);
                            ItemStack give = fromBox.clone();
                            give.setAmount(takeCount);
                            ItemStack leftover = addToInventory(give);
                            int leftoverCount = leftover == null ? 0 : leftover.getAmount();
                            int moved = takeCount - leftoverCount;
                            setStackAmount(box, ix, fromBox, fromBox.getAmount() - moved);
                            if (moved > 0 && pass == 0) {
                                extracted++;
                            }

                            if (leftoverCount > 0 && pass == 1) {
                                missed++;
                            }

                            if (moved > 0) {
                                if (++pulls >= maxPulls) {
                                    break pass;
                                }
                            }
                        }
                    }
                }
            }
        }

        extractedTotal += extracted;
        missedTotal += missed;
        equippedTotal += equipBetterArmor();
    }

    private double needScore(ItemStack st) {
        int want = wantFor(st);
        return needScore(want, targetFor(st), want > 0 ? classWeight(st) : 0.0);
    }

    static double needScore(int want, int target, double weight) {
        if (want <= 0) {
            return 0.0;
        } else {
            double deficit = Math.min(1.0, (double) want / Math.max(1, target));
            return weight * (0.5 + deficit);
        }
    }

    private double classWeight(ItemStack st) {
        BotTuning.Profile profile = ctx.tuning();
        Material item = st.getType();
        boolean hurt = bot.getHealth()
                < Ent.maxHealth(bot) * profile.number(TuningKey.RESTOCK_HURT_FRACTION).doubleValue();
        if (ArmorEvaluator.isArmor(st)) {
            EquipmentSlot slot = ArmorEvaluator.getSlot(st);
            boolean wornGone = slot != null && !isServiceable(bot.getEquipment().getItem(slot));
            return wornGone
                    ? profile.number(TuningKey.RESTOCK_WEIGHT_ARMOR_MISSING).doubleValue()
                    : profile.number(TuningKey.RESTOCK_WEIGHT_ARMOR_SPARE).doubleValue();
        } else if (item == Material.ELYTRA) {
            ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
            return isServiceable(chest) && !ItemKinds.is(chest, Material.ELYTRA)
                    ? profile.number(TuningKey.RESTOCK_WEIGHT_ELYTRA_SPARE).doubleValue()
                    : (countServiceableElytra() == 0
                            ? profile.number(TuningKey.RESTOCK_WEIGHT_ELYTRA_MISSING).doubleValue()
                            : profile.number(TuningKey.RESTOCK_WEIGHT_ELYTRA_SPARE).doubleValue());
        } else if (isWeaponLike(st)) {
            return countServiceableWeaponsLike(st) == 0
                    ? profile.number(TuningKey.RESTOCK_WEIGHT_WEAPON_MISSING).doubleValue()
                    : profile.number(TuningKey.RESTOCK_WEIGHT_WEAPON_SPARE).doubleValue();
        } else if (item == Material.SHIELD) {
            return countUsableShields() == 0
                    ? profile.number(TuningKey.RESTOCK_WEIGHT_SHIELD_MISSING).doubleValue()
                    : profile.number(TuningKey.RESTOCK_WEIGHT_SHIELD_SPARE).doubleValue();
        } else if (!isHealingItem(st)) {
            if (item == Material.TOTEM_OF_UNDYING) {
                return countInInventory(Material.TOTEM_OF_UNDYING) == 0
                        ? profile.number(TuningKey.RESTOCK_WEIGHT_TOTEM_MISSING).doubleValue()
                        : profile.number(TuningKey.RESTOCK_WEIGHT_TOTEM_SPARE).doubleValue();
            }

            double fixed = fixedWeight(profile, item, hurt);
            if (fixed >= 0.0) {
                return fixed;
            } else if (isShulkerBox(st)) {
                return profile.number(TuningKey.RESTOCK_WEIGHT_SHULKER).doubleValue();
            } else if (ItemKinds.isArrow(st) || isBuildingBlock(st)) {
                return profile.number(TuningKey.RESTOCK_WEIGHT_ARROW_BLOCK).doubleValue();
            } else {
                return ItemKinds.isFood(st)
                        ? profile.number(TuningKey.RESTOCK_WEIGHT_FOOD).doubleValue()
                        : profile.number(TuningKey.RESTOCK_WEIGHT_OTHER).doubleValue();
            }
        } else {
            return healingCharges() != 0 && !hurt
                    ? profile.number(TuningKey.RESTOCK_WEIGHT_HEALING_STOCKED).doubleValue()
                    : profile.number(TuningKey.RESTOCK_WEIGHT_HEALING_NEEDED).doubleValue();
        }
    }

    /** Weight of the items classed by material alone; -1 when the material is not one of them. */
    static double fixedWeight(BotTuning.Profile profile, Material item, boolean hurt) {
        return switch (item) {
            case ENDER_CHEST -> profile.number(TuningKey.RESTOCK_WEIGHT_ENDER_CHEST).doubleValue();
            case ENDER_PEARL, FIREWORK_ROCKET, COBWEB, WIND_CHARGE ->
                    profile.number(TuningKey.RESTOCK_WEIGHT_MOBILITY).doubleValue();
            case END_CRYSTAL, OBSIDIAN, RESPAWN_ANCHOR, GLOWSTONE ->
                    profile.number(TuningKey.RESTOCK_WEIGHT_CRYSTAL_KIT).doubleValue();
            case TNT_MINECART, RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL ->
                    profile.number(TuningKey.RESTOCK_WEIGHT_CART_KIT).doubleValue();
            case EXPERIENCE_BOTTLE -> profile.number(TuningKey.RESTOCK_WEIGHT_XP).doubleValue();
            case SPLASH_POTION, LINGERING_POTION, POTION -> hurt
                    ? profile.number(TuningKey.RESTOCK_WEIGHT_POTION_HURT).doubleValue()
                    : profile.number(TuningKey.RESTOCK_WEIGHT_POTION).doubleValue();
            case WATER_BUCKET -> profile.number(TuningKey.RESTOCK_WEIGHT_WATER_BUCKET).doubleValue();
            case COBBLESTONE, DIRT, END_STONE, NETHERRACK, STONE ->
                    profile.number(TuningKey.RESTOCK_WEIGHT_BUILDING).doubleValue();
            default -> -1.0;
        };
    }

    public static boolean isRestockWorthy(ItemStack st) {
        if (ItemKinds.isEmpty(st)) {
            return false;
        } else {
            Material item = st.getType();
            return isHealingItem(st)
                    || isWeaponLike(st)
                    || ArmorEvaluator.isArmor(st)
                    || isBuildingBlock(st)
                    || isRailItem(item)
                    || ItemKinds.isArrow(st)
                    || ItemKinds.isPickaxe(st)
                    || ItemKinds.isFood(st)
                    || isShulkerBoxItem(st)
                    || RESTOCK_WORTHY.contains(item);
        }
    }

    private static final Set<Material> RESTOCK_WORTHY = EnumSet.of(
            Material.TOTEM_OF_UNDYING, Material.ENDER_PEARL, Material.FIREWORK_ROCKET, Material.COBWEB, Material.WIND_CHARGE,
            Material.EXPERIENCE_BOTTLE, Material.END_CRYSTAL, Material.OBSIDIAN, Material.RESPAWN_ANCHOR, Material.GLOWSTONE,
            Material.TNT_MINECART, Material.ELYTRA, Material.SHIELD, Material.ENDER_CHEST, Material.SPLASH_POTION,
            Material.LINGERING_POTION, Material.POTION, Material.WATER_BUCKET, Material.MILK_BUCKET, Material.BUCKET,
            Material.CHORUS_FRUIT, Material.BREEZE_ROD, Material.SHULKER_SHELL, Material.FLINT_AND_STEEL, Material.SHEARS,
            Material.HONEY_BOTTLE, Material.SNOWBALL, Material.EGG, Material.FIRE_CHARGE, Material.TNT, Material.LADDER);

    public static boolean isShulkerBoxItem(ItemStack st) {
        return ItemKinds.isShulkerBox(st);
    }

    private static boolean isHealingItem(ItemStack st) {
        Material item = st.getType();
        return item == Material.GOLDEN_APPLE || item == Material.ENCHANTED_GOLDEN_APPLE || item == Material.GOLDEN_CARROT;
    }

    private int healingCharges() {
        PlayerInventory inv = bot.getInventory();
        int n = 0;

        for (int i = 0; i < InventoryUtils.SIZE; i++) {
            ItemStack st = inv.getItem(i);
            if (!ItemKinds.isEmpty(st) && isHealingItem(st)) {
                n += st.getAmount();
            }
        }

        return n;
    }

    private int countServiceableElytra() {
        ItemStack chest = bot.getEquipment().getItem(EquipmentSlot.CHEST);
        int n = isServiceable(chest) && ItemKinds.is(chest, Material.ELYTRA) ? 1 : 0;
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < InventoryUtils.SIZE; i++) {
            ItemStack st = inv.getItem(i);
            if (ItemKinds.is(st, Material.ELYTRA) && isServiceable(st)) {
                n++;
            }
        }

        return n;
    }

    private static boolean isBuildingBlock(ItemStack st) {
        return ItemKinds.isPlanks(st) || isBuildingMaterial(st.getType());
    }

    static boolean isBuildingMaterial(Material item) {
        return item == Material.COBBLESTONE
                || item == Material.DIRT
                || item == Material.END_STONE
                || item == Material.NETHERRACK
                || item == Material.STONE;
    }

    private int wantFor(ItemStack st) {
        if (ArmorEvaluator.isArmor(st)) {
            EquipmentSlot slot = ArmorEvaluator.getSlot(st);
            if (slot == null) {
                return 0;
            } else {
                ItemStack worn = bot.getEquipment().getItem(slot);
                int have = isServiceable(worn) ? 1 : 0;
                have += countCarriedServiceableArmorFor(slot);
                int want = Math.max(0, gearTarget(st, 2) - have);
                if (want == 0 && !isNearlyBroken(st) && !canMendInstead(worn) && ArmorEvaluator.score(st) > bestHeldArmorScore(slot)) {
                    want = 1;
                }

                return want;
            }
        } else if (isWeaponLike(st)) {
            return Math.max(0, gearTarget(st, 2) - countServiceableWeaponsLike(st));
        } else {
            return st.getType() == Material.SHIELD ? Math.max(0, carryTarget(st) - countUsableShields()) : carryTarget(st) - countHeldLike(st);
        }
    }

    private int targetFor(ItemStack st) {
        if (ArmorEvaluator.isArmor(st)) {
            return gearTarget(st, 2);
        } else {
            return isWeaponLike(st) ? gearTarget(st, 2) : carryTarget(st);
        }
    }

    private int gearTarget(ItemStack st, int builtIn) {
        int override = plan.carryTarget(ctx.squad().key(), st);
        return override > 0 ? override : builtIn;
    }

    private double bestHeldArmorScore(EquipmentSlot slot) {
        ItemStack worn = bot.getEquipment().getItem(slot);
        double best = ItemKinds.isEmpty(worn) ? Double.NEGATIVE_INFINITY : ArmorEvaluator.score(worn);
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack held = inv.getItem(i);
            if (!ItemKinds.isEmpty(held) && ArmorEvaluator.getSlot(held) == slot && !isNearlyBroken(held)) {
                best = Math.max(best, ArmorEvaluator.score(held));
            }
        }

        return best;
    }

    private static boolean isWeaponMaterial(Material item) {
        return item == Material.MACE || item == Material.TRIDENT || item == Material.BOW || item == Material.CROSSBOW;
    }

    private static boolean isWeaponLike(ItemStack st) {
        return ItemKinds.isSword(st) || ItemKinds.isAxe(st) || ItemKinds.isSpear(st) || isWeaponMaterial(st.getType());
    }

    private static boolean sameWeaponType(ItemStack a, ItemStack b) {
        if (ItemKinds.isSword(a)) {
            return ItemKinds.isSword(b);
        } else if (ItemKinds.isAxe(a)) {
            return ItemKinds.isAxe(b);
        } else {
            return ItemKinds.isSpear(a) ? ItemKinds.isSpear(b) : a.getType() == b.getType();
        }
    }

    private int countServiceableWeaponsLike(ItemStack st) {
        int n = 0;
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < InventoryUtils.SIZE; i++) {
            ItemStack held = inv.getItem(i);
            if (!ItemKinds.isEmpty(held) && isWeaponLike(held) && sameWeaponType(st, held) && isServiceable(held)) {
                n++;
            }
        }

        return n;
    }

    private int countCarriedServiceableArmorFor(EquipmentSlot slot) {
        int n = 0;
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack held = inv.getItem(i);
            if (!ItemKinds.isEmpty(held) && ArmorEvaluator.isArmor(held) && ArmorEvaluator.getSlot(held) == slot && isServiceable(held)) {
                n++;
            }
        }

        return n;
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private int equipBetterArmor() {
        if (suitUp.holdsArmour()) {
            return 0;
        } else {
            int changed = 0;
            PlayerInventory inv = bot.getInventory();

            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack worn = bot.getEquipment().getItem(slot);
                if (!canMendInstead(worn)) {
                    int bestIdx = -1;

                    for (int i = 0; i < 36; i++) {
                        ItemStack cand = inv.getItem(i);
                        if (!ItemKinds.isEmpty(cand)
                                && ArmorEvaluator.isArmor(cand)
                                && ArmorEvaluator.getSlot(cand) == slot
                                && !isNearlyBroken(cand)
                                && (ItemKinds.isEmpty(worn) || isNearlyBroken(worn) || !(ArmorEvaluator.score(cand) <= ArmorEvaluator.score(worn)))
                                && (bestIdx < 0 || !(ArmorEvaluator.score(cand) <= ArmorEvaluator.score(inv.getItem(bestIdx))))) {
                            bestIdx = i;
                        }
                    }

                    if (bestIdx >= 0) {
                        ItemStack better = inv.getItem(bestIdx).clone();
                        inv.setItem(bestIdx, ItemKinds.isEmpty(worn) ? null : worn.clone());
                        bot.getEquipment().setItem(slot, better);
                        changed++;
                    }
                }
            }

            return changed;
        }
    }

    private boolean canMendInstead(ItemStack worn) {
        return !ItemKinds.isEmpty(worn)
                && EnchantmentUtils.hasEnchantAtLeast(worn, "mending", 1)
                && countInInventory(Material.EXPERIENCE_BOTTLE) > 0;
    }

    private boolean isWornOut(ItemStack st) {
        if (ItemKinds.isEmpty(st)) {
            return true;
        } else {
            return ItemKinds.damageable(st) && ItemKinds.maxDamage(st) > 0
                    ? ItemKinds.damage(st) >= ItemKinds.maxDamage(st)
                            * ctx.tuning().number(TuningKey.RESTOCK_REGEAR_WEAR).doubleValue()
                    : false;
        }
    }

    private boolean isServiceable(ItemStack st) {
        return !ItemKinds.isEmpty(st) && (!isWornOut(st) || canMendInstead(st));
    }

    private static boolean isNearlyBroken(ItemStack st) {
        if (ItemKinds.isEmpty(st)) {
            return true;
        } else {
            return ItemKinds.damageable(st) && ItemKinds.maxDamage(st) > 0 ? ItemKinds.damage(st) >= ItemKinds.maxDamage(st) * 9 / 10 : false;
        }
    }

    private void lookAtPlaced() {
        if (placedPos != null) {
            WebPlacer.lookAt(ctx, placedPos);
        }
    }

    // ---- supplies ----

    private Supply itemSupply(String key, BooleanSupplier relevant, int lowAt, Predicate<ItemStack> matches, Material... items) {
        return new Supply(key, relevant, () -> sumInInventory(items), lowAt, matches, items, false, null);
    }

    private int sumInInventory(Material[] items) {
        int n = 0;

        for (Material item : items) {
            n += countInInventory(item);
        }

        return n;
    }

    private static boolean isOf(ItemStack st, Material material) {
        return ItemKinds.is(st, material);
    }

    private List<Supply> buildSupplies() {
        List<Supply> s = new ArrayList<>();
        s.add(new Supply("food", null, this::countFood, 2, ItemKinds::isFood, null, true, null));
        s.add(new Supply("arrows", this::hasBowInHotbar, this::countArrows, 4, ItemKinds::isArrow, null, true, null));
        s.add(itemSupply("ender_pearl", null, 2, st -> isOf(st, Material.ENDER_PEARL), Material.ENDER_PEARL));
        s.add(itemSupply("totem", null, 1, st -> isOf(st, Material.TOTEM_OF_UNDYING), Material.TOTEM_OF_UNDYING));
        s.add(itemSupply("throw_potion", null, 2, st -> isOf(st, Material.SPLASH_POTION) || isOf(st, Material.LINGERING_POTION),
                Material.SPLASH_POTION, Material.LINGERING_POTION));
        s.add(itemSupply("water_bucket", null, 1, st -> isOf(st, Material.WATER_BUCKET), Material.WATER_BUCKET));
        s.add(itemSupply("wind_charge", null, 2, st -> isOf(st, Material.WIND_CHARGE), Material.WIND_CHARGE));
        s.add(itemSupply("cobweb", null, 2, st -> isOf(st, Material.COBWEB), Material.COBWEB));
        s.add(itemSupply("end_crystal", null, 2, st -> isOf(st, Material.END_CRYSTAL), Material.END_CRYSTAL));
        s.add(itemSupply("respawn_anchor", null, 1, st -> isOf(st, Material.RESPAWN_ANCHOR), Material.RESPAWN_ANCHOR));
        s.add(new Supply(
                "carts",
                () -> ctx.settings().useCarting(),
                () -> Math.min(countInInventory(Material.TNT_MINECART), countRailsInInventory()),
                3,
                st -> isOf(st, Material.TNT_MINECART) || isRailItem(st.getType()),
                null,
                true,
                new Material[]{Material.TNT_MINECART}));
        s.add(new Supply("armor", null, this::countWornArmor, 4, ArmorEvaluator::isArmor, null, false, null));
        s.add(new Supply(
                "weapon",
                null,
                this::countWeapons,
                1,
                st -> ItemKinds.isSword(st) || ItemKinds.isAxe(st) || ItemKinds.isSpear(st) || isOf(st, Material.MACE) || isOf(st, Material.TRIDENT),
                null,
                true,
                null));
        s.add(new Supply("shield", null, this::countUsableShields, 1, st -> isOf(st, Material.SHIELD), null, true, new Material[]{Material.SHIELD}));
        s.add(itemSupply("rockets", this::hasElytra, 2, st -> isOf(st, Material.FIREWORK_ROCKET), Material.FIREWORK_ROCKET));
        s.add(itemSupply("obsidian", null, 2, st -> isOf(st, Material.OBSIDIAN), Material.OBSIDIAN));
        s.add(itemSupply("glowstone", null, 2, st -> isOf(st, Material.GLOWSTONE), Material.GLOWSTONE));
        s.add(new Supply("blocks", null, this::countBuildingBlocks, 8, RestockController::isSupplyBlock, null, true, null));
        s.add(itemSupply("xp_bottle", null, 4, st -> isOf(st, Material.EXPERIENCE_BOTTLE), Material.EXPERIENCE_BOTTLE));
        s.add(itemSupply("gapple", null, 2, st -> isOf(st, Material.GOLDEN_APPLE) || isOf(st, Material.ENCHANTED_GOLDEN_APPLE),
                Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE));
        return s;
    }

    private static boolean isSupplyBlock(ItemStack st) {
        Material m = st.getType();
        return ItemKinds.isPlanks(st) || m == Material.COBBLESTONE || m == Material.DIRT || m == Material.END_STONE || m == Material.OBSIDIAN;
    }

    private static boolean isRelevant(Supply s) {
        return s.relevant() == null || s.relevant().getAsBoolean();
    }

    private int count(Supply s) {
        return s.items() != null ? sumInInventory(s.items()) : s.count().getAsInt();
    }

    private boolean stockedNow(Supply s) {
        if (s.carried()) {
            if (tallySize == 0) {
                return false;
            }

            if (s.needs() != null && !anyTallied(s.needs())) {
                return false;
            }
        }

        return count(s) >= s.lowAt();
    }

    private boolean anyTallied(Material[] items) {
        for (Material item : items) {
            for (int j = 0; j < tallySize; j++) {
                if (tallyItems[j] == item) {
                    return true;
                }
            }
        }

        return false;
    }

    private int countBuildingBlocks() {
        int n = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack st = stackAt(i);
            if (!ItemKinds.isEmpty(st) && isSupplyBlock(st)) {
                n += st.getAmount();
            }
        }

        return n;
    }

    private int countWornArmor() {
        int n = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (isServiceable(bot.getEquipment().getItem(slot))) {
                n++;
            }
        }

        return n;
    }

    private int countUsableShields() {
        int n = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack st = stackAt(i);
            if (ItemKinds.is(st, Material.SHIELD) && isServiceable(st)) {
                n++;
            }
        }

        ItemStack off = bot.getInventory().getItemInOffHand();
        if (ItemKinds.is(off, Material.SHIELD) && isServiceable(off)) {
            n++;
        }

        return n;
    }

    private int countWeapons() {
        int n = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack st = stackAt(i);
            if (!ItemKinds.isEmpty(st)
                    && isServiceable(st)
                    && (ItemKinds.isSword(st) || ItemKinds.isAxe(st) || ItemKinds.isSpear(st) || isOf(st, Material.MACE) || isOf(st, Material.TRIDENT))) {
                n++;
            }
        }

        return n;
    }

    private boolean hasElytra() {
        return ItemKinds.is(bot.getEquipment().getItem(EquipmentSlot.CHEST), Material.ELYTRA) ? true : countInInventory(Material.ELYTRA) > 0;
    }

    private void updateBaseline() {
        try {
            for (int i = 0; i < everStocked.length; i++) {
                if (!everStocked[i]) {
                    Supply s = supplies.get(i);
                    if (tallySize < 0) {
                        openCheck();
                    }

                    if (isRelevant(s) && stockedNow(s)) {
                        everStocked[i] = true;
                    }
                }
            }
        } finally {
            tallySize = -1;
        }
    }

    private List<Supply> currentLow() {
        List<Supply> low = new ArrayList<>();
        openCheck();

        try {
            for (int i = 0; i < everStocked.length; i++) {
                Supply s = supplies.get(i);
                if (isRelevant(s) && everStocked[i] && count(s) < s.lowAt()) {
                    low.add(s);
                }
            }
        } finally {
            tallySize = -1;
        }

        return low;
    }

    private void openCheck() {
        PlayerInventory inv = bot.getInventory();
        int n = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            checkStacks[i] = st;
            n = tallyAdd(n, st);
        }

        tallySize = tallyAdd(n, inv.getItemInOffHand());
    }

    private int tallyAdd(int n, ItemStack st) {
        if (ItemKinds.isEmpty(st)) {
            return n;
        } else {
            Material item = st.getType();

            for (int j = 0; j < n; j++) {
                if (tallyItems[j] == item) {
                    tallyCounts[j] = tallyCounts[j] + st.getAmount();
                    return n;
                }
            }

            tallyItems[n] = item;
            tallyCounts[n] = st.getAmount();
            return n + 1;
        }
    }

    private int tallied(Material item) {
        for (int j = 0; j < tallySize; j++) {
            if (tallyItems[j] == item) {
                return tallyCounts[j];
            }
        }

        return 0;
    }

    private ItemStack stackAt(int i) {
        return tallySize >= 0 ? checkStacks[i] : bot.getInventory().getItem(i);
    }

    private static Set<String> keySet(List<Supply> supplies) {
        Set<String> out = new HashSet<>();

        for (Supply s : supplies) {
            out.add(s.key());
        }

        return out;
    }

    private double wornArmorScore() {
        double total = 0.0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack worn = bot.getEquipment().getItem(slot);
            if (!ItemKinds.isEmpty(worn)) {
                total += ArmorEvaluator.score(worn);
            }
        }

        return total;
    }

    private List<Supply> shulkerUpgrade() {
        if (!Double.isNaN(lastUpgradeWornScore) && wornArmorScore() == lastUpgradeWornScore) {
            return List.of();
        } else {
            PlayerInventory inv = bot.getInventory();
            boolean wantArmor = false;
            boolean wantTotem = false;
            boolean noTotem = countInInventory(Material.TOTEM_OF_UNDYING) == 0;

            for (int i = 0; i < 36 && (!wantArmor || !wantTotem); i++) {
                ItemStack box = inv.getItem(i);
                if (isShulkerBox(box)) {
                    for (ItemStack st : ItemKinds.containerContents(box)) {
                        if (noTotem && st.getType() == Material.TOTEM_OF_UNDYING) {
                            wantTotem = true;
                        } else if (ArmorEvaluator.isArmor(st)) {
                            EquipmentSlot slot = ArmorEvaluator.getSlot(st);
                            if (slot != null) {
                                ItemStack worn = bot.getEquipment().getItem(slot);
                                double wornScore = ItemKinds.isEmpty(worn) ? Double.NEGATIVE_INFINITY : ArmorEvaluator.score(worn);
                                if (ArmorEvaluator.score(st) > wornScore) {
                                    wantArmor = true;
                                }
                            }
                        }
                    }
                }
            }

            if (!wantArmor && !wantTotem) {
                return List.of();
            } else {
                List<Supply> out = new ArrayList<>();

                for (Supply s : supplies) {
                    if (wantArmor && "armor".equals(s.key())) {
                        out.add(s);
                    }

                    if (wantTotem && "totem".equals(s.key())) {
                        out.add(s);
                    }
                }

                if (!out.isEmpty()) {
                    ctx.log().decision("RestockController", ARMOR_UPGRADE_AVAILABLE, "[Restock] " + ctx.name() + " has an upgrade waiting in a carried shulker (" + keySet(out) + ")");
                }

                return out;
            }
        }
    }

    private int findShulkerSlot(List<Supply> missing) {
        if (missing != null && !missing.isEmpty()) {
            PlayerInventory inv = bot.getInventory();
            if (keySet(missing).contains("armor")) {
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = inv.getItem(i);
                    if (isShulkerBox(stack) && holdsArmorUpgrade(stack)) {
                        return i;
                    }
                }
            }

            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getItem(i);
                if (isShulkerBox(stack) && shulkerSuppliesAny(stack, missing)) {
                    return i;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    private boolean holdsArmorUpgrade(ItemStack shulker) {
        for (ItemStack st : ItemKinds.containerContents(shulker)) {
            EquipmentSlot slot = ArmorEvaluator.getSlot(st);
            if (slot != null && !isNearlyBroken(st) && ArmorEvaluator.score(st) > bestHeldArmorScore(slot)) {
                return true;
            }
        }

        return false;
    }

    private boolean shulkerSuppliesAny(ItemStack shulker, List<Supply> missing) {
        for (ItemStack s : ItemKinds.containerContents(shulker)) {
            if (matchesAny(s, missing)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesAny(ItemStack stack, List<Supply> missing) {
        for (Supply sup : missing) {
            if (sup.matches().test(stack)) {
                return true;
            }
        }

        return false;
    }

    // ---- ender chest ----

    private boolean enderRegearEnabled() {
        return ctx.settings().enderChestRegear();
    }

    private int findEnderChestSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.ENDER_CHEST)) {
                return i;
            }
        }

        return -1;
    }

    private int findEnderShulkerSlot(Inventory ec, List<Supply> missing) {
        if (missing != null && !missing.isEmpty()) {
            for (int i = 0; i < ec.getSize(); i++) {
                ItemStack stack = ec.getItem(i);
                if (isShulkerBox(stack) && shulkerSuppliesAny(stack, missing)) {
                    return i;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    private boolean doPlaceEnderChest() {
        int chestSlot = findEnderChestSlot();
        if (chestSlot < 0) {
            return false;
        } else {
            BlockPos pos = findSidePlacementPos();
            if (pos == null) {
                ctx.log().decision("RestockController", ENDER_CHEST_PLACE_BLOCKED, "[Restock] " + ctx.name() + " - nowhere beside it to put the ender chest", true);
                return false;
            } else if (!placeBlockFromSlot(chestSlot, pos, Material.ENDER_CHEST)) {
                return false;
            } else {
                enderPos = pos;
                ctx.log().decision("RestockController", PLACE_ENDER_CHEST, "[Restock] " + ctx.name() + " put its ender chest down at " + pos);
                return true;
            }
        }
    }

    private BlockPos findSidePlacementPos() {
        Vec3 look = Ent.look(bot);
        int leftX = (int) Math.round(-look.z());
        int leftZ = (int) Math.round(look.x());
        int rightX = -leftX;
        int rightZ = -leftZ;

        for (int[] d : new int[][]{{leftX, leftZ}, {rightX, rightZ}}) {
            if (d[0] != 0 || d[1] != 0) {
                BlockPos spot = groundedSpot(Ent.blockPos(bot).offset(d[0], 0, d[1]));
                if (spot != null) {
                    return spot;
                }
            }
        }

        return findPlacementPos();
    }

    private boolean takeShulkerFromEnder(Inventory ec, List<Supply> missing) {
        int slot = findEnderShulkerSlot(ec, missing);
        if (slot < 0) {
            return false;
        } else {
            ItemStack box = ec.getItem(slot).clone();
            if (ItemKinds.isEmpty(box)) {
                return false;
            } else if (addToInventory(box.clone()) != null) {
                return false;
            } else {
                ec.setItem(slot, null);
                enderSlotUsed = slot;
                ctx.hands().swing(EquipmentSlot.HAND);
                ctx.log().decision("RestockController", TAKE_SHULKER, "[Restock] " + ctx.name() + " took a shulker out of its ender chest");
                return true;
            }
        }
    }

    private boolean enderHasLoose(Inventory ec, List<Supply> missing) {
        if (missing != null && !missing.isEmpty()) {
            for (int i = 0; i < ec.getSize(); i++) {
                ItemStack stack = ec.getItem(i);
                if (!ItemKinds.isEmpty(stack) && !isShulkerBox(stack) && matchesAny(stack, missing)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private int takeLooseFromEnder(Inventory ec, List<Supply> missing) {
        if (missing != null && !missing.isEmpty()) {
            int taken = 0;

            for (int i = 0; i < ec.getSize(); i++) {
                ItemStack src = ec.getItem(i);
                if (!ItemKinds.isEmpty(src) && !isShulkerBox(src) && matchesAny(src, missing)) {
                    int want = wantFor(src);
                    if (want > 0) {
                        ItemStack move = src.clone();
                        move.setAmount(Math.min(want, src.getAmount()));
                        int offered = move.getAmount();
                        ItemStack leftover = addToInventory(move);
                        int moved = offered - (leftover == null ? 0 : leftover.getAmount());
                        if (moved > 0) {
                            setStackAmount(ec, i, src, src.getAmount() - moved);
                            taken++;
                        }
                    }
                }
            }

            if (taken > 0) {
                ctx.hands().swing(EquipmentSlot.HAND);
                equipBetterArmor();
                ctx.log().decision("RestockController", TAKE_LOOSE_STACKS, "[Restock] " + ctx.name() + " took " + taken + " loose stack(s) from its ender chest");
            }

            return taken;
        } else {
            return 0;
        }
    }

    private void stowShulkerInEnder(Inventory ec) {
        PlayerInventory inv = bot.getInventory();
        int i = returnedBoxSlot;
        returnedBoxSlot = -1;
        if (i >= 0 && i < 36) {
            ItemStack stack = inv.getItem(i);
            if (isShulkerBox(stack)) {
                int dest = enderSlotUsed >= 0 && ItemKinds.isEmpty(ec.getItem(enderSlotUsed)) ? enderSlotUsed : firstEmptySlot(ec);
                if (dest >= 0) {
                    ec.setItem(dest, stack.clone());
                    inv.setItem(i, null);
                    ctx.hands().swing(EquipmentSlot.HAND);
                    ctx.log().decision("RestockController", STOW_SHULKER, "[Restock] " + ctx.name() + " put the shulker back in its ender chest");
                }
            }
        }
    }

    private int firstEmptySlot(Inventory ec) {
        for (int i = 0; i < ec.getSize(); i++) {
            if (ItemKinds.isEmpty(ec.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    // ---- placement spots ----

    private BlockPos findPlacementPos() {
        Vec3 look = Ent.look(bot);
        double dx = look.x();
        double dz = look.z();
        int offX;
        int offZ;
        if (Math.abs(dx) >= Math.abs(dz)) {
            offX = dx > 0.0 ? 1 : -1;
            offZ = 0;
        } else {
            offX = 0;
            offZ = dz > 0.0 ? 1 : -1;
        }

        int[][] dirs = new int[][]{{offX, offZ}, {offZ, offX}, {-offZ, -offX}, {-offX, -offZ}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] d : dirs) {
            if (d[0] != 0 || d[1] != 0) {
                BlockPos col = Ent.blockPos(bot).offset(d[0], 0, d[1]);
                BlockPos spot = groundedSpot(col);
                if (spot != null) {
                    return spot;
                }
            }
        }

        return null;
    }

    private BlockPos groundedSpot(BlockPos col) {
        for (int dy = 0; dy <= GROUND_SCAN_DEPTH; dy++) {
            BlockPos pos = col.below(dy);
            if (ctx.world().isAir(pos) && ctx.world().blocksMotion(pos.below())) {
                return pos;
            }
        }

        return null;
    }

    // ---- item classification ----

    private boolean isShulkerBox(ItemStack stack) {
        return !ItemKinds.isEmpty(stack) && ItemKinds.isShulkerBox(stack);
    }

    private int carryTarget(ItemStack st) {
        int override = plan.carryTarget(ctx.squad().key(), st);
        return override > 0 ? override : builtInTarget(ctx.tuning(), st);
    }

    public static int builtInTarget(BotTuning.Profile profile, ItemStack st) {
        int fixed = fixedTarget(profile, st.getType());
        if (fixed >= 0) {
            return fixed;
        } else if (ItemKinds.isPickaxe(st)) {
            return profile.integer(TuningKey.RESTOCK_TARGET_PICKAXE);
        } else if (ItemKinds.isArrow(st)) {
            return profile.integer(TuningKey.RESTOCK_TARGET_ARROW);
        } else if (ItemKinds.isFood(st)) {
            return profile.integer(TuningKey.RESTOCK_TARGET_FOOD);
        } else if (ItemKinds.isSword(st) || ItemKinds.isAxe(st) || ItemKinds.isSpear(st)) {
            return profile.integer(TuningKey.RESTOCK_TARGET_MELEE);
        } else if (ArmorEvaluator.isArmor(st)) {
            return profile.integer(TuningKey.RESTOCK_TARGET_ARMOR);
        } else {
            Material item = st.getType();
            return !ItemKinds.isPlanks(st) && item != Material.COBBLESTONE && item != Material.DIRT && item != Material.END_STONE
                    ? profile.integer(TuningKey.RESTOCK_TARGET_OTHER)
                    : profile.integer(TuningKey.RESTOCK_TARGET_CHEAP_BLOCK);
        }
    }

    /** Carry target of the items classed by material alone; -1 when the material is not one of them. */
    static int fixedTarget(BotTuning.Profile profile, Material item) {
        return switch (item) {
            case TOTEM_OF_UNDYING -> profile.integer(TuningKey.RESTOCK_TARGET_TOTEM);
            case ENDER_PEARL, GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE, GOLDEN_CARROT, FIREWORK_ROCKET ->
                    profile.integer(TuningKey.RESTOCK_TARGET_PEARL_GAP);
            case EXPERIENCE_BOTTLE -> profile.integer(TuningKey.RESTOCK_TARGET_XP);
            case WIND_CHARGE, END_CRYSTAL, OBSIDIAN, COBWEB -> profile.integer(TuningKey.RESTOCK_TARGET_CRYSTAL_WC);
            case GLOWSTONE, CHORUS_FRUIT, BREEZE_ROD -> profile.integer(TuningKey.RESTOCK_TARGET_GLOWSTONE_CHORUS);
            case RESPAWN_ANCHOR, WATER_BUCKET, MILK_BUCKET, HONEY_BOTTLE ->
                    profile.integer(TuningKey.RESTOCK_TARGET_ANCHOR_BUCKET);
            case SHULKER_SHELL -> profile.integer(TuningKey.RESTOCK_TARGET_SHULKER_SHELL);
            case SPLASH_POTION, LINGERING_POTION -> profile.integer(TuningKey.RESTOCK_TARGET_SPLASH);
            case TNT_MINECART -> profile.integer(TuningKey.RESTOCK_TARGET_TNT_CART);
            case RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL -> profile.integer(TuningKey.RESTOCK_TARGET_RAIL);
            case FLINT_AND_STEEL, ELYTRA, SHEARS, BUCKET -> profile.integer(TuningKey.RESTOCK_TARGET_SINGLE_TOOL);
            case SHIELD, MACE, TRIDENT, BOW, CROSSBOW -> profile.integer(TuningKey.RESTOCK_TARGET_SHIELD_WEAPON);
            case POTION -> profile.integer(TuningKey.RESTOCK_TARGET_POTION);
            case SNOWBALL, EGG, LADDER -> profile.integer(TuningKey.RESTOCK_TARGET_THROWABLE);
            case FIRE_CHARGE, TNT -> profile.integer(TuningKey.RESTOCK_TARGET_FIRE_TNT);
            case SLIME_BLOCK, HAY_BLOCK -> profile.integer(TuningKey.RESTOCK_TARGET_SLIME_HAY);
            default -> -1;
        };
    }

    private int countHeldLike(ItemStack st) {
        Material item = st.getType();
        boolean armour = ArmorEvaluator.isArmor(st);
        boolean weapon = isWeaponLike(st);
        int n = 0;
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < InventoryUtils.SIZE; i++) {
            ItemStack held = inv.getItem(i);
            if (!ItemKinds.isEmpty(held)) {
                if (armour) {
                    if (ArmorEvaluator.isArmor(held) && isServiceable(held)) {
                        n += held.getAmount();
                    }
                } else if (weapon) {
                    if (isWeaponLike(held) && isServiceable(held)) {
                        n += held.getAmount();
                    }
                } else if (held.getType() == item) {
                    n += held.getAmount();
                }
            }
        }

        return n;
    }

    private static final Set<Material> NEEDED = EnumSet.of(
            Material.ENDER_CHEST, Material.WIND_CHARGE, Material.COBWEB, Material.WATER_BUCKET, Material.TOTEM_OF_UNDYING,
            Material.SPLASH_POTION, Material.LINGERING_POTION, Material.GLOWSTONE, Material.END_CRYSTAL, Material.OBSIDIAN,
            Material.RESPAWN_ANCHOR, Material.ENDER_PEARL, Material.EXPERIENCE_BOTTLE, Material.BREEZE_ROD, Material.FIREWORK_ROCKET,
            Material.CHORUS_FRUIT, Material.MACE, Material.TRIDENT, Material.BOW, Material.CROSSBOW, Material.SHIELD, Material.ELYTRA,
            Material.HONEY_BOTTLE, Material.MILK_BUCKET, Material.SHULKER_SHELL, Material.SHEARS, Material.BUCKET, Material.SNOWBALL,
            Material.EGG, Material.POTION, Material.FIRE_CHARGE, Material.TNT, Material.SLIME_BLOCK, Material.HAY_BLOCK, Material.LADDER,
            Material.COBBLESTONE, Material.DIRT, Material.END_STONE);

    private boolean isNeededItem(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return false;
        } else {
            Material item = stack.getType();
            if (ctx.settings().useCarting() && (item == Material.TNT_MINECART || isRailItem(item) || item == Material.FLINT_AND_STEEL)) {
                return true;
            }

            return NEEDED.contains(item)
                    || isShulkerBox(stack)
                    || ItemKinds.isFood(stack)
                    || ItemKinds.isArrow(stack)
                    || ItemKinds.isPickaxe(stack)
                    || ArmorEvaluator.isArmor(stack)
                    || ItemKinds.isSword(stack)
                    || ItemKinds.isAxe(stack)
                    || ItemKinds.isSpear(stack)
                    || ItemKinds.isPlanks(stack);
        }
    }

    // The tail checks after isNeededItem (tools, weapons, armour, shield, pearl) are all already covered by it
    private boolean isUsefulItem(ItemStack stack) {
        if (ItemKinds.isEmpty(stack)) {
            return true;
        } else if ((ArmorEvaluator.isArmor(stack) || isWeaponLike(stack) || stack.getType() == Material.SHIELD)
                && isWornOut(stack)
                && !canMendInstead(stack)) {
            return false;
        } else {
            return isNeededItem(stack);
        }
    }

    private boolean hasEnderPearl() {
        return countInInventory(Material.ENDER_PEARL) > 0;
    }

    private int countInInventory(Material item) {
        if (tallySize >= 0) {
            return tallied(item);
        } else {
            int count = 0;

            for (int i = 0; i < 36; i++) {
                ItemStack stack = bot.getInventory().getItem(i);
                if (ItemKinds.is(stack, item)) {
                    count += stack.getAmount();
                }
            }

            ItemStack off = bot.getInventory().getItemInOffHand();
            if (ItemKinds.is(off, item)) {
                count += off.getAmount();
            }

            return count;
        }
    }

    private int countRailsInInventory() {
        if (tallySize >= 0) {
            return tallied(Material.RAIL) + tallied(Material.POWERED_RAIL) + tallied(Material.DETECTOR_RAIL) + tallied(Material.ACTIVATOR_RAIL);
        } else {
            int count = 0;

            for (int i = 0; i < 36; i++) {
                ItemStack stack = bot.getInventory().getItem(i);
                if (!ItemKinds.isEmpty(stack) && isRailItem(stack.getType())) {
                    count += stack.getAmount();
                }
            }

            ItemStack off = bot.getInventory().getItemInOffHand();
            if (!ItemKinds.isEmpty(off) && isRailItem(off.getType())) {
                count += off.getAmount();
            }

            return count;
        }
    }

    static boolean isRailItem(Material item) {
        return item == Material.RAIL || item == Material.POWERED_RAIL || item == Material.DETECTOR_RAIL || item == Material.ACTIVATOR_RAIL;
    }

    private int countFood() {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = stackAt(i);
            if (ItemKinds.isFood(stack)) {
                count += stack.getAmount();
            }
        }

        ItemStack off = bot.getInventory().getItemInOffHand();
        if (ItemKinds.isFood(off)) {
            count += off.getAmount();
        }

        return count;
    }

    private int countArrows() {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack s = stackAt(i);
            if (ItemKinds.isArrow(s)) {
                count += s.getAmount();
            }
        }

        ItemStack off = bot.getInventory().getItemInOffHand();
        if (ItemKinds.isArrow(off)) {
            count += off.getAmount();
        }

        return count;
    }

    private boolean hasBowInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = stackAt(i);
            if (ItemKinds.is(s, Material.BOW) || ItemKinds.is(s, Material.CROSSBOW)) {
                return true;
            }
        }

        return false;
    }

    private int freeSlots() {
        PlayerInventory inv = bot.getInventory();
        int n = 0;

        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isEmpty(inv.getItem(i))) {
                n++;
            }
        }

        return n;
    }

    private int roomKeepingReserve(ItemStack stack) {
        PlayerInventory inv = bot.getInventory();
        int room = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack existing = inv.getItem(i);
            if (!ItemKinds.isEmpty(existing) && existing.isSimilar(stack)) {
                room += Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            }
        }

        return room
                + Math.max(0, freeSlots() - ctx.tuning().integer(TuningKey.RESTOCK_CONTAINER_RESERVE))
                        * stack.getMaxStackSize();
    }

    /** Adds the stack to the bot's own inventory; returns what did not fit, or null when everything did. */
    private ItemStack addToInventory(ItemStack stack) {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36 && stack != null; i++) {
            ItemStack existing = inv.getItem(i);
            if (!ItemKinds.isEmpty(existing) && existing.isSimilar(stack)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space > 0) {
                    int take = Math.min(space, stack.getAmount());
                    ItemStack grown = existing.clone();
                    grown.setAmount(existing.getAmount() + take);
                    inv.setItem(i, grown);
                    stack.setAmount(stack.getAmount() - take);
                    if (stack.getAmount() <= 0) {
                        stack = null;
                    }
                }
            }
        }

        for (int i = 0; i < 36 && stack != null; i++) {
            if (ItemKinds.isEmpty(inv.getItem(i))) {
                inv.setItem(i, stack.clone());
                return null;
            }
        }

        return stack;
    }

    private enum Phase {
        IDLE,
        PEARL_WAIT,
        ELYTRA_LAUNCH,
        PLACE_ENDER,
        TAKE_SHULKER,
        PLACE,
        OPEN,
        MINE,
        COLLECT,
        STOW_SHULKER,
        MINE_ENDER,
        COLLECT_ENDER
    }

    private record Supply(
            String key, BooleanSupplier relevant, IntSupplier count, int lowAt, Predicate<ItemStack> matches, Material[] items, boolean carried, Material[] needs) {
    }
}
