package dev.nandi0813.zppbots.ai.gear;

import dev.nandi0813.zppbots.ai.move.PathFollower;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

public final class ShulkerRaidController {

    private static final String RAID_APPROACH = "RAID_APPROACH";
    private static final String RAID_BOX_LOST = "RAID_BOX_LOST";
    private static final String RAID_SUCCESS = "RAID_SUCCESS";

    private static final double REACH = 4.0;
    private static final int SCAN_EVERY = 20;
    private static final int APPROACH_TIMEOUT = 100;
    private static final int MINE_TIMEOUT = 200;
    private static final int COLLECT_TICKS = 40;
    private static final double DROP_FALL_REACH = 8.0;

    private final BotContext ctx;
    private final Player bot;
    private final PlacedBoxClaims claims;
    private final PathFollower path;
    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int scanTick = 0;
    private int cooldown = 0;
    private BlockPos boxPos = null;
    private int slotBefore = -1;
    private int boxesBefore = 0;

    public ShulkerRaidController(BotContext ctx, PlacedBoxClaims claims) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.claims = claims;
        this.path = new PathFollower(ctx);
    }

    public boolean isExecuting() {
        return phase != Phase.IDLE;
    }

    public boolean tick(LivingEntity target) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (phase != Phase.IDLE) {
            return tickRaid();
        } else if (cooldown > 0) {
            return false;
        } else if (target == null || !Ent.alive(target)) {
            return false;
        } else if (!ctx.settings().shulkerSteal()) {
            return false;
        } else if (bot.getHealth() < ctx.tuning().number(TuningKey.SHULKER_RAID_MIN_HP).floatValue()) {
            return false;
        } else if (findPickaxe() < 0) {
            return false;
        } else if (++scanTick < SCAN_EVERY) {
            return false;
        } else {
            scanTick = 0;
            BlockPos found = findEnemyShulker(target);
            if (found == null) {
                return false;
            } else {
                boxPos = found;
                phase = Phase.APPROACH;
                phaseTick = 0;
                slotBefore = InventoryUtils.selected(ctx);
                boxesBefore = countShulkers();
                ctx.log().decision("ShulkerRaidController", RAID_APPROACH, "[ShulkerRaid] " + ctx.name() + " going for " + target.getName() + "'s shulker at " + boxPos);
                return true;
            }
        }
    }

    public void reset() {
        if (phase != Phase.IDLE) {
            ctx.controls().stopMining();
            restoreSlot();
            path.reset();
        }

        phase = Phase.IDLE;
        phaseTick = 0;
        boxPos = null;
    }

    private boolean tickRaid() {
        phaseTick++;
        if (phase != Phase.COLLECT && !isShulkerAt(boxPos)) {
            ctx.log().decision("ShulkerRaidController", RAID_BOX_LOST, "[ShulkerRaid] " + ctx.name() + " lost the box (owner recovered it)");
            finish();
            return false;
        }

        switch (phase) {
            case APPROACH:
                if (phaseTick > APPROACH_TIMEOUT) {
                    finish();
                    return false;
                }

                double dist = Math.sqrt(Ent.pos(bot).distanceToSqr(Vec3.atCenterOf(boxPos)));
                if (dist <= REACH) {
                    beginMining();
                    phase = Phase.MINE;
                    phaseTick = 0;
                } else {
                    path.chase(Vec3.atCenterOf(boxPos));
                }
                break;
            case MINE:
                if (phaseTick > MINE_TIMEOUT) {
                    finish();
                    return false;
                }

                lookAtBox();
                if (!isShulkerAt(boxPos)) {
                    ctx.controls().stopMining();
                    phase = Phase.COLLECT;
                    phaseTick = 0;
                }
                break;
            case COLLECT:
                if (collectDrop()) {
                    finish();
                    return false;
                }

                if (phaseTick >= COLLECT_TICKS) {
                    finish();
                    return false;
                }
                break;
            default:
                finish();
                return false;
        }

        return true;
    }

    private void beginMining() {
        int pick = findPickaxe();
        if (pick >= 0) {
            InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, pick));
        }

        path.reset();
        ctx.controls().stopMoving();
        lookAtBox();
        ctx.controls().mineContinuous();
    }

    /** Walks over the dropped box; the server picks it up. True once the inventory holds more boxes than at the start. */
    private boolean collectDrop() {
        if (boxPos == null) {
            return false;
        }

        if (countShulkers() > boxesBefore) {
            ctx.log().decision("ShulkerRaidController", RAID_SUCCESS, "[ShulkerRaid] " + ctx.name() + " stole the shulker");
            return true;
        }

        AABB area = AABB.ofBlock(boxPos).inflate(3.0).expandTowards(0.0, -DROP_FALL_REACH, 0.0);
        Item nearest = null;
        double best = Double.MAX_VALUE;
        for (Item ie : ctx.world().entities(Item.class, area, e -> ItemKinds.isShulkerBox(e.getItemStack()))) {
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

        return false;
    }

    private void finish() {
        ctx.controls().stopMining();
        restoreSlot();
        path.reset();
        phase = Phase.IDLE;
        phaseTick = 0;
        boxPos = null;
        cooldown = ctx.tuning().integer(TuningKey.SHULKER_RAID_COOLDOWN_TICKS);
    }

    private BlockPos findEnemyShulker(LivingEntity target) {
        BlockPos center = Ent.blockPos(target);
        int r = 8;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        double maxTravel = ctx.tuning().number(TuningKey.SHULKER_RAID_MAX_TRAVEL).doubleValue();
        double maxSqr = maxTravel * maxTravel;
        Vec3 me = Ent.pos(bot);

        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            if (ItemKinds.isShulkerBox(ctx.world().type(p)) && isRaidable(claims.ownerOf(p), bot.getUniqueId(), isAllyId(claims.ownerOf(p)))) {
                double d = me.distanceToSqr(Vec3.atCenterOf(p));
                if (d <= maxSqr && d < bestDist) {
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }

        return best;
    }

    /** Unclaimed boxes and boxes of other teams are fair game; own and allied boxes are not. */
    public static boolean isRaidable(UUID owner, UUID self, boolean ownerAllied) {
        return owner == null || (!owner.equals(self) && !ownerAllied);
    }

    private boolean isAllyId(UUID owner) {
        if (owner == null) {
            return false;
        }

        for (Player p : ctx.world().players()) {
            if (p.getUniqueId().equals(owner)) {
                return ctx.match().isAlly(p);
            }
        }

        return false;
    }

    private boolean isShulkerAt(BlockPos pos) {
        return pos != null && ItemKinds.isShulkerBox(ctx.world().type(pos));
    }

    private int countShulkers() {
        PlayerInventory inv = bot.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isShulkerBox(inv.getItem(i))) {
                n += inv.getItem(i).getAmount();
            }
        }

        return n;
    }

    private int findPickaxe() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ItemKinds.isPickaxe(inv.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private void lookAtBox() {
        if (boxPos != null) {
            ctx.controls().lookAt(Vec3.atCenterOf(boxPos));
        }
    }

    private void restoreSlot() {
        if (slotBefore >= 0 && slotBefore < 9) {
            InventoryUtils.select(ctx, slotBefore);
        }

        slotBefore = -1;
    }

    private enum Phase {
        IDLE,
        APPROACH,
        MINE,
        COLLECT
    }
}
