package com.fadi.rloptimizer.api;

import com.fadi.rloptimizer.rl.EnergyModel;
import com.fadi.rloptimizer.rl.RLAgent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final ExecutorService pool = Executors.newCachedThreadPool();

    @GetMapping(value = "/simulate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter simulate(
            @RequestParam(defaultValue = "16")  int cameras,
            @RequestParam(defaultValue = "200") int episodes,
            @RequestParam(defaultValue = "50")  long delayMs) {

        SseEmitter emitter = new SseEmitter(120_000L);

        pool.submit(() -> {
            try {
                EnergyModel model = new EnergyModel(cameras);
                RLAgent     agent = new RLAgent(model);

                for (int ep = 1; ep <= episodes; ep++) {
                    int action    = agent.selectAction();
                    double reward = agent.rewardFor(action);
                    agent.update(action, reward);

                    int best = agent.greedy();
                    double bestEnergyKJ = model.estimatedEnergy(best) / 1000.0;

                    SimulationStep step = new SimulationStep(
                            ep,
                            action,
                            EnergyModel.ACTION_NAMES[action],
                            reward,
                            agent.getQValues(),
                            best,
                            EnergyModel.ACTION_NAMES[best],
                            bestEnergyKJ,
                            agent.getEpsilon()
                    );

                    emitter.send(SseEmitter.event().name("step").data(step));
                    Thread.sleep(delayMs);
                }

                emitter.send(SseEmitter.event().name("done").data("ok"));
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
