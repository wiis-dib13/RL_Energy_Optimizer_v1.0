package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.TimeKeeper;

import java.util.*;

/**
 * Online Reinforcement Learning for Module Placement
 * Runs multiple simulation episodes, learns from real energy measurements.
 */
public class OnlineRLPlacement {

    private static final int EPISODES = 50;               // number of learning episodes
    private static final double ALPHA = 0.1;              // learning rate
    private static final double GAMMA = 0.9;              // discount factor
    private static final double EPSILON_START = 1.0;
    private static final double EPSILON_MIN = 0.1;
    private static final double EPSILON_DECAY = 0.95;
    private static final int N_ACTIONS = 4;
    private static final String[] ACTION_NAMES = {"EDGE", "PROXY", "CAMERA", "CLOUD"};

    private double[] Q = new double[N_ACTIONS];
    private int[] counts = new int[N_ACTIONS];
    private double epsilon = EPSILON_START;
    private Random rand = new Random(42);

    public static void main(String[] args) {
        new OnlineRLPlacement().run();
    }

    private void run() {
        System.out.println("=== Online RL Placement Learning ===");
        System.out.println("Each episode runs a full simulation with real energy measurement.\n");

        for (int episode = 0; episode < EPISODES; episode++) {
            // 1. Choose action (epsilon-greedy)
            int action = selectAction();
            String placement = ACTION_NAMES[action];

            // 2. Run one simulation with this placement and measure real energy
            double totalEnergyJoules = runSimulation(placement);

            // 3. Compute reward (negative normalized energy, lower energy = higher reward)
            // Normalize roughly: max observed energy ~ 3.2e6 J (cloud), min ~ 1.6e5 J (edge)
            double normalizedEnergy = totalEnergyJoules / 3.5e6;
            double reward = -normalizedEnergy;   // between -0.9 and -0.05 approx

            // 4. Update Q-value
            updateQ(action, reward);

            // 5. Print progress
            System.out.printf("Episode %3d: %-6s → Energy = %8.0f J, Reward = %.3f, Q = %.4f%n",
                    episode + 1, placement, totalEnergyJoules, reward, Q[action]);
        }

        // Final policy
        int best = argmax(Q);
        System.out.println("\n=== Final policy ===");
        System.out.println("Best placement: " + ACTION_NAMES[best]);
        System.out.println("Q-values: " + Arrays.toString(Q));
    }

    /** Select action using epsilon-greedy */
    private int selectAction() {
        if (rand.nextDouble() < epsilon) {
            // Exploration: choose random action
            return rand.nextInt(N_ACTIONS);
        } else {
            // Exploitation: choose best action (argmax Q)
            return argmax(Q);
        }
    }

    /** Update Q(s,a) = Q + α (r + γ max_a' Q(s,a') - Q) */
    private void updateQ(int action, double reward) {
        int bestNext = argmax(Q);
        double maxNextQ = Q[bestNext];
        double oldQ = Q[action];
        Q[action] = oldQ + ALPHA * (reward + GAMMA * maxNextQ - oldQ);
        counts[action]++;
        // Decay epsilon
        epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
    }

    private int argmax(double[] array) {
        int best = 0;
        for (int i = 1; i < array.length; i++)
            if (array[i] > array[best]) best = i;
        return best;
    }

    /**
     * Runs a full CloudSim simulation with a given module placement choice.
     * Returns the total energy consumed (Joules) measured from all fog devices.
     */
    private double runSimulation(String placementChoice) {
        try {
            // Reinitialize CloudSim (required for fresh runs)
            CloudSim.init(1, Calendar.getInstance(), false);

            String appId = "dcns";
            FogBroker broker = new FogBroker("broker");
            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            // Create fog devices (cloud, proxy, routers, cameras)
            List<FogDevice> fogDevices = new ArrayList<>();
            List<Sensor> sensors = new ArrayList<>();
            List<Actuator> actuators = new ArrayList<>();
            createFogDevices(fogDevices, sensors, actuators, broker.getId(), appId);

            // Build module mapping (motion_detector always on cameras)
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("m")) {
                    moduleMapping.addModuleToDevice("motion_detector", device.getName());
                }
            }
            moduleMapping.addModuleToDevice("user_interface", "cloud");

