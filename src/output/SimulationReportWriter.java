package output;

import config.SimulationConfig;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.CallOperator;
import model.CallRequest;
import model.RequestStatus;
import result.SimulationResults;

public final class SimulationReportWriter {
    private static final DecimalFormat NUMBER_FORMAT =
            new DecimalFormat("0.#######", DecimalFormatSymbols.getInstance(Locale.US));

    private SimulationReportWriter() {
    }

    public static Path writeReport(SimulationConfig config, SimulationResults results, Path outputPath) throws IOException {
        Path absolutePath = outputPath.toAbsolutePath().normalize();
        if (absolutePath.getParent() != null) {
            Files.createDirectories(absolutePath.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(absolutePath, StandardCharsets.UTF_8)) {
            writeLine(writer, "Поток заявок по интервалам (lambda):");
            writeValuesLine(writer, config.lambdaByInterval);
            writeLine(writer, "");

            writeLine(writer, "Добавление операторов по интервалам:");
            writeValuesLine(writer, config.operatorsSchedule);
            writeLine(writer, "");

            writeLine(writer, "Количество операторов, работавших по интервалам:");
            for (int interval = 0; interval < config.totalIntervals; interval++) {
                writeLine(writer, "Интервал " + interval + ", работало операторов " +
                    results.operatorsCountByInterval.getOrDefault(interval, 0L));
}

            writeLine(writer, "");

            writeCompletedRequests(writer, results.allRequests);
            writeLine(writer, "");

            writeLine(writer, "K_client по интервалам:");
            for (int interval = 0; interval < config.totalIntervals; interval++) {
                writeLine(writer, "Интервал " + interval + ", K_client " +
                        formatNumber(results.kClientByInterval.getOrDefault(interval, 0.0)));
            }
            writeLine(writer, "");

            writeLine(writer, "Время занятости операторов по интервалам:");
            for (CallOperator operator : results.allOperators) {
                writeLine(writer, "Канал " + operator.getId());
                for (int interval = 0; interval < config.totalIntervals; interval++) {
                    writeLine(writer, "Интервал " + interval + " время " +
                            formatNumber(operator.getBusyTimeByInterval().getOrDefault(interval, 0.0)));
                }
            }
            writeLine(writer, "");

            writeLine(writer, "Загрузка системы:");
            for (int interval = 0; interval < config.totalIntervals; interval++) {
                writeLine(writer, "Интервал " + interval + ", загрузка " +
                        formatNumber(results.kOperByInterval.getOrDefault(interval, 0.0)));
            }
            writeLine(writer, "");

            writeLine(writer, "Коэффициент устойчивости системы по интервалам:");
            for (int interval = 0; interval < config.totalIntervals; interval++) {
                writeLine(writer, "Интервал " + interval + ", коэффициент устойчивости " +
                        formatNumber(results.stabilityByInterval.getOrDefault(interval, 0.0)));
            }
            writeLine(writer, "");

            writeStabilityDetails(writer, config, results);
            writeLine(writer, "");

            writeLine(writer, "Функция:");
            for (int interval = 0; interval < config.totalIntervals; interval++) {
                writeLine(writer, "Интервал " + interval + ", функция " +
                        formatNumber(results.targetFunctionByInterval.getOrDefault(interval, 0.0)));
            }
            writeLine(writer, "");

            writeLines(writer, buildSummaryLines(results));
        }

        return absolutePath;
    }

    public static List<String> buildSummaryLines(SimulationResults results) {
        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("Итоговые параметры:");
        summaryLines.add("Всего поступило заявок: " + results.totalArrived);
        summaryLines.add("Успешно обслужено: " + results.totalServed);
        summaryLines.add("Ушло из-за ожидания: " + results.totalAbandoned);
        summaryLines.add("Незавершенных к окончанию модели: " + results.totalUnresolved);
        summaryLines.add("Среднее время в очереди: " + formatNumber(results.averageQueueTime));
        summaryLines.add("Среднее время обслуживания: " + formatNumber(results.averageServiceTime));
        summaryLines.add("Вероятность обслуживания: " + formatNumber(results.serviceProbability));
        summaryLines.add("Вероятность ухода: " + formatNumber(results.abandonmentProbability));
        summaryLines.add("Минимальный коэффициент устойчивости: " + formatNumber(results.minimumStability));
        summaryLines.add("Система устойчива: " + formatBoolean(results.systemStable));
        return summaryLines;
    }

    private static void writeStabilityDetails(BufferedWriter writer, SimulationConfig config, SimulationResults results)
            throws IOException {
        writeLine(writer, "Подробный расчет устойчивости системы:");
        writeLine(writer, "Формула: K_уст = (N_оп * mu) / lambda.");
        writeLine(writer, "N_оп - число операторов, работающих в интервале.");
        writeLine(writer, "mu - интенсивность обслуживания одним оператором.");
        writeLine(writer, "lambda - интенсивность входящего потока заявок.");
        writeLine(writer, "Критерий устойчивости: K_уст >= 1.");
        writeLine(writer, "Минимальный K_уст по модели: " + formatNumber(results.minimumStability));
        writeLine(writer, "Общий вывод по модели: система " + buildStabilityConclusion(results.minimumStability) + ".");
        writeLine(writer, "");

        for (int interval = 0; interval < config.totalIntervals; interval++) {
            double lambda = config.lambdaByInterval.get(interval);
            long operatorsCount = results.operatorsCountByInterval.getOrDefault(interval, 0L);
            double serviceCapacity = results.serviceCapacityByInterval.getOrDefault(interval, 0.0);
            double stability = results.stabilityByInterval.getOrDefault(interval, 0.0);

            writeLine(writer, "Интервал " + interval + ":");
            writeLine(writer, "lambda = " + formatNumber(lambda)
                    + ", N_оп = " + operatorsCount
                    + ", N_оп * mu = " + formatNumber(serviceCapacity));

            if (lambda <= 0.0) {
                writeLine(writer, "Расчет: входящий поток отсутствует, поэтому K_уст = INF.");
                writeLine(writer, "Вывод: интервал не создает нагрузки, система считается устойчивой.");
            } else {
                writeLine(writer, "Расчет: K_уст = (" + operatorsCount
                        + " * " + formatNumber(config.mu)
                        + ") / " + formatNumber(lambda)
                        + " = " + formatNumber(serviceCapacity)
                        + " / " + formatNumber(lambda)
                        + " = " + formatNumber(stability));
                writeLine(writer, "Запас устойчивости: K_уст - 1 = " + formatNumber(stability - 1.0));
                writeLine(writer, "Вывод: система " + buildStabilityConclusion(stability) + " в этом интервале.");
            }

            if (interval < config.totalIntervals - 1) {
                writeLine(writer, "");
            }
        }
    }

    private static void writeCompletedRequests(BufferedWriter writer, List<CallRequest> requests) throws IOException {
        writeLine(writer, "Завершенные заявки:");
        for (CallRequest request : requests) {
            if (request.getStatus() == RequestStatus.SERVED) {
                writeLine(writer, "Ид заявки: " + request.getId()
                        + ", обслуживший оператор: " + request.getOperatorId()
                        + ", время в очереди: " + formatNumber(request.getQueueTime()));
                writeLine(writer, "Время появления " + formatNumber(request.getAppearanceTime()));
                writeLine(writer, "Время ухода " + formatNumber(request.getLeavingTime()));
            } else if (request.getStatus() == RequestStatus.ABANDONED) {
                writeLine(writer, "Ид заявки: " + request.getId()
                        + ", заявка не обслуживалась: " + formatNumber(request.getQueueTime()));
                writeLine(writer, "Время появления " + formatNumber(request.getAppearanceTime()));
                writeLine(writer, "Время ухода " + formatNumber(request.getLeavingTime()));
            }
        }
    }

    private static void writeValuesLine(BufferedWriter writer, List<?> values) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (builder.length() > 0) {
                builder.append("  ");
            }
            if (value instanceof Number number) {
                builder.append(formatNumber(number.doubleValue()));
            } else {
                builder.append(value);
            }
        }
        writeLine(writer, builder.toString());
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "INF" : "-INF";
        }
        return NUMBER_FORMAT.format(value);
    }

    private static String formatBoolean(boolean value) {
        return value ? "да" : "нет";
    }

    private static String buildStabilityConclusion(double stability) {
        return stability >= 1.0 ? "устойчива" : "неустойчива";
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private static void writeLines(BufferedWriter writer, List<String> lines) throws IOException {
        for (String line : lines) {
            writeLine(writer, line);
        }
    }
}
