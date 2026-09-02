package org.karpfen.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BenchmarkResultExporter {

    private static final Path REPORT_DIR = Path.of("build/benchmark-reports");
    private static final List<BenchmarkMetrics> allMetrics = new ArrayList<>();
    private static final List<ScalabilityDataPoint> scalabilityData = new ArrayList<>();
    private static final List<IdempotencyDataPoint> idempotencyData = new ArrayList<>();

    public static synchronized void recordMetric(BenchmarkMetrics metric) {
        allMetrics.add(metric);
        System.out.println("[BENCHMARK] " + metric);
    }

    public static synchronized void recordScalability(ScalabilityDataPoint point) {
        scalabilityData.add(point);
        System.out.println(String.format(Locale.ROOT,
                "[SCALABILITY] Size: %4d | T2D: %7.3f ms | D2T: %7.3f ms | Total: %7.3f ms | Heap: %6.2f MB",
                point.elementCount, point.t2dMs, point.d2tMs, point.totalMs, point.memoryDeltaMb));
    }

    public static synchronized void recordIdempotency(IdempotencyDataPoint point) {
        idempotencyData.add(point);
        System.out.println(
                String.format(Locale.ROOT, "[IDEMPOTENCY] %s (%s) | Cycles: %d | 0-Byte Diff: %s | AST Equivalent: %s",
                        point.modelName, point.engine, point.cycles, point.zeroByteDiff ? "PASS" : "FAIL",
                        point.astEquivalent ? "PASS" : "FAIL"));
    }

    public static synchronized void exportAllReports() {
        try {
            Files.createDirectories(REPORT_DIR);

            exportLatencyCsv(REPORT_DIR.resolve("latency_benchmarks.csv"));
            exportEngineComparisonCsv(REPORT_DIR.resolve("serializer_comparison.csv"));
            exportScalabilityCsv(REPORT_DIR.resolve("scalability.csv"));
            exportIdempotencyCsv(REPORT_DIR.resolve("idempotency.csv"));

            System.out.println("BENCHMARK CSV REPORTS GENERATED IN: " + REPORT_DIR.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to export benchmark reports: " + e.getMessage());
        }
    }

    private static void exportLatencyCsv(Path filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Target,Phase,Mean_ms,StdDev_ms,Median_ms,P95_ms,Throughput_ops_s\n");

        for (BenchmarkMetrics m : allMetrics) {
            if ("TransformationBenchmark".equals(m.getBenchmarkName())
                    || "FormatterBenchmark".equals(m.getBenchmarkName())) {
                sb.append(String.format(Locale.ROOT, "%s,%s,%.4f,%.4f,%.4f,%.4f,%.2f\n",
                        m.getTargetName(),
                        m.getPhase(),
                        m.getMeanMs(),
                        m.getStdDevMs(),
                        m.getMedianMs(),
                        m.getP95Ms(),
                        m.getThroughputOpsPerSec()));
            }
        }

        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void exportEngineComparisonCsv(Path filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Target,Engine,ColdStart_ms,WarmMean_ms,StdDev_ms,Median_ms,P95_ms,Throughput_ops_s\n");

        for (BenchmarkMetrics m : allMetrics) {
            if ("SerializerComparison".equals(m.getBenchmarkName())) {
                sb.append(String.format(Locale.ROOT, "%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f\n",
                        m.getTargetName(),
                        m.getPhase(),
                        m.getColdStartMs(),
                        m.getMeanMs(),
                        m.getStdDevMs(),
                        m.getMedianMs(),
                        m.getP95Ms(),
                        m.getThroughputOpsPerSec()));
            }
        }

        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void exportScalabilityCsv(Path filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Elements,T2D_ms,D2T_ms,Total_ms,HeapDelta_MB\n");

        for (ScalabilityDataPoint p : scalabilityData) {
            sb.append(String.format(Locale.ROOT, "%d,%.4f,%.4f,%.4f,%.4f\n",
                    p.elementCount, p.t2dMs, p.d2tMs, p.totalMs, p.memoryDeltaMb));
        }

        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void exportIdempotencyCsv(Path filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Target,Engine,Cycles,ZeroByteDiff,AstParity\n");

        for (IdempotencyDataPoint p : idempotencyData) {
            sb.append(String.format(Locale.ROOT, "%s,%s,%d,%s,%s\n",
                    p.modelName,
                    p.engine,
                    p.cycles,
                    p.zeroByteDiff ? "PASS" : "FAIL",
                    p.astEquivalent ? "PASS" : "FAIL"));
        }

        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
    }

    public static class ScalabilityDataPoint {
        public final int elementCount;
        public final double t2dMs;
        public final double d2tMs;
        public final double totalMs;
        public final double memoryDeltaMb;

        public ScalabilityDataPoint(int elementCount, double t2dMs, double d2tMs, double totalMs,
                double memoryDeltaMb) {
            this.elementCount = elementCount;
            this.t2dMs = t2dMs;
            this.d2tMs = d2tMs;
            this.totalMs = totalMs;
            this.memoryDeltaMb = memoryDeltaMb;
        }
    }

    public static class IdempotencyDataPoint {
        public final String modelName;
        public final String engine;
        public final int cycles;
        public final boolean zeroByteDiff;
        public final boolean astEquivalent;

        public IdempotencyDataPoint(String modelName, String engine, int cycles, boolean zeroByteDiff,
                boolean astEquivalent) {
            this.modelName = modelName;
            this.engine = engine;
            this.cycles = cycles;
            this.zeroByteDiff = zeroByteDiff;
            this.astEquivalent = astEquivalent;
        }
    }
}
