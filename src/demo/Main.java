package demo;

import config.SimulationConfig;
import input.DatasetConfigLoader;
import integration.AnyLogicAdapter;
import output.SimulationReportWriter;
import result.SimulationResults;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String PROJECT_NAME = "CallCenterJava";

    public static void main(String[] args) throws Exception {
        Path datasetPath = resolveDatasetPath(args);
        Path reportPath = resolveReportPath(args);
        SimulationConfig config = DatasetConfigLoader.load(datasetPath);

        AnyLogicAdapter adapter = new AnyLogicAdapter();
        SimulationResults results = adapter.runFastFullSimulation(config);
        reportPath = SimulationReportWriter.writeReport(config, results, reportPath);

        System.out.println("Dataset: " + datasetPath.toAbsolutePath().normalize());
        System.out.println("Report: " + reportPath);
        System.out.println("Total operators: " + config.totalOperators);
        System.out.println("Scheduled operators: " + config.operatorsSchedule.stream().mapToInt(Integer::intValue).sum());
        System.out.println("Intervals: " + config.totalIntervals);
        for (String line : SimulationReportWriter.buildSummaryLines(results)) {
            System.out.println(line);
        }
    }

    private static Path resolveDatasetPath(String[] args) {
        if (args.length > 0 && looksLikeJsonPath(args[0])) {
            return Paths.get(args[0]);
        }
        return resolveDefaultDatasetPath();
    }

    private static Path resolveReportPath(String[] args) {
        if (args.length == 0) {
            return resolveDefaultReportPath();
        }

        if (looksLikeJsonPath(args[0])) {
            if (args.length > 1) {
                return Paths.get(args[1]);
            }
            return resolveDefaultReportPath();
        }

        return Paths.get(args[0]);
    }

    private static boolean looksLikeJsonPath(String value) {
        return value != null && value.toLowerCase().endsWith(".json");
    }

    private static Path resolveDefaultDatasetPath() {
        return resolveProjectRoot().resolve(Paths.get("src", "input", "dataset.json"));
    }

    private static Path resolveDefaultReportPath() {
        return resolveProjectRoot().resolve("simulation_report.txt");
    }

    private static Path resolveProjectRoot() {
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();

        if (isProjectRoot(workingDirectory)) {
            return workingDirectory;
        }

        Path nestedProjectRoot = workingDirectory.resolve(PROJECT_NAME);
        if (isProjectRoot(nestedProjectRoot)) {
            return nestedProjectRoot;
        }

        Path current = workingDirectory.getParent();
        while (current != null) {
            if (isProjectRoot(current)) {
                return current;
            }
            current = current.getParent();
        }

        return workingDirectory;
    }

    private static boolean isProjectRoot(Path path) {
        return path != null
                && path.getFileName() != null
                && PROJECT_NAME.equals(path.getFileName().toString())
                && Files.isDirectory(path.resolve("src"));
    }
}
