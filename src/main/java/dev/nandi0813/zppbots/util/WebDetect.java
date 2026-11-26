package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Mth;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;

public final class WebDetect {

    private WebDetect() {
    }

    public static boolean touchingWeb(Entity entity) {
        return firstWeb(entity) != null;
    }

    public static BlockPos firstWeb(Entity entity) {
        World world = entity.getWorld();
        AABB box = Ent.box(entity);
        int minX = Mth.floor(box.minX());
        int maxX = Mth.floor(box.maxX());
        int minY = Math.max(Mth.floor(box.minY()), world.getMinHeight());
        int maxY = Math.min(Mth.floor(box.maxY()), world.getMaxHeight() - 1);
        int minZ = Mth.floor(box.minZ());
        int maxZ = Mth.floor(box.maxZ());

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.isChunkLoaded(x >> 4, z >> 4) && world.getBlockAt(x, y, z).getType() == Material.COBWEB) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }
}
