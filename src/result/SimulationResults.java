package result;

import java.util.List;
import java.util.Map;
import model.CallRequest;
import model.CallOperator;

public class SimulationResults {
    public List<CallRequest> allRequests;
    public List<CallOperator> allOperators;
    public Map<Integer, Double> kClientByInterval;
    public Map<Integer, Double> kOperByInterval;
    public Map<Integer, Double> targetFunctionByInterval;
    public int totalArrived;
    public int totalServed;
    public int totalAbandoned;
    public int totalUnresolved;
    public double averageQueueTime;
    public double averageServiceTime;
    public double serviceProbability;
    public double abandonmentProbability;
}
