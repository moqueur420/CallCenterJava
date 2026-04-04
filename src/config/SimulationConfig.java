package config;

import java.util.List;

public class SimulationConfig {
    public double mu = 1.0 / 240.0;
    public double nu = 1.0 / 240.0;
    public double intervalLength = 1200.0;
    public int totalIntervals = 36;
    public double shiftLength = 14400.0;
    
    public List<Double> lambdaByInterval;
    public List<Integer> operatorsSchedule;
    
    public long seed = System.currentTimeMillis();
    public boolean useRandomServiceTime = true;
}