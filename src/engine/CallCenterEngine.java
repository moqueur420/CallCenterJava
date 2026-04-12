package engine;

import config.SimulationConfig;
import model.CallOperator;
import model.CallRequest;
import model.RequestStatus;
import result.SimulationResults;
import result.SimulationSnapshot;

import java.util.*;
import java.util.stream.Collectors;

public class CallCenterEngine {
    private final SimulationConfig config;
    private final Random random;
    private double currentTime = 0.0;
    private int currentIntervalIndex = 0;
    private int lastRequestId = 0;
    private int lastOperatorId = 0;

    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();
    private final LinkedList<CallRequest> requestQueue = new LinkedList<>();
    private final List<CallOperator> activeOperators = new ArrayList<>();
    private final List<CallOperator> allOperators = new ArrayList<>();
    private final List<CallRequest> allRequests = new ArrayList<>();

    public CallCenterEngine(SimulationConfig config) {
        this.config = config;
        this.random = new Random(config.seed);
    }

    public void initialize() {
        currentTime = 0.0;
        currentIntervalIndex = 0;
        addOperatorsForInterval(0);
        generateArrivalsForInterval(0);
    }

    public boolean runNextInterval() {
        if (currentIntervalIndex >= config.totalIntervals) return false;
        double intervalEndTime = (currentIntervalIndex + 1) * config.intervalLength;

        while (!eventQueue.isEmpty() && eventQueue.peek().time <= intervalEndTime) {
            SimEvent event = eventQueue.poll();
            currentTime = event.time;
            if (event.type == SimEvent.EventType.ARRIVAL) {
                processArrival(event.request);
            } else if (event.type == SimEvent.EventType.DEPARTURE) {
                processDeparture(event.operatorId);
            }
        }

        currentTime = intervalEndTime;
        currentIntervalIndex++;

        if (currentIntervalIndex < config.totalIntervals) {
            addOperatorsForInterval(currentIntervalIndex);
            generateArrivalsForInterval(currentIntervalIndex);
        } else {
            finalizeRemainingEvents();
        }
        return true;
    }

    private void generateArrivalsForInterval(int intervalIndex) {
        double lambda = config.lambdaByInterval.get(intervalIndex);
        double intervalStart = intervalIndex * config.intervalLength;
        double intervalEnd = intervalStart + config.intervalLength;
        double time = intervalStart;

        while (true) {
            double u = random.nextDouble();
            time += -Math.log(1 - u) / lambda;
            if (time > intervalEnd) break;

            lastRequestId++;
            double patience = config.useRandomServiceTime ? (-Math.log(1 - random.nextDouble()) / config.nu) : (1.0 / config.nu);
            CallRequest req = new CallRequest(lastRequestId, time, patience, intervalIndex);
            allRequests.add(req);
            eventQueue.add(new SimEvent(time, SimEvent.EventType.ARRIVAL, req, -1));
        }
    }

    private void processArrival(CallRequest req) {
        lazyQueueCleanup();
        CallOperator bestOp = findOptimalOperator();
        if (bestOp != null) {
            assignRequestToOperator(req, bestOp);
        } else {
            requestQueue.add(req);
        }
    }

    private void processDeparture(int operatorId) {
        CallOperator op = activeOperators.stream().filter(o -> o.getId() == operatorId).findFirst().orElse(null);
        if (op == null) return;

        CallRequest finishedReq = allRequests.get(op.getCurrentRequestId() - 1);
        finishedReq.completeService(currentTime);
        op.freeUp();

        if (currentTime >= op.getShiftEndTime()) {
            activeOperators.remove(op);
            return;
        }

        lazyQueueCleanup();
        if (!requestQueue.isEmpty()) {
            assignRequestToOperator(requestQueue.poll(), op);
        }
    }

    private void assignRequestToOperator(CallRequest req, CallOperator op) {
        double serviceTime = config.useRandomServiceTime ? (-Math.log(1 - random.nextDouble()) / config.mu) : (1.0 / config.mu);
        
        if (!op.canTakeRequest(currentTime, serviceTime)) {
            activeOperators.remove(op);
            requestQueue.addFirst(req); 
            processDeparture(op.getId());
            return;
        }

        req.startService(currentTime, op.getId());
        op.assignRequest(req.getId(), currentTime, serviceTime);
        distributeOperatorBusyTime(op, currentTime, currentTime + serviceTime);
        eventQueue.add(new SimEvent(currentTime + serviceTime, SimEvent.EventType.DEPARTURE, null, op.getId()));
    }

    private void lazyQueueCleanup() {
        Iterator<CallRequest> iterator = requestQueue.iterator();
        while (iterator.hasNext()) {
            CallRequest req = iterator.next();
            if (currentTime >= req.getAppearanceTime() + req.getPatienceTime()) {
                req.abandon(req.getAppearanceTime() + req.getPatienceTime());
                iterator.remove();
            }
        }
    }

    private CallOperator findOptimalOperator() {
        return activeOperators.stream()
                .filter(o -> !o.isBusy())
                .min(Comparator.comparingDouble(CallOperator::getReleaseTime))
                .orElse(null);
    }

    private void addOperatorsForInterval(int intervalIndex) {
        int opsToAdd = config.operatorsSchedule.get(intervalIndex);
        for (int i = 0; i < opsToAdd; i++) {
            lastOperatorId++;
            CallOperator newOp = new CallOperator(lastOperatorId, currentTime, config.shiftLength);
            activeOperators.add(newOp);
            allOperators.add(newOp);
        }
    }

