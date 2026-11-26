package dev.nandi0813.zppbots.brain;

import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Direction;
import dev.nandi0813.zppbots.mc.Vec3;
import org.bukkit.FluidCollisionMode;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Bukkit view of the bot's world. Never loads chunks: positions in unloaded chunks (or outside the height range)
 * behave like air and {@link #isLoaded} is false.
 */
public final class BotWorld {

    private static final double EPSILON = 1.0E-7;

    private final Player player;

    public BotWorld(Player player) {
        this.player = player;
    }

    public World bukkit() {
        return player.getWorld();
    }

    public long gameTime() {
        return bukkit().getGameTime();
    }

    public int minY() {
        return bukkit().getMinHeight();
    }

    public int maxY() {
        return bukkit().getMaxHeight() - 1;
    }

    public boolean inBounds(BlockPos pos) {
        return pos.getY() >= minY() && pos.getY() <= maxY();
    }

    public boolean isLoaded(BlockPos pos) {
        return bukkit().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public Block block(BlockPos pos) {
        return bukkit().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
    }

    /** The block, or null when out of bounds or not loaded. */
    private Block at(BlockPos pos) {
        return inBounds(pos) && isLoaded(pos) ? block(pos) : null;
    }

    public Material type(BlockPos pos) {
        Block block = at(pos);
        return block == null ? Material.AIR : block.getType();
    }

    public BlockData data(BlockPos pos) {
        Block block = at(pos);
        return block == null ? Material.AIR.createBlockData() : block.getBlockData();
    }

    public boolean isAir(BlockPos pos) {
        return type(pos).isAir();
    }

    public boolean blocksMotion(BlockPos pos) {
        Block block = at(pos);
        return block != null && !block.isPassable() && !block.isLiquid();
    }

    public boolean isFullBlock(BlockPos pos) {
        Block block = at(pos);
        if (block == null) return false;
        List<BoundingBox> boxes = new ArrayList<>(block.getCollisionShape().getBoundingBoxes());
        if (boxes.size() != 1) return false;
        BoundingBox box = boxes.get(0);
        return Math.abs(box.getMinX()) < EPSILON && Math.abs(box.getMinY()) < EPSILON && Math.abs(box.getMinZ()) < EPSILON
                && Math.abs(box.getMaxX() - 1.0) < EPSILON && Math.abs(box.getMaxY() - 1.0) < EPSILON
                && Math.abs(box.getMaxZ() - 1.0) < EPSILON;
    }

    public List<AABB> collisionBoxes(BlockPos pos) {
        Block block = at(pos);
        if (block == null) return List.of();
        List<AABB> boxes = new ArrayList<>();
        for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
            boxes.add(AABB.of(box).move(pos.getX(), pos.getY(), pos.getZ()));
        }
        return boxes;
    }

    /** Highest world Y of the collision boxes, NaN when there are none. */
    public double collisionTop(BlockPos pos) {
        double top = Double.NaN;
        for (AABB box : collisionBoxes(pos)) {
            if (Double.isNaN(top) || box.maxY() > top) top = box.maxY();
        }
        return top;
    }

    public boolean canReplace(BlockPos pos) {
        Block block = at(pos);
        return block == null || block.isReplaceable();
    }

    public boolean isWater(BlockPos pos) {
        Block block = at(pos);
        if (block == null) return false;
        return switch (block.getType()) {
            case WATER, BUBBLE_COLUMN, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> true;
            default -> block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
        };
    }

    public boolean isLava(BlockPos pos) {
        return type(pos) == Material.LAVA;
    }

    public boolean isWaterSource(BlockPos pos) {
        Block block = at(pos);
        if (block == null) return false;
        BlockData data = block.getBlockData();
        if (block.getType() == Material.WATER) return data instanceof Levelled levelled && levelled.getLevel() == 0;
        return data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    public boolean isLavaSource(BlockPos pos) {
        Block block = at(pos);
        return block != null && block.getType() == Material.LAVA
                && block.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
    }

    public BlockHit clip(Vec3 from, Vec3 to, boolean outline, boolean fluids) {
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        World world = bukkit();
        if (distance == 0.0 || !isLoaded(BlockPos.containing(from)) || !isLoaded(BlockPos.containing(to))) {
            return BlockHit.MISS;
        }
        Vec3 direction = delta.normalize();
        RayTraceResult result = world.rayTraceBlocks(from.toLocation(world), direction.toVector(), distance,
                fluids ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER, !outline);
        if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) return BlockHit.MISS;
        BlockFace face = result.getHitBlockFace();
        Vector hit = result.getHitPosition();
        return new BlockHit(true, BlockPos.of(result.getHitBlock()), Direction.fromBlockFace(face), Vec3.of(hit));
    }

    public <T extends Entity> List<T> entities(Class<T> type, AABB box, Predicate<? super T> filter) {
        List<T> found = new ArrayList<>();
        for (Entity entity : bukkit().getNearbyEntities(box.toBoundingBox(),
                e -> type.isInstance(e) && filter.test(type.cast(e)))) {
            found.add(type.cast(entity));
        }
        return found;
    }

    public List<Player> players() {
        return bukkit().getPlayers();
    }

    public int surfaceY(int x, int z) {
        if (!bukkit().isChunkLoaded(x >> 4, z >> 4)) return minY();
        return bukkit().getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
    }

    public float breakSpeed(BlockPos pos) {
        Block block = at(pos);
        return block == null ? 0.0f : block.getBreakSpeed(player);
    }
}
