package org.karpfen.benchmark;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.karpfen.resource.KarpfenResourceInitializer;
import org.karpfen.serializer.EcoreToKMetaManualSerializer;
import org.karpfen.serializer.EcoreToKModelManualSerializer;
import org.karpfen.serializer.EcoreToKStatesManualSerializer;
import org.karpfen.serializer.KarpfenDslFormatter;
import org.karpfen.transformer.KMetaToEcoreTransformer;
import org.karpfen.transformer.KModelToEcoreInstanceTransformer;
import org.karpfen.transformer.KStatesToEcoreTransformer;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KmodelDSLConverter;
import dsl.textual.KstatesDSLConverter;
import instance.Model;
import meta.Metamodel;
import states.StateMachine;

public class TransformationBenchmarkTest {

    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURE_ROUNDS = 25;

    private static String kmetaText;
    private static String kmodelText;
    private static String kstatesText;

    private static Metamodel metaAST;
    private static EPackage ePackage;
    private static EObject modelRoot;
    private static EObject statesRoot;

    private static String rawKMeta;
    private static String rawKModel;
    private static String rawKStates;

    @BeforeAll
    static void setUp() throws IOException {
        KarpfenResourceInitializer.init();

        Path base = Path.of("").toAbsolutePath();
        Path kmetaPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kmeta").normalize();
        Path kmodelPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kmodel").normalize();
        Path kstatesPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kstates").normalize();

        assertTrue(Files.exists(kmetaPath), "kmeta file must exist");
        assertTrue(Files.exists(kmodelPath), "kmodel file must exist");
        assertTrue(Files.exists(kstatesPath), "kstates file must exist");

        kmetaText = Files.readString(kmetaPath, StandardCharsets.UTF_8);
        kmodelText = Files.readString(kmodelPath, StandardCharsets.UTF_8);
        kstatesText = Files.readString(kstatesPath, StandardCharsets.UTF_8);

        metaAST = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaText, Collections.emptyList());
        KMetaToEcoreTransformer metaTransformer = new KMetaToEcoreTransformer();
        ePackage = metaTransformer.transform(metaAST, "cleaning_robot", "http://github/karpfen/cleaning_robot",
                "cleaning_robot");

