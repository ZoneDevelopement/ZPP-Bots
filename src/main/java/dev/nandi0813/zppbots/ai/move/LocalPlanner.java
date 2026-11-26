package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Mth;
import dev.nandi0813.zppbots.mc.Vec3;
import dev.nandi0813.zppbots.path.BlockProbe;
import dev.nandi0813.zppbots.path.MoveResult;
import dev.nandi0813.zppbots.path.Moves;
import dev.nandi0813.zppbots.path.PathFinder;
import dev.nandi0813.zppbots.path.PathSettings;
import dev.nandi0813.zppbots.tuning.BotTuning;
import dev.nandi0813.zppbots.tuning.TuningKey;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LocalPlanner {

    private static final int ASTAR_BUDGET = 1500;
    private static final int[] SIDE_X = {0, 0, 1, -1};
    private static final int[] SIDE_Z = {-1, 1, 0, 0};

    private final BotContext ctx;
    private final Player bot;
    private final PathSettings settings;
    private final MoveResult scratch = new MoveResult();
    private final Moves[] moves = Moves.values();
    private final ArrayDeque<BlockPos> window = new ArrayDeque<>();
    private Vec3 lastGoal = null;
    private BlockPos planAnchor = null;
    private int noProgressTicks = 0;
    private double lastFrontDist = Double.MAX_VALUE;
    private final PathSettings aStarSettings;
    private List<BlockPos> path = new ArrayList<>();
    private int pathIndex = 0;
    private Vec3 pathGoal = null;
    private int aStarCooldown = 0;
    private int directCooldown = 0;
    private boolean directOk = false;

    public LocalPlanner(BotContext ctx) {
        this.ctx = ctx;
        this.bot = ctx.player();
        this.settings = new PathSettings(ctx.tuning());
        this.aStarSettings = new PathSettings(ctx.tuning());
        if (ctx.tuning().flag(TuningKey.PATH_ALLOW_LOCAL_WEB)) {
            this.settings.removeAvoidedBlock(Material.COBWEB);
        }
    }

    public Vec3 tick(Vec3 goal, boolean combat) {
        BlockPos botCell = Ent.blockPos(bot);
        Vec3 botPos = Ent.pos(bot);
        if (aStarCooldown > 0) {
            aStarCooldown--;
        }

        if (directCooldown > 0) {
            directCooldown--;
        }

        double distToGoal = botPos.distanceTo(goal);
        if (directCooldown == 0) {
            directOk = directLaneClear(botCell, goal, distToGoal);
            directCooldown = ctx.tuning().integer(TuningKey.PLANNER_DIRECT_INTERVAL);
        }

        if (directOk) {
            path.clear();
            return goal;
        } else {
            if (!path.isEmpty()) {
                advancePath();
                boolean stale = pathIndex >= path.size()
                        || pathGoal != null
                                && goal.distanceToSqr(pathGoal)
                                        > square(ctx.tuning()
                                                .number(TuningKey.PLANNER_ASTAR_GOAL_MOVE)
                                                .doubleValue())
                        || offPath()
                        || noProgressTicks > ctx.tuning().integer(TuningKey.PLANNER_NO_PROGRESS_REPLAN);
                if (!stale) {
                    BlockPos wp = path.get(pathIndex);
                    trackProgress(wp);
                    return new Vec3(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5);
                }

                path.clear();
            }

            if (aStarCooldown == 0
                    && distToGoal > ctx.tuning().number(TuningKey.PLANNER_ASTAR_MIN_DIST).doubleValue()
                    && distToGoal
                            <= ctx.tuning().number(TuningKey.PLANNER_ASTAR_MAX_DIST).doubleValue()) {
                aStarCooldown = ctx.tuning().integer(TuningKey.PLANNER_ASTAR_MIN_INTERVAL)
                        + (bot.getEntityId() & 3);
                List<BlockPos> found = PathFinder.findPath(
                        ctx.world(), botCell, goal, aStarSettings, ctx, ASTAR_BUDGET);
                if (found != null && !found.isEmpty()) {
                    path = found;
                    pathIndex = 0;
                    pathGoal = goal;
                    noProgressTicks = 0;
                    lastFrontDist = Double.MAX_VALUE;
                    advancePath();
                    if (pathIndex < path.size()) {
                        BlockPos wp = path.get(pathIndex);
                        trackProgress(wp);
                        return new Vec3(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5);
                    }

                    path.clear();
                }
            }

            boolean replan = window.isEmpty()
                    || lastGoal == null
                    || goal.distanceToSqr(lastGoal)
                            > square(ctx.tuning().number(TuningKey.PLANNER_REPLAN_GOAL_MOVE).doubleValue())
                    || planAnchor != null && botPos.distanceToSqr(Vec3.atCenterOf(planAnchor))
                            > square(ctx.tuning().number(TuningKey.PLANNER_REPLAN_DISPLACE).doubleValue())
                    || noProgressTicks > ctx.tuning().integer(TuningKey.PLANNER_NO_PROGRESS_REPLAN);

            try (BlockProbe probe = BlockProbe.open(ctx.world(), settings)) {
                if (replan) {
                    rebuild(probe, botCell, goal);
                } else {
                    slide(probe, botCell, goal);
                }
            }

            lastGoal = goal;
            if (window.isEmpty()) {
                return null;
            } else {
                BlockPos front = window.peekFirst();
                double frontDist = botPos.distanceTo(Vec3.atCenterOf(front));
                if (frontDist
                        < lastFrontDist
                                - ctx.tuning().number(TuningKey.PLANNER_PROGRESS_EPS).doubleValue()) {
                    lastFrontDist = frontDist;
                    noProgressTicks = 0;
                } else {
                    noProgressTicks++;
                }

                return new Vec3(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
            }
        }
    }

    private void advancePath() {
        Vec3 pos = Ent.pos(bot);
        while (pathIndex < path.size()) {
            BlockPos wp = path.get(pathIndex);
            double h = PathFinder.closestHDistToBlock(wp, pos);
            double v = Math.abs(pos.y() - wp.getY());
            if (h <= ctx.tuning().number(TuningKey.PLANNER_REACH_H_PATH).doubleValue()
                    && v <= ctx.tuning().number(TuningKey.PLANNER_REACH_VERT).doubleValue()) {
                pathIndex++;
                lastFrontDist = Double.MAX_VALUE;
                noProgressTicks = 0;
                continue;
            }
            break;
        }
    }

    private boolean offPath() {
        if (pathIndex >= path.size()) {
            return true;
        } else {
            Vec3 pos = Ent.pos(bot);
            BlockPos wp = path.get(pathIndex);
            double h = PathFinder.closestHDistToBlock(wp, pos);
            double v = Math.abs(pos.y() - wp.getY());
            return h > ctx.tuning().number(TuningKey.PLANNER_OFF_PATH_HORIZ).doubleValue()
                    || v > ctx.tuning().number(TuningKey.PLANNER_OFF_PATH_VERT).doubleValue();
        }
    }

    private void trackProgress(BlockPos wp) {
        double d = Ent.pos(bot).distanceTo(Vec3.atCenterOf(wp));
        if (d < lastFrontDist
                        - ctx.tuning().number(TuningKey.PLANNER_PROGRESS_EPS).doubleValue()) {
            lastFrontDist = d;
            noProgressTicks = 0;
        } else {
            noProgressTicks++;
        }
    }

    private boolean directLaneClear(BlockPos botCell, Vec3 goal, double dist) {
        Vec3 pos = Ent.pos(bot);
        if (!(dist > ctx.tuning().number(TuningKey.PLANNER_DIRECT_MAX_DIST).doubleValue())
                && !(Math.abs(goal.y() - pos.y())
                        > ctx.tuning().number(TuningKey.PLANNER_DIRECT_MAX_VERT).doubleValue())) {
            double dx = goal.x() - pos.x();
            double dz = goal.z() - pos.z();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz < ctx.tuning().number(TuningKey.PLANNER_DIRECT_NEAR_HORIZ).doubleValue()) {
                return true;
            } else {
                double stepX = dx / horiz;
                double stepZ = dz / horiz;
                int y = botCell.getY();
                int steps = (int) Math.min(
                        horiz, ctx.tuning().number(TuningKey.PLANNER_DIRECT_MAX_DIST).doubleValue());
                BlockPos goalCell = BlockPos.containing(goal);

                try (BlockProbe probe = BlockProbe.open(ctx.world(), settings)) {
                    for (int i = 1; i <= steps; i++) {
                        int x = Mth.floor(pos.x() + stepX * i);
                        int z = Mth.floor(pos.z() + stepZ * i);
                        if (!probe.isWalkable(x, y, z)) {
                            if (probe.isWalkable(x, y + 1, z)) {
                                y++;
                            } else {
                                if (!probe.isWalkable(x, y - 1, z)) {
                                    return false;
                                }

                                y--;
                            }
                        }

                        if (webAt(probe, x, y, z) && !nearGoal(x, y, z, goalCell)) {
                            return false;
                        }
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    private static double square(double value) {
        return value * value;
    }

    private static boolean webAt(BlockProbe probe, int x, int y, int z) {
        return probe.type(x, y, z) == Material.COBWEB || probe.type(x, y + 1, z) == Material.COBWEB;
    }

    private boolean nearGoal(int x, int y, int z, BlockPos goalCell) {
        return Math.abs(x - goalCell.getX())
                        <= ctx.tuning().integer(TuningKey.PLANNER_WEB_GOAL_HORIZ)
                && Math.abs(z - goalCell.getZ())
                        <= ctx.tuning().integer(TuningKey.PLANNER_WEB_GOAL_HORIZ)
                && Math.abs(y - goalCell.getY())
                        <= ctx.tuning().integer(TuningKey.PLANNER_WEB_GOAL_VERT);
    }

    private void rebuild(BlockProbe probe, BlockPos anchor, Vec3 goal) {
        window.clear();
        planAnchor = anchor;
        noProgressTicks = 0;
        lastFrontDist = Double.MAX_VALUE;
        window.addAll(beam(
                probe, anchor, goal, ctx.tuning().integer(TuningKey.PLANNER_WINDOW_LEN)));
        prunePassed();
    }

    private void slide(BlockProbe probe, BlockPos botCell, Vec3 goal) {
        boolean popped;
        for (popped = false; !window.isEmpty() && reached(window.peekFirst()); popped = true) {
            window.pollFirst();
        }

        if (popped) {
            lastFrontDist = Double.MAX_VALUE;
            noProgressTicks = 0;
        }

        while (window.size() < ctx.tuning().integer(TuningKey.PLANNER_WINDOW_LEN)) {
            BlockPos from = window.isEmpty() ? botCell : window.peekLast();
            List<BlockPos> next = beam(probe, from, goal, 1);
            if (next.isEmpty()) {
                break;
            }

            BlockPos b = next.get(0);
            if (b.equals(botCell) || window.contains(b)) {
                break;
            }

            window.addLast(b);
        }

        if (planAnchor == null) {
            planAnchor = botCell;
        }
    }

    private void prunePassed() {
        while (!window.isEmpty() && reached(window.peekFirst())) {
            window.pollFirst();
        }
    }

    private boolean reached(BlockPos cell) {
        Vec3 pos = Ent.pos(bot);
        double h = PathFinder.closestHDistToBlock(cell, pos);
        double v = Math.abs(pos.y() - cell.getY());
        return h <= ctx.tuning().number(TuningKey.PLANNER_REACH_HORIZ).doubleValue()
                && v <= ctx.tuning().number(TuningKey.PLANNER_REACH_VERT).doubleValue();
    }

    private List<BlockPos> beam(BlockProbe probe, BlockPos start, Vec3 goal, int count) {
        int maxJump = maxJump();
        int maxFall = maxFall();
        List<Cand> beam = new ArrayList<>();
        beam.add(new Cand(new ArrayList<>(), botHeading(), 0.0));
        List<BlockPos> best = new ArrayList<>();

        for (int depth = 0; depth < count; depth++) {
            List<Cand> next = new ArrayList<>();

            for (Cand p : beam) {
                BlockPos frontier = p.cells().isEmpty() ? start : p.cells().get(p.cells().size() - 1);
                int fx = frontier.getX();
                int fy = frontier.getY();
                int fz = frontier.getZ();

                for (Moves m : moves) {
                    scratch.reset();
                    m.apply(probe, fx, fy, fz, maxJump, maxFall, scratch);
                    if (!(scratch.cost >= Double.POSITIVE_INFINITY)) {
                        BlockPos cand = new BlockPos(scratch.x, scratch.y, scratch.z);
                        if (!cand.equals(frontier) && !p.cells().contains(cand) && !window.contains(cand)) {
                            double[] stepDir = dir(frontier, cand);
                            double sc = p.score()
                                    + score(probe, frontier, cand, scratch.cost, p.headDir(), stepDir, goal);
                            List<BlockPos> cells = new ArrayList<>(p.cells());
                            cells.add(cand);
                            next.add(new Cand(cells, stepDir, sc));
                        }
                    }
                }
            }

            if (next.isEmpty()) {
                break;
            }

            next.sort(Comparator.comparingDouble(Cand::score).reversed());
            beam = new ArrayList<>(next.subList(
                    0, Math.min(ctx.tuning().integer(TuningKey.PLANNER_BEAM_WIDTH), next.size())));
            best = beam.get(0).cells();
        }

        return best;
    }

    private double score(
            BlockProbe probe,
            BlockPos from,
            BlockPos to,
            double moveCost,
            double[] prevDir,
            double[] stepDir,
            Vec3 goal) {
        double verticalWeight =
                ctx.tuning().number(TuningKey.PLANNER_PROGRESS_VERT_WEIGHT).doubleValue();
        double pFrom = PathFinder.closestHDistToBlock(from, goal)
                + Math.abs(from.getY() - goal.y()) * verticalWeight;
        double pTo = PathFinder.closestHDistToBlock(to, goal)
                + Math.abs(to.getY() - goal.y()) * verticalWeight;
        double progress = pFrom - pTo;
        double turn = 1.0 - (prevDir[0] * stepDir[0] + prevDir[1] * stepDir[1]);
        double vert = Math.abs(to.getY() - from.getY());
        double open = wallProximity(probe, to);
        double diff = blockDifficulty(probe, to) - ctx.tuning().integer(TuningKey.PLANNER_BASE_COST);
        return weightedScore(ctx.tuning(), progress, moveCost, turn, vert, open, diff);
    }

    private int blockDifficulty(BlockProbe probe, BlockPos cell) {
        int x = cell.getX();
        int y = cell.getY();
        int z = cell.getZ();
        BotTuning.Profile profile = ctx.tuning();
        int d = bodyDifficulty(profile, probe.type(x, y, z));
        d = Math.max(d, bodyDifficulty(profile, probe.type(x, y + 1, z)));
        return Math.max(d, floorDifficulty(profile, probe.type(x, y - 1, z)));
    }

    static int bodyDifficulty(BotTuning.Profile profile, Material material) {
        return switch (material) {
            case COBWEB -> profile.integer(TuningKey.PLANNER_COBWEB_COST);
            case POWDER_SNOW -> profile.integer(TuningKey.PLANNER_POWDER_SNOW_COST);
            case SWEET_BERRY_BUSH -> profile.integer(TuningKey.PLANNER_BERRY_COST);
            case WATER -> profile.integer(TuningKey.PLANNER_WATER_COST);
            case BAMBOO, BAMBOO_SAPLING, SUGAR_CANE -> profile.integer(TuningKey.PLANNER_BAMBOO_COST);
            case SCAFFOLDING -> profile.integer(TuningKey.PLANNER_SCAFFOLDING_COST);
            case SNOW -> profile.integer(TuningKey.PLANNER_SNOW_COST);
            default -> profile.integer(TuningKey.PLANNER_BASE_COST);
        };
    }

    static int floorDifficulty(BotTuning.Profile profile, Material material) {
        return switch (material) {
            case HONEY_BLOCK -> profile.integer(TuningKey.PLANNER_HONEY_COST);
            case SOUL_SAND -> profile.integer(TuningKey.PLANNER_SOUL_SAND_COST);
            case SLIME_BLOCK, MUD, MUDDY_MANGROVE_ROOTS -> profile.integer(TuningKey.PLANNER_MUD_COST);
            case ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE -> profile.integer(TuningKey.PLANNER_ICE_COST);
            case SOUL_SOIL -> profile.integer(TuningKey.PLANNER_SOUL_SOIL_COST);
            default -> profile.integer(TuningKey.PLANNER_BASE_COST);
        };
    }

    static double weightedScore(
            BotTuning.Profile profile,
            double progress,
            double moveCost,
            double turn,
            double vert,
            double open,
            double difficulty) {
        return profile.number(TuningKey.PLANNER_W_PROGRESS).doubleValue() * progress
                - profile.number(TuningKey.PLANNER_W_MOVE).doubleValue() * moveCost
                - profile.number(TuningKey.PLANNER_W_TURN).doubleValue() * turn
                - profile.number(TuningKey.PLANNER_W_VERT).doubleValue() * vert
                - profile.number(TuningKey.PLANNER_W_OPEN).doubleValue() * open
                - profile.number(TuningKey.PLANNER_W_DIFFICULTY).doubleValue() * difficulty;
    }

    private double wallProximity(BlockProbe probe, BlockPos cell) {
        int solid = 0;
        int x = cell.getX();
        int y = cell.getY();
        int z = cell.getZ();

        for (int s = 0; s < 4; s++) {
            int nx = x + SIDE_X[s];
            int nz = z + SIDE_Z[s];
            if (probe.isSolid(nx, y, nz) || probe.isSolid(nx, y + 1, nz)) {
                solid++;
            }
        }

        return solid;
    }

    static double[] dir(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        return len < 1.0E-6 ? new double[]{0.0, 0.0} : new double[]{dx / len, dz / len};
    }

    private double[] botHeading() {
        Vec3 v = Ent.velocity(bot);
        double len = Math.sqrt(v.x() * v.x() + v.z() * v.z());
        if (len > ctx.tuning().number(TuningKey.PLANNER_HEADING_MIN_SPEED).doubleValue()) {
            return new double[]{v.x() / len, v.z() / len};
        } else {
            double yaw = Math.toRadians(bot.getLocation().getYaw());
            return new double[]{-Math.sin(yaw), Math.cos(yaw)};
        }
    }

    private int maxJump() {
        PotionEffect boost = bot.getPotionEffect(PotionEffectType.JUMP_BOOST);
        return maxJump(boost != null ? boost.getAmplifier() + 1 : 0);
    }

    static int maxJump(int jumpBoostLevel) {
        double velocity = 0.42 + jumpBoostLevel * 0.1;
        return (int) (velocity * velocity / 0.16);
    }

    private int maxFall() {
        return bot.getGameMode() == GameMode.CREATIVE ? 256 : (int) Ent.attr(bot, Attribute.SAFE_FALL_DISTANCE);
    }

    public boolean hasWindow() {
        return !path.isEmpty() || !window.isEmpty();
    }

    public void invalidate() {
        window.clear();
        path.clear();
        pathIndex = 0;
        pathGoal = null;
        lastGoal = null;
        planAnchor = null;
        aStarCooldown = 0;
        directCooldown = 0;
        directOk = false;
    }

    public void reset() {
        invalidate();
        noProgressTicks = 0;
        lastFrontDist = Double.MAX_VALUE;
    }

    private record Cand(List<BlockPos> cells, double[] headDir, double score) {
    }
}