    private void distributeOperatorBusyTime(CallOperator op, double start, double end) {
        double currentStart = start;
        while (currentStart < end) {
            int interval = (int) (currentStart / config.intervalLength);
            double intervalEnd = Math.min(end, (interval + 1) * config.intervalLength);
            op.addBusyTime(interval, intervalEnd - currentStart);
            currentStart = intervalEnd;
        }
    }

    private void finalizeRemainingEvents() {
        while (!eventQueue.isEmpty()) {
            SimEvent event = eventQueue.poll();
            currentTime = event.time;
            if (event.type == SimEvent.EventType.DEPARTURE) processDeparture(event.operatorId);
        }
        lazyQueueCleanup();
    }

    public SimulationSnapshot getSnapshot() {
        SimulationSnapshot snap = new SimulationSnapshot();
        snap.currentTime = currentTime;
        snap.currentInterval = currentIntervalIndex;
        snap.queueLength = requestQueue.size();
        snap.activeOperatorsCount = activeOperators.size();
        snap.busyOperatorsCount = (int) activeOperators.stream().filter(CallOperator::isBusy).count();
        snap.totalProcessed = (int) allRequests.stream().filter(r -> r.getStatus() == RequestStatus.SERVED).count();
        snap.totalAbandoned = (int) allRequests.stream().filter(r -> r.getStatus() == RequestStatus.ABANDONED).count();
        return snap;
    }

    public SimulationResults generateResults() {
        SimulationResults res = new SimulationResults();
        res.allRequests = allRequests;
        res.allOperators = allOperators;
        List<CallRequest> servedRequests = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.SERVED)
                .collect(Collectors.toList());
        List<CallRequest> abandonedRequests = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.ABANDONED)
                .collect(Collectors.toList());
        List<CallRequest> resolvedRequests = new ArrayList<>(servedRequests);
        resolvedRequests.addAll(abandonedRequests);

        res.totalArrived = allRequests.size();
        res.totalServed = servedRequests.size();
        res.totalAbandoned = abandonedRequests.size();
        res.totalUnresolved = res.totalArrived - res.totalServed - res.totalAbandoned;
        res.averageQueueTime = resolvedRequests.stream().mapToDouble(CallRequest::getQueueTime).average().orElse(0.0);
        res.averageServiceTime = servedRequests.stream()
                .mapToDouble(r -> r.getLeavingTime() - r.getServiceStartTime())
                .average()
                .orElse(0.0);
        res.serviceProbability = res.totalArrived == 0 ? 0.0 : (double) res.totalServed / res.totalArrived;
        res.abandonmentProbability = res.totalArrived == 0 ? 0.0 : (double) res.totalAbandoned / res.totalArrived;

        res.kClientByInterval = new LinkedHashMap<>();
        res.kOperByInterval = new LinkedHashMap<>();
        res.stabilityByInterval = new LinkedHashMap<>();
        res.targetFunctionByInterval = new LinkedHashMap<>();

        Map<Integer, List<CallRequest>> reqsByInterval = new HashMap<>();
        for (CallRequest req : resolvedRequests) {
            reqsByInterval.computeIfAbsent(req.getIntervalIndex(), k -> new ArrayList<>()).add(req);
        }

        double C1 = 1.0, C2 = 1.0;
        double minimumStability = Double.POSITIVE_INFINITY;
        boolean systemStable = true;
        for (int i = 0; i < config.totalIntervals; i++) {
            List<CallRequest> intervalReqs = reqsByInterval.getOrDefault(i, Collections.emptyList());
            double avgQueue = intervalReqs.isEmpty() ? 0 : intervalReqs.stream().mapToDouble(CallRequest::getQueueTime).average().orElse(0);
            double kClient = avgQueue * config.nu;
            res.kClientByInterval.put(i, kClient);

            int finalI = i;
            double totalBusyTime = allOperators.stream().mapToDouble(op -> op.getBusyTimeByInterval().getOrDefault(finalI, 0.0)).sum();
            long opsInInterval = countOperatorsInInterval(i);
            double kOper = opsInInterval == 0 ? 0 : totalBusyTime / (opsInInterval * config.intervalLength);
            double stability = calculateStability(opsInInterval, config.lambdaByInterval.get(i));

            res.kOperByInterval.put(i, kOper);
            res.stabilityByInterval.put(i, stability);
            res.targetFunctionByInterval.put(i, C1 * kOper - C2 * kClient);

            if (config.lambdaByInterval.get(i) > 0.0) {
                minimumStability = Math.min(minimumStability, stability);
                if (stability < 1.0) {
                    systemStable = false;
                }
            }
        }

        res.minimumStability = minimumStability;
        res.systemStable = systemStable;
        return res;
    }

    private long countOperatorsInInterval(int intervalIndex) {
        double intervalStart = intervalIndex * config.intervalLength;
        return allOperators.stream()
                .filter(op -> op.getShiftStartTime() <= intervalStart
                        && op.getShiftEndTime() > intervalStart)
                .count();
    }

    private double calculateStability(long operatorsInInterval, double lambda) {
        if (lambda <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        if (operatorsInInterval == 0) {
            return 0.0;
        }
        return operatorsInInterval * config.mu / lambda;
    }
}
