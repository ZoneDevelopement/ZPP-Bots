package dev.nandi0813.zppbots.path;

import dev.nandi0813.zppbots.brain.BotContext;
import dev.nandi0813.zppbots.brain.BotWorld;
import dev.nandi0813.zppbots.mc.BlockPos;
import dev.nandi0813.zppbots.mc.Ent;
import dev.nandi0813.zppbots.mc.Vec3;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PathFinder {
    private static final int NO_FALL = 256;
    private static final Moves[] ALL_MOVES = Moves.values();

    private static int getMaxFallDistance(Player player) {
        return player.getGameMode() == GameMode.CREATIVE ? NO_FALL : (int) Ent.attr(player, Attribute.SAFE_FALL_DISTANCE);
    }

    private static int getMaxJumpHeight(Player player) {
        int level = 0;
        PotionEffect jumpBoost = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jumpBoost != null) {
            level = jumpBoost.getAmplifier() + 1;
        }

        double velocity = 0.42 + level * 0.1;
        return (int)(velocity * velocity / 0.16);
    }

    public static List<BlockPos> findPath(BotWorld world, BlockPos start, Vec3 target, PathSettings settings, BotContext ctx) {
        return findPath(world, start, target, settings, ctx, 50000);
    }

    public static List<BlockPos> findPath(BotWorld world, BlockPos start, Vec3 target, PathSettings settings, BotContext ctx, int maxIterations) {
        Player player = ctx.player();
        settings.calculateSwimCost(Ent.attr(player, Attribute.WATER_MOVEMENT_EFFICIENCY), player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE));
        try (BlockProbe probe = BlockProbe.open(world, settings)) {
            return new Search().run(probe, start, target, getMaxJumpHeight(player), getMaxFallDistance(player), maxIterations);
        }
    }

    static List<BlockPos> findPath(BlockProbe probe, BlockPos start, Vec3 target, int maxJump, int maxFall, int maxIterations) {
        return new Search().run(probe, start, target, maxJump, maxFall, maxIterations);
    }

    private static boolean isWithinGoal(int x, int y, int z, Vec3 target, PathSettings settings) {
        double hDist = closestHDistToBlock(x, z, target);
        double vDist = Math.abs(y - target.y());
        return settings.isWithinTarget(hDist, vDist);
    }

    private static double heuristic(int x, int y, int z, Vec3 target) {
        double dx = x + 0.5 - target.x();
        double dy = y - target.y();
        double dz = z + 0.5 - target.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double closestHDistToBlock(int x, int z, Vec3 target) {
        double closestX = Math.clamp(target.x(), (double)x, x + 1.0);
        double closestZ = Math.clamp(target.z(), (double)z, z + 1.0);
        double dx = closestX - target.x();
        double dz = closestZ - target.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double closestHDistToBlock(BlockPos pos, Vec3 target) {
        double closestX = Math.clamp(target.x(), (double)pos.getX(), pos.getX() + 1.0);
        double closestZ = Math.clamp(target.z(), (double)pos.getZ(), pos.getZ() + 1.0);
        double dx = closestX - target.x();
        double dz = closestZ - target.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static List<BlockPos> smoothPath(List<BlockPos> path, BlockProbe probe) {
        if (path.size() <= 2) {
            return path;
        } else {
            List<BlockPos> smoothed = new ArrayList<>();
            smoothed.add(path.getFirst());
            int current = 0;

            label54:
            while (current < path.size() - 1) {
                if (!isParkourSegment(path, current, probe)) {
                    int farthest = current + 1;
                    int i = path.size() - 1;

                    while (i > current + 1) {
                        label47: {
                            if (!containsParkourSegment(path, current + 1, i, probe)) {
                                boolean bothWater = probe.isWater(path.get(current).getX(), path.get(current).getY(), path.get(current).getZ())
                                    && probe.isWater(path.get(i).getX(), path.get(i).getY(), path.get(i).getZ());
                                if (bothWater) {
                                    if (hasWaterLineOfSight(probe, path.get(current), path.get(i))) {
                                        farthest = i;
                                        break label47;
                                    }
                                } else if (path.get(i).getY() == path.get(current).getY() && hasLineOfSight(probe, path.get(current), path.get(i))) {
                                    farthest = i;
                                    break label47;
                                }
                            }

                            i--;
                            continue;
                        }

                        smoothed.add(path.get(farthest));
                        current = farthest;
                        continue label54;
                    }
                }

                smoothed.add(path.get(current + 1));
                current++;
            }

            return smoothed;
        }
    }

    private static boolean isParkourSegment(List<BlockPos> path, int index, BlockProbe probe) {
        if (index + 1 >= path.size()) {
            return false;
        } else {
            BlockPos from = path.get(index);
            BlockPos to = path.get(index + 1);
            int dx = Math.abs(to.getX() - from.getX());
            int dz = Math.abs(to.getZ() - from.getZ());
            int dist = dx + dz;
            if (dist < 2 || dist > 4) {
                return false;
            } else if (dx != 0 && dz != 0) {
                return false;
            } else {
                int stepX = Integer.compare(to.getX() - from.getX(), 0);
                int stepZ = Integer.compare(to.getZ() - from.getZ(), 0);
                return !probe.canWalkOn(from.getX() + stepX, from.getY() - 1, from.getZ() + stepZ);
            }
        }
    }

    private static boolean containsParkourSegment(List<BlockPos> path, int startIdx, int endIdx, BlockProbe probe) {
        for (int i = startIdx; i < endIdx; i++) {
            if (isParkourSegment(path, i, probe)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasLineOfSight(BlockProbe probe, BlockPos from, BlockPos to) {
        int y = from.getY();
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int sx = Integer.compare(dx, 0);
        int sz = Integer.compare(dz, 0);
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        int x = from.getX();
        int z = from.getZ();
        int error = adx - adz;

        while (x != to.getX() || z != to.getZ()) {
            int e2 = error * 2;
            if (e2 > -adz) {
                error -= adz;
                x += sx;
            }

            if (e2 < adx) {
                error += adx;
                z += sz;
            }

            if (!probe.isWalkable(x, y, z)) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasWaterLineOfSight(BlockProbe probe, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) {
            return true;
        } else {
            for (int step = 1; step <= steps; step++) {
                int x = from.getX() + dx * step / steps;
                int y = from.getY() + dy * step / steps;
                int z = from.getZ() + dz * step / steps;
                if (!probe.canSwimThrough(x, y, z)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static final class Search {
        private static final int NODES = 4096;
        private static final int KEEP_NODES = 16384;
        private static final int CELL_BITS = 13;
        private static final int KEEP_BITS = 15;
        private final MoveResult result = new MoveResult();
        private int[] nx;
        private int[] ny;
        private int[] nz;
        private int[] parent;
        private double[] ng;
        private boolean[] stale;
        private int nodes;
        private int[] heap;
        private double[] heapF;
        private int size;
        private int cellBits;
        private long[] cellKeys;
        private int[] cellNodes;
        private int[] cellStamps;
        private int cellCount;
        private int cellStamp;

        private Search() {
            this.allocNodes(4096);
            this.allocCells(13);
        }

        List<BlockPos> run(BlockProbe probe, BlockPos start, Vec3 target, int maxJump, int maxFall, int maxIterations) {
            PathSettings settings = probe.settings();
            this.nodes = 0;
            this.size = 0;
            this.cellCount = 0;
            if (++this.cellStamp == 0) {
                Arrays.fill(this.cellStamps, 0);
                this.cellStamp = 1;
            }

            int root = this.addNode(start.getX(), start.getY(), start.getZ(), 0.0, -1);
            this.push(root, PathFinder.heuristic(start.getX(), start.getY(), start.getZ(), target));
            this.putCell(this.cellSlot(start.asLong()), start.asLong(), root);

            while (this.size > 0 && maxIterations-- > 0) {
                int current = this.poll();
                int cx = this.nx[current];
                int cy = this.ny[current];
                int cz = this.nz[current];
                if (PathFinder.isWithinGoal(cx, cy, cz, target, settings)) {
                    return PathFinder.smoothPath(this.reconstructPath(current, start), probe);
                }

                if (!this.stale[current]) {
                    double gCost = this.ng[current];

                    for (Moves move : PathFinder.ALL_MOVES) {
                        this.result.reset();
                        move.apply(probe, cx, cy, cz, maxJump, maxFall, this.result);
                        if (!(this.result.cost >= Double.POSITIVE_INFINITY)) {
                            double tentativeG = gCost + this.result.cost;
                            long key = BlockPos.asLong(this.result.x, this.result.y, this.result.z);
                            int slot = this.cellSlot(key);
                            int prev = slot >= 0 ? this.cellNodes[slot] : -1;
                            if (tentativeG < (prev >= 0 ? this.ng[prev] : Double.MAX_VALUE)) {
                                int node = this.addNode(this.result.x, this.result.y, this.result.z, tentativeG, current);
                                if (prev >= 0) {
                                    this.stale[prev] = true;
                                }

                                this.putCell(slot, key, node);
                                this.push(node, tentativeG + PathFinder.heuristic(this.result.x, this.result.y, this.result.z, target));
                            }
                        }
                    }
                }
            }

            return null;
        }

        private List<BlockPos> reconstructPath(int node, BlockPos start) {
            List<BlockPos> path = new ArrayList<>();

            while (node >= 0) {
                path.add(node == 0 ? start : new BlockPos(this.nx[node], this.ny[node], this.nz[node]));
                node = this.parent[node];
            }

            Collections.reverse(path);
            return path;
        }

        private int addNode(int x, int y, int z, double g, int from) {
            int i = this.nodes;
            if (i == this.nx.length) {
                this.growNodes();
            }

            this.nx[i] = x;
            this.ny[i] = y;
            this.nz[i] = z;
            this.ng[i] = g;
            this.parent[i] = from;
            this.stale[i] = false;
            this.nodes = i + 1;
            return i;
        }

        private void allocNodes(int n) {
            this.nx = new int[n];
            this.ny = new int[n];
            this.nz = new int[n];
            this.parent = new int[n];
            this.ng = new double[n];
            this.stale = new boolean[n];
            this.heap = new int[n];
            this.heapF = new double[n];
        }

        private void growNodes() {
            int n = this.nx.length * 2;
            this.nx = Arrays.copyOf(this.nx, n);
            this.ny = Arrays.copyOf(this.ny, n);
            this.nz = Arrays.copyOf(this.nz, n);
            this.parent = Arrays.copyOf(this.parent, n);
            this.ng = Arrays.copyOf(this.ng, n);
            this.stale = Arrays.copyOf(this.stale, n);
            this.heap = Arrays.copyOf(this.heap, n);
            this.heapF = Arrays.copyOf(this.heapF, n);
        }

        private void push(int node, double f) {
            int k = this.size;

            while (k > 0) {
                int up = k - 1 >>> 1;
                if (Double.compare(f, this.heapF[up]) >= 0) {
                    break;
                }

                this.heap[k] = this.heap[up];
                this.heapF[k] = this.heapF[up];
                k = up;
            }

            this.heap[k] = node;
            this.heapF[k] = f;
            this.size++;
        }

        private int poll() {
            int top = this.heap[0];
            int n = --this.size;
            if (n > 0) {
                int x = this.heap[n];
                double xf = this.heapF[n];
                int k = 0;
                int half = n >>> 1;

                while (k < half) {
                    int child = (k << 1) + 1;
                    int right = child + 1;
                    if (right < n && Double.compare(this.heapF[child], this.heapF[right]) > 0) {
                        child = right;
                    }

                    if (Double.compare(xf, this.heapF[child]) <= 0) {
                        break;
                    }

                    this.heap[k] = this.heap[child];
                    this.heapF[k] = this.heapF[child];
                    k = child;
                }

                this.heap[k] = x;
                this.heapF[k] = xf;
            }

            return top;
        }

        private void allocCells(int bits) {
            this.cellBits = bits;
            this.cellKeys = new long[1 << bits];
            this.cellNodes = new int[1 << bits];
            this.cellStamps = new int[1 << bits];
            this.cellCount = 0;
            this.cellStamp = 1;
        }

        private int cellSlot(long key) {
            int mask = (1 << this.cellBits) - 1;

            int i;
            for (i = (int)(key * -7046029254386353131L >>> 64 - this.cellBits); this.cellStamps[i] == this.cellStamp; i = i + 1 & mask) {
                if (this.cellKeys[i] == key) {
                    return i;
                }
            }

            return ~i;
        }

        private void putCell(int slot, long key, int node) {
            if (slot >= 0) {
                this.cellNodes[slot] = node;
            } else {
                int i = ~slot;
                this.cellKeys[i] = key;
                this.cellNodes[i] = node;
                this.cellStamps[i] = this.cellStamp;
                if (++this.cellCount > 1 << this.cellBits >>> 1) {
                    this.growCells();
                }
            }
        }

        private void growCells() {
            long[] keys = this.cellKeys;
            int[] vals = this.cellNodes;
            int[] stamps = this.cellStamps;
            int live = this.cellStamp;
            this.allocCells(this.cellBits + 1);

            for (int i = 0; i < keys.length; i++) {
                if (stamps[i] == live) {
                    int slot = ~this.cellSlot(keys[i]);
                    this.cellKeys[slot] = keys[i];
                    this.cellNodes[slot] = vals[i];
                    this.cellStamps[slot] = this.cellStamp;
                    this.cellCount++;
                }
            }
        }
    }
}
