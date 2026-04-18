package com.fadi.rloptimizer.api;

public record SimulationStep(
        int      episode,
        int      action,
        String   actionName,
        double   reward,
        double[] qValues,
        int      bestAction,
        String   bestActionName,
        double   bestEnergyKJ,
        double   epsilon
) {}
