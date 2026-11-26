package dev.nandi0813.zppbots.ai.move;

import dev.nandi0813.zppbots.mc.Vec3;

import java.util.Locale;

public final class FollowFormation {
    public static final int DEFAULT_SIZE = 6;
    public static final double SPACING = 2.0;
    public static final int DEFAULT_ROW_WIDTH = 3;

    public static boolean isCentred(FollowFormation.Type t) {
        return t == FollowFormation.Type.SQUARE || t == FollowFormation.Type.CIRCLE || t == FollowFormation.Type.HEXAGON || t == FollowFormation.Type.WALL;
    }

    public static boolean worldAligned(FollowFormation.Type t) {
        return t == FollowFormation.Type.SQUARE || t == FollowFormation.Type.CIRCLE || t == FollowFormation.Type.HEXAGON;
    }

    private FollowFormation() {
    }

    public static FollowFormation.Type fromString(String s) {
        if (s == null) {
            return FollowFormation.Type.NONE;
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "column", "col", "file", "vertical", "line-vertical" -> FollowFormation.Type.COLUMN;
            case "line", "abreast", "horizontal" -> FollowFormation.Type.LINE;
            case "rows", "row" -> FollowFormation.Type.ROWS;
            case "triangle", "wedge", "v", "arrow" -> FollowFormation.Type.TRIANGLE;
            case "grid" -> FollowFormation.Type.GRID;
            case "square", "box" -> FollowFormation.Type.SQUARE;
            case "circle", "ring", "round", "surround" -> FollowFormation.Type.CIRCLE;
            case "hexagon", "hex", "hexagonal" -> FollowFormation.Type.HEXAGON;
            case "wall", "shieldwall", "shield_wall", "phalanx" -> FollowFormation.Type.WALL;
            default -> FollowFormation.Type.NONE;
        };
    }

    public static String[] names() {
        return new String[]{"column", "line", "rows", "triangle", "grid", "square", "circle", "hexagon", "wall"};
    }

    public static double[] localOffset(FollowFormation.Type type, int i, int n, int rowWidth) {
        return localOffset(type, i, n, rowWidth, 2.0);
    }

    public static double[] localOffset(FollowFormation.Type type, int i, int n, int rowWidth, double spacing) {
        double s = spacing > 0.0 ? spacing : 2.0;
        int w = rowWidth > 0 ? rowWidth : 3;
        switch (type) {
            case COLUMN:
                return new double[]{(i + 1) * s, 0.0};
            case LINE:
                return new double[]{s, (i - (n - 1) / 2.0) * s};
            case ROWS:
            case GRID: {
                int rows = (n + w - 1) / w;
                int r = i / w;
                int col = i % w;
                int width = Math.min(w, n - r * w);
                double back = (r + 1) * s;
                return new double[]{back, (col - (width - 1) / 2.0) * s};
            }
            case TRIANGLE: {
                int r = 0;

                int start;
                for (start = 0; start + r + 1 <= i; r++) {
                    start += r + 1;
                }

                int posInRow = i - start;
                int width = r + 1;
                return new double[]{(r + 1) * s, (posInRow - (width - 1) / 2.0) * s};
            }
            case SQUARE:
                return squareOffset(i, size(rowWidth), s);
            case CIRCLE:
                return circleOffset(i, n, size(rowWidth), s);
            case HEXAGON:
                return hexOffset(i, s);
            case WALL: {
                int wide = size(rowWidth);
                int r = i / wide;
                int col = i % wide;
                int width = Math.min(wide, n - r * wide);
                return new double[]{r * s, (col - (width - 1) / 2.0) * s};
            }
            default:
                return new double[]{0.0, 0.0};
        }
    }

    private static int size(int rowWidth) {
        return rowWidth > 0 ? rowWidth : 6;
    }

