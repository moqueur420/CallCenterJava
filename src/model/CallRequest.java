package model;

public class CallRequest {
    private final int id;
    private final double appearanceTime;
    private final double patienceTime;
    private final int intervalIndex;
    
    private double queueTime = 0.0;
    private RequestStatus status = RequestStatus.WAITING;
    private Integer operatorId = null;
    private double serviceStartTime = 0.0;
    private double leavingTime = 0.0;

    public CallRequest(int id, double appearanceTime, double patienceTime, int intervalIndex) {
        this.id = id;
        this.appearanceTime = appearanceTime;
        this.patienceTime = patienceTime;
        this.intervalIndex = intervalIndex;
    }

    public void startService(double currentTime, int operatorId) {
        this.status = RequestStatus.IN_SERVICE;
        this.operatorId = operatorId;
        this.queueTime = currentTime - this.appearanceTime;
        this.serviceStartTime = currentTime;
    }

    public void completeService(double currentTime) {
        this.status = RequestStatus.SERVED;
        this.leavingTime = currentTime;
    }

    public void abandon(double currentTime) {
        this.status = RequestStatus.ABANDONED;
        this.queueTime = this.patienceTime;
        this.leavingTime = currentTime;
    }

    public int getId() { return id; }
    public double getAppearanceTime() { return appearanceTime; }
    public double getPatienceTime() { return patienceTime; }
    public double getQueueTime() { return queueTime; }
    public RequestStatus getStatus() { return status; }
    public Integer getOperatorId() { return operatorId; }
    public double getServiceStartTime() { return serviceStartTime; }
    public double getLeavingTime() { return leavingTime; }
    public int getIntervalIndex() { return intervalIndex; }
}