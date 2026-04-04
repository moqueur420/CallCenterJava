package engine;

import model.CallRequest;

public class SimEvent implements Comparable<SimEvent> {
    public enum EventType { ARRIVAL, DEPARTURE }

    public double time;
    public EventType type;
    public CallRequest request;
    public int operatorId;

    public SimEvent(double time, EventType type, CallRequest request, int operatorId) {
        this.time = time;
        this.type = type;
        this.request = request;
        this.operatorId = operatorId;
    }

    @Override
    public int compareTo(SimEvent other) {
        return Double.compare(this.time, other.time);
    }
}