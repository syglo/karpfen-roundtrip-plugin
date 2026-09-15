package org.karpfen.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BenchmarkMetrics {

    private final String benchmarkName;
    private final String targetName;
    private final String phase;
    private final List<Long> durationsNs = new ArrayList<>();
    private long warmupCount;
    private double memoryDeltaMb;
    private double coldStartMs;

    public BenchmarkMetrics(String benchmarkName, String targetName, String phase) {
        this.benchmarkName = benchmarkName;
        this.targetName = targetName;
        this.phase = phase;
    }

    public void setColdStartMs(double coldStartMs) {
        this.coldStartMs = coldStartMs;
    }

    public double getColdStartMs() {
        return (coldStartMs > 0.0) ? coldStartMs : getMinMs();
    }

    public void recordIteration(long durationNs) {
        durationsNs.add(durationNs);
    }

    public void setWarmupCount(long warmupCount) {
        this.warmupCount = warmupCount;
    }

    public void setMemoryDeltaMb(double memoryDeltaMb) {
        this.memoryDeltaMb = memoryDeltaMb;
    }

    public String getBenchmarkName() {
        return benchmarkName;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getPhase() {
        return phase;
    }

    public int getIterations() {
        return durationsNs.size();
    }

    public long getWarmupCount() {
        return warmupCount;
    }

    public double getMemoryDeltaMb() {
        return memoryDeltaMb;
    }

    public double getMeanMs() {
        if (durationsNs.isEmpty())
            return 0.0;
        double sum = 0.0;
        for (long d : durationsNs) {
            sum += d / 1_000_000.0;
        }
        return sum / durationsNs.size();
    }

    public double getMinMs() {
        if (durationsNs.isEmpty())
            return 0.0;
        long min = Collections.min(durationsNs);
        return min / 1_000_000.0;
    }

    public double getMaxMs() {
        if (durationsNs.isEmpty())
            return 0.0;
        long max = Collections.max(durationsNs);
        return max / 1_000_000.0;
    }

    public double getMedianMs() {
        if (durationsNs.isEmpty())
            return 0.0;
        List<Long> sorted = new ArrayList<>(durationsNs);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid) / 1_000_000.0;
        } else {
            return ((sorted.get(mid - 1) + sorted.get(mid)) / 2.0) / 1_000_000.0;
        }
    }

    public double getP95Ms() {
        return getPercentileMs(95.0);
    }

    public double getP99Ms() {
        return getPercentileMs(99.0);
    }

    public double getPercentileMs(double percentile) {
        if (durationsNs.isEmpty())
            return 0.0;
        List<Long> sorted = new ArrayList<>(durationsNs);
        Collections.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index) / 1_000_000.0;
    }

    public double getStdDevMs() {
        if (durationsNs.size() < 2)
            return 0.0;
        double mean = getMeanMs();
        double sumSq = 0.0;
        for (long d : durationsNs) {
            double ms = d / 1_000_000.0;
            sumSq += Math.pow(ms - mean, 2);
        }
        return Math.sqrt(sumSq / (durationsNs.size() - 1));
    }

    public double getThroughputOpsPerSec() {
        double meanMs = getMeanMs();
        if (meanMs <= 0.0)
            return 0.0;
        return 1000.0 / meanMs;
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "[%s | %s | %s] Mean: %.3f ms (±%.3f ms), Min: %.3f ms, Median: %.3f ms, P95: %.3f ms, Max: %.3f ms | Throughput: %.2f ops/sec (N=%d, Warmup=%d)",
                benchmarkName, targetName, phase,
                getMeanMs(), getStdDevMs(), getMinMs(), getMedianMs(), getP95Ms(), getMaxMs(),
                getThroughputOpsPerSec(), getIterations(), warmupCount);
    }

    @SuppressWarnings("removal")
    public static double measureUsedMemoryMb() {
        System.gc();
        System.runFinalization();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
        System.gc();
        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        return usedBytes / (1024.0 * 1024.0);
    }
}
