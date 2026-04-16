package input;

import config.SimulationConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DatasetConfigLoader {
    private DatasetConfigLoader() {
    }

    public static SimulationConfig load(Path datasetPath) throws IOException {
        Path normalizedPath = datasetPath.toAbsolutePath().normalize();
        String json = Files.readString(normalizedPath, StandardCharsets.UTF_8);

        Object parsedRoot = SimpleJsonParser.parse(json);
        if (!(parsedRoot instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Dataset root must be a JSON object");
        }

        Map<String, Object> root = castStringKeyMap(rawMap, "dataset root");
        int totalOperators = readRequiredInt(root, "totalOperators");
        List<Object> rawSchedule = readRequiredList(root, "schedule");
        if (rawSchedule.isEmpty()) {
            throw new IllegalArgumentException("Dataset schedule must not be empty");
        }

        List<ScheduleEntry> scheduleEntries = new ArrayList<>();
        for (Object rawEntry : rawSchedule) {
            if (!(rawEntry instanceof Map<?, ?> rawEntryMap)) {
                throw new IllegalArgumentException("Each schedule entry must be a JSON object");
            }

            Map<String, Object> entry = castStringKeyMap(rawEntryMap, "schedule entry");
            scheduleEntries.add(new ScheduleEntry(
                    readRequiredLong(entry, "timeFromStartMs"),
                    readRequiredInt(entry, "callCount")));
        }

        scheduleEntries.sort(Comparator.comparingLong(ScheduleEntry::timeFromStartMs));

        double intervalLengthSeconds = resolveIntervalLengthSeconds(scheduleEntries);

        SimulationConfig config = new SimulationConfig();
        config.totalOperators = totalOperators;
        config.totalIntervals = scheduleEntries.size();
        config.intervalLength = intervalLengthSeconds;
        config.arrivalsByInterval = buildArrivalsByInterval(scheduleEntries);
        return config;
    }

    private static Map<String, Object> castStringKeyMap(Map<?, ?> source, String label) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Non-string key found in " + label);
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static double resolveIntervalLengthSeconds(List<ScheduleEntry> scheduleEntries) {
        long firstStart = scheduleEntries.get(0).timeFromStartMs();
        if (firstStart != 0L) {
            throw new IllegalArgumentException("The first interval must start at 0 ms");
        }

        if (scheduleEntries.size() == 1) {
            return new SimulationConfig().intervalLength;
        }

        long stepMs = scheduleEntries.get(1).timeFromStartMs() - firstStart;
        if (stepMs <= 0L) {
            throw new IllegalArgumentException("Interval length must be positive");
        }

        for (int i = 1; i < scheduleEntries.size(); i++) {
            long expectedStart = firstStart + (long) i * stepMs;
            long actualStart = scheduleEntries.get(i).timeFromStartMs();
            if (actualStart != expectedStart) {
                throw new IllegalArgumentException("Schedule intervals must be evenly spaced");
            }
        }

        return stepMs / 1000.0;
    }

    private static List<Integer> buildArrivalsByInterval(List<ScheduleEntry> scheduleEntries) {
        List<Integer> arrivalsByInterval = new ArrayList<>(scheduleEntries.size());
        for (ScheduleEntry scheduleEntry : scheduleEntries) {
            arrivalsByInterval.add(scheduleEntry.callCount());
        }
        return arrivalsByInterval;
    }

    private static List<Object> readRequiredList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof List<?> listValue)) {
            throw new IllegalArgumentException("Field '" + key + "' must be an array");
        }
        return new ArrayList<>(listValue);
    }

    private static int readRequiredInt(Map<String, Object> source, String key) {
        long value = readRequiredLong(source, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Field '" + key + "' is out of int range");
        }
        return (int) value;
    }

    private static long readRequiredLong(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof Number numberValue)) {
            throw new IllegalArgumentException("Field '" + key + "' must be numeric");
        }

        double asDouble = numberValue.doubleValue();
        if (Math.rint(asDouble) != asDouble) {
            throw new IllegalArgumentException("Field '" + key + "' must be an integer");
        }
        return numberValue.longValue();
    }

    private static final class ScheduleEntry {
        private final long timeFromStartMs;
        private final int callCount;

        private ScheduleEntry(long timeFromStartMs, int callCount) {
            this.timeFromStartMs = timeFromStartMs;
            this.callCount = callCount;
        }

        private long timeFromStartMs() {
            return timeFromStartMs;
        }

        private int callCount() {
            return callCount;
        }
    }
}