            // Place object_detector and object_tracker according to learning choice
            switch (placementChoice) {
                case "EDGE":
                    for (FogDevice d : fogDevices)
                        if (d.getName().startsWith("d-")) {    // routers (fog-*)
                            moduleMapping.addModuleToDevice("object_detector", d.getName());
                            moduleMapping.addModuleToDevice("object_tracker", d.getName());
                        }
                    break;
                case "PROXY":
                    moduleMapping.addModuleToDevice("object_detector", "proxy-server");
                    moduleMapping.addModuleToDevice("object_tracker", "proxy-server");
                    break;
                case "CAMERA":
                    for (FogDevice d : fogDevices)
                        if (d.getName().startsWith("m-")) {
                            moduleMapping.addModuleToDevice("object_detector", d.getName());
                            moduleMapping.addModuleToDevice("object_tracker", d.getName());
                        }
                    break;
                case "CLOUD":
                    moduleMapping.addModuleToDevice("object_detector", "cloud");
                    moduleMapping.addModuleToDevice("object_tracker", "cloud");
                    break;
            }

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(application,
                    new ModulePlacementMapping(fogDevices, application, moduleMapping)); // reuses your mapping class but now with correct placement

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // Measure total energy (sum of all devices)
            double totalEnergy = 0;
            for (FogDevice fd : fogDevices) {
                totalEnergy += fd.getTotalEnergyConsumption();  // iFogSim provides this
            }
            return totalEnergy;

        } catch (Exception e) {
            e.printStackTrace();
            return 1e9; // penalty value on failure
        }
    }

    private static void createFogDevices(List<FogDevice> fogDevices, List<Sensor> sensors,
                                         List<Actuator> actuators, int userId, String appId) {
        // Exactly the same as DCNSFog.createFogDevices but using passed lists
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 107.339, 83.4333);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);

        for (int i = 0; i < 4; i++) {
            addArea(i+"", fogDevices, sensors, actuators, userId, appId, proxy.getId());
        }
    }

    private static FogDevice addArea(String id, List<FogDevice> fogDevices, List<Sensor> sensors,
                                     List<Actuator> actuators, int userId, String appId, int parentId) {
        FogDevice router = createFogDevice("d-"+id, 2800, 4000, 10000, 10000, 2, 0.0, 107.339, 83.4333);
        fogDevices.add(router);
        router.setUplinkLatency(2);
        for (int i=0; i<4; i++) {
            String mobileId = id+"-"+i;
            FogDevice camera = addCamera(mobileId, fogDevices, sensors, actuators, userId, appId, router.getId());
            camera.setUplinkLatency(2);
            fogDevices.add(camera);
        }
        router.setParentId(parentId);
        return router;
    }

    private static FogDevice addCamera(String id, List<FogDevice> fogDevices, List<Sensor> sensors,
                                       List<Actuator> actuators, int userId, String appId, int parentId) {
        FogDevice camera = createFogDevice("m-"+id, 500, 1000, 10000, 10000, 3, 0, 87.53, 82.44);
        camera.setParentId(parentId);
        Sensor sensor = new Sensor("s-"+id, "CAMERA", userId, appId, new org.fog.utils.distribution.DeterministicDistribution(5));
        sensors.add(sensor);
        Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL");
        actuators.add(ptz);
        sensor.setGatewayDeviceId(camera.getId());
        sensor.setLatency(1.0);
        ptz.setGatewayDeviceId(camera.getId());
        ptz.setLatency(1.0);
        return camera;
    }

    private static Application createApplication(String appId, int userId) {
        // Same as DCNSFog.createApplication
        Application app = Application.createApplication(appId, userId);
        app.addAppModule("object_detector", 10);
        app.addAppModule("motion_detector", 10);
        app.addAppModule("object_tracker", 10);
        app.addAppModule("user_interface", 10);
        app.addAppEdge("CAMERA", "motion_detector", 1000, 20000, "CAMERA", org.fog.entities.Tuple.UP, AppEdge.SENSOR);
        app.addAppEdge("motion_detector", "object_detector", 2000, 2000, "MOTION_VIDEO_STREAM", org.fog.entities.Tuple.UP, AppEdge.MODULE);
        app.addAppEdge("object_detector", "user_interface", 500, 2000, "DETECTED_OBJECT", org.fog.entities.Tuple.UP, AppEdge.MODULE);
        app.addAppEdge("object_detector", "object_tracker", 1000, 100, "OBJECT_LOCATION", org.fog.entities.Tuple.UP, AppEdge.MODULE);
        app.addAppEdge("object_tracker", "PTZ_CONTROL", 100, 28, 100, "PTZ_PARAMS", org.fog.entities.Tuple.DOWN, AppEdge.ACTUATOR);
        app.addTupleMapping("motion_detector", "CAMERA", "MOTION_VIDEO_STREAM", new org.fog.application.selectivity.FractionalSelectivity(1.0));
        app.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "OBJECT_LOCATION", new org.fog.application.selectivity.FractionalSelectivity(1.0));
        app.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "DETECTED_OBJECT", new org.fog.application.selectivity.FractionalSelectivity(0.05));
        return app;
    }

    private static FogDevice createFogDevice(String name, long mips, int ram, long upBw, long downBw,
                                             int level, double ratePerMips, double busyPower, double idlePower) {
        // Use the exact same implementation as DCNSFog.createFogDevice
        // (copied from your original code)
        List<org.cloudbus.cloudsim.Pe> peList = new ArrayList<>();
        peList.add(new org.cloudbus.cloudsim.Pe(0, new org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking(mips)));
        int hostId = org.fog.utils.FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;
        org.cloudbus.cloudsim.power.PowerHost host = new org.cloudbus.cloudsim.power.PowerHost(
                hostId,
                new org.cloudbus.cloudsim.provisioners.RamProvisionerSimple(ram),
                new org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking(bw),
                storage,
                peList,
                new org.fog.scheduler.StreamOperatorScheduler(peList),
                new org.fog.utils.FogLinearPowerModel(busyPower, idlePower));
        List<org.cloudbus.cloudsim.Host> hostList = new ArrayList<>();
        hostList.add(host);
        org.fog.entities.FogDeviceCharacteristics characteristics = new org.fog.entities.FogDeviceCharacteristics(
                "x86", "Linux", "Xen", host, 10.0, 3.0, 0.05, 0.001, 0.0);
        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(name, characteristics, new org.fog.policy.AppModuleAllocationPolicy(hostList),
                    new LinkedList<>(), 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) { e.printStackTrace(); }
        fogdevice.setLevel(level);
        return fogdevice;
    }
}