package dev.nandi0813.zppbots.ai.buildfight;

import dev.nandi0813.zppbots.ai.move.ScaffoldController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/** Water, lava and wall moves for bots with a build kit. Each move is rolled per tick by difficulty level and never consumes the tick. */
public final class BuildFightController {

    private static final String WATER_RUSH = "WATER_RUSH";
    private static final String LAVA_TRAP = "LAVA_TRAP";
    private static final String WALL_HEAL = "WALL_HEAL";
    private static final String SOURCE_PICKUP = "SOURCE_PICKUP";
    private static final String SOURCE_BLOCKED = "SOURCE_BLOCKED";

    // Blocks the crystal and anchor doctrines place; they never count as build blocks.
    private static final Set<Material> DOCTRINE_BLOCKS = EnumSet.of(
            Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.RESPAWN_ANCHOR, Material.GLOWSTONE);

    private final BotContext ctx;
    private final Player bot;
    private int waterCooldown = 0;
    private int lavaCooldown = 0;
    private int wallCooldown = 0;
    private int armedUntil = 0;
    private BlockPos waterSource = null;
    private int waterPlacedTick = 0;
    private BlockPos lavaSource = null;
    private int lavaPlacedTick = 0;
    private int neutralizeCooldown = 0;

    public BuildFightController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public void tick(LivingEntity target, double dist, boolean healing) {
        if (waterCooldown > 0) waterCooldown--;
        if (lavaCooldown > 0) lavaCooldown--;
        if (wallCooldown > 0) wallCooldown--;
        tickPickups(target);
        tickNeutralize();
        if (target == null || healing || !Ent.alive(target) || bot.isInWater() || !ctx.settings().buildFight() || !hasBuildKit()) return;

        // A passed roll stays armed for a while, waiting for the target to be inside the move's window.
        if (ctx.tick() >= armedUntil) {
            if (ctx.random().nextDouble() >= ctx.difficulty().buildMoveChance) return;
            armedUntil = ctx.tick() + ctx.tuning().integer(TuningKey.BUILD_FIGHT_ARM_TICKS);
        }

        if (tryWaterRush(target, dist) || ctx.settings().difficulty().level() >= ctx.difficulty().lavaTrapLevel && tryLavaTrap(target, dist)) {
            armedUntil = 0;
        }
    }

    /** Two blocks between the bot and a close target, one at feet and one at head height, before a gapple or head eat. */
    public void wallForHeal(LivingEntity target) {
        if (!ctx.settings().buildFight() || wallCooldown > 0 || target == null || !Ent.alive(target) || !bot.isOnGround()
                || Ent.dist(bot, target) > ctx.tuning().number(TuningKey.BUILD_FIGHT_WALL_MAX_DIST).doubleValue()) return;
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        Direction side = Direction.getNearest(targetPos.x() - botPos.x(), 0.0, targetPos.z() - botPos.z());
        if (!side.isHorizontal()) return;

        BlockPos feet = Ent.blockPos(bot).relative(side);
        BlockPos head = feet.above();
        int slot = findSlot(this::isBuildBlock);
        if (slot < 0 || !ctx.world().isAir(feet) || !ctx.world().isFullBlock(feet.below()) || !ctx.world().isAir(head)) return;

        useOnTop(slot, feet.below());
        useOnTop(slot, feet);
        wallCooldown = ctx.tuning().integer(TuningKey.BUILD_FIGHT_WALL_COOLDOWN);
        ctx.log().decision("BuildFightController", WALL_HEAL, "walled " + side + " of the bot before healing (target " + Ent.dist(bot, target) + " blocks).");
    }

    private boolean tryWaterRush(LivingEntity target, double dist) {
        if (waterCooldown > 0 || dist < ctx.tuning().number(TuningKey.BUILD_FIGHT_WATER_MIN_DIST).doubleValue()
                || dist > ctx.tuning().number(TuningKey.BUILD_FIGHT_WATER_MAX_DIST).doubleValue() || !target.isOnGround()) return false;
        boolean sprintingAtBot = target instanceof Player tp && tp.isSprinting();
        if (bot.getHealth() > target.getHealth() + ctx.tuning().number(TuningKey.BUILD_FIGHT_WATER_HP_MARGIN).doubleValue() && !sprintingAtBot) return false;

        BlockPos feet = Ent.blockPos(target);
        BlockPos floor = feet.below();
        if (!ctx.world().isAir(feet) || !ctx.world().isFullBlock(floor)) return false;
        int slot = findSlot(stack -> ItemKinds.is(stack, Material.WATER_BUCKET));
        if (slot < 0) return false;

        pourOnTop(slot, floor);
        waterSource = feet;
        waterPlacedTick = ctx.tick();
        waterCooldown = ctx.tuning().integer(TuningKey.BUILD_FIGHT_WATER_COOLDOWN);
        ctx.log().decision("BuildFightController", WATER_RUSH, "water on the rush at " + feet + " (target " + dist + " blocks).");
        return true;
    }

