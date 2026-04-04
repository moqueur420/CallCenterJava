package output;

import config.SimulationConfig;
import model.CallOperator;
import model.CallRequest;
import model.RequestStatus;
import result.SimulationResults;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

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

            writeLine(writer, "Функция:");
            for (int interval = 0; interval < config.totalIntervals; interval++) {
                writeLine(writer, "Интервал " + interval + ", функция " +
                        formatNumber(results.targetFunctionByInterval.getOrDefault(interval, 0.0)));
            }
            writeLine(writer, "");

            writeLine(writer, "Итоговые параметры:");
            writeLine(writer, "Всего поступило заявок: " + results.totalArrived);
            writeLine(writer, "Успешно обслужено: " + results.totalServed);
            writeLine(writer, "Ушло из-за ожидания: " + results.totalAbandoned);
            writeLine(writer, "Незавершенных к окончанию модели: " + results.totalUnresolved);
            writeLine(writer, "Среднее время в очереди: " + formatNumber(results.averageQueueTime));
            writeLine(writer, "Среднее время обслуживания: " + formatNumber(results.averageServiceTime));
            writeLine(writer, "Вероятность обслуживания: " + formatNumber(results.serviceProbability));
            writeLine(writer, "Вероятность ухода: " + formatNumber(results.abandonmentProbability));
        }

        return absolutePath;
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
        return NUMBER_FORMAT.format(value);
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }
}
