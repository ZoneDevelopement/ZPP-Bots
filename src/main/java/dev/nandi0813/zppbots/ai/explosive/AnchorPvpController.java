package dev.nandi0813.zppbots.ai.explosive;

import dev.nandi0813.zppbots.ai.move.PathFollower;
import dev.nandi0813.zppbots.ai.web.PlacementRaycasts;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.settings.DifficultyParams;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.BotMath;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class AnchorPvpController {

    private static final String ANCHOR_START = "ANCHOR_START";
    private static final double PLACE_RANGE = 4.5;
    private static final double ATTACK_REACH = 3.0;
    private static final int MAX_SESSION_TICKS = 200;
    private static final double CHAIN_REACH = 4.5;
    private static final int CHAIN_MAX_DEPTH = 8;
    private static final double CHAIN_BLAST_REACH = 10.0;
    private static final int CHAIN_WALK_LIMIT = 30;

    private final BotContext ctx;
    private final Player bot;
    private boolean active = false;
    private int sessionTick = 0;
    private int cooldownLeft = 0;
    private int lastRoundTick = -1000;
    private boolean kbLaunched = false;
    private int prevSlot = 0;
    private Mode mode = Mode.SINGLE;
    private int roundsLeft = 0;
    private int chainX;
    private int chainZ;
    private int chainTopY;
    private int chainWalkTicks = 0;
    private boolean chainEnded = false;
    private int lastDetonate = -10000;
    private BlockPos roundCell = null;
    private boolean roundCharged = false;

    public AnchorPvpController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick(LivingEntity target, double dist, boolean chain) {
        if (cooldownLeft > 0) {
            cooldownLeft--;
            return false;
        } else {
            return active ? tickActive(target, dist) : tickIdle(target, dist, chain);
        }
    }

    public boolean isExecuting() {
        return active;
    }

    public boolean isCoolingDown() {
        return !active && cooldownLeft > 0;
    }

    public boolean takeChainEnded() {
        boolean e = chainEnded;
        chainEnded = false;
        return e;
    }

    public int lastDetonateTick() {
        return lastDetonate;
    }

    public void reset() {
        if (active) {
            InventoryUtils.select(ctx, prevSlot);
        }

        active = false;
        sessionTick = 0;
        kbLaunched = false;
        lastRoundTick = -1000;
        mode = Mode.SINGLE;
        roundsLeft = 0;
        chainWalkTicks = 0;
        chainEnded = false;
        roundCell = null;
        roundCharged = false;
    }

    private boolean tickIdle(LivingEntity target, double dist, boolean chain) {
        if (target == null || !Ent.alive(target)) {
            return false;
        } else if (!bot.hasLineOfSight(target)) {
            return false;
        } else if (!(dist < ctx.tuning().number(TuningKey.ANCHOR_MIN_RANGE).doubleValue())
                && !(dist > ctx.tuning().number(TuningKey.ANCHOR_MAX_RANGE).doubleValue())) {
            if (ctx.world().bukkit().getEnvironment() == World.Environment.NETHER) {
                return false;
            } else if (findInInventory(Material.RESPAWN_ANCHOR) < 0) {
                return false;
            } else if (findInInventory(Material.GLOWSTONE) < 0) {
                return false;
            } else if (CrystalSkill.skipSession(ctx)) {
                cooldownLeft = CrystalSkill.hesitateTicks(ctx);
                return false;
            } else {
                int rounds = Math.min(
                        Math.min(countInInventory(Material.RESPAWN_ANCHOR), countInInventory(Material.GLOWSTONE)),
                        CrystalSkill.maxRounds(ctx));
                BlockPos spot = chain ? chainStart(target) : null;
                if (spot != null) {
                    mode = Mode.CHAIN;
                    roundsLeft = Math.min(
                            ctx.tuning().integer(TuningKey.ANCHOR_CHAIN_ROUNDS_MIN)
                                    + ctx.random().nextInt(ctx.tuning().integer(TuningKey.ANCHOR_CHAIN_ROUNDS_SPAN)),
                            rounds);
                    chainX = spot.getX();
                    chainZ = spot.getZ();
                    chainTopY = Ent.blockPos(target).getY();
                    chainWalkTicks = 0;
                } else if (rounds >= 2) {
                    mode = Mode.SPAM;
                    roundsLeft = Math.min(
                            ctx.tuning().integer(TuningKey.ANCHOR_SPAM_ROUNDS_MIN)
                                    + ctx.random().nextInt(ctx.tuning().integer(TuningKey.ANCHOR_SPAM_ROUNDS_SPAN)),
                            rounds);
                } else {
                    mode = Mode.SINGLE;
                    roundsLeft = 1;
                }

                prevSlot = InventoryUtils.selected(ctx);
                active = true;
                sessionTick = 0;
                kbLaunched = false;
                lastRoundTick = -1000;
                ctx.log().decision("AnchorPvpController", ANCHOR_START, String.format("[Anchor] starting %s mode (%d rounds) at dist=%.1f", mode, roundsLeft, dist));
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean tickActive(LivingEntity target, double dist) {
        sessionTick++;
        double abortDist = mode == Mode.SINGLE
                ? ctx.tuning().number(TuningKey.ANCHOR_SINGLE_ABORT_DIST).doubleValue()
                : ctx.tuning().number(TuningKey.ANCHOR_CHAIN_ABORT_DIST).doubleValue();
        if (target != null
                && Ent.alive(target)
                && (mode == Mode.CHAIN || bot.hasLineOfSight(target))
                && !(dist > abortDist)
                && sessionTick <= MAX_SESSION_TICKS) {
            if (mode == Mode.CHAIN) {
                return tickChain(target);
            } else {
                Vec3 targetPos = Ent.pos(target);
                aimAt(targetPos.x(), targetPos.y() + 0.5, targetPos.z());
                ctx.controls().stopMoving();
                if (!kbLaunched) {
                    if (bot.isOnGround() && dist <= ATTACK_REACH && bot.hasLineOfSight(target)) {
                        kbLaunch(target);
                    }

                    kbLaunched = true;
                    return true;
                } else {
                    BlockPos cell = roundCell;
                    if (cell == null) {
                        if (sessionTick - lastRoundTick < roundGap()) {
                            return true;
                        }

                        if (findInInventory(Material.RESPAWN_ANCHOR) < 0 || findInInventory(Material.GLOWSTONE) < 0) {
                            finish();
                            return false;
                        }

                        cell = mode == Mode.SPAM ? findSpamPlacePosition(target) : findPlacePosition(target);
                        if (cell == null) {
                            cell = chainStart(target);
                        }

                        if (cell == null) {
                            finish();
                            return false;
                        }
                    }

                    Step st = stepRound(cell);
                    if (st == Step.FAIL) {
                        finish();
                        return false;
                    } else {
                        if (st == Step.DONE && --roundsLeft <= 0) {
                            finish();
                        }

                        return true;
                    }
                }
            }
        } else {
            finish();
            return false;
        }
    }

    private boolean tickChain(LivingEntity target) {
        Vec3 spot = new Vec3(chainX + 0.5, chainTopY + 0.5, chainZ + 0.5);
        if (Ent.pos(target).distanceTo(spot) > CHAIN_BLAST_REACH) {
            finish();
            return false;
        } else if (roundCell != null) {
            ctx.controls().stopMoving();
            Step st = stepRound(roundCell);
            if (st == Step.FAIL) {
                finish();
                return false;
            } else {
                if (st == Step.DONE && --roundsLeft <= 0) {
                    finish();
                }

                return true;
            }
        } else if (sessionTick - lastRoundTick < roundGap()) {
            ctx.controls().stopMoving();
            return true;
        } else if (findInInventory(Material.RESPAWN_ANCHOR) >= 0 && findInInventory(Material.GLOWSTONE) >= 0) {
            BlockPos cell = chainCell();
            if (cell == null) {
                finish();
                return false;
            } else if (!(Ent.eyePos(bot).distanceTo(Vec3.atCenterOf(cell)) > CHAIN_REACH)
                    && PlacementRaycasts.canSeePlacement(ctx, cell)) {
                chainWalkTicks = 0;
                ctx.controls().stopMoving();
                if (stepRound(cell) == Step.FAIL) {
                    finish();
                    return false;
                } else {
                    return true;
                }
            } else if (++chainWalkTicks > CHAIN_WALK_LIMIT) {
                finish();
                return false;
            } else {
                walkToward(Vec3.atCenterOf(cell), target);
                return true;
            }
        } else {
            finish();
            return false;
        }
    }

    private BlockPos chainStart(LivingEntity target) {
        BlockPos feet = Ent.blockPos(target);
        chainTopY = feet.getY();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    BlockPos c = columnFloor(feet.getX() + dx, feet.getZ() + dz);
                    if (c != null
                            && free(c)
                            && !(Ent.eyePos(bot).distanceTo(Vec3.atCenterOf(c)) > CHAIN_REACH)
                            && PlacementRaycasts.canSeePlacement(ctx, c)) {
                        double score = (dx != 0 && dz != 0 ? 10.0 : 0.0)
                                + Ent.pos(bot).distanceTo(Vec3.atCenterOf(c));
                        if (score < bestScore) {
                            bestScore = score;
                            best = c;
                        }
                    }
                }
            }
        }

        return best;
    }

    private BlockPos chainCell() {
        BlockPos c = columnFloor(chainX, chainZ);
        if (c != null && free(c)) {
            return c;
        } else if (c == null && !ctx.world().canReplace(new BlockPos(chainX, chainTopY, chainZ))) {
            return null;
        } else {
            for (Direction d : Direction.HORIZONTAL) {
                BlockPos s = columnFloor(chainX + d.getStepX(), chainZ + d.getStepZ());
                if (s != null && free(s)) {
                    return s;
                }
            }

            return null;
        }
    }

    private BlockPos columnFloor(int x, int z) {
        for (int y = chainTopY; y >= chainTopY - CHAIN_MAX_DEPTH; y--) {
            BlockPos c = new BlockPos(x, y, z);
            if (ctx.world().type(c) == Material.RESPAWN_ANCHOR) {
                return c;
            }

            if (!ctx.world().canReplace(c)) {
                return null;
            }

            if (ctx.world().isFullBlock(c.below())) {
                return c;
            }
        }

        return null;
    }

    private boolean free(BlockPos c) {
        return ctx.world().entities(LivingEntity.class, AABB.ofBlock(c), e -> true).isEmpty();
    }

    private void walkToward(Vec3 p, LivingEntity target) {
        Vec3 pos = Ent.pos(bot);
        double dx = p.x() - pos.x();
        double dz = p.z() - pos.z();
        Vec3 targetPos = Ent.pos(target);
        aimAt(targetPos.x(), targetPos.y() + 0.5, targetPos.z());
        if (dx * dx + dz * dz > 1.0E-6) {
            float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float[] input = PathFollower.yawToInput(bot.getLocation().getYaw(), wantYaw);
            ctx.controls().setMovement(input[0], input[1]);
        }
        ctx.controls().setSprinting(true);
    }

    private Step stepRound(BlockPos cell) {
        if (CrystalSkill.fumble(ctx)) {
            return Step.BUSY;
        } else if (roundCell != null) {
            if (ctx.world().type(roundCell) != Material.RESPAWN_ANCHOR) {
                roundCell = null;
                return Step.FAIL;
            } else if (!roundCharged) {
                if (CrystalSkill.anchorDud(ctx)) {
                    roundCharged = true;
                    return Step.BUSY;
                } else if (!use(Material.GLOWSTONE, roundCell, Direction.UP, Vec3.atCenterOf(roundCell))) {
                    roundCell = null;
                    return Step.FAIL;
                } else {
                    roundCharged = true;
                    return Step.BUSY;
                }
            } else {
                int totem = findInInventory(Material.TOTEM_OF_UNDYING);
                select(totem >= 0 ? totem : nonGlowstoneSlot());
                Vec3 hit = Vec3.atCenterOf(roundCell);
                lookAt(hit);
                ctx.hands().useItemOn(EquipmentSlot.HAND, roundCell, Direction.UP, hit);
                ctx.hands().swing(EquipmentSlot.HAND);
                roundCell = null;
                lastRoundTick = sessionTick;
                lastDetonate = ctx.tick();
                return Step.DONE;
            }
        } else if (ctx.world().type(cell) == Material.RESPAWN_ANCHOR
                || use(Material.RESPAWN_ANCHOR, cell.below(), Direction.UP, new Vec3(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5))
                        && ctx.world().type(cell) == Material.RESPAWN_ANCHOR) {
            roundCell = cell;
            roundCharged = false;
            return Step.BUSY;
        } else {
            return Step.FAIL;
        }
    }

    private int roundGap() {
        return Math.max(1, getPlaceInterval(ctx.difficulty()));
    }

    private boolean use(Material item, BlockPos on, Direction face, Vec3 hit) {
        int slot = findInInventory(item);
        if (slot < 0) {
            return false;
        } else {
            select(slot);
            ItemStack stack = bot.getInventory().getItem(InventoryUtils.selected(ctx));
            if (!ItemKinds.is(stack, item)) {
                return false;
            } else {
                lookAt(hit);
                ctx.hands().useItemOn(EquipmentSlot.HAND, on, face, hit);
                ctx.hands().swing(EquipmentSlot.HAND);
                return true;
            }
        }
    }

    private void select(int slot) {
        if (slot >= 9) {
            slot = ensureHotbar(slot);
        }

        InventoryUtils.select(ctx, slot);
    }

    private int nonGlowstoneSlot() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (!ItemKinds.is(inv.getItem(i), Material.GLOWSTONE)) {
                return i;
            }
        }

        return 0;
    }

    private int anchorCooldown() {
        return Math.max(1, ctx.difficulty().anchorCooldownTicks);
    }

    private BlockPos findPlaceBehindTarget(LivingEntity target) {
        Vec3 targetPos = Ent.pos(target);
        Vec3 look = Ent.look(target);
        Vec3 facing = new Vec3(look.x(), 0.0, look.z());
        if (facing.lengthSqr() < 1.0E-4) {
            return null;
        } else {
            facing = facing.normalize();
            BlockPos best = null;
            double bestScore = 0.3;

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = 0; dy >= -1; dy--) {
                        BlockPos at = BlockPos.containing(targetPos.x() + dx, targetPos.y() + dy, targetPos.z() + dz);
                        if (canPlaceAt(at) && !(Ent.pos(bot).distanceTo(Vec3.atCenterOf(at)) > PLACE_RANGE)) {
                            Vec3 flat = new Vec3(at.getX() + 0.5 - targetPos.x(), 0.0, at.getZ() + 0.5 - targetPos.z());
                            if (!(flat.lengthSqr() < 0.25)) {
                                double score = -flat.normalize().dot(facing);
                                if (!(score <= bestScore)) {
                                    bestScore = score;
                                    best = at;
                                }
                            }
                        }
                    }
                }
            }

            return best;
        }
    }

    private BlockPos findPlacePosition(LivingEntity target) {
        Vec3 targetPos = Ent.pos(target);
        if (target instanceof HumanEntity human && human.isBlocking()) {
            BlockPos behind = findPlaceBehindTarget(target);
            if (behind != null) {
                return behind;
            }
        }

        Vec3 botPos = Ent.pos(bot);
        Vec3 dir = targetPos.subtract(botPos).normalize();

        for (double d = 2.0; d <= 4.0; d += 0.5) {
            Vec3 cand = botPos.add(dir.scale(d));
            BlockPos base = BlockPos.containing(cand.x(), targetPos.y(), cand.z());
            if (canPlaceAt(base)) {
                double distToBot = botPos.distanceTo(Vec3.atCenterOf(base));
                if (distToBot <= PLACE_RANGE) {
                    return base;
                }
            }

            base = BlockPos.containing(cand.x(), targetPos.y() - 1.0, cand.z());
            if (canPlaceAt(base)) {
                double distToBot = botPos.distanceTo(Vec3.atCenterOf(base));
                if (distToBot <= PLACE_RANGE) {
                    return base;
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos check = BlockPos.containing(targetPos.x() + dx, targetPos.y(), targetPos.z() + dz);
                if (canPlaceAt(check)) {
                    double distToBot = botPos.distanceTo(Vec3.atCenterOf(check));
                    if (distToBot <= PLACE_RANGE) {
                        return check;
                    }
                }
            }
        }

        return null;
    }

    private BlockPos findSpamPlacePosition(LivingEntity target) {
        Vec3 tp = Ent.pos(target);
        Vec3 botPos = Ent.pos(bot);
        BlockPos above = BlockPos.containing(tp.x(), tp.y() + 2.0, tp.z());
        if (canPlaceAt(above) && botPos.distanceTo(Vec3.atCenterOf(above)) <= PLACE_RANGE) {
            return above;
        } else {
            Vec3 toBot = botPos.subtract(tp).normalize();

            for (double d = 1.0; d <= 3.0; d += 0.5) {
                Vec3 cand = tp.add(toBot.scale(d));
                BlockPos bp = BlockPos.containing(cand.x(), tp.y(), cand.z());
                if (canPlaceAt(bp) && botPos.distanceTo(Vec3.atCenterOf(bp)) <= PLACE_RANGE) {
                    return bp;
                }

                bp = BlockPos.containing(cand.x(), tp.y() - 1.0, cand.z());
                if (canPlaceAt(bp) && botPos.distanceTo(Vec3.atCenterOf(bp)) <= PLACE_RANGE) {
                    return bp;
                }
            }

            return findPlacePosition(target);
        }
    }

    private int countInInventory(Material item) {
        int count = 0;
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemKinds.is(s, item)) {
                count += s.getAmount();
            }
        }

        return count;
    }

    private boolean canPlaceAt(BlockPos pos) {
        if (!PlacementRaycasts.canSeePlacement(ctx, pos)) {
            return false;
        } else if (!ctx.world().isAir(pos)) {
            return false;
        } else if (!ctx.world().isFullBlock(pos.below())) {
            return false;
        } else {
            return ctx.world().entities(LivingEntity.class, AABB.ofBlock(pos).inflate(0.1), e -> true).isEmpty();
        }
    }

    private void kbLaunch(LivingEntity target) {
        ctx.controls().setSprinting(true);
        ctx.hands().attack(target);
        ctx.hands().swing(EquipmentSlot.HAND);
    }

    private int getPlaceInterval(DifficultyParams params) {
        return params.anchorIntervalTicks;
    }

    private void finish() {
        if (active) {
            InventoryUtils.select(ctx, prevSlot);
        }

        if (active && mode == Mode.CHAIN) {
            chainEnded = true;
        }

        active = false;
        cooldownLeft = anchorCooldown();
        lastRoundTick = -1000;
        roundsLeft = 0;
        mode = Mode.SINGLE;
        chainWalkTicks = 0;
        roundCell = null;
        roundCharged = false;
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

    private int ensureHotbar(int slot) {
        return InventoryUtils.moveToHotbarSafe(ctx, slot);
    }

    private int findInInventory(Material item) {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), item)) {
                return i;
            }
        }

        return -1;
    }

    private void lookAt(Vec3 pos) {
        aimAt(pos.x(), pos.y(), pos.z());
    }

    private enum Mode {
        SINGLE,
        SPAM,
        CHAIN
    }

    private enum Step {
        BUSY,
        DONE,
        FAIL
    }
}