    private boolean tryLavaTrap(LivingEntity target, double dist) {
        if (lavaCooldown > 0 || dist < ctx.tuning().number(TuningKey.BUILD_FIGHT_LAVA_MIN_DIST).doubleValue()
                || dist > ctx.tuning().number(TuningKey.BUILD_FIGHT_LAVA_MAX_DIST).doubleValue()
                || !bot.isOnGround() || !target.isOnGround() || bot.getFireTicks() > 0) return false;
        // Lava must not flow back to the bot: the bot is not below the target, and the ground between is not lower than the bot's feet.
        if (bot.getLocation().getY() < target.getLocation().getY()) return false;
        Vec3 botPos = Ent.pos(bot);
        BlockPos between = BlockPos.containing(botPos.add(Ent.pos(target)).scale(0.5)).below();
        if (between.getY() < Ent.blockPos(bot).getY() - 1) return false;

        BlockPos feet = Ent.blockPos(target);
        BlockPos floor = feet.below();
        if (!ctx.world().isAir(feet) || !ctx.world().isFullBlock(floor)) return false;
        // Lava spreads about two blocks from its source.
        if (botPos.distanceTo(Vec3.atCenterOf(feet)) <= ctx.tuning().number(TuningKey.BUILD_FIGHT_LAVA_FLOW_RADIUS).doubleValue()) return false;
        int slot = findSlot(stack -> ItemKinds.is(stack, Material.LAVA_BUCKET));
        if (slot < 0) return false;

        pourOnTop(slot, floor);
        lavaSource = feet;
        lavaPlacedTick = ctx.tick();
        lavaCooldown = ctx.tuning().integer(TuningKey.BUILD_FIGHT_LAVA_COOLDOWN);
        ctx.log().decision("BuildFightController", LAVA_TRAP, "lava trap at " + feet + " (target " + dist + " blocks).");
        return true;
    }

    private void tickPickups(LivingEntity target) {
        // Pickup starts at 4 blocks; past the 4.5 block interaction reach the source is given up.
        if (waterSource != null) {
            double reach = Ent.pos(bot).distanceTo(Vec3.atCenterOf(waterSource));
            if (ctx.tick() - waterPlacedTick >= ctx.tuning().integer(TuningKey.BUILD_FIGHT_WATER_PICKUP_TICKS)
                    || reach >= ctx.tuning().number(TuningKey.BUILD_FIGHT_PICKUP_DIST).doubleValue()) {
                // Beyond the interaction reach the scoop would miss, so the source is given up.
                if (reach <= ctx.tuning().number(TuningKey.BUILD_FIGHT_PICKUP_REACH).doubleValue()) scoop(waterSource, false);
                waterSource = null;
            }
        }

        if (lavaSource != null) {
            double reach = Ent.pos(bot).distanceTo(Vec3.atCenterOf(lavaSource));
            boolean targetMoved = target == null
                    || Ent.pos(target).distanceTo(Vec3.atCenterOf(lavaSource)) > ctx.tuning().number(TuningKey.BUILD_FIGHT_LAVA_LEAVE_DIST).doubleValue();
            if (ctx.tick() - lavaPlacedTick >= ctx.tuning().integer(TuningKey.BUILD_FIGHT_LAVA_PICKUP_TICKS)
                    || targetMoved || reach >= ctx.tuning().number(TuningKey.BUILD_FIGHT_PICKUP_DIST).doubleValue()) {
                if (reach <= ctx.tuning().number(TuningKey.BUILD_FIGHT_PICKUP_REACH).doubleValue()) scoop(lavaSource, true);
                lavaSource = null;
            }
        }
    }

