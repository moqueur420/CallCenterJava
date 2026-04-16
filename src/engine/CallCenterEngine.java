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
        prepareSimulationConfig();
        currentTime = 0.0;
        currentIntervalIndex = 0;
        addOperatorsForInterval(0);
        generateArrivalsForInterval(0);
    }

    private void prepareSimulationConfig() {
        validateCoreConfig();
        ensureArrivalsByInterval();
        ensureLambdaByInterval();
        ensureOperatorsSchedule();
    }

    private void validateCoreConfig() {
        if (config.totalOperators < 0) {
            throw new IllegalArgumentException("totalOperators must not be negative");
        }
        if (config.totalIntervals < 0) {
            throw new IllegalArgumentException("totalIntervals must not be negative");
        }
        if (config.intervalLength <= 0.0) {
            throw new IllegalArgumentException("intervalLength must be positive");
        }
        if (config.shiftLength <= 0.0) {
            throw new IllegalArgumentException("shiftLength must be positive");
        }
        if (config.mu <= 0.0) {
            throw new IllegalArgumentException("mu must be positive");
        }
        if (config.nu <= 0.0) {
            throw new IllegalArgumentException("nu must be positive");
        }
    }

    private void ensureArrivalsByInterval() {
        if (config.arrivalsByInterval == null) {
            return;
        }

        if (config.arrivalsByInterval.size() != config.totalIntervals) {
            throw new IllegalArgumentException("arrivalsByInterval size must match totalIntervals");
        }

        for (Integer arrivals : config.arrivalsByInterval) {
            if (arrivals == null || arrivals < 0) {
                throw new IllegalArgumentException("arrivalsByInterval must contain only non-negative values");
            }
        }
    }

    private void ensureLambdaByInterval() {
        if (config.lambdaByInterval == null) {
            if (config.arrivalsByInterval == null) {
                throw new IllegalArgumentException("lambdaByInterval or arrivalsByInterval must be provided");
            }
            config.lambdaByInterval = buildLambdaByInterval(config.arrivalsByInterval, config.intervalLength);
            return;
        }

        if (config.lambdaByInterval.size() != config.totalIntervals) {
            throw new IllegalArgumentException("lambdaByInterval size must match totalIntervals");
        }

        for (Double lambda : config.lambdaByInterval) {
            if (lambda == null || lambda < 0.0) {
                throw new IllegalArgumentException("lambdaByInterval must contain only non-negative values");
            }
        }
    }

    private void ensureOperatorsSchedule() {
        if (config.operatorsSchedule == null) {
            config.operatorsSchedule = buildOperatorsSchedule();
            return;
        }

        if (config.operatorsSchedule.size() != config.totalIntervals) {
            throw new IllegalArgumentException("operatorsSchedule size must match totalIntervals");
        }

        int scheduledOperators = 0;
        for (Integer operatorsToAdd : config.operatorsSchedule) {
            if (operatorsToAdd == null || operatorsToAdd < 0) {
                throw new IllegalArgumentException("operatorsSchedule must contain only non-negative values");
            }
            scheduledOperators += operatorsToAdd;
        }

        if (scheduledOperators > config.totalOperators) {
            throw new IllegalArgumentException("operatorsSchedule exceeds totalOperators");
        }
    }

    private List<Double> buildLambdaByInterval(List<Integer> arrivalsByInterval, double intervalLengthSeconds) {
        List<Double> lambdas = new ArrayList<>(arrivalsByInterval.size());
        for (Integer arrivalCount : arrivalsByInterval) {
            lambdas.add(arrivalCount / intervalLengthSeconds);
        }
        return lambdas;
    }

    private List<Integer> buildOperatorsSchedule() {
        List<Integer> emptySchedule = new ArrayList<>(config.totalIntervals);
        for (int i = 0; i < config.totalIntervals; i++) {
            emptySchedule.add(0);
        }
        if (config.totalOperators == 0 || config.totalIntervals == 0) {
            return emptySchedule;
        }

        int shiftCoverageIntervals = resolveShiftCoverageIntervals(config.shiftLength, config.intervalLength);
        List<Double> requiredOperatorsByInterval = buildRequiredOperatorsByInterval(config.lambdaByInterval, config.mu);

        List<Integer> minimalCoverageSchedule =
                buildMinimalCoverageSchedule(requiredOperatorsByInterval, config.totalIntervals, shiftCoverageIntervals);

        if (sumOperators(minimalCoverageSchedule) <= config.totalOperators) {
            return minimalCoverageSchedule;
        }

        return buildBestEffortSchedule(
                requiredOperatorsByInterval,
                config.totalIntervals,
                shiftCoverageIntervals,
                config.totalOperators);
    }

    private int resolveShiftCoverageIntervals(double shiftLengthSeconds, double intervalLengthSeconds) {
        return Math.max(1, (int) Math.ceil(shiftLengthSeconds / intervalLengthSeconds));
    }

    private List<Double> buildRequiredOperatorsByInterval(List<Double> lambdaByInterval, double mu) {
        List<Double> requiredOperators = new ArrayList<>(lambdaByInterval.size());
        for (double lambda : lambdaByInterval) {
            requiredOperators.add(lambda <= 0.0 ? 0.0 : lambda / mu);
        }
        return requiredOperators;
    }

    private List<Integer> buildMinimalCoverageSchedule(
            List<Double> requiredOperatorsByInterval,
            int totalIntervals,
            int shiftCoverageIntervals) {
        List<Integer> schedule = new ArrayList<>(totalIntervals);
        for (int i = 0; i < totalIntervals; i++) {
            schedule.add(0);
        }

        int[] shiftEnds = new int[totalIntervals + shiftCoverageIntervals + 1];
        int activeOperators = 0;

        for (int interval = 0; interval < totalIntervals; interval++) {
            activeOperators -= shiftEnds[interval];

            int requiredOperators = ceilToInt(requiredOperatorsByInterval.get(interval));
            int operatorsToAdd = requiredOperators - activeOperators;
            if (operatorsToAdd <= 0) {
                continue;
            }

            schedule.set(interval, operatorsToAdd);
            activeOperators += operatorsToAdd;
            shiftEnds[interval + shiftCoverageIntervals] += operatorsToAdd;
        }

        return schedule;
    }

    private List<Integer> buildBestEffortSchedule(
            List<Double> requiredOperatorsByInterval,
            int totalIntervals,
            int shiftCoverageIntervals,
            int totalOperators) {
        List<Integer> schedule = new ArrayList<>(totalIntervals);
        for (int i = 0; i < totalIntervals; i++) {
            schedule.add(0);
        }

        double[] activeOperatorsByInterval = new double[totalIntervals];
        for (int operator = 0; operator < totalOperators; operator++) {
            int bestStartInterval = -1;
            double bestGain = 0.0;
            double bestPressure = 0.0;

            for (int startInterval = 0; startInterval < totalIntervals; startInterval++) {
                double marginalGain = 0.0;
                double remainingPressure = 0.0;
                int endInterval = Math.min(totalIntervals, startInterval + shiftCoverageIntervals);

                for (int interval = startInterval; interval < endInterval; interval++) {
                    double remainingDemand =
                            requiredOperatorsByInterval.get(interval) - activeOperatorsByInterval[interval];
                    if (remainingDemand <= 0.0) {
                        continue;
                    }

                    marginalGain += Math.min(1.0, remainingDemand);
                    remainingPressure += remainingDemand;
                }

                if (marginalGain > bestGain
                        || (almostEqual(marginalGain, bestGain) && remainingPressure > bestPressure)
                        || (almostEqual(marginalGain, bestGain)
                                && almostEqual(remainingPressure, bestPressure)
                                && startInterval > bestStartInterval)) {
                    bestStartInterval = startInterval;
                    bestGain = marginalGain;
                    bestPressure = remainingPressure;
                }
            }

            if (bestStartInterval < 0 || bestGain <= 0.0) {
                break;
            }

            schedule.set(bestStartInterval, schedule.get(bestStartInterval) + 1);
            applyShift(activeOperatorsByInterval, bestStartInterval, shiftCoverageIntervals, totalIntervals, 1);
        }

        improveScheduleLocally(schedule, requiredOperatorsByInterval, totalIntervals, shiftCoverageIntervals);
        return schedule;
    }

    private void improveScheduleLocally(
            List<Integer> schedule,
            List<Double> requiredOperatorsByInterval,
            int totalIntervals,
            int shiftCoverageIntervals) {
        double[] activeOperatorsByInterval =
                buildActiveOperatorsByInterval(schedule, totalIntervals, shiftCoverageIntervals);
        double currentScore = calculateCoverageScore(activeOperatorsByInterval, requiredOperatorsByInterval);

        while (true) {
            int bestFromInterval = -1;
            int bestToInterval = -1;
            double bestScore = currentScore;

            for (int fromInterval = 0; fromInterval < totalIntervals; fromInterval++) {
                if (schedule.get(fromInterval) <= 0) {
                    continue;
                }

                applyShift(activeOperatorsByInterval, fromInterval, shiftCoverageIntervals, totalIntervals, -1);

                for (int toInterval = 0; toInterval < totalIntervals; toInterval++) {
                    if (toInterval == fromInterval) {
                        continue;
                    }

                    applyShift(activeOperatorsByInterval, toInterval, shiftCoverageIntervals, totalIntervals, 1);
                    double candidateScore =
                            calculateCoverageScore(activeOperatorsByInterval, requiredOperatorsByInterval);
                    if (candidateScore > bestScore && !almostEqual(candidateScore, bestScore)) {
                        bestScore = candidateScore;
                        bestFromInterval = fromInterval;
                        bestToInterval = toInterval;
                    }
                    applyShift(activeOperatorsByInterval, toInterval, shiftCoverageIntervals, totalIntervals, -1);
                }

                applyShift(activeOperatorsByInterval, fromInterval, shiftCoverageIntervals, totalIntervals, 1);
            }

            if (bestFromInterval < 0 || bestToInterval < 0) {
                return;
            }

            schedule.set(bestFromInterval, schedule.get(bestFromInterval) - 1);
            schedule.set(bestToInterval, schedule.get(bestToInterval) + 1);

            activeOperatorsByInterval =
                    buildActiveOperatorsByInterval(schedule, totalIntervals, shiftCoverageIntervals);
            currentScore = bestScore;
        }
    }

    private double[] buildActiveOperatorsByInterval(
            List<Integer> schedule,
            int totalIntervals,
            int shiftCoverageIntervals) {
        double[] activeOperatorsByInterval = new double[totalIntervals];
        for (int startInterval = 0; startInterval < schedule.size(); startInterval++) {
            int operatorCount = schedule.get(startInterval);
            if (operatorCount <= 0) {
                continue;
            }

            applyShift(activeOperatorsByInterval, startInterval, shiftCoverageIntervals, totalIntervals, operatorCount);
        }
        return activeOperatorsByInterval;
    }

    private void applyShift(
            double[] activeOperatorsByInterval,
            int startInterval,
            int shiftCoverageIntervals,
            int totalIntervals,
            int operatorDelta) {
        int endInterval = Math.min(totalIntervals, startInterval + shiftCoverageIntervals);
        for (int interval = startInterval; interval < endInterval; interval++) {
            activeOperatorsByInterval[interval] += operatorDelta;
        }
    }

    private double calculateCoverageScore(
            double[] activeOperatorsByInterval,
            List<Double> requiredOperatorsByInterval) {
        double score = 0.0;
        for (int interval = 0; interval < requiredOperatorsByInterval.size(); interval++) {
            score += Math.min(activeOperatorsByInterval[interval], requiredOperatorsByInterval.get(interval));
        }
        return score;
    }

    private int sumOperators(List<Integer> schedule) {
        int total = 0;
        for (Integer value : schedule) {
            total += value;
        }
        return total;
    }

    private int ceilToInt(double value) {
        if (value <= 0.0) {
            return 0;
        }
        double rounded = Math.rint(value);
        if (Math.abs(rounded - value) < 1e-9) {
            return (int) rounded;
        }
        return (int) Math.ceil(value);
    }

    private boolean almostEqual(double left, double right) {
        return Math.abs(left - right) < 1e-9;
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
        if (config.arrivalsByInterval != null && intervalIndex < config.arrivalsByInterval.size()) {
            generateFixedArrivalsForInterval(intervalIndex, config.arrivalsByInterval.get(intervalIndex));
            return;
        }

        double lambda = config.lambdaByInterval.get(intervalIndex);
        if (lambda <= 0.0) {
            return;
        }
        double intervalStart = intervalIndex * config.intervalLength;
        double intervalEnd = intervalStart + config.intervalLength;
        double time = intervalStart;

        while (true) {
            double u = random.nextDouble();
            time += -Math.log(1 - u) / lambda;
            if (time > intervalEnd) break;

            enqueueRequest(time, intervalIndex);
        }
    }

    private void generateFixedArrivalsForInterval(int intervalIndex, int arrivalsCount) {
        if (arrivalsCount <= 0) {
            return;
        }

        double intervalStart = intervalIndex * config.intervalLength;
        List<Double> arrivalTimes = new ArrayList<>(arrivalsCount);
        for (int i = 0; i < arrivalsCount; i++) {
            arrivalTimes.add(intervalStart + random.nextDouble() * config.intervalLength);
        }
        Collections.sort(arrivalTimes);

        for (double arrivalTime : arrivalTimes) {
            enqueueRequest(arrivalTime, intervalIndex);
        }
    }

    private void enqueueRequest(double arrivalTime, int intervalIndex) {
        lastRequestId++;
        double patience = config.useRandomServiceTime
                ? (-Math.log(1 - random.nextDouble()) / config.nu)
                : (1.0 / config.nu);
        CallRequest req = new CallRequest(lastRequestId, arrivalTime, patience, intervalIndex);
        allRequests.add(req);
        eventQueue.add(new SimEvent(arrivalTime, SimEvent.EventType.ARRIVAL, req, -1));
    }

    private void processArrival(CallRequest req) {
        lazyQueueCleanup();
        if (!tryAssignRequest(req)) {
            requestQueue.add(req);
        }
    }

    private void processDeparture(int operatorId) {
        CallOperator op = activeOperators.stream().filter(o -> o.getId() == operatorId).findFirst().orElse(null);
        if (op == null) return;

        CallRequest finishedReq = allRequests.get(op.getCurrentRequestId() - 1);
        finishedReq.completeService(currentTime);
        op.freeUp();

        removeExpiredIdleOperators();
        assignQueuedRequests();
    }

    private void assignRequestToOperator(CallRequest req, CallOperator op, double serviceTime) {
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

    private boolean tryAssignRequest(CallRequest req) {
        double serviceTime = sampleServiceTime();
        CallOperator bestOp = findOptimalOperator(serviceTime);
        if (bestOp == null) {
            return false;
        }

        assignRequestToOperator(req, bestOp, serviceTime);
        return true;
    }

    private void assignQueuedRequests() {
        while (true) {
            lazyQueueCleanup();
            if (requestQueue.isEmpty()) {
                return;
            }

            CallRequest nextRequest = requestQueue.peek();
            if (!tryAssignRequest(nextRequest)) {
                return;
            }

            requestQueue.poll();
        }
    }

    private double sampleServiceTime() {
        return config.useRandomServiceTime
                ? (-Math.log(1 - random.nextDouble()) / config.mu)
                : (1.0 / config.mu);
    }

    private CallOperator findOptimalOperator(double serviceTime) {
        removeExpiredIdleOperators();
        return activeOperators.stream()
                .filter(o -> o.canTakeRequest(currentTime, serviceTime))
                .min(Comparator.comparingDouble(CallOperator::getReleaseTime)
                        .thenComparingDouble(CallOperator::getShiftEndTime)
                        .thenComparingInt(CallOperator::getId))
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

        assignQueuedRequests();
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

    private void removeExpiredIdleOperators() {
        activeOperators.removeIf(op -> !op.isBusy() && currentTime >= op.getShiftEndTime());
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
        res.operatorsCountByInterval = new LinkedHashMap<>();
        res.serviceCapacityByInterval = new LinkedHashMap<>();
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
            double serviceCapacity = opsInInterval * config.mu;
            double kOper = opsInInterval == 0 ? 0 : totalBusyTime / (opsInInterval * config.intervalLength);
            double stability = calculateStability(opsInInterval, config.lambdaByInterval.get(i));

            res.kOperByInterval.put(i, kOper);
            res.stabilityByInterval.put(i, stability);
            res.operatorsCountByInterval.put(i, opsInInterval);
            res.serviceCapacityByInterval.put(i, serviceCapacity);
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