        Model modelAST = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelText, metaAST);
        KModelToEcoreInstanceTransformer instanceTransformer = new KModelToEcoreInstanceTransformer();
        List<EObject> roots = instanceTransformer.transform(modelAST, ePackage);
        modelRoot = roots.get(0);

        StateMachine smAST = KstatesDSLConverter.INSTANCE.parseKstatesString(kstatesText);
        KStatesToEcoreTransformer statesTransformer = new KStatesToEcoreTransformer();
        statesRoot = statesTransformer.transform(smAST, "cleaning_robot_states");

        rawKMeta = new EcoreToKMetaManualSerializer().serialize(ePackage);
        rawKModel = new EcoreToKModelManualSerializer().serialize(modelRoot);
        rawKStates = new EcoreToKStatesManualSerializer().serialize(statesRoot);
    }

    @AfterAll
    static void tearDown() {
        BenchmarkResultExporter.exportAllReports();
    }

    @Test
    void testKMetaTransformationLatency() {
        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();
        EcoreToKMetaManualSerializer serializer = new EcoreToKMetaManualSerializer();

        // T2D
        BenchmarkMetrics t2dMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmeta", "T2D");
        t2dMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            Metamodel ast = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaText, Collections.emptyList());
            transformer.transform(ast, "cleaning_robot", "http://github/karpfen/cleaning_robot", "cleaning_robot");
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            Metamodel ast = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaText, Collections.emptyList());
            EPackage pkg = transformer.transform(ast, "cleaning_robot", "http://github/karpfen/cleaning_robot",
                    "cleaning_robot");
            long duration = System.nanoTime() - start;
            assertNotNull(pkg);
            t2dMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(t2dMetric);

        // D2T without formatter
        BenchmarkMetrics d2tRawMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmeta",
                "D2T-Raw");
        d2tRawMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            serializer.serialize(ePackage);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String raw = serializer.serialize(ePackage);
            long duration = System.nanoTime() - start;
            assertNotNull(raw);
            d2tRawMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tRawMetric);

        // D2T with formatter
        BenchmarkMetrics d2tFormatterMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmeta",
                "D2T-Formatter");
        d2tFormatterMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKMeta(rawKMeta);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String formatted = KarpfenDslFormatter.formatKMeta(rawKMeta);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            d2tFormatterMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tFormatterMetric);

        // D2T total
        BenchmarkMetrics d2tTotalMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmeta",
                "D2T-Total");
        d2tTotalMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKMeta(serializer.serialize(ePackage));
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String raw = serializer.serialize(ePackage);
            String formatted = KarpfenDslFormatter.formatKMeta(raw);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            d2tTotalMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tTotalMetric);

        // Roundtrip
        BenchmarkMetrics roundtripMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmeta",
                "Roundtrip");
        roundtripMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            Metamodel ast = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaText, Collections.emptyList());
            EPackage pkg = transformer.transform(ast, "cleaning_robot", "http://github/karpfen/cleaning_robot",
                    "cleaning_robot");
            String raw = serializer.serialize(pkg);
            String formatted = KarpfenDslFormatter.formatKMeta(raw);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            roundtripMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(roundtripMetric);
    }

    @Test
    void testKModelTransformationLatency() {
        KModelToEcoreInstanceTransformer transformer = new KModelToEcoreInstanceTransformer();
        EcoreToKModelManualSerializer serializer = new EcoreToKModelManualSerializer();

        // T2D
        BenchmarkMetrics t2dMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmodel", "T2D");
        t2dMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            Model ast = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelText, metaAST);
            transformer.transform(ast, ePackage);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            Model ast = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelText, metaAST);
            List<EObject> roots = transformer.transform(ast, ePackage);
            long duration = System.nanoTime() - start;
            assertNotNull(roots);
            t2dMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(t2dMetric);

        // D2T without formatter
        BenchmarkMetrics d2tRawMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmodel",
                "D2T-Raw");
        d2tRawMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            serializer.serialize(modelRoot);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String raw = serializer.serialize(modelRoot);
            long duration = System.nanoTime() - start;
            assertNotNull(raw);
            d2tRawMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tRawMetric);

        // D2T with formatter
        BenchmarkMetrics d2tFormatterMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmodel",
                "D2T-Formatter");
        d2tFormatterMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKModel(rawKModel);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String formatted = KarpfenDslFormatter.formatKModel(rawKModel);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            d2tFormatterMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tFormatterMetric);

        // D2T total
        BenchmarkMetrics d2tTotalMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmodel",
                "D2T-Total");
        d2tTotalMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKModel(serializer.serialize(modelRoot));
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String raw = serializer.serialize(modelRoot);
            String formatted = KarpfenDslFormatter.formatKModel(raw);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            d2tTotalMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tTotalMetric);

        // Roundtrip
        BenchmarkMetrics roundtripMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kmodel",
                "Roundtrip");
        roundtripMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            Model ast = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelText, metaAST);
            List<EObject> roots = transformer.transform(ast, ePackage);
            String raw = serializer.serialize(roots.get(0));
            String formatted = KarpfenDslFormatter.formatKModel(raw);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            roundtripMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(roundtripMetric);
    }

    @Test
    void testKStatesTransformationLatency() {
        KStatesToEcoreTransformer transformer = new KStatesToEcoreTransformer();
        EcoreToKStatesManualSerializer serializer = new EcoreToKStatesManualSerializer();

        // T2D
        BenchmarkMetrics t2dMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kstates", "T2D");
        t2dMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            StateMachine ast = KstatesDSLConverter.INSTANCE.parseKstatesString(kstatesText);
            transformer.transform(ast, "cleaning_robot_states");
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            StateMachine ast = KstatesDSLConverter.INSTANCE.parseKstatesString(kstatesText);
            EObject smObj = transformer.transform(ast, "cleaning_robot_states");
            long duration = System.nanoTime() - start;
            assertNotNull(smObj);
            t2dMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(t2dMetric);

        // D2T without formatter
        BenchmarkMetrics d2tRawMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kstates",
                "D2T-Raw");
        d2tRawMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            serializer.serialize(statesRoot);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String raw = serializer.serialize(statesRoot);
            long duration = System.nanoTime() - start;
            assertNotNull(raw);
            d2tRawMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tRawMetric);

        // D2T with formatter
        BenchmarkMetrics d2tFormatterMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kstates",
                "D2T-Formatter");
        d2tFormatterMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKStates(rawKStates);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String formatted = KarpfenDslFormatter.formatKStates(rawKStates);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            d2tFormatterMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tFormatterMetric);

        // D2T total
        BenchmarkMetrics d2tTotalMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kstates",
                "D2T-Total");
        d2tTotalMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKStates(serializer.serialize(statesRoot));
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String raw = serializer.serialize(statesRoot);
            String formatted = KarpfenDslFormatter.formatKStates(raw);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            d2tTotalMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(d2tTotalMetric);

        // Roundtrip
        BenchmarkMetrics roundtripMetric = new BenchmarkMetrics("TransformationBenchmark", "cleaning_robot.kstates",
                "Roundtrip");
        roundtripMetric.setWarmupCount(WARMUP_ROUNDS);
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            StateMachine ast = KstatesDSLConverter.INSTANCE.parseKstatesString(kstatesText);
            EObject smObj = transformer.transform(ast, "cleaning_robot_states");
            String raw = serializer.serialize(smObj);
            String formatted = KarpfenDslFormatter.formatKStates(raw);
            long duration = System.nanoTime() - start;
            assertNotNull(formatted);
            roundtripMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(roundtripMetric);
    }

    @Test
    void testIsolatedKarpfenDslFormatter() {
        String[] targets = { "cleaning_robot.kmeta", "cleaning_robot.kmodel", "cleaning_robot.kstates" };
        String[] rawTexts = { rawKMeta, rawKModel, rawKStates };

        for (int idx = 0; idx < targets.length; idx++) {
            String targetName = targets[idx];
            String rawText = rawTexts[idx];

            BenchmarkMetrics formatterMetric = new BenchmarkMetrics("FormatterBenchmark", targetName, "Format-Auto");
            formatterMetric.setWarmupCount(WARMUP_ROUNDS);

            for (int i = 0; i < WARMUP_ROUNDS; i++) {
                KarpfenDslFormatter.formatAuto(rawText, targetName);
            }

            for (int i = 0; i < MEASURE_ROUNDS; i++) {
                long start = System.nanoTime();
                String formatted = KarpfenDslFormatter.formatAuto(rawText, targetName);
                long duration = System.nanoTime() - start;
                assertNotNull(formatted);
                formatterMetric.recordIteration(duration);
            }
            BenchmarkResultExporter.recordMetric(formatterMetric);
        }
    }
}
