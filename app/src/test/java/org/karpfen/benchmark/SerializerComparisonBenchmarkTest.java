package org.karpfen.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import org.karpfen.serializer.AcceleoKMetaSerializer;
import org.karpfen.serializer.AcceleoKModelSerializer;
import org.karpfen.serializer.AcceleoKStatesSerializer;
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

public class SerializerComparisonBenchmarkTest {

    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURE_ROUNDS = 20;

    private static EPackage ePackage;
    private static EObject modelRoot;
    private static EObject statesRoot;
    private static Metamodel metaAST;

    @BeforeAll
    static void setUp() throws IOException {
        KarpfenResourceInitializer.init();

        Path base = Path.of("").toAbsolutePath();
        Path kmetaPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kmeta").normalize();
        Path kmodelPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kmodel").normalize();
        Path kstatesPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kstates").normalize();

        String kmetaText = Files.readString(kmetaPath, StandardCharsets.UTF_8);
        String kmodelText = Files.readString(kmodelPath, StandardCharsets.UTF_8);
        String kstatesText = Files.readString(kstatesPath, StandardCharsets.UTF_8);

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
    }

    @AfterAll
    static void tearDown() {
        BenchmarkResultExporter.exportAllReports();
    }

    @Test
    void testKMetaEngineComparison() {
        EcoreToKMetaManualSerializer directSerializer = new EcoreToKMetaManualSerializer();
        AcceleoKMetaSerializer acceleoSerializer = new AcceleoKMetaSerializer();

        // Java
        BenchmarkMetrics directMetric = new BenchmarkMetrics("SerializerComparison", "cleaning_robot.kmeta",
                "Direct Java");
        directMetric.setWarmupCount(WARMUP_ROUNDS);

        long directColdStart = System.nanoTime();
        KarpfenDslFormatter.formatKMeta(directSerializer.serialize(ePackage));
        directMetric.setColdStartMs((System.nanoTime() - directColdStart) / 1_000_000.0);

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKMeta(directSerializer.serialize(ePackage));
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String res = KarpfenDslFormatter.formatKMeta(directSerializer.serialize(ePackage));
            long duration = System.nanoTime() - start;
            assertNotNull(res);
            directMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(directMetric);

        // Acceleo
        BenchmarkMetrics acceleoMetric = new BenchmarkMetrics("SerializerComparison", "cleaning_robot.kmeta",
                "Acceleo 4");
        acceleoMetric.setWarmupCount(WARMUP_ROUNDS);

        long acceleoColdStart = System.nanoTime();
        acceleoSerializer.serialize(ePackage);
        acceleoMetric.setColdStartMs((System.nanoTime() - acceleoColdStart) / 1_000_000.0);

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            acceleoSerializer.serialize(ePackage);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String res = acceleoSerializer.serialize(ePackage);
            long duration = System.nanoTime() - start;
            assertNotNull(res);
            acceleoMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(acceleoMetric);

        // AST semantic parity check
        String directOutput = KarpfenDslFormatter.formatKMeta(directSerializer.serialize(ePackage));
        String acceleoOutput = acceleoSerializer.serialize(ePackage);
        Metamodel directAST = KmetaDSLConverter.INSTANCE.parseKmetaString(directOutput, Collections.emptyList());
        Metamodel acceleoAST = KmetaDSLConverter.INSTANCE.parseKmetaString(acceleoOutput, Collections.emptyList());
        assertEquals(directAST.getTypes().size(), acceleoAST.getTypes().size(),
                "Both engines must produce identical class count");
    }

