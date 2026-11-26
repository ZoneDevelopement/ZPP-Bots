package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.ItemKinds;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

public final class WaterBucketClutchController {

    private static final String WATER_CLUTCH_DEPLOYED = "WATER_CLUTCH_DEPLOYED";

    private final BotContext ctx;
    private final Player bot;
    private boolean rolledThisFall = false;
    private boolean willClutch = false;
    private int bucketSlot = -1;
    private int prevSelected = -1;
    private boolean collectingClutch = false;
    private BlockPos clutchWaterPos = null;
    private int clutchBucketSlot = -1;
    private int clutchTimer = 0;

    public WaterBucketClutchController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick() {
        PlayerInventory inv = bot.getInventory();
        if (bot.isOnGround()) {
            if (collectingClutch) {
                collectClutchWater(inv);
            } else if (willClutch) {
                restoreSlot();
            }

            rolledThisFall = false;
            willClutch = false;
            bucketSlot = -1;
            return false;
        } else if (collectingClutch) {
            if (++clutchTimer >= ctx.tuning().integer(TuningKey.WATER_CLUTCH_COLLECT_TIMEOUT)) {
                collectClutchWater(inv);
            }

            return true;
        } else if (bot.isGliding()) {
            return false;
        } else if (Ent.velocity(bot).y() >= 0.0) {
            return false;
        } else if (bot.getFallDistance() < ctx.tuning().number(TuningKey.WATER_CLUTCH_MIN_FALL_DIST).floatValue()) {
            return false;
        } else {
            if (!rolledThisFall) {
                rolledThisFall = true;
                if (ctx.settings().waterBucketClutch() && ctx.settings().rollClutch()) {
                    int slot = findWaterBucket(inv);
                    if (slot >= 0) {
                        prevSelected = InventoryUtils.selected(ctx);
                        bucketSlot = slot;
                        willClutch = true;
                        InventoryUtils.select(ctx, slot);
                    }
                }
            }

            if (!willClutch) {
                return false;
            } else {
                InventoryUtils.select(ctx, bucketSlot);
                int deployAt = deployHeight(ctx.tuning(), Ent.velocity(bot).y());
                if (blocksToGround() > deployAt) {
                    return true;
                } else {
                    deployWater(inv);
                    return true;
                }
            }
        }
    }

    static int deployHeight(BotTuning.Profile profile, double velocityY) {
        return Math.max(
                profile.integer(TuningKey.WATER_CLUTCH_DEPLOY_HEIGHT),
                Math.min(
                        profile.integer(TuningKey.WATER_CLUTCH_MAX_DEPLOY_HEIGHT),
                        (int) Math.ceil(-velocityY) + profile.integer(TuningKey.WATER_CLUTCH_DEPLOY_EXTRA)));
    }

    private void deployWater(PlayerInventory inv) {
        if (!ItemKinds.is(inv.getItem(bucketSlot), Material.WATER_BUCKET)) {
            willClutch = false;
            restoreSlot();
        } else {
            ctx.controls().look(bot.getLocation().getYaw(), 90.0F);
            ctx.hands().useItem(EquipmentSlot.HAND);
            ctx.hands().swing(EquipmentSlot.HAND);
            clutchWaterPos = BlockPos.containing(Ent.pos(bot));
            clutchBucketSlot = bucketSlot;
            collectingClutch = true;
            clutchTimer = 0;
            willClutch = false;
            ctx.log().decision("WaterBucketClutchController", WATER_CLUTCH_DEPLOYED, "water bucket clutch! (difficulty: " + ctx.settings().difficulty() + ")");
        }
    }

    private void collectClutchWater(PlayerInventory inv) {
        if (clutchWaterPos != null && clutchBucketSlot >= 0) {
            BlockPos waterAt = null;

            for (BlockPos candidate : new BlockPos[]{clutchWaterPos, clutchWaterPos.below()}) {
                if (ctx.world().type(candidate) == Material.WATER) {
                    waterAt = candidate;
                    break;
                }
            }

            int emptySlot = -1;

            for (int i = 0; i < 9; i++) {
                if (ItemKinds.is(inv.getItem(i), Material.BUCKET)) {
                    emptySlot = i;
                    break;
                }
            }

            if (waterAt != null && emptySlot >= 0) {
                int prevSel = InventoryUtils.selected(ctx);
                InventoryUtils.select(ctx, emptySlot);
                Vec3 eye = Ent.eyePos(bot);
                double dx = waterAt.getX() + 0.5 - eye.x();
                double dy = waterAt.getY() + 0.5 - eye.y();
                double dz = waterAt.getZ() + 0.5 - eye.z();
                double horiz = Math.sqrt(dx * dx + dz * dz);
                ctx.controls().look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) (-Math.toDegrees(Math.atan2(dy, horiz))));
                ctx.hands().useItem(EquipmentSlot.HAND);
                ctx.hands().swing(EquipmentSlot.HAND);
                InventoryUtils.select(ctx, prevSel);
            }
        }

        restoreSlot();
        collectingClutch = false;
        clutchWaterPos = null;
        clutchBucketSlot = -1;
        clutchTimer = 0;
    }

    public void suppressThisFall() {
        rolledThisFall = true;
        willClutch = false;
    }

    private void restoreSlot() {
        if (prevSelected >= 0) {
            InventoryUtils.select(ctx, prevSelected);
            prevSelected = -1;
        }
    }

    private int findWaterBucket(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (ItemKinds.is(inv.getItem(i), Material.WATER_BUCKET)) {
                return i;
            }
        }

        for (int ix = 9; ix < 36; ix++) {
            if (ItemKinds.is(inv.getItem(ix), Material.WATER_BUCKET)) {
                return InventoryUtils.moveToHotbarSafe(ctx, ix);
            }
        }

        return -1;
    }

    private int blocksToGround() {
        BlockPos feet = BlockPos.containing(Ent.pos(bot));

        for (int i = 1; i <= ctx.tuning().integer(TuningKey.WATER_CLUTCH_SCAN_DEPTH); i++) {
            if (ctx.world().blocksMotion(feet.below(i))) {
                return i;
            }
        }

        return ctx.tuning().integer(TuningKey.WATER_CLUTCH_SCAN_DEPTH);
    }
}
