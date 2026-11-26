package dev.nandi0813.zppbots.ai.explosive;

import dev.nandi0813.zppbots.ai.gear.OffhandManager;
import dev.nandi0813.zppbots.ai.move.PathFollower;
import dev.nandi0813.zppbots.ai.web.PlacementRaycasts;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.BotMath;
import dev.nandi0813.zppbots.util.InventoryUtils;
import dev.nandi0813.zppbots.util.LedgeGuard;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public final class CrystalPvpController {

    private static final String CRYSTAL_START = "CRYSTAL_START";
    private static final double PLACE_RANGE = 4.5;
    private static final double ATTACK_REACH = 3.0;
    private static final double CRYSTAL_BLAST_DIAMETER = 12.0;
    private static final int REPOSITION_TIMEOUT = 20;
    private static final double REPOSITION_ARRIVE = 1.2;
    private static final int REPOSITION_SCAN = 6;
    private static final int REPOSITION_LOCKOUT = 40;
    private static final int OFFHAND_SLOT = 40;

    private final BotContext ctx;
    private final Player bot;
    private boolean active = false;
    private int sessionTick = 0;
    private int cooldownLeft = 0;
    private BlockPos obsidianPos = null;
    private EnderCrystal lastCrystal = null;
    private boolean kbLaunched = false;
    private int prevSlot = 0;
    private Vec3 kbLaunchPos = null;
    private int shieldWaitTicks = 0;
    private boolean repositioning = false;
    private Vec3 repositionGoal = null;
    private int repositionTicks = 0;
    private int lowerScanLockout = 0;
    private int lastPlaceTick = -1000;
    private boolean seededThisTick = false;
    private int retotemDelay = -1;
    private EnderCrystal detapSeen;
    private long detapReadyTick;

    public CrystalPvpController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick(LivingEntity target, double dist) {
        if (lowerScanLockout > 0) {
            lowerScanLockout--;
        }

        if (cooldownLeft > 0) {
            cooldownLeft--;
            return false;
        } else if (repositioning) {
            return tickRepositioning(target, dist);
        } else {
            return active ? tickActive(target, dist) : tickIdle(target, dist);
        }
    }

    public boolean isExecuting() {
        return active || repositioning;
    }

    public boolean isRepositioning() {
        return repositioning;
    }

    public boolean tickDetap(LivingEntity target) {
        if (target == null) {
            return false;
        } else {
            EnderCrystal closest = null;
            double bestDist = 3.0;

            for (EnderCrystal crystal : ctx.world().entities(EnderCrystal.class, Ent.box(bot).inflate(3.0), e -> true)) {
                if (crystal != lastCrystal) {
                    double d = Ent.dist(bot, crystal);
                    if (d <= bestDist) {
                        bestDist = d;
                        closest = crystal;
                    }
                }
            }

            if (closest == null) {
                return false;
            } else if (!bot.hasLineOfSight(closest)) {
                return false;
            } else {
                long now = ctx.world().gameTime();
                if (closest != detapSeen) {
                    detapSeen = closest;
                    detapReadyTick = now + ctx.difficulty().rollReactionTicks(ctx.random());
                }

                if (now < detapReadyTick) {
                    return false;
                }

                Vec3 at = Ent.pos(closest);
                double aimY = at.y() - bot.getLocation().getY()
                        >= ctx.tuning().number(TuningKey.CRYSTAL_HIGH_AIM_HEIGHT).doubleValue()
                        ? at.y() + 0.5
                        : at.y() + closest.getHeight() * 0.5;
                aimAt(at.x(), aimY, at.z());
                ctx.hands().attack(closest);
                ctx.hands().swing(EquipmentSlot.HAND);
                return true;
            }
        }
    }

    /** Steps away from a crystal at the bot's own height next to the target; true while stepping. */
    public boolean tickDangerAvoid(LivingEntity target) {
        if (target == null || isExecuting() || !ctx.tuning().flag(TuningKey.CRYSTAL_DANGER_AVOID)) {
            return false;
        }

        double radius = ctx.tuning().number(TuningKey.CRYSTAL_DANGER_RADIUS).doubleValue();
        double maxDy = ctx.tuning().number(TuningKey.CRYSTAL_DANGER_SAME_LEVEL_DY).doubleValue();
        double enemyReach = ctx.tuning().number(TuningKey.CRYSTAL_DANGER_ENEMY_REACH).doubleValue();
        Vec3 botPos = Ent.pos(bot);
        EnderCrystal closest = null;
        double best = radius;
        for (EnderCrystal crystal : ctx.world().entities(EnderCrystal.class, Ent.box(bot).inflate(radius), e -> true)) {
            if (crystal == lastCrystal || !dangerous(botPos, Ent.pos(crystal), Ent.pos(target), maxDy, enemyReach)) {
                continue;
            }

            double d = Ent.dist(bot, crystal);
            if (d <= best) {
                best = d;
                closest = crystal;
            }
        }

        if (closest == null) {
            return false;
        }

        Vec3 at = Ent.pos(closest);
        double awayX = botPos.x() - at.x();
        double awayZ = botPos.z() - at.z();
        if (awayX * awayX + awayZ * awayZ < 1.0E-4) {
            return false;
        }

        float awayYaw = (float) Math.toDegrees(Math.atan2(-awayX, awayZ));
        if (LedgeGuard.wouldFallDangerously(ctx, -Math.sin(Math.toRadians(awayYaw)), Math.cos(Math.toRadians(awayYaw)), 3)) {
            return false;
        }

        ctx.controls().look(awayYaw, 0.0F);
        ctx.controls().setSprinting(true);
        ctx.controls().moveForward();
        return true;
    }

    /** A crystal is dangerous at the bot's own height while the target is close enough to hit it. */
    static boolean dangerous(Vec3 bot, Vec3 crystal, Vec3 target, double maxDy, double enemyReach) {
        if (Math.abs(crystal.y() - bot.y()) > maxDy) {
            return false;
        }

        double dx = crystal.x() - target.x();
        double dy = crystal.y() - target.y();
        double dz = crystal.z() - target.z();
        return dx * dx + dy * dy + dz * dz <= enemyReach * enemyReach;
    }

    public void reset() {
        if (active) {
            InventoryUtils.select(ctx, prevSlot);
        }

        active = false;
        sessionTick = 0;
        lastCrystal = null;
        kbLaunched = false;
        repositioning = false;
        repositionGoal = null;
        repositionTicks = 0;
        lowerScanLockout = 0;
        kbLaunchPos = null;
        shieldWaitTicks = 0;
        obsidianPos = null;
        retotemDelay = -1;
        detapSeen = null;
    }

    private boolean tickRepositioning(LivingEntity target, double dist) {
        repositionTicks++;
        if (target != null
                && Ent.alive(target)
                && repositionGoal != null
                && repositionTicks <= REPOSITION_TIMEOUT
                && !(Ent.pos(bot).distanceTo(repositionGoal) <= REPOSITION_ARRIVE)) {
            Vec3 pos = Ent.pos(bot);
            double dx = repositionGoal.x() - pos.x();
            double dz = repositionGoal.z() - pos.z();
            Vec3 at = Ent.pos(target);
            aimAt(at.x(), at.y() + 0.5, at.z());
            if (dx * dx + dz * dz > 1.0E-6) {
                float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float[] input = PathFollower.yawToInput(bot.getLocation().getYaw(), wantYaw);
                ctx.controls().setMovement(input[0], input[1]);
            }
            ctx.controls().setSprinting(true);
            return true;
        } else {
            if (repositionTicks > REPOSITION_TIMEOUT) {
                lowerScanLockout = REPOSITION_LOCKOUT;
            }

            repositioning = false;
            repositionGoal = null;
            repositionTicks = 0;
            ctx.controls().stopMoving();
            return false;
        }
    }

    private boolean tickIdle(LivingEntity target, double dist) {
        if (target == null || !Ent.alive(target)) {
            return false;
        } else if (dist < ctx.tuning().number(TuningKey.CRYSTAL_MIN_RANGE).doubleValue()
                || dist > ctx.tuning().number(TuningKey.CRYSTAL_MAX_RANGE).doubleValue()) {
            return false;
        } else if (findInInventory(Material.END_CRYSTAL) < 0) {
            return false;
        } else if (findInInventory(Material.OBSIDIAN) < 0) {
            return false;
        } else if (!targetReady(target)) {
            return false;
        } else if (!botSafeToEngage()) {
            return false;
        } else if (CrystalSkill.skipSession(ctx)) {
            cooldownLeft = CrystalSkill.hesitateTicks(ctx);
            return false;
        } else {
            if (bot.getLocation().getY() >= target.getLocation().getY() - 0.5) {
                Vec3 lower = lowerScanLockout > 0 ? null : scanForLowerGround(target);
                if (lower != null) {
                    repositioning = true;
                    repositionGoal = lower;
                    repositionTicks = 0;
                    return true;
                }
            }

            prevSlot = InventoryUtils.selected(ctx);
            active = true;
            sessionTick = 0;
            kbLaunched = false;
            obsidianPos = null;
            lastCrystal = null;
            lastPlaceTick = -1000;
            shieldWaitTicks = 0;
            kbLaunchPos = Ent.pos(target);
            equipTotemToOffhand();
            ctx.log().decision("CrystalPvpController", CRYSTAL_START, String.format("[Crystal] starting crystal PVP at dist=%.1f", dist));
            return true;
        }
    }

    private boolean tickActive(LivingEntity target, double dist) {
        sessionTick++;
        if (!tickRetotem()) {
            finish();
            return false;
        } else if (retotemDelay >= 0 && ctx.tuning().flag(TuningKey.TOTEM_REGRAB_HOLDS_CRYSTALS)) {
            // Restocking the offhand: a player has the inventory open and cannot place or break crystals.
            return true;
        } else if (sessionTick > ctx.tuning().integer(TuningKey.CRYSTAL_SESSION_TICKS)) {
            finish();
            cooldownLeft = Math.max(CrystalSkill.sessionGap(ctx), crystalCooldown());
            return false;
        } else if (target == null
                || !Ent.alive(target)
                || dist > ctx.tuning().number(TuningKey.CRYSTAL_ABORT_DIST).doubleValue()) {
            finish();
            return false;
        } else if (findInInventory(Material.END_CRYSTAL) >= 0 && findInInventory(Material.OBSIDIAN) >= 0) {
            Vec3 targetPos = Ent.pos(target);
            if (kbLaunchPos != null) {
                double horizFled = Math.hypot(targetPos.x() - kbLaunchPos.x(), targetPos.z() - kbLaunchPos.z());
                if (horizFled > ctx.tuning().number(TuningKey.CRYSTAL_KB_FLEE_DIST).doubleValue()) {
                    finish();
                    return false;
                }
            }

            if (obsidianPos != null) {
                Vec3 spot = Vec3.atCenterOf(obsidianPos.above());
                aimAt(spot.x(), spot.y(), spot.z());
            } else {
                aimAt(targetPos.x(), targetPos.y() + 0.5, targetPos.z());
            }

            if (!kbLaunched && bot.isOnGround() && dist <= ATTACK_REACH) {
                if (bot.hasLineOfSight(target)) {
                    kbLaunch(target);
                }

                kbLaunched = true;
                kbLaunchPos = Ent.pos(target);
                return true;
            } else {
                if (obsidianPos == null || !isObsidianOrBedrock(obsidianPos)) {
                    seededThisTick = false;
                    obsidianPos = pickObsidianSpot(target);
                    if (obsidianPos == null) {
                        return true;
                    }

                    if (seededThisTick) {
                        return true;
                    }
                }

                if (!safeToPlace(obsidianPos.above())) {
                    BlockPos retry = pickObsidianSpot(target);
                    if (retry == null || !safeToPlace(retry.above())) {
                        obsidianPos = null;
                        return true;
                    }

                    obsidianPos = retry;
                }

                if (CrystalSkill.fumble(ctx)) {
                    return true;
                } else {
                    if (sessionTick - lastPlaceTick >= crystalCooldown()) {
                        breakCrystalOn(obsidianPos);
                        EnderCrystal crystal = placeCrystal(obsidianPos);
                        if (crystal == null) {
                            obsidianPos = null;
                        } else {
                            lastPlaceTick = sessionTick;
                            lastCrystal = crystal;
                            if (inCrystalReach(crystal) && bot.hasLineOfSight(crystal) && !CrystalSkill.noInstantHit(ctx)) {
                                lookAt(Ent.pos(crystal).add(0.0, 0.5, 0.0));
                                ctx.hands().attack(crystal);
                                ctx.hands().swing(EquipmentSlot.HAND);
                                lastCrystal = null;
                            }
                        }
                    }

                    return true;
                }
            }
        } else {
            finish();
            return false;
        }
    }

    private boolean targetReady(LivingEntity target) {
        PotionEffect resistance = target.getPotionEffect(PotionEffectType.RESISTANCE);
        if (resistance != null && resistance.getAmplifier() >= 3) {
            return false;
        } else {
            Vec3 v = ctx.match().velocityOf(target);
            return !(v.y() > 1.0) || !(Math.hypot(v.x(), v.z()) > 0.5);
        }
    }

    private boolean botSafeToEngage() {
        return countInInventory(Material.TOTEM_OF_UNDYING) > 0;
    }

    public boolean targetInHole(LivingEntity target) {
        Vec3 tp = Ent.pos(target);
        BlockPos feet = BlockPos.containing(tp.x(), tp.y(), tp.z());
        int higher = 0;
        int[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        for (int[] o : offs) {
            BlockPos col = feet.offset(o[0], 0, o[1]);
            if (!ctx.world().isAir(col) || !ctx.world().isAir(col.above())) {
                higher++;
            }
        }

        return higher >= 4;
    }

    private boolean tickRetotem() {
        if (offhandHasTotem()) {
            retotemDelay = -1;
            return true;
        } else {
            int slot = findInInventory(Material.TOTEM_OF_UNDYING);
            if (slot < 0) {
                return false;
            } else {
                if (retotemDelay < 0) {
                    retotemDelay = OffhandManager.totemRegrabDelay(
                            ctx.tuning().integer(TuningKey.TOTEM_REGRAB_BASE_TICKS) + ctx.difficulty().totemRegrabExtraTicks,
                            ctx.difficulty().rollReactionTicks(ctx.random()));
                }

                if (retotemDelay > 0) {
                    retotemDelay--;
                    return true;
                } else {
                    InventoryUtils.swap(ctx, slot, OFFHAND_SLOT);
                    retotemDelay = -1;
                    return true;
                }
            }
        }
    }

    private boolean offhandHasTotem() {
        return ItemKinds.is(bot.getInventory().getItem(OFFHAND_SLOT), Material.TOTEM_OF_UNDYING);
    }

    private BlockPos pickObsidianSpot(LivingEntity target) {
        Vec3 targetPos = Ent.pos(target);
        Vec3 targetMid = targetPos.add(0.0, target.getHeight() * 0.5, 0.0);
        Vec3 botMid = Ent.eyePos(bot);
        Vec3 facing = null;
        if (target instanceof HumanEntity human && human.isBlocking()) {
            Vec3 look = Ent.look(target);
            Vec3 flat = new Vec3(look.x(), 0.0, look.z());
            if (flat.lengthSqr() > 1.0E-4) {
                facing = flat.normalize();
            }
        }

        BlockPos best = null;
        boolean bestReady = false;
        double bestScore = 0.0;
        double selfCare = CrystalSkill.selfCare(ctx);
        List<BlockPos> worthIt = new ArrayList<>();
        List<Boolean> worthReady = new ArrayList<>();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos at = BlockPos.containing(targetPos.x() + dx, targetPos.y() + dy, targetPos.z() + dz);
                    boolean ready = isObsidianOrBedrock(at);
                    if ((ready
                                    ? !(Ent.pos(bot).distanceTo(Vec3.atCenterOf(at)) > PLACE_RANGE)
                                            && ctx.world().isAir(at.above())
                                            && ctx.world()
                                                    .entities(
                                                            Entity.class,
                                                            AABB.ofBlock(at.above()).expandTowards(0.0, 1.0, 0.0),
                                                            e -> !isSpectator(e) && !(e instanceof EnderCrystal))
                                                    .isEmpty()
                                            && PlacementRaycasts.canSeePlacement(ctx, at)
                                    : canSeedObsidian(at, target))
                            && safeToPlace(at.above())
                            && seatInReach(at)) {
                        Vec3 crystal = Vec3.atCenterOf(at.above());
                        double score = blastAt(crystal, targetMid) - selfCare * blastAt(crystal, botMid);
                        score += behindBonus(ctx.tuning(), crystal, targetPos, facing);

                        if (ready) {
                            score += 0.05;
                        }

                        if (score > 0.0) {
                            worthIt.add(at);
                            worthReady.add(ready);
                        }

                        if (score > bestScore) {
                            bestScore = score;
                            best = at;
                            bestReady = ready;
                        }
                    }
                }
            }
        }

        if (best != null && worthIt.size() > 1 && CrystalSkill.badSeat(ctx)) {
            int i = ctx.random().nextInt(worthIt.size());
            best = worthIt.get(i);
            bestReady = worthReady.get(i);
        }

        if (best != null) {
            return bestReady ? best : seedObsidian(best);
        } else {
            Vec3 botPos = Ent.pos(bot);
            Vec3 dir = targetPos.subtract(botPos).normalize();
            BlockPos placed = tryPlaceObsidianRow(botPos, dir, botPos.y(), target);
            return placed != null ? placed : tryPlaceObsidianRow(botPos, dir, targetPos.y(), target);
        }
    }

    static double blastAt(Vec3 blast, Vec3 victim) {
        double d = blast.distanceTo(victim);
        return d >= CRYSTAL_BLAST_DIAMETER ? 0.0 : 1.0 - d / CRYSTAL_BLAST_DIAMETER;
    }

    /** Bonus for a crystal behind a blocking target; 0 when the target is not blocking (facing null). */
    static double behindBonus(BotTuning.Profile profile, Vec3 crystal, Vec3 targetPos, Vec3 facing) {
        if (facing == null) {
            return 0.0;
        }
        Vec3 flat = new Vec3(crystal.x() - targetPos.x(), 0.0, crystal.z() - targetPos.z());
        if (flat.lengthSqr() > 0.25) {
            double behind = -flat.normalize().dot(facing);
            if (behind > 0.0) {
                return behind * profile.number(TuningKey.CRYSTAL_SHIELD_BONUS).doubleValue();
            }
        }
        return 0.0;
    }

    private static boolean isSpectator(Entity e) {
        return e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean canSeedObsidian(BlockPos pos, LivingEntity target) {
        if (!ctx.world().isAir(pos)) {
            return false;
        } else if (!ctx.world().isFullBlock(pos.below())) {
            return false;
        } else if (!ctx.world().isAir(pos.above())) {
            return false;
        } else if (!ctx.world().isAir(pos.above().above())) {
            return false;
        } else if (!hasEntityAt(pos) && !hasEntityAt(pos.above())) {
            if (Vec3.atCenterOf(pos).distanceTo(Ent.pos(target)) < 1.0) {
                return false;
            } else {
                return Ent.pos(bot).distanceTo(Vec3.atCenterOf(pos)) > PLACE_RANGE
                        ? false
                        : PlacementRaycasts.canSeePlacement(ctx, pos);
            }
        } else {
            return false;
        }
    }

    private BlockPos seedObsidian(BlockPos pos) {
        if (!PlacementRaycasts.canSeePlacement(ctx, pos)) {
            return null;
        } else {
            int obsSlot = findInInventory(Material.OBSIDIAN);
            if (obsSlot < 0) {
                return null;
            } else {
                obsSlot = InventoryUtils.moveToHotbarSafe(ctx, obsSlot);
                InventoryUtils.select(ctx, obsSlot);
                ItemStack obsStack = bot.getInventory().getItem(obsSlot);
                if (!ItemKinds.is(obsStack, Material.OBSIDIAN)) {
                    return null;
                } else {
                    Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    lookAt(hitVec);
                    ctx.hands().useItemOn(EquipmentSlot.HAND, pos.below(), Direction.UP, hitVec);
                    ctx.hands().swing(EquipmentSlot.HAND);
                    if (!isObsidianOrBedrock(pos)) {
                        return null;
                    } else {
                        seededThisTick = true;
                        return pos;
                    }
                }
            }
        }
    }

    private boolean safeToPlace(BlockPos crystalSpot) {
        double floor = offhandHasTotem()
                ? ctx.tuning().number(TuningKey.CRYSTAL_SELF_BLAST_TOTEM).doubleValue()
                : ctx.tuning().number(TuningKey.CRYSTAL_SELF_BLAST_SAFE).doubleValue();
        Vec3 center = Vec3.atCenterOf(crystalSpot);
        return Ent.eyePos(bot).distanceTo(center) >= floor;
    }

    private int crystalCooldown() {
        return Math.max(0, ctx.difficulty().crystalCooldownTicks);
    }

    private void breakCrystalOn(BlockPos obsPos) {
        List<EnderCrystal> found =
                ctx.world().entities(EnderCrystal.class, AABB.ofBlock(obsPos.above()).inflate(0.5), e -> true);
        if (!found.isEmpty()) {
            EnderCrystal c = found.get(0);
            if (inCrystalReach(c) && bot.hasLineOfSight(c)) {
                lookAt(Ent.pos(c).add(0.0, 0.5, 0.0));
                ctx.hands().attack(c);
                ctx.hands().swing(EquipmentSlot.HAND);
                if (c == lastCrystal) {
                    lastCrystal = null;
                }
            }
        }
    }

    private EnderCrystal placeCrystal(BlockPos obsPos) {
        BlockPos above = obsPos.above();
        if (!ctx.world().isAir(above)) {
            return null;
        } else if (!ctx.world().isAir(above.above())) {
            return null;
        } else {
            AABB spotBox = AABB.ofBlock(above).inflate(0.5);
            List<EnderCrystal> existing = ctx.world().entities(EnderCrystal.class, spotBox, e -> true);
            if (!existing.isEmpty()) {
                lastCrystal = existing.get(0);
                if (inCrystalReach(lastCrystal) && bot.hasLineOfSight(lastCrystal)) {
                    ctx.hands().attack(lastCrystal);
                    ctx.hands().swing(EquipmentSlot.HAND);
                }

                return null;
            } else {
                int crystalSlot = findInInventory(Material.END_CRYSTAL);
                if (crystalSlot < 0) {
                    return null;
                } else {
                    crystalSlot = InventoryUtils.moveToHotbarSafe(ctx, crystalSlot);
                    InventoryUtils.select(ctx, crystalSlot);
                    ItemStack crystalStack = bot.getInventory().getItem(crystalSlot);
                    if (!ItemKinds.is(crystalStack, Material.END_CRYSTAL)) {
                        return null;
                    } else {
                        Vec3 hitVec = new Vec3(obsPos.getX() + 0.5, obsPos.getY() + 1.0, obsPos.getZ() + 0.5);
                        lookAt(hitVec);
                        ctx.hands().useItemOn(EquipmentSlot.HAND, obsPos, Direction.UP, hitVec);
                        List<EnderCrystal> spawned = ctx.world().entities(EnderCrystal.class, spotBox, e -> true);
                        return spawned.isEmpty() ? null : spawned.get(0);
                    }
                }
            }
        }
    }

    private BlockPos tryPlaceObsidianRow(Vec3 botPos, Vec3 dir, double y, LivingEntity target) {
        for (double d = 2.0; d <= 4.0; d += 0.5) {
            Vec3 cand = botPos.add(dir.scale(d));
            BlockPos pos = BlockPos.containing(cand.x(), y, cand.z());
            if (!hasEntityAt(pos)
                    && !hasEntityAt(pos.above())
                    && !(Vec3.atCenterOf(pos).distanceTo(Ent.pos(target)) < 1.0)
                    && ctx.world().isAir(pos)
                    && ctx.world().isFullBlock(pos.below())
                    && ctx.world().isAir(pos.above())
                    && ctx.world().isAir(pos.above().above())) {
                BlockPos placed = seedObsidian(pos);
                if (placed != null) {
                    return placed;
                }
            }
        }

        return null;
    }

    private boolean isObsidianOrBedrock(BlockPos pos) {
        Material type = ctx.world().type(pos);
        return type == Material.OBSIDIAN || type == Material.BEDROCK;
    }

    private Vec3 scanForLowerGround(LivingEntity target) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double targetY = targetPos.y();
        double bestScore = Double.MAX_VALUE;
        Vec3 best = null;

        for (int dx = -REPOSITION_SCAN; dx <= REPOSITION_SCAN; dx++) {
            for (int dz = -REPOSITION_SCAN; dz <= REPOSITION_SCAN; dz++) {
                for (int dy = 1; dy <= REPOSITION_SCAN; dy++) {
                    BlockPos floor = BlockPos.containing(botPos.x() + dx, botPos.y() - dy, botPos.z() + dz);
                    BlockPos stand = floor.above();
                    if (!ctx.world().isAir(stand) || !ctx.world().isAir(stand.above())) {
                        break;
                    }

                    if (ctx.world().isFullBlock(floor)) {
                        if (!(stand.getY() >= targetY - 0.5)) {
                            Vec3 standVec = Vec3.atCenterOf(stand);
                            if (!(standVec.distanceTo(targetPos) > PLACE_RANGE)) {
                                double score = stand.getY() * 100.0 + botPos.distanceTo(standVec);
                                if (score < bestScore) {
                                    bestScore = score;
                                    best = standVec;
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        return best;
    }

    private void kbLaunch(LivingEntity target) {
        ctx.controls().setSprinting(true);
        ctx.hands().attack(target);
        ctx.hands().swing(EquipmentSlot.HAND);
    }

    private boolean inCrystalReach(EnderCrystal crystal) {
        return Ent.box(crystal).distanceToSqr(Ent.eyePos(bot)) <= 9.0;
    }

    private boolean seatInReach(BlockPos obsidian) {
        AABB box = new AABB(
                obsidian.getX() - 0.5,
                obsidian.getY() + 1.0,
                obsidian.getZ() - 0.5,
                obsidian.getX() + 1.5,
                obsidian.getY() + 3.0,
                obsidian.getZ() + 1.5);
        return box.distanceToSqr(Ent.eyePos(bot)) <= 9.0;
    }

    private boolean hasEntityAt(BlockPos pos) {
        return !ctx.world().entities(LivingEntity.class, AABB.ofBlock(pos).inflate(0.1), e -> true).isEmpty();
    }

    private void finish() {
        if (active) {
            InventoryUtils.select(ctx, prevSlot);
        }

        active = false;
        cooldownLeft = crystalCooldown();
        lastCrystal = null;
        obsidianPos = null;
        shieldWaitTicks = 0;
        kbLaunchPos = null;
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

    private int findInInventory(Material item) {
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.is(bot.getInventory().getItem(i), item)) {
                return i;
            }
        }

        return -1;
    }

    private int countInInventory(Material item) {
        int total = 0;

        for (int i = 0; i < InventoryUtils.SIZE; i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (ItemKinds.is(s, item)) {
                total += s.getAmount();
            }
        }

        return total;
    }

    private void equipTotemToOffhand() {
        if (!offhandHasTotem()) {
            int totemSlot = findInInventory(Material.TOTEM_OF_UNDYING);
            if (totemSlot >= 0) {
                InventoryUtils.swap(ctx, totemSlot, OFFHAND_SLOT);
            }
        }
    }

    private void lookAt(Vec3 pos) {
        aimAt(pos.x(), pos.y(), pos.z());
    }
}
