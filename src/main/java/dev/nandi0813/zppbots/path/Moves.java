package dev.nandi0813.zppbots.path;

public enum Moves {
    DOWNWARD(0, -1, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            if (p.isPassable(x, y - 1, z)) {
                if (p.canWalkOn(x, y - 2, z)) {
                    result.x = x;
                    result.y = y - 1;
                    result.z = z;
                    result.cost = p.settings().getVerticalMoveCost();
                }
            }
        }
    },
    PILLAR(0, 1, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            if (p.isPassable(x, y + 2, z)) {
                if (p.canWalkOn(x, y, z)) {
                    result.x = x;
                    result.y = y + 1;
                    result.z = z;
                    result.cost = p.settings().getVerticalMoveCost() + 0.5;
                }
            }
        }
    },
    TRAVERSE_NORTH(0, 0, -1) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.traverseCost(p, y, x, z - 1, result);
        }
    },
    TRAVERSE_SOUTH(0, 0, 1) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.traverseCost(p, y, x, z + 1, result);
        }
    },
    TRAVERSE_EAST(1, 0, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.traverseCost(p, y, x + 1, z, result);
        }
    },
    TRAVERSE_WEST(-1, 0, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.traverseCost(p, y, x - 1, z, result);
        }
    },
    ASCEND_NORTH(0, 1, -1) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.ascendCost(p, x, y, z, x, z - 1, maxJump, result);
        }
    },
    ASCEND_SOUTH(0, 1, 1) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.ascendCost(p, x, y, z, x, z + 1, maxJump, result);
        }
    },
    ASCEND_EAST(1, 1, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.ascendCost(p, x, y, z, x + 1, z, maxJump, result);
        }
    },
    ASCEND_WEST(-1, 1, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.ascendCost(p, x, y, z, x - 1, z, maxJump, result);
        }
    },
    DESCEND_NORTH(0, -1, -1, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.descendCost(p, y, x, z - 1, maxFall, result);
        }
    },
    DESCEND_SOUTH(0, -1, 1, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.descendCost(p, y, x, z + 1, maxFall, result);
        }
    },
    DESCEND_EAST(1, -1, 0, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.descendCost(p, y, x + 1, z, maxFall, result);
        }
    },
    DESCEND_WEST(-1, -1, 0, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.descendCost(p, y, x - 1, z, maxFall, result);
        }
    },
    DIAGONAL_NORTHEAST(1, 0, -1, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.diagonalCost(p, x, y, z, x + 1, z - 1, result);
        }
    },
    DIAGONAL_NORTHWEST(-1, 0, -1, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.diagonalCost(p, x, y, z, x - 1, z - 1, result);
        }
    },
    DIAGONAL_SOUTHEAST(1, 0, 1, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.diagonalCost(p, x, y, z, x + 1, z + 1, result);
        }
    },
    DIAGONAL_SOUTHWEST(-1, 0, 1, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.diagonalCost(p, x, y, z, x - 1, z + 1, result);
        }
    },
    PARKOUR_NORTH(0, 0, -4, true, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.parkourCost(p, x, y, z, 0, -1, result);
        }
    },
    PARKOUR_SOUTH(0, 0, 4, true, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.parkourCost(p, x, y, z, 0, 1, result);
        }
    },
    PARKOUR_EAST(4, 0, 0, true, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.parkourCost(p, x, y, z, 1, 0, result);
        }
    },
    PARKOUR_WEST(-4, 0, 0, true, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.parkourCost(p, x, y, z, -1, 0, result);
        }
    },
    SWIM_NORTH(0, 0, -1) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.swimHorizontal(p, x, y, z, x, z - 1, result);
        }
    },
    SWIM_SOUTH(0, 0, 1) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.swimHorizontal(p, x, y, z, x, z + 1, result);
        }
    },
    SWIM_EAST(1, 0, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.swimHorizontal(p, x, y, z, x + 1, z, result);
        }
    },
    SWIM_WEST(-1, 0, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.swimHorizontal(p, x, y, z, x - 1, z, result);
        }
    },
    SWIM_UP(0, 1, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.swimVertical(p, x, y, z, 1, result);
        }
    },
    SWIM_DOWN(0, -1, 0) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            Moves.swimVertical(p, x, y, z, -1, result);
        }
    },
    BUBBLE_COLUMN_UP(0, 1, 0, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            if (p.isBubbleColumnUp(x, y, z)) {
                int height = p.getBubbleColumnHeight(x, y, z, true);
                int destY = y + height;
                if (p.canSwimThrough(x, destY, z) || p.canWalkOn(x, destY - 1, z)) {
                    result.x = x;
                    result.y = destY;
                    result.z = z;
                    result.cost = height * 0.1;
                }
            }
        }
    },
    BUBBLE_COLUMN_DOWN(0, -1, 0, false, true) {
        @Override
        public void apply(BlockProbe p, int x, int y, int z, int maxJump, int maxFall, MoveResult result) {
            if (p.isBubbleColumnDown(x, y, z)) {
                int depth = p.getBubbleColumnHeight(x, y, z, false);
                if (depth >= 2) {
                    int destY = y - depth + 1;
                    if (p.canSwimThrough(x, destY, z) || p.canWalkOn(x, destY - 1, z)) {
                        result.x = x;
                        result.y = destY;
                        result.z = z;
                        result.cost = depth * 0.1;
                    }
                }
            }
        }
    };

    private static final double SQRT_2 = Math.sqrt(2.0);
    private static final double JUMP_PENALTY = 0.5;
    private static final double SPRINT_MULTIPLIER = 0.6;
    private static final double FALL_COST_PER_BLOCK = 0.3;
    private static final double BUBBLE_COST_PER_BLOCK = 0.1;
    public final int xOffset;
    public final int yOffset;
    public final int zOffset;
    public final boolean dynamicXZ;
    public final boolean dynamicY;

    private Moves(int x, int y, int z) {
        this(x, y, z, false, false);
    }

    private Moves(int x, int y, int z, boolean dynamicXZ, boolean dynamicY) {
        this.xOffset = x;
        this.yOffset = y;
        this.zOffset = z;
        this.dynamicXZ = dynamicXZ;
        this.dynamicY = dynamicY;
    }

    public abstract void apply(BlockProbe var1, int var2, int var3, int var4, int var5, int var6, MoveResult var7);

    private static void traverseCost(BlockProbe p, int y, int destX, int destZ, MoveResult result) {
        PathSettings settings = p.settings();
        if (p.isPassable(destX, y, destZ)) {
            if (p.isPassable(destX, y + 1, destZ)) {
                if (p.canWalkOn(destX, y - 1, destZ)) {
                    result.x = destX;
                    result.y = y;
                    result.z = destZ;
                    double cost = settings.getHorizontalMoveCost();
                    if (settings.getMoveType() != PathSettings.MoveType.WALK) {
                        cost *= 0.6;
                    }

                    result.cost = cost;
                }
            }
        }
    }

    private static void ascendCost(BlockProbe p, int x, int y, int z, int destX, int destZ, int maxJump, MoveResult result) {
        PathSettings settings = p.settings();
        if (maxJump >= 1) {
            if (p.isPassable(x, y + 2, z)) {
                if (p.isPassable(destX, y + 1, destZ)) {
                    if (p.isPassable(destX, y + 2, destZ)) {
                        if (p.canWalkOn(destX, y, destZ)) {
                            result.x = destX;
                            result.y = y + 1;
                            result.z = destZ;
                            result.cost = settings.getHorizontalMoveCost() + settings.getVerticalMoveCost() + 0.5;
                        }
                    }
                }
            }
        }
    }

    private static void descendCost(BlockProbe p, int y, int destX, int destZ, int maxFall, MoveResult result) {
        PathSettings settings = p.settings();
        if (p.isPassable(destX, y, destZ)) {
            if (p.isPassable(destX, y + 1, destZ)) {
                if (p.canWalkOn(destX, y - 2, destZ) && p.isPassable(destX, y - 1, destZ)) {
                    result.x = destX;
                    result.y = y - 1;
                    result.z = destZ;
                    result.cost = settings.getHorizontalMoveCost() + 0.3;
                } else if (p.isPassable(destX, y - 1, destZ)) {
                    for (int drop = 2; drop <= maxFall; drop++) {
                        int landY = y - drop;
                        if (p.canWalkOn(destX, landY - 1, destZ) && p.isPassable(destX, landY, destZ) && p.isPassable(destX, landY + 1, destZ)) {
                            result.x = destX;
                            result.y = landY;
                            result.z = destZ;
                            result.cost = settings.getHorizontalMoveCost() + drop * 0.3;
                            return;
                        }

                        if (!p.isPassable(destX, landY, destZ)) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private static void diagonalCost(BlockProbe p, int x, int y, int z, int destX, int destZ, MoveResult result) {
        PathSettings settings = p.settings();
        if (p.isPassable(destX, y, destZ)) {
            if (p.isPassable(destX, y + 1, destZ)) {
                if (p.canWalkOn(destX, y - 1, destZ)) {
                    if (p.isPassable(destX, y, z)) {
                        if (p.isPassable(destX, y + 1, z)) {
                            if (p.isPassable(x, y, destZ)) {
                                if (p.isPassable(x, y + 1, destZ)) {
                                    result.x = destX;
                                    result.y = y;
                                    result.z = destZ;
                                    double cost = settings.getHorizontalMoveCost() * SQRT_2;
                                    if (settings.getMoveType() != PathSettings.MoveType.WALK) {
                                        cost *= 0.6;
                                    }

                                    result.cost = cost;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void parkourCost(BlockProbe p, int x, int y, int z, int dx, int dz, MoveResult result) {
        PathSettings settings = p.settings();
        if (settings.getMoveType() != PathSettings.MoveType.WALK) {
            int maxDist = settings.getMoveType() == PathSettings.MoveType.SPRINT_JUMP ? 4 : 3;
            if (p.canWalkOn(x, y - 1, z)) {
                int adjX = x + dx;
                int adjZ = z + dz;
                if (!p.canWalkOn(adjX, y - 1, adjZ)) {
                    if (p.isPassable(adjX, y, adjZ)) {
                        if (p.isPassable(adjX, y + 1, adjZ)) {
                            if (p.isPassable(x, y + 2, z)) {
                                for (int dist = 2; dist <= maxDist; dist++) {
                                    int landX = x + dx * dist;
                                    int landZ = z + dz * dist;
                                    boolean clearPath = true;

                                    for (int d = 2; d < dist; d++) {
                                        int midX = x + dx * d;
                                        int midZ = z + dz * d;
                                        if (!p.isPassable(midX, y, midZ) || !p.isPassable(midX, y + 1, midZ) || !p.isPassable(midX, y + 2, midZ)) {
                                            clearPath = false;
                                            break;
                                        }
                                    }

                                    if (!clearPath) {
                                        break;
                                    }

                                    if (p.isPassable(landX, y + 2, landZ)
                                        && p.isPassable(landX, y, landZ)
                                        && p.isPassable(landX, y + 1, landZ)
                                        && p.canWalkOn(landX, y - 1, landZ)) {
                                        double cost = settings.getHorizontalMoveCost() * 0.6 * dist + 0.5;
                                        result.x = landX;
                                        result.y = y;
                                        result.z = landZ;
                                        result.cost = cost;
                                        return;
                                    }
                                }

                                int maxAscendDist = 3;

                                for (int dist = 2; dist <= maxAscendDist; dist++) {
                                    int landX = x + dx * dist;
                                    int landZ = z + dz * dist;
                                    boolean clearPath = true;

                                    for (int dxx = 1; dxx < dist; dxx++) {
                                        int midX = x + dx * dxx;
                                        int midZ = z + dz * dxx;
                                        if (!p.isPassable(midX, y + 1, midZ) || !p.isPassable(midX, y + 2, midZ)) {
                                            clearPath = false;
                                            break;
                                        }
                                    }

                                    if (!clearPath) {
                                        break;
                                    }

                                    if (p.isPassable(landX, y + 1, landZ)
                                        && p.isPassable(landX, y + 2, landZ)
                                        && p.isPassable(landX, y + 3, landZ)
                                        && p.canWalkOn(landX, y, landZ)) {
                                        double cost = settings.getHorizontalMoveCost() * 0.6 * dist + settings.getVerticalMoveCost() + 0.5;
                                        result.x = landX;
                                        result.y = y + 1;
                                        result.z = landZ;
                                        result.cost = cost;
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void swimHorizontal(BlockProbe p, int x, int y, int z, int destX, int destZ, MoveResult result) {
        PathSettings settings = p.settings();
        boolean srcWater = p.isWater(x, y, z);
        boolean swimming = p.isWater(destX, y, destZ);
        if (srcWater || swimming) {
            if (p.canSwimThrough(destX, y, destZ) || p.isPassable(destX, y, destZ)) {
                if (p.canSwimThrough(destX, y + 1, destZ) || p.isPassable(destX, y + 1, destZ)) {
                    boolean landing = !swimming && p.canWalkOn(destX, y - 1, destZ);
                    if (landing || swimming) {
                        result.x = destX;
                        result.y = y;
                        result.z = destZ;
                        result.cost = swimming ? settings.getHorizontalMoveCost() * settings.getSwimCostMultiplier() : settings.getHorizontalMoveCost();
                    }
                }
            }
        }
    }

    private static void swimVertical(BlockProbe p, int x, int y, int z, int dy, MoveResult result) {
        PathSettings settings = p.settings();
        if (p.isWater(x, y, z)) {
            int destY = y + dy;
            if (dy > 0) {
                if (!p.canSwimThrough(x, destY, z) && !p.isPassable(x, destY, z)) {
                    return;
                }

                if (!p.canSwimThrough(x, destY + 1, z) && !p.isPassable(x, destY + 1, z)) {
                    return;
                }
            } else if (!p.canSwimThrough(x, destY, z)) {
                return;
            }

            result.x = x;
            result.y = destY;
            result.z = z;
            result.cost = settings.getVerticalMoveCost() * settings.getSwimCostMultiplier();
        }
    }
}
