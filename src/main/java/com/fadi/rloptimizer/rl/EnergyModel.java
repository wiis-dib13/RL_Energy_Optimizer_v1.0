package com.fadi.rloptimizer.rl;

/**
 * Modèle énergétique des 4 stratégies de placement.
 * Classe orientée-objet : instanciée pour un nombre de caméras donné,
 * chaque instance calcule l'énergie attendue de chaque action en tenant
 * compte du workload (nombre de caméras).
 *
 * Formule : energy(action) = overhead_idle + coût_par_camera * N
 *
 * Les constantes sont calibrées sur les mesures iFogSim à N = 16
 * (4 zones × 4 caméras), disponibles dans "affichage before and after.txt".
 */
public class EnergyModel {

    public static final String[] ACTION_NAMES = {"EDGE", "PROXY", "CAMERA", "CLOUD"};
    public static final int N_ACTIONS = 4;

    // Référence : N = 16 caméras → ces valeurs retombent sur les mesures
    // réelles du fichier affichage before and after.txt.
    private static final double EDGE_IDLE    = 100_000.0;
    private static final double EDGE_PER_CAM =   4_000.0;

    private static final double PROXY_IDLE    = 120_000.0;
    private static final double PROXY_PER_CAM =   5_000.0;

    private static final double CAMERA_IDLE    = 100_000.0;
    private static final double CAMERA_PER_CAM =  36_000.0;

    private static final double CLOUD_IDLE    = 200_000.0;
    private static final double CLOUD_PER_CAM = 190_000.0;

    private final int numCameras;

    public EnergyModel(int numCameras) {
        if (numCameras < 1) throw new IllegalArgumentException("numCameras >= 1");
        this.numCameras = numCameras;
    }

    public int getNumCameras() { return numCameras; }

    /** Énergie attendue (Joules) pour une action donnée. Scale avec numCameras. */
    public double estimatedEnergy(int action) {
        switch (action) {
            case 0: return EDGE_IDLE   + EDGE_PER_CAM   * numCameras;  // EDGE
            case 1: return PROXY_IDLE  + PROXY_PER_CAM  * numCameras;  // PROXY
            case 2: return CAMERA_IDLE + CAMERA_PER_CAM * numCameras;  // CAMERA
            case 3: return CLOUD_IDLE  + CLOUD_PER_CAM  * numCameras;  // CLOUD
            default: throw new IllegalArgumentException("action: " + action);
        }
    }

    /** Borne supérieure d'énergie (pour normaliser les rewards). */
    public double maxEnergy() {
        double max = 0;
        for (int a = 0; a < N_ACTIONS; a++) max = Math.max(max, estimatedEnergy(a));
        return max;
    }

    /** Pénalité de surcharge par action. Dépend aussi de N. */
    public double overloadScore(int action) {
        double camLoad = Math.min(1.0, numCameras / 20.0);
        double[] v = {0.10, 0.40, 0.20 + 0.60 * camLoad, 0.05};
        return v[action];
    }

    /** Score de latence par action. */
    public double latencyScore(int action) {
        double[] v = {0.15, 0.35, 0.05, 0.70};
        return v[action];
    }
}
