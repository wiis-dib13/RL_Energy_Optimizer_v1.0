package com.fadi.rloptimizer.rl;

import java.util.Random;

/**
 * Agent Q-Learning (ε-greedy + UCB).
 * Tous les hyperparamètres (alpha, gamma, ε, UCB-C, poids de récompense)
 * sont injectés depuis ModelConfig (chargé du JSON), aucune valeur en dur.
 */
public class RLAgent {

    private final EnergyModel               model;
    private final ModelConfig.RLConfig      rl;
    private final ModelConfig.RewardWeights w;
    private final double[] Q;
    private final int[]    visits;
    private int    totalVisits = 0;
    private double epsilon;
    private final Random rand = new Random(42);

    public RLAgent(EnergyModel model) {
        this.model   = model;
        this.rl      = model.getConfig().rl();
        this.w       = model.getConfig().weights();
        this.Q       = new double[model.nActions()];
        this.visits  = new int[model.nActions()];
        this.epsilon = rl.epsilonStart();
        for (int a = 0; a < model.nActions(); a++) Q[a] = rewardFor(a);
    }

    public EnergyModel getModel() { return model; }

    public int greedy() {
        int best = 0;
        for (int i = 1; i < Q.length; i++) if (Q[i] > Q[best]) best = i;
        return best;
    }

    public int ucbSelect() {
        int best = 0;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int a = 0; a < Q.length; a++) {
            double bonus = visits[a] == 0
                    ? Double.MAX_VALUE
                    : rl.ucbC() * Math.sqrt(Math.log(totalVisits + 1) / visits[a]);
            double v = Q[a] + bonus;
            if (v > bestValue) { bestValue = v; best = a; }
        }
        return best;
    }

    public int selectAction() {
        return rand.nextDouble() < epsilon ? ucbSelect() : greedy();
    }

    public double rewardFor(int action) {
        double normEnergy  = model.estimatedEnergy(action) / model.maxEnergy();
        double normLoad    = model.overloadScore(action);
        double normLatency = model.latencyScore(action);
        double penalty = w.energy() * normEnergy + w.load() * normLoad + w.latency() * normLatency;
        return -penalty + model.actionBonus(action);
    }

    public void update(int action, double reward) {
        double maxNext = Q[greedy()];
        double alpha = rl.alpha() / (1 + 0.02 * visits[action]);
        Q[action] += alpha * (reward + rl.gamma() * maxNext - Q[action]);
        visits[action]++;
        totalVisits++;
        epsilon = Math.max(rl.epsilonMin(), epsilon * rl.epsilonDecay());
    }

    public double[] getQValues() { return Q.clone(); }
    public double   getEpsilon() { return epsilon; }
}
