package com.fadi.rloptimizer.rl;

import java.util.List;

/**
 * Modèle énergétique des stratégies de placement.
 * Toutes les valeurs (idle, perCamera, overload, latency) viennent du
 * fichier JSON chargé par ModelConfig — pas de constantes en dur.
 *
 * Formule : energy(action) = idle_joules + per_camera_joules * N
 */
public class EnergyModel {

    private final ModelConfig config;
    private final int numCameras;

    public EnergyModel(ModelConfig config, int numCameras) {
        if (numCameras < 1) throw new IllegalArgumentException("numCameras >= 1");
        this.config     = config;
        this.numCameras = numCameras;
    }

    public ModelConfig getConfig()      { return config; }
    public int         getNumCameras()  { return numCameras; }
    public int         nActions()       { return config.nActions(); }
    public String      actionName(int i){ return config.profile(i).name(); }
    public List<String> actionNames() {
        return config.profiles().stream().map(ModelConfig.ActionProfile::name).toList();
    }

    /**
     * Énergie attendue (Joules) pour une action — formule chargée depuis le JSON.
     * energy(action) = idle + perCamera * N * (1 + saturationFactor * N)
     * Le terme saturationFactor modélise les limites de capacité par architecture.
     */
    public double estimatedEnergy(int action) {
        ModelConfig.ActionProfile p = config.profile(action);
        double load = numCameras * (1.0 + p.saturationFactor() * numCameras);
        return p.idleJoules() + p.perCameraJoules() * load;
    }

    public double maxEnergy() {
        double max = 0;
        for (int a = 0; a < nActions(); a++) max = Math.max(max, estimatedEnergy(a));
        return max;
    }

    public double overloadScore(int action) {
        ModelConfig.ActionProfile p = config.profile(action);
        return Math.min(1.0, p.overloadBase() + p.overloadPerCam() * numCameras);
    }

    public double latencyScore(int action) {
        return config.profile(action).latency();
    }

    public double actionBonus(int action) {
        return config.profile(action).bonus();
    }
}
