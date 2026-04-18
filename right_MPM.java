package org.fog.placement;

import java.util.*;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;

public class ModulePlacementMapping extends ModulePlacement {

    private static final int EPISODES = 200;
    private static final double ALPHA = 0.15;
    private static final double GAMMA = 0.90;
    private static final double EPSILON_DECAY = 0.99;
    private static final double EPSILON_MIN = 0.05;

    private static final int N_ACTIONS = 4;
    private static final String[] ACTION_NAMES = {"EDGE", "PROXY", "CAMERA", "CLOUD"};

    private final ModuleMapping moduleMapping;

    // REAL history (offline learning)
    private static final Map<Integer, List<Double>> energyHistory = new HashMap<>();
    private static final Map<Integer, List<Double>> costHistory   = new HashMap<>();

    public static int lastChosenAction = 0; // IMPORTANT

    static {
        for (int i = 0; i < N_ACTIONS; i++) {
            energyHistory.put(i, new ArrayList<>());
            costHistory.put(i, new ArrayList<>());
        }
    }

    public ModulePlacementMapping(List<FogDevice> fogDevices,
                                  Application application,
                                  ModuleMapping moduleMapping) {
        this.setFogDevices(fogDevices);
        this.setApplication(application);
        this.moduleMapping = moduleMapping;

        this.setModuleToDeviceMap(new HashMap<>());
        this.setDeviceToModuleMap(new HashMap<>());
        this.setModuleInstanceCountMap(new HashMap<>());

        for (FogDevice d : fogDevices)
            getModuleInstanceCountMap().put(d.getId(), new HashMap<>());

        mapModules();
    }

    @Override
    protected void mapModules() {

        RLAgent agent = new RLAgent();

        for (int ep = 0; ep < EPISODES; ep++) {
            int action = agent.selectAction();
            double reward = agent.rewardFor(action);
            agent.update(action, reward);
        }

        int chosen = agent.greedy();
        lastChosenAction = chosen;

        System.out.println("\n[RL] CHOSEN: " + ACTION_NAMES[chosen]);

        // FIXED mappings (same as your scenario)
        for (Map.Entry<String, List<String>> entry : moduleMapping.getModuleMapping().entrySet()) {
            FogDevice device = getDeviceByName(entry.getKey());
            if (device == null) continue;
            for (String moduleName : entry.getValue()) {
                AppModule module = getApplication().getModuleByName(moduleName);
                if (module != null) createModuleInstanceOnDevice(module, device);
            }
        }

        deployModules(chosen);
    }

    private void deployModules(int action) {
        switch (action) {

            case 0: // EDGE
                for (FogDevice d : getFogDevices())
                    if (d.getName().startsWith("fog-")) {
                        place("object_detector", d.getName());
                        place("object_tracker",  d.getName());
                    }
                break;

            case 1: // PROXY
                place("object_detector", "proxy-server");
                place("object_tracker",  "proxy-server");
                break;

            case 2: // CAMERA
                for (FogDevice d : getFogDevices())
                    if (d.getName().startsWith("m-")) {
                        place("object_detector", d.getName());
                        place("object_tracker",  d.getName());
                    }
                break;

            case 3: // CLOUD
                place("object_detector", "cloud");
                place("object_tracker",  "cloud");
                break;
        }
    }

    private void place(String moduleName, String deviceName) {
        AppModule module = getApplication().getModuleByName(moduleName);
        FogDevice device = getDeviceByName(deviceName);
        if (module != null && device != null)
            createModuleInstanceOnDevice(module, device);
    }

    // ===== RL AGENT =====
    private class RLAgent {

        double[] Q = new double[N_ACTIONS];
        int[] visits = new int[N_ACTIONS];
        double epsilon = 1.0;
        Random rand = new Random();

        int selectAction() {
            if (rand.nextDouble() < epsilon)
                return rand.nextInt(N_ACTIONS);
            return greedy();
        }

        int greedy() {
            int best = 0;
            for (int i = 1; i < N_ACTIONS; i++)
                if (Q[i] > Q[best]) best = i;
            return best;
        }

        double rewardFor(int action) {

            List<Double> h = energyHistory.get(action);

            double energy;

            if (h.isEmpty()) {
                // fallback (first run only)
                energy = (action == 3) ? 3000000 : 200000;
            } else {
                energy = h.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            }

            return -energy;
        }

        void update(int action, double reward) {
            double alpha = ALPHA / (1 + visits[action]);
            Q[action] += alpha * (reward - Q[action]);

            visits[action]++;
            epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
        }
    }

    // ===== STORE REAL RESULTS =====
    public static void recordSimulationResult(int action, double energy, double cost) {
        energyHistory.get(action).add(energy);
        costHistory.get(action).add(cost);

        System.out.println("[RL] Stored REAL result → " +
            ACTION_NAMES[action] + " energy=" + energy);
    }

    public ModuleMapping getModuleMapping() {
        return moduleMapping;
    }
}