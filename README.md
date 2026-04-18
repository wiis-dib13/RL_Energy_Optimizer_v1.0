# RL_Energy_Optimizer_v1.0

Q-Learning Energy Optimizer pour Fog Computing — version Spring Boot.

## Technologies

- **Java 17** (JDK)
- **Spring Boot 3.3** (backend web + REST)
- **Server-Sent Events** (streaming des épisodes RL en temps réel)
- **Chart.js** (visualisation style JFreeChart)
- **Maven** (build)

## Architecture

```
src/main/java/com/fadi/rloptimizer/
├── RlOptimizerApplication.java      # Spring Boot main
├── rl/
│   ├── EnergyModel.java             # Valeurs d'énergie réelles (iFogSim)
│   └── RLAgent.java                 # Agent Q-Learning (ε-greedy + UCB)
└── api/
    ├── SimulationController.java    # REST /api/simulate (SSE)
    └── SimulationStep.java          # DTO

src/main/resources/static/index.html # UI (Swing-look)
```

Les fichiers à la racine (`DCNSFog_4fogs`, `moduleplacementmapping.java`, etc.)
sont la version iFogSim/CloudSim d'origine qui a produit les mesures d'énergie
réelles utilisées par le backend Spring Boot.

## Lancer l'application

Maven est embarqué dans le dossier `apache-maven-3.9.15/`, un wrapper `mvnw.cmd`
est fourni.

```cmd
mvnw spring-boot:run
```

Puis ouvre **http://localhost:8080** dans un navigateur.

## Build d'un JAR autonome

```cmd
mvnw clean package
java -jar target/rl-sim-1.0.jar
```
