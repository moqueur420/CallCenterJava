package demo;

import config.SimulationConfig;
import integration.AnyLogicAdapter;
import output.SimulationReportWriter;
import result.SimulationResults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Запуск симуляции колл-центра...");

        SimulationConfig config = new SimulationConfig();
        List<Double> lambdas = new ArrayList<>();
        Random rand = new Random(config.seed);
        int lambdaBase = 100;
        lambdas.add(lambdaBase / 3600.0);
        for (int i = 1; i < 36; i++) {
            lambdaBase = rand.nextInt(33) + (lambdaBase - 16);
            lambdas.add(lambdaBase / 3600.0);
        }
        config.lambdaByInterval = lambdas;

        Integer[] scheduleArray = {
            2, 0, 1, 0, 1, 3, 1, 0, 0, 1, 0, 1, 0, 1, 0, 2, 2, 1, 1, 0, 1, 2, 0, 1, 3, 
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        config.operatorsSchedule = Arrays.asList(scheduleArray);

        AnyLogicAdapter adapter = new AnyLogicAdapter();
        SimulationResults results = adapter.runFastFullSimulation(config);
        Path reportPath = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("CCJava", "CallCenterJava", "simulation_report.txt");

        reportPath = SimulationReportWriter.writeReport(config, results, reportPath);

        System.out.println("Отчет сохранен в файл: " + reportPath);
        System.out.println("Интервалов в отчете: " + config.totalIntervals);
        System.out.println("Всего поступило заявок: " + results.totalArrived);
        System.out.println("Успешно обслужено: " + results.totalServed);
        System.out.println("Ушло из-за ожидания: " + results.totalAbandoned);
    }
}