    private static double[] squareOffset(int i, int size, double s) {
        int sq = Math.max(1, size);
        int seen = 0;

        for (int k = 0; k * 2 < sq; k++) {
            int side = sq - 2 * k;
            int cells = side <= 1 ? 1 : 4 * side - 4;
            if (i < seen + cells) {
                int j = i - seen;
                int col;
                int row;
                if (side <= 1) {
                    row = k;
                    col = k;
                } else if (j < side) {
                    col = k + j;
                    row = k;
                } else if (j < side + (side - 1)) {
                    col = k + side - 1;
                    row = k + 1 + (j - side);
                } else if (j < side + 2 * (side - 1)) {
                    int t = j - (side + side - 1);
                    col = k + side - 2 - t;
                    row = k + side - 1;
                } else {
                    int t = j - (side + 2 * (side - 1));
                    col = k;
                    row = k + side - 2 - t;
                }

                double centre = (sq - 1) / 2.0;
                return new double[]{(row - centre) * s, (col - centre) * s};
            }

            seen += cells;
        }

        return new double[]{0.0, 0.0};
    }

    private static double[] circleOffset(int i, int n, int size, double s) {
        int count = Math.max(1, n);
        double radius = Math.max(1, size - 1) * s / 2.0;
        if (count > 1) {
            radius = Math.max(radius, s / (2.0 * Math.sin(Math.PI / count)));
        }

        double angle = (Math.PI * 2) * i / count;
        return new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
    }

    private static double[] hexOffset(int i, double s) {
        if (i == 0) {
            return new double[]{0.0, 0.0};
        } else {
            int idx = i - 1;
            int seen = 0;

            for (int r = 1; r < 64; r++) {
                int cells = 6 * r;
                if (idx < seen + cells) {
                    int j = idx - seen;
                    int edge = j / r;
                    int step = j % r;
                    double radius = r * s;
                    double a0 = Math.toRadians(60.0 * edge);
                    double a1 = Math.toRadians(60.0 * (edge + 1));
                    double x0 = Math.cos(a0) * radius;
                    double z0 = Math.sin(a0) * radius;
                    double x1 = Math.cos(a1) * radius;
                    double z1 = Math.sin(a1) * radius;
                    double t = (double)step / r;
                    return new double[]{x0 + (x1 - x0) * t, z0 + (z1 - z0) * t};
                }

                seen += cells;
            }

            return new double[]{0.0, 0.0};
        }
    }

    public static boolean reservesCentre(FollowFormation.Type t) {
        return t == FollowFormation.Type.HEXAGON;
    }

    public static Vec3 slotWorld(FollowFormation.Type type, int i, int n, int rowWidth, Vec3 leaderPos, float leaderYawDeg, boolean centreTaken) {
        boolean shift = centreTaken && reservesCentre(type);
        return slotWorld(type, shift ? i + 1 : i, shift ? n + 1 : n, rowWidth, leaderPos, leaderYawDeg);
    }

    public static Vec3 slotWorldSpacedCentred(
        FollowFormation.Type type, int i, int n, int rowWidth, Vec3 leaderPos, float leaderYawDeg, boolean centreTaken, double spacing
    ) {
        boolean shift = centreTaken && reservesCentre(type);
        return slotWorldSpaced(type, shift ? i + 1 : i, shift ? n + 1 : n, rowWidth, leaderPos, leaderYawDeg, spacing);
    }

    public static Vec3 slotWorld(FollowFormation.Type type, int i, int n, int rowWidth, Vec3 leaderPos, float leaderYawDeg) {
        return slotWorldSpaced(type, i, n, rowWidth, leaderPos, leaderYawDeg, 2.0);
    }

    public static Vec3 slotWorldSpaced(FollowFormation.Type type, int i, int n, int rowWidth, Vec3 leaderPos, float leaderYawDeg, double spacing) {
        if (type == FollowFormation.Type.NONE) {
            return leaderPos;
        } else {
            double[] off = localOffset(type, i, Math.max(1, n), rowWidth, spacing);
            double back = off[0];
            double side = off[1];
            double yaw = Math.toRadians(leaderYawDeg);
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            double rx = Math.cos(yaw);
            double rz = Math.sin(yaw);
            double x = leaderPos.x() - fx * back + rx * side;
            double z = leaderPos.z() - fz * back + rz * side;
            return new Vec3(x, leaderPos.y(), z);
        }
    }

    public static enum Type {
        NONE,
        COLUMN,
        LINE,
        ROWS,
        TRIANGLE,
        GRID,
        SQUARE,
        CIRCLE,
        HEXAGON,
        WALL;
    }
}
