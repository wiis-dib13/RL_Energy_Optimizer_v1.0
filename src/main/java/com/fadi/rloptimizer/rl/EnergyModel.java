package com.fadi.rloptimizer.rl;

public final class EnergyModel {

    public static final double CLOUD_ENERGY  = 3_233_631.0;
    public static final double EDGE_ENERGY   = 166_866.0;
    public static final double CAMERA_ENERGY = 169_221.0;

    public static final String[] ACTION_NAMES = {"EDGE", "PROXY", "CAMERA", "CLOUD"};
    public static final int N_ACTIONS = 4;

    private EnergyModel() {}

    public static double estimatedEnergy(int action, int numCameras) {
        switch (action) {
            case 0: return EDGE_ENERGY;
            case 1: return EDGE_ENERGY * 1.2;
            case 2: return CAMERA_ENERGY * (numCameras / 4.0);
            case 3: return CLOUD_ENERGY;
            default: return CLOUD_ENERGY;
        }
    }

    public static double overloadScore(int action) {
        double[] v = {0.10, 0.40, 0.80, 0.05};
        return v[action];
    }

    public static double latencyScore(int action) {
        double[] v = {0.15, 0.35, 0.05, 0.70};
        return v[action];
    }
}
