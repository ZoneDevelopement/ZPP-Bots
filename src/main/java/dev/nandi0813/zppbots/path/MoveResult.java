package dev.nandi0813.zppbots.path;

public class MoveResult {
    public int x;
    public int y;
    public int z;
    public double cost;
    public static final double COST_INF = Double.POSITIVE_INFINITY;

    public MoveResult() {
        this.reset();
    }

    public void reset() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.cost = Double.POSITIVE_INFINITY;
    }
}
