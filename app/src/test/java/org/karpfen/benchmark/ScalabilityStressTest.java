package org.karpfen.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.karpfen.resource.KarpfenResourceInitializer;
import org.karpfen.serializer.EcoreToKModelManualSerializer;
import org.karpfen.serializer.KarpfenDslFormatter;
import org.karpfen.transformer.KMetaToEcoreTransformer;
import org.karpfen.transformer.KModelToEcoreInstanceTransformer;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KmodelDSLConverter;
import instance.Model;
import meta.Metamodel;

public class ScalabilityStressTest {

    private static final int[] MODEL_SIZES = { 10, 50, 100, 250, 500, 1000 };
    private static final int MEASURE_RUNS_PER_SIZE = 5;

    private static String syntheticKMeta;
    private static Metamodel syntheticMetaAST;
    private static EPackage syntheticEPackage;

    @BeforeAll
    static void setUp() {
        KarpfenResourceInitializer.init();

        syntheticKMeta = """
                type "Sensor" "A sensor node" {
                    prop("reading", "number")
                    prop("status", "string")
                }

                type "Device" "An interconnected smart device" {
                    prop("name", "string")
                    prop("active", "boolean")
                    has("sensor", "Sensor")
                    knows("neighbors", list("Device"))
                }

                type "GridNetwork" "Root container of the smart grid" {
                    prop("networkId", "string")
                    has("devices", list("Device"))
                }
                """;

        syntheticMetaAST = KmetaDSLConverter.INSTANCE.parseKmetaString(syntheticKMeta, Collections.emptyList());
        KMetaToEcoreTransformer metaTransformer = new KMetaToEcoreTransformer();
        syntheticEPackage = metaTransformer.transform(syntheticMetaAST, "grid_domain", "http://github/karpfen/grid",
                "grid_domain");
    }

    @AfterAll
    static void tearDown() {
        BenchmarkResultExporter.exportAllReports();
    }

    @Test
    void testModelScalabilityStress() {
        KModelToEcoreInstanceTransformer transformer = new KModelToEcoreInstanceTransformer();
        EcoreToKModelManualSerializer serializer = new EcoreToKModelManualSerializer();

        for (int size : MODEL_SIZES) {
            String kmodelText = generateSyntheticKModel(size);

            double memBefore = BenchmarkMetrics.measureUsedMemoryMb();

            double totalT2dMs = 0.0;
            double totalD2tMs = 0.0;
            double totalRoundtripMs = 0.0;

            for (int r = 0; r < MEASURE_RUNS_PER_SIZE; r++) {
                // T2D
                long t2dStart = System.nanoTime();
                Model modelAST = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelText, syntheticMetaAST);
                List<EObject> roots = transformer.transform(modelAST, syntheticEPackage);
                long t2dEnd = System.nanoTime();

                assertNotNull(roots);
                assertEquals(1, roots.size());

                // D2T
                long d2tStart = System.nanoTime();
                String rawKModel = serializer.serialize(roots.get(0));
                String formatted = KarpfenDslFormatter.formatKModel(rawKModel);
                long d2tEnd = System.nanoTime();

                assertNotNull(formatted);

                double t2dDuration = (t2dEnd - t2dStart) / 1_000_000.0;
                double d2tDuration = (d2tEnd - d2tStart) / 1_000_000.0;

                totalT2dMs += t2dDuration;
                totalD2tMs += d2tDuration;
                totalRoundtripMs += (t2dDuration + d2tDuration);
            }

            double memAfter = BenchmarkMetrics.measureUsedMemoryMb();
            double memDelta = Math.max(0.0, memAfter - memBefore);

            double avgT2d = totalT2dMs / MEASURE_RUNS_PER_SIZE;
            double avgD2t = totalD2tMs / MEASURE_RUNS_PER_SIZE;
            double avgTotal = totalRoundtripMs / MEASURE_RUNS_PER_SIZE;

            BenchmarkResultExporter.recordScalability(
                    new BenchmarkResultExporter.ScalabilityDataPoint(size, avgT2d, avgD2t, avgTotal, memDelta));
        }
    }

    private String generateSyntheticKModel(int deviceCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("make object \"grid_root\":\"GridNetwork\" {\n");
        sb.append("prop(\"networkId\") -> \"NET-2026-SCALE\"\n\n");

        for (int i = 1; i <= deviceCount; i++) {
            String devId = "dev_" + i;
            String nextDevId = (i < deviceCount) ? "dev_" + (i + 1) : "dev_1";

            sb.append(String.format(java.util.Locale.ROOT, "has(\"devices\") -> make object \"%s\":\"Device\" {\n",
                    devId));
            sb.append(String.format(java.util.Locale.ROOT, "prop(\"name\") -> \"DeviceUnit_%d\"\n", i));
            sb.append(String.format(java.util.Locale.ROOT, "prop(\"active\") -> \"%s\"\n",
                    (i % 2 == 0) ? "true" : "false"));
            sb.append(String.format(java.util.Locale.ROOT,
                    "has(\"sensor\") -> make object \"sns_%d\":\"Sensor\" {\n", i));
            sb.append(String.format(java.util.Locale.ROOT, "prop(\"reading\") -> \"%.2f\"\n",
                    20.0 + (i * 0.1)));
            sb.append("prop(\"status\") -> \"OK\"\n");
            sb.append("}\n");
            sb.append(String.format(java.util.Locale.ROOT, "knows(\"neighbors\") -> \"%s\"\n", nextDevId));
            sb.append("}\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }
}