    /** Fills or scoops a water or lava source the bot did not place, like a player blocking the enemy's bucket. */
    private void tickNeutralize() {
        if (neutralizeCooldown > 0) {
            neutralizeCooldown--;
            return;
        }
        if (!ctx.settings().buildFight()
                || ctx.settings().difficulty().level() < ctx.tuning().integer(TuningKey.BUILD_FIGHT_NEUTRALIZE_LEVEL)) return;
        neutralizeCooldown = ctx.tuning().integer(TuningKey.BUILD_FIGHT_NEUTRALIZE_SCAN_TICKS);

        double radius = ctx.tuning().number(TuningKey.BUILD_FIGHT_NEUTRALIZE_RADIUS).doubleValue();
        Vec3 eye = Ent.eyePos(bot);
        BlockPos center = Ent.blockPos(bot);
        int r = (int) Math.ceil(radius);
        BlockPos best = null;
        boolean bestLava = false;
        double bestDist = radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    boolean lava = ctx.world().isLavaSource(pos);
                    boolean water = !lava && ctx.world().isWaterSource(pos);
                    if (!(lava || water) || !foreign(pos, waterSource, lavaSource)) continue;
                    double d = eye.distanceTo(Vec3.atCenterOf(pos));
                    if (d > radius) continue;
                    // Lava first: it burns the bot, water only pushes it. Within a kind, the nearest wins.
                    if (best == null || lava && !bestLava || lava == bestLava && d < bestDist) {
                        best = pos;
                        bestLava = lava;
                        bestDist = d;
                    }
                }
            }
        }
        if (best == null) return;

        int bucket = findSlot(stack -> ItemKinds.is(stack, Material.BUCKET));
        int block = findSlot(this::isBuildBlock);
        if (bucket >= 0) {
            scoop(best, bestLava);
        } else if (block >= 0 && ctx.world().isFullBlock(best.below())) {
            useOnTop(block, best.below());
        } else {
            return;
        }
        neutralizeCooldown = ctx.tuning().integer(TuningKey.BUILD_FIGHT_NEUTRALIZE_COOLDOWN);
        ctx.log().decision("BuildFightController", SOURCE_BLOCKED, (bestLava ? "lava" : "water") + " source blocked at " + best + ".");
    }

    static boolean foreign(BlockPos pos, BlockPos ownWater, BlockPos ownLava) {
        return !pos.equals(ownWater) && !pos.equals(ownLava);
    }

    // Scooping uses the bot's own reach: a source farther than the interaction range stays in the world.
    private void scoop(BlockPos at, boolean lava) {
        if (lava ? !ctx.world().isLavaSource(at) : !ctx.world().isWaterSource(at)) return;
        int slot = findSlot(stack -> ItemKinds.is(stack, Material.BUCKET));
        if (slot < 0) return;

        int prev = InventoryUtils.selected(ctx);
        InventoryUtils.select(ctx, slot);
        ctx.controls().lookAt(Vec3.atCenterOf(at));
        ctx.hands().useItem(EquipmentSlot.HAND);
        ctx.hands().swing(EquipmentSlot.HAND);
        InventoryUtils.select(ctx, prev);
        ctx.log().decision("BuildFightController", SOURCE_PICKUP, (lava ? "lava" : "water") + " source picked up at " + at + ".");
    }

    // Buckets have no use-on-block action: like a player, aim at the floor's top face and use the item.
    private void pourOnTop(int slot, BlockPos floor) {
        int prev = InventoryUtils.selected(ctx);
        InventoryUtils.select(ctx, slot);
        ctx.controls().lookAt(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0));
        ctx.hands().useItem(EquipmentSlot.HAND);
        ctx.hands().swing(EquipmentSlot.HAND);
        InventoryUtils.select(ctx, prev);
    }

    private void useOnTop(int slot, BlockPos support) {
        int prev = InventoryUtils.selected(ctx);
        InventoryUtils.select(ctx, slot);
        ctx.controls().lookAt(Vec3.atCenterOf(support));
        ctx.hands().useItemOn(EquipmentSlot.HAND, support, Direction.UP, Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
        ctx.hands().swing(EquipmentSlot.HAND);
        InventoryUtils.select(ctx, prev);
    }

    private int findSlot(Predicate<ItemStack> wanted) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (wanted.test(inv.getItem(i))) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (wanted.test(inv.getItem(i))) return InventoryUtils.moveToHotbarSafe(ctx, i);
        }
        return -1;
    }

    /** Enough build blocks for the scaffold and jump-place moves even when the settings leave them off. */
    public boolean scaffoldKit() {
        return blockCount() >= ctx.tuning().integer(TuningKey.BUILD_FIGHT_SCAFFOLD_KIT_BLOCKS);
    }

    private boolean hasBuildKit() {
        return blockCount() >= ctx.tuning().integer(TuningKey.BUILD_FIGHT_MIN_BUILD_BLOCKS) || holds(Material.WATER_BUCKET) || holds(Material.LAVA_BUCKET);
    }

    private boolean isBuildBlock(ItemStack stack) {
        return ScaffoldController.isPillarBlock(ctx, stack) && !DOCTRINE_BLOCKS.contains(stack.getType());
    }

    private int blockCount() {
        PlayerInventory inv = bot.getInventory();
        int total = 0;
        for (int i = 0; i <= 40; i++) {
            ItemStack stack = inv.getItem(i);
            if (isBuildBlock(stack)) total += stack.getAmount();
        }
        return total;
    }

    private boolean holds(Material material) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i <= 40; i++) {
            if (ItemKinds.is(inv.getItem(i), material)) return true;
        }
        return false;
    }
}
