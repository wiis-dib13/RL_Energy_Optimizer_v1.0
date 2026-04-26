package com.fadi.rloptimizer.rl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Service Spring qui charge la configuration du modèle depuis
 * src/main/resources/module_placement_mapping.json au démarrage.
 *
 * Toutes les valeurs énergétiques et les hyperparamètres RL sont
 * lus depuis ce fichier — aucune constante n'est figée dans le code.
 */
@Service
public class ModelConfig {

    public record ActionProfile(
            String name,
            double idleJoules,
            double perCameraJoules,
            double saturationFactor,
            double overloadBase,
            double overloadPerCam,
            double latency,
            double bonus
    ) {}

    public record RLConfig(
            int    episodes,
            double alpha,
            double gamma,
            double epsilonStart,
            double epsilonDecay,
            double epsilonMin,
            double ucbC
    ) {}

    public record RewardWeights(
            double energy,
            double load,
            double latency
    ) {}

    private record EnergyModelSection(
            String source,
            String referenceFile,
            int baselineNumCameras,
            List<ActionProfile> profiles
    ) {}

    private record Root(
            @JsonProperty("RL_Config") RLConfig rlConfig,
            RewardWeights rewardWeights,
            @JsonProperty("energy_model") EnergyModelSection energyModel
    ) {}

    private final RLConfig          rlConfig;
    private final RewardWeights     rewardWeights;
    private final List<ActionProfile> profiles;
    private final String            source;

    public ModelConfig() {
        try (InputStream is = new ClassPathResource("module_placement_mapping.json").getInputStream()) {
            ObjectMapper mapper = new ObjectMapper()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            Root root = mapper.readValue(is, Root.class);
            this.rlConfig      = root.rlConfig();
            this.rewardWeights = root.rewardWeights();
            this.profiles      = root.energyModel().profiles();
            this.source        = root.energyModel().source();

            System.out.println("[ModelConfig] Loaded model from JSON — source: " + source);
            for (ActionProfile p : profiles) {
                System.out.printf("[ModelConfig]   %-7s idle=%.0f J  perCam=%.0f J%n",
                        p.name(), p.idleJoules(), p.perCameraJoules());
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot load module_placement_mapping.json", e);
        }
    }

    public RLConfig         rl()           { return rlConfig; }
    public RewardWeights    weights()      { return rewardWeights; }
    public List<ActionProfile> profiles()  { return profiles; }
    public ActionProfile    profile(int i) { return profiles.get(i); }
    public int              nActions()     { return profiles.size(); }
    public String           source()       { return source; }
}
