package integration;

import config.SimulationConfig;
import engine.CallCenterEngine;
import result.SimulationResults;
import result.SimulationSnapshot;

public class AnyLogicAdapter {
    private CallCenterEngine engine;

    public void initSimulation(SimulationConfig config) {
        engine = new CallCenterEngine(config);
        engine.initialize();
    }

    public boolean stepInterval() {
        return engine != null && engine.runNextInterval();
    }

    public SimulationSnapshot getCurrentSnapshot() {
        return engine != null ? engine.getSnapshot() : new SimulationSnapshot();
    }

    public SimulationResults getFinalResults() {
        return engine != null ? engine.generateResults() : null;
    }

    public SimulationResults runFastFullSimulation(SimulationConfig config) {
        initSimulation(config);
        while (stepInterval()) {}
        return getFinalResults();
    }
}