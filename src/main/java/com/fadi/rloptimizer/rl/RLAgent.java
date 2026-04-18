package com.fadi.rloptimizer.rl;

import java.util.Random;

public class RLAgent {

    public static final double ALPHA          = 0.15;
    public static final double GAMMA          = 0.90;
    public static final double EPSILON_START  = 1.0;
    public static final double EPSILON_MIN    = 0.05;
    public static final double EPSILON_DECAY  = 0.99;
    public static final double UCB_C          = 2.0;

    public static final double W_ENERGY  = 0.85;
    public static final double W_LOAD    = 0.10;
    public static final double W_LATENCY = 0.05;

    private final int numCameras;
    private final double[] Q = new double[EnergyModel.N_ACTIONS];
    private final int[] visits = new int[EnergyModel.N_ACTIONS];
    private int totalVisits = 0;
    private double epsilon = EPSILON_START;
    private final Random rand = new Random(42);

    public RLAgent(int numCameras) {
        this.numCameras = numCameras;
        for (int a = 0; a < EnergyModel.N_ACTIONS; a++) {
            Q[a] = rewardFor(a);
        }
    }

    public int greedy() {
        int best = 0;
        for (int i = 1; i < EnergyModel.N_ACTIONS; i++) {
            if (Q[i] > Q[best]) best = i;
        }
        return best;
    }

    public int ucbSelect() {
        int best = 0;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int a = 0; a < EnergyModel.N_ACTIONS; a++) {
            double bonus = visits[a] == 0
                    ? Double.MAX_VALUE
                    : UCB_C * Math.sqrt(Math.log(totalVisits + 1) / visits[a]);
            double value = Q[a] + bonus;
            if (value > bestValue) { bestValue = value; best = a; }
        }
        return best;
    }

    public int selectAction() {
        return rand.nextDouble() < epsilon ? ucbSelect() : greedy();
    }

    public double rewardFor(int action) {
        double normEnergy  = EnergyModel.estimatedEnergy(action, numCameras) / EnergyModel.CLOUD_ENERGY;
        double normLoad    = EnergyModel.overloadScore(action);
        double normLatency = EnergyModel.latencyScore(action);
        double penalty = W_ENERGY * normEnergy + W_LOAD * normLoad + W_LATENCY * normLatency;
        double actionBonus = action == 0 ? 0.3 : action == 3 ? -0.5 : 0;
        return -penalty + actionBonus;
    }

    public void update(int action, double reward) {
        double maxNext = Q[greedy()];
        double alpha = ALPHA / (1 + 0.02 * visits[action]);
        Q[action] += alpha * (reward + GAMMA * maxNext - Q[action]);
        visits[action]++;
        totalVisits++;
        epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
    }

    public double[] getQValues() { return Q.clone(); }
    public double getEpsilon()   { return epsilon; }
}
