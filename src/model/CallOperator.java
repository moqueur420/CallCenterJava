package model;

import java.util.HashMap;
import java.util.Map;

public class CallOperator {
    private final int id;
    private final double shiftStartTime;
    private final double shiftEndTime;
    private boolean busy = false;
    private Integer currentRequestId = null;
    private double releaseTime = 0.0;
    private final Map<Integer, Double> busyTimeByInterval = new HashMap<>();

    public CallOperator(int id, double shiftStartTime, double shiftLength) {
        this.id = id;
        this.shiftStartTime = shiftStartTime;
        this.shiftEndTime = shiftStartTime + shiftLength;
        this.releaseTime = shiftStartTime;
    }

    public double getShiftStartTime() { 
        return shiftStartTime; }

    public boolean canTakeRequest(double currentTime, double estimatedServiceTime) {
        return !busy && (currentTime + estimatedServiceTime <= shiftEndTime);
    }

    public void assignRequest(int requestId, double currentTime, double serviceTime) {
        this.busy = true;
        this.currentRequestId = requestId;
        this.releaseTime = currentTime + serviceTime;
    }

    public void freeUp() {
        this.busy = false;
        this.currentRequestId = null;
    }

    public void addBusyTime(int interval, double time) {
        busyTimeByInterval.put(interval, busyTimeByInterval.getOrDefault(interval, 0.0) + time);
    }

    public int getId() { return id; }
    public double getShiftEndTime() { return shiftEndTime; }
    public boolean isBusy() { return busy; }
    public Integer getCurrentRequestId() { return currentRequestId; }
    public double getReleaseTime() { return releaseTime; }
    public Map<Integer, Double> getBusyTimeByInterval() { return busyTimeByInterval; }
}