    @Test
    void testKModelEngineComparison() {
        EcoreToKModelManualSerializer directSerializer = new EcoreToKModelManualSerializer();
        AcceleoKModelSerializer acceleoSerializer = new AcceleoKModelSerializer();

        // Java
        BenchmarkMetrics directMetric = new BenchmarkMetrics("SerializerComparison", "cleaning_robot.kmodel",
                "Direct Java");
        directMetric.setWarmupCount(WARMUP_ROUNDS);

        long directColdStart = System.nanoTime();
        KarpfenDslFormatter.formatKModel(directSerializer.serialize(modelRoot));
        directMetric.setColdStartMs((System.nanoTime() - directColdStart) / 1_000_000.0);

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKModel(directSerializer.serialize(modelRoot));
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String res = KarpfenDslFormatter.formatKModel(directSerializer.serialize(modelRoot));
            long duration = System.nanoTime() - start;
            assertNotNull(res);
            directMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(directMetric);

        // Acceleo
        BenchmarkMetrics acceleoMetric = new BenchmarkMetrics("SerializerComparison", "cleaning_robot.kmodel",
                "Acceleo 4");
        acceleoMetric.setWarmupCount(WARMUP_ROUNDS);

        long acceleoColdStart = System.nanoTime();
        acceleoSerializer.serialize(modelRoot);
        acceleoMetric.setColdStartMs((System.nanoTime() - acceleoColdStart) / 1_000_000.0);

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            acceleoSerializer.serialize(modelRoot);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String res = acceleoSerializer.serialize(modelRoot);
            long duration = System.nanoTime() - start;
            assertNotNull(res);
            acceleoMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(acceleoMetric);

        // AST semantic parity check
        String directOutput = KarpfenDslFormatter.formatKModel(directSerializer.serialize(modelRoot));
        String acceleoOutput = acceleoSerializer.serialize(modelRoot);
        Model directModel = KmodelDSLConverter.INSTANCE.parseKmodelString(directOutput, metaAST);
        Model acceleoModel = KmodelDSLConverter.INSTANCE.parseKmodelString(acceleoOutput, metaAST);
        assertEquals(directModel.getObjects().size(), acceleoModel.getObjects().size(),
                "Both engines must produce identical object count");
    }

    @Test
    void testKStatesEngineComparison() {
        EcoreToKStatesManualSerializer directSerializer = new EcoreToKStatesManualSerializer();
        AcceleoKStatesSerializer acceleoSerializer = new AcceleoKStatesSerializer();

        // Java
        BenchmarkMetrics directMetric = new BenchmarkMetrics("SerializerComparison", "cleaning_robot.kstates",
                "Direct Java");
        directMetric.setWarmupCount(WARMUP_ROUNDS);

        long directColdStart = System.nanoTime();
        KarpfenDslFormatter.formatKStates(directSerializer.serialize(statesRoot));
        directMetric.setColdStartMs((System.nanoTime() - directColdStart) / 1_000_000.0);

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            KarpfenDslFormatter.formatKStates(directSerializer.serialize(statesRoot));
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String res = KarpfenDslFormatter.formatKStates(directSerializer.serialize(statesRoot));
            long duration = System.nanoTime() - start;
            assertNotNull(res);
            directMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(directMetric);

        // Acceleo
        BenchmarkMetrics acceleoMetric = new BenchmarkMetrics("SerializerComparison", "cleaning_robot.kstates",
                "Acceleo 4");
        acceleoMetric.setWarmupCount(WARMUP_ROUNDS);

        long acceleoColdStart = System.nanoTime();
        acceleoSerializer.serialize(statesRoot);
        acceleoMetric.setColdStartMs((System.nanoTime() - acceleoColdStart) / 1_000_000.0);

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            acceleoSerializer.serialize(statesRoot);
        }
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            String res = acceleoSerializer.serialize(statesRoot);
            long duration = System.nanoTime() - start;
            assertNotNull(res);
            acceleoMetric.recordIteration(duration);
        }
        BenchmarkResultExporter.recordMetric(acceleoMetric);

        // AST semantic parity check
        String directOutput = KarpfenDslFormatter.formatKStates(directSerializer.serialize(statesRoot));
        String acceleoOutput = acceleoSerializer.serialize(statesRoot);
        StateMachine directSM = KstatesDSLConverter.INSTANCE.parseKstatesString(directOutput);
        StateMachine acceleoSM = KstatesDSLConverter.INSTANCE.parseKstatesString(acceleoOutput);
        assertEquals(directSM.getStates().size(), acceleoSM.getStates().size(),
                "Both engines must produce identical state count");
        assertEquals(directSM.getTransitions().size(), acceleoSM.getTransitions().size(),
                "Both engines must produce identical transition count");
    }
}
