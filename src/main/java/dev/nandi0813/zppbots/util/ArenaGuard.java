package dev.nandi0813.zppbots.util;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.AABB;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;

public final class ArenaGuard {

    public static final double WALK_INSET = 0.75;
    public static final double PEARL_INSET = 2.0;
    static final int MAX_SAFE_DROP = 3;

    private ArenaGuard() {
    }

    /** True when walking one block along (dirX, dirZ) would leave the arena or drop more than MAX_SAFE_DROP blocks. */
    public static boolean blocksStep(BotContext ctx, double dirX, double dirZ) {
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1.0E-4) return false;
        Vec3 p = Ent.pos(ctx.player());
        AABB box = ctx.match().bounds();
        double nx = p.x() + dirX / len, nz = p.z() + dirZ / len;
        // in the edge band only a step that gets less far outside is allowed, so the bot can walk back in but not slide along the edge
        if (outsideXZ(box, nx, nz, WALK_INSET)
                && outsideDepth(box, nx, nz, WALK_INSET) >= outsideDepth(box, p.x(), p.z(), WALK_INSET)) return true;
        return LedgeGuard.wouldFallDangerously(ctx, dirX, dirZ, MAX_SAFE_DROP);
    }

    /** True when a pearl point or landing is out of the arena or not above the dead zone. */
    public static boolean pearlOut(BotContext ctx, Vec3 at, double inset) {
        return outsideXZ(ctx.match().bounds(), at.x(), at.z(), inset) || at.y() <= ctx.match().deadZoneY();
    }

    /** True when a point lies outside the arena shrunk by inset, sides and ceiling; an empty box never blocks. */
    public static boolean outside(AABB box, Vec3 at, double inset) {
        return outsideXZ(box, at.x(), at.z(), inset) || box.maxY() > box.minY() && at.y() > box.maxY() - inset;
    }

    /** How far (x plus z) a point lies outside the inset box; 0 inside or for an empty box. */
    static double outsideDepth(AABB box, double x, double z, double inset) {
        if (box.maxX() <= box.minX() || box.maxZ() <= box.minZ()) return 0.0;
        double dx = Math.max(0.0, Math.max(box.minX() + inset - x, x - (box.maxX() - inset)));
        double dz = Math.max(0.0, Math.max(box.minZ() + inset - z, z - (box.maxZ() - inset)));
        return dx + dz;
    }

    /** An empty box (no arena known) never blocks. */
    static boolean outsideXZ(AABB box, double x, double z, double inset) {
        if (box.maxX() <= box.minX() || box.maxZ() <= box.minZ()) return false;
        return x < box.minX() + inset || x > box.maxX() - inset || z < box.minZ() + inset || z > box.maxZ() - inset;
    }
}
