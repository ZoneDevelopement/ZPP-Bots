package dev.nandi0813.zppbots.ai.escape;

import dev.nandi0813.zppbots.ai.move.ScaffoldController;
import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.tuning.TuningKey;
import dev.nandi0813.zppbots.util.InventoryUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Edge clutch: right after the bot falls off an edge, it places a block against that edge under its feet. */
public final class BlockClutchController {

    private static final String BLOCK_CLUTCH = "BLOCK_CLUTCH";

    private final BotContext ctx;
    private final Player bot;
    private BlockPos support;
    private boolean rolled;
    private boolean willClutch;

    public BlockClutchController(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
    }

    public boolean tick() {
        if (bot.isOnGround()) {
            BlockPos below = Ent.blockPos(bot).below();
            support = ctx.world().isAir(below) ? null : below;
            rolled = false;
            return false;
        }

        if (support == null || bot.isGliding() || !ctx.tuning().flag(TuningKey.BLOCK_CLUTCH)
                || Ent.velocity(bot).y() >= 0.0
                || bot.getFallDistance() > ctx.tuning().number(TuningKey.BLOCK_CLUTCH_MAX_FALL).floatValue()
                || bot.getLocation().getY() < support.getY() + 1.0) {
            return false;
        }

        BlockPos feet = Ent.blockPos(bot);
        BlockPos cell = new BlockPos(feet.getX(), support.getY(), feet.getZ());
        Direction face = clutchFace(support, cell);
        if (face == null || !ctx.world().canReplace(cell) || !longDrop(cell)) {
            return false;
        }

        if (!rolled) {
            rolled = true;
            willClutch = ctx.settings().rollClutch();
        }

        int slot = findBlock();
        if (!willClutch || slot < 0) {
            return false;
        }

        InventoryUtils.select(ctx, InventoryUtils.moveToHotbarSafe(ctx, slot));
        Vec3 hit = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, 0.0, face.getStepZ() * 0.5);
        ctx.controls().lookAt(hit);
        ctx.hands().useItemOn(EquipmentSlot.HAND, support, face, hit);
        ctx.hands().swing(EquipmentSlot.HAND);
        ctx.log().decision("BlockClutchController", BLOCK_CLUTCH, "[Clutch] placed a block under the bot after falling off an edge");
        support = null;
        return true;
    }

    /** The face of {@code support} that touches {@code cell}, or null unless the two are side by side. */
    static Direction clutchFace(BlockPos support, BlockPos cell) {
        int dx = cell.getX() - support.getX();
        int dz = cell.getZ() - support.getZ();
        if (cell.getY() != support.getY() || Math.abs(dx) + Math.abs(dz) != 1) {
            return null;
        }

        return Direction.getNearest(dx, 0, dz);
    }

    private boolean longDrop(BlockPos cell) {
        int minDrop = ctx.tuning().integer(TuningKey.BLOCK_CLUTCH_MIN_DROP);
        for (int i = 0; i < minDrop; i++) {
            if (!ctx.world().isAir(cell.below(i))) {
                return false;
            }
        }

        return true;
    }

    private int findBlock() {
        for (int i = 0; i < InventoryUtils.SIZE; i++) {
            if (ScaffoldController.isPillarBlock(ctx, bot.getInventory().getItem(i))) {
                return i;
            }
        }

        return -1;
    }
}
