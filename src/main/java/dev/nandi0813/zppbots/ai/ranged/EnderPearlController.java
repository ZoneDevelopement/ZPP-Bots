package dev.nandi0813.zppbots.ai.ranged;

import dev.nandi0813.zppbots.ai.mace.DiveLead;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
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

public final class EnderPearlController {

    private static final String PEARL_CHAIN = "PEARL_CHAIN";
    private static final String PEARL_ESCAPE = "PEARL_ESCAPE";
    private static final String PEARL_APPROACH = "PEARL_APPROACH";
    private static final String PEARL_DROP_IN = "PEARL_DROP_IN";
    private static final String PEARL_CLIMB = "PEARL_CLIMB";
    private static final String PEARL_THROW_FAILED = "PEARL_THROW_FAILED";
    private static final float THROW_PITCH = -22.0F;
    private static final int PEARL_FLIGHT_MAX_TICKS = 60;
    private static final int JUMP_QUEUE_MAX_TICKS = 4;
    private static final float[] ESCAPE_YAWS = {0.0F, 25.0F, -25.0F, 50.0F, -50.0F};
    private static final float[] ESCAPE_PITCHES = {-22.0F, -30.0F, -14.0F};
    private static final int DROP_IN_MAX_TICKS = 60;

    private final BotContext ctx;
    private final Player bot;
    private final DiveLead lead;
    private int cooldownLeft = 0;
    private int offensiveCooldownLeft = 0;
    private boolean chainUsed = false;
    private int pendingTicks = 0;
    private LivingEntity pendingEntity = null;
    private boolean pendingToward = false;
    private boolean offensivePearlInFlight = false;
    private int postLandWebBlock = 0;
    private int flightTicks = 0;
    private Vec3 prevBotPos = null;
    private int noShotTicks = 0;
    private int dropInTicks = 0;
    private int dropInLastTick = -10;
    private int climbStallTicks = 0;
    private double climbStartY = 0.0;
    private int climbLastTick = -10;

    public EnderPearlController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.lead = new DiveLead(ctx);
    }

    public boolean isThrowPending() {
        return pendingEntity != null;
    }

    public boolean tryThrow(LivingEntity threat) {
        if (!ctx.match().live()) {
            return false;
        }

        if (pendingEntity != null) {
            return false;
        } else if (pearlOnItemCooldown()) {
            return false;
        } else {
            if (cooldownLeft > 0) {
                int elapsed = ctx.tuning().integer(TuningKey.PEARL_COOLDOWN_TICKS) - cooldownLeft;
                boolean chased = ctx.difficulty().pearlChain && !chainUsed && elapsed >= ctx.tuning().integer(TuningKey.PEARL_CHAIN_MIN_ELAPSED)
                        && elapsed <= ctx.tuning().integer(TuningKey.PEARL_CHAIN_MAX_ELAPSED)
                        && threat != null && Ent.alive(threat)
                        && Ent.dist(bot, threat) <= ctx.tuning().number(TuningKey.PEARL_CHAIN_THREAT_DIST).doubleValue();
                if (!chased) {
                    return false;
                }

                chainUsed = true;
                ctx.log().decision("EnderPearlController", PEARL_CHAIN, "[Pearl] double-pearls - " + threat.getName() + " chased through the first");
            }

            if (findInInventory() < 0) {
                return false;
            } else {
                cooldownLeft = ctx.tuning().integer(TuningKey.PEARL_COOLDOWN_TICKS);
                if (bot.isOnGround()) {
                    ctx.controls().jumpOnce();
                    pendingEntity = threat;
                    pendingToward = false;
                    pendingTicks = JUMP_QUEUE_MAX_TICKS;
                    return true;
                } else {
                    return fireEscape(threat);
                }
            }
        }
    }

    public boolean tryThrowToward(LivingEntity target) {
        if (!ctx.match().live()) {
            return false;
        }

        if (pendingEntity != null) {
            return false;
        } else if (pearlOnItemCooldown()) {
            return false;
        } else if (offensiveCooldownLeft > 0) {
            return false;
        } else if (target == null || !Ent.alive(target)) {
            return false;
        } else if (!bot.isOnGround()) {
            return false;
        } else if (!target.isOnGround()) {
            return false;
        } else if (findInInventory() < 0) {
            return false;
        } else if (noShotTicks > 0) {
            return false;
        } else if (aimAt(target) == null) {
            noShotTicks = ctx.tuning().integer(TuningKey.PEARL_NO_SHOT_RETRY_TICKS);
            return false;
        } else {
            offensiveCooldownLeft = ctx.tuning().integer(TuningKey.PEARL_OFFENSIVE_COOLDOWN_TICKS);
            if (bot.isOnGround()
                    && Ent.dist(bot, target) >= ctx.tuning().number(TuningKey.PEARL_JUMP_BOOST_MIN_DIST).doubleValue()) {
                ctx.controls().jumpOnce();
                pendingEntity = target;
                pendingToward = true;
                pendingTicks = JUMP_QUEUE_MAX_TICKS;
                return true;
            } else {
                return fireOffensive(target);
            }
        }
    }

    public static float awayYaw(double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            dx = 1.0;
            dz = 0.0;
        } else {
            dx /= len;
            dz /= len;
        }

        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private boolean fireEscape(LivingEntity threat) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 threatPos = Ent.pos(threat);
        float away = awayYaw(botPos.x() - threatPos.x(), botPos.z() - threatPos.z());
        float bestYaw = away;
        float bestPitch = THROW_PITCH;
        double best = -1.0;

        for (float off : ESCAPE_YAWS) {
            for (float pitch : ESCAPE_PITCHES) {
                float yaw = away + off;
                Vec3 at = PearlAim.landing(ctx, yaw, pitch);
                if (at != null && !(at.distanceTo(botPos) < ctx.tuning().number(TuningKey.PEARL_ESCAPE_MIN_DIST)
                        .doubleValue())) {
                    BlockPos cell = BlockPos.containing(at);
                    if (!ctx.world().isLava(cell) && !ctx.world().isLava(cell.below())) {
                        double fromThreat = at.distanceTo(threatPos);
                        if (fromThreat > best) {
                            best = fromThreat;
                            bestYaw = yaw;
                            bestPitch = pitch;
                        }
                    }
                }
            }
        }

        if (best < 0.0) {
            return false;
        } else {
            ctx.controls().look(bestYaw, bestPitch);
            if (!useHeldPearl()) {
                return false;
            } else {
                ctx.log().decision("EnderPearlController", PEARL_ESCAPE, "[Pearl] threw ender pearl away from " + threat.getName());
                return true;
            }
        }
    }

    private boolean fireOffensive(LivingEntity target) {
        PearlAim.Shot shot = aimAt(target);
        if (shot == null) {
            return false;
        } else {
            ctx.controls().look(shot.yaw(), shot.pitch());
            if (!useHeldPearl()) {
                return false;
            } else {
                offensivePearlInFlight = true;
                flightTicks = 0;
                prevBotPos = Ent.pos(bot);
                ctx.log().decision("EnderPearlController", PEARL_APPROACH, "[Pearl] pearled TOWARD " + target.getName() + " (" + shot.ticks() + " ticks of flight)");
                return true;
            }
        }
    }

    private PearlAim.Shot aimAt(LivingEntity target) {
        Vec3 targetPos = Ent.pos(target);
        Vec3 base = new Vec3(targetPos.x(), targetPos.y() + 0.4, targetPos.z());
        PearlAim.Shot shot = PearlAim.solve(ctx, base);
        if (shot == null) {
            return null;
        } else {
            Vec3 v = lead.track(target);
            if (target.isOnGround()
                    && !(Math.sqrt(v.x() * v.x() + v.z() * v.z())
                    <= ctx.tuning().number(TuningKey.PEARL_LEAD_MIN_SPEED).doubleValue())) {
                for (int i = 0; i < 2; i++) {
                    double t = Math.min(shot.ticks(), ctx.tuning().integer(TuningKey.PEARL_LEAD_MAX_TICKS))
                            * ctx.tuning().number(TuningKey.PEARL_LEAD_TRUST).doubleValue();
                    PearlAim.Shot led = PearlAim.solve(ctx, base.add(v.x() * t, 0.0, v.z() * t));
                    if (led == null) {
                        break;
                    }

                    shot = led;
                }

                return shot;
            } else {
                return shot;
            }
        }
    }

    public boolean tickDropIn(LivingEntity target) {
        if (!ctx.match().live()) {
            return false;
        }

        if (target != null && Ent.alive(target) && target.isOnGround()) {
            Vec3 botPos = Ent.pos(bot);
            Vec3 targetPos = Ent.pos(target);
            double hx = Math.floor(targetPos.x()) + 0.5 - botPos.x();
            double hz = Math.floor(targetPos.z()) + 0.5 - botPos.z();
            double h = Math.sqrt(hx * hx + hz * hz);
            if (botPos.y() - targetPos.y() < ctx.tuning().number(TuningKey.PEARL_DROP_IN_MIN_HEIGHT).doubleValue()
                    || h > ctx.tuning().number(TuningKey.PEARL_DROP_IN_MAX_HORIZ).doubleValue()) {
                return endDropIn();
            } else if (!bot.isOnGround() && dropInTicks == 0) {
                return endDropIn();
            } else if (pendingEntity == null && !pearlOnItemCooldown() && offensiveCooldownLeft <= 0 && findInInventory() >= 0) {
                if (++dropInTicks > DROP_IN_MAX_TICKS) {
                    offensiveCooldownLeft = ctx.tuning().integer(TuningKey.PEARL_OFFENSIVE_COOLDOWN_TICKS);
                    return endDropIn();
                } else {
                    dropInLastTick = ctx.tick();
                    ctx.controls().setSneaking(true);
                    if (bot.isOnGround() && clearDownTo(target)) {
                        ctx.controls().stopMoving();
                        ctx.controls().look(bot.getLocation().getYaw(), 90.0F);
                        if (useHeldPearl()) {
                            offensiveCooldownLeft = ctx.tuning().integer(TuningKey.PEARL_OFFENSIVE_COOLDOWN_TICKS);
                            offensivePearlInFlight = true;
                            flightTicks = 0;
                            prevBotPos = Ent.pos(bot);
                            ctx.log().decision("EnderPearlController", PEARL_DROP_IN, "[Pearl] dropped a pearl down onto " + target.getName());
                        }

                        endDropIn();
                        return true;
                    } else {
                        float yaw = (float) Math.toDegrees(Math.atan2(-hx, hz));
                        ctx.controls().look(yaw, 60.0F);
                        ctx.controls().setMovement(0.5F, 0.0F);
                        return true;
                    }
                }
            } else {
                return endDropIn();
            }
        } else {
            return endDropIn();
        }
    }

    private boolean clearDownTo(LivingEntity target) {
        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double dx = targetPos.x() - botPos.x();
        double dz = targetPos.z() - botPos.z();
        if (dx * dx + dz * dz > 0.81) {
            return false;
        } else {
            Vec3 from = new Vec3(botPos.x(), Ent.eyePos(bot).y() - 0.1, botPos.z());
            Vec3 to = new Vec3(botPos.x(), targetPos.y() + 0.3, botPos.z());
            return !ctx.world().clip(from, to, false, false).hit();
        }
    }

    private boolean endDropIn() {
        if (dropInTicks > 0) {
            ctx.controls().setSneaking(false);
            ctx.controls().stopMoving();
        }

        dropInTicks = 0;
        return false;
    }

    /** Pearls up to a target above the bot; false while the bot still paces underneath, so the stall counts real movement. */
    public boolean tickClimb(LivingEntity target) {
        if (!ctx.match().live() || target == null || !Ent.alive(target) || !target.isOnGround() || !bot.isOnGround()) {
            return false;
        }

        Vec3 botPos = Ent.pos(bot);
        Vec3 targetPos = Ent.pos(target);
        double hx = targetPos.x() - botPos.x();
        double hz = targetPos.z() - botPos.z();
        if (targetPos.y() - botPos.y() < ctx.tuning().number(TuningKey.PEARL_CLIMB_MIN_HEIGHT).doubleValue()
                || Math.sqrt(hx * hx + hz * hz) > ctx.tuning().number(TuningKey.PEARL_CLIMB_MAX_HORIZ).doubleValue()) {
            climbStallTicks = 0;
            return false;
        }

        if (climbStallTicks == 0 || botPos.y() > climbStartY + 1.0 || ctx.tick() - climbLastTick != 1) {
            climbStartY = botPos.y();
            climbStallTicks = 0;
        }

        climbLastTick = ctx.tick();
        if (++climbStallTicks < ctx.tuning().integer(TuningKey.PEARL_CLIMB_STALL_TICKS)) {
            return false;
        } else if (tryThrowToward(target)) {
            climbStallTicks = 0;
            ctx.log().decision("EnderPearlController", PEARL_CLIMB, "[Pearl] pearled up to " + target.getName());
            return true;
        } else {
            return false;
        }
    }

    private boolean useHeldPearl() {
        int pearlSlot = findInInventory();
        if (pearlSlot < 0) {
            return false;
        } else {
            pearlSlot = InventoryUtils.moveToHotbarSafe(ctx, pearlSlot);
            InventoryUtils.select(ctx, pearlSlot);
            ItemStack stack = bot.getInventory().getItem(pearlSlot);
            if (!ItemKinds.is(stack, Material.ENDER_PEARL)) {
                return false;
            } else {
                try {
                    return ctx.hands().useItem(EquipmentSlot.HAND);
                } catch (Exception e) {
                    ctx.log().decision("EnderPearlController", PEARL_THROW_FAILED, "[Pearl] throw failed: " + e.getMessage(), true);
                    return false;
                }
            }
        }
    }

    private boolean pearlOnItemCooldown() {
        return bot.hasCooldown(new ItemStack(Material.ENDER_PEARL));
    }

    public void tickPearlState(LivingEntity target) {
        if (target != null && Ent.alive(target)) {
            lead.track(target);
        }

        if (cooldownLeft > 0 && --cooldownLeft == 0) {
            chainUsed = false;
        }

        if (offensiveCooldownLeft > 0) {
            offensiveCooldownLeft--;
        }

        if (noShotTicks > 0) {
            noShotTicks--;
        }

        if (dropInTicks > 0 && ctx.tick() - dropInLastTick > 1) {
            endDropIn();
        }

        if (pendingEntity != null) {
            pendingTicks--;
            boolean rising = !bot.isOnGround() && Ent.velocity(bot).y() > 0.05;
            if (rising || pendingTicks <= 0) {
                LivingEntity e = pendingEntity;
                boolean toward = pendingToward;
                pendingEntity = null;
                if (e != null && Ent.alive(e)) {
                    if (toward) {
                        fireOffensive(e);
                    } else {
                        fireEscape(e);
                    }
                }
            }
        }

        if (postLandWebBlock > 0) {
            postLandWebBlock--;
        }

        if (offensivePearlInFlight) {
            flightTicks++;
            Vec3 now = Ent.pos(bot);
            boolean teleported = prevBotPos != null && now.distanceTo(prevBotPos) > 3.0;
            if (teleported || flightTicks >= PEARL_FLIGHT_MAX_TICKS) {
                offensivePearlInFlight = false;
                postLandWebBlock = ctx.tuning().integer(TuningKey.PEARL_POST_LAND_WEB_BLOCK_TICKS);
            }

            prevBotPos = now;
        }
    }

    public boolean suppressesWebs() {
        return offensivePearlInFlight || postLandWebBlock > 0;
    }

    private int findInInventory() {
        PlayerInventory inv = bot.getInventory();

        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.ENDER_PEARL)) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.ENDER_PEARL)) {
                return i;
            }
        }

        return -1;
    }
}
