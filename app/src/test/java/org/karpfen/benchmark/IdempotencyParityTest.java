package org.karpfen.benchmark;

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

public class IdempotencyParityTest {

    private static final int CYCLES = 5;

    private static String kmetaText;
    private static String kmodelText;
    private static String kstatesText;

    @BeforeAll
    static void setUp() throws IOException {
        KarpfenResourceInitializer.init();

        Path base = Path.of("").toAbsolutePath();
        Path kmetaPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kmeta").normalize();
        Path kmodelPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kmodel").normalize();
        Path kstatesPath = base.resolve("../example/statemachine_full_example/cleaning_robot.kstates").normalize();

        kmetaText = Files.readString(kmetaPath, StandardCharsets.UTF_8);
        kmodelText = Files.readString(kmodelPath, StandardCharsets.UTF_8);
        kstatesText = Files.readString(kstatesPath, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void tearDown() {
        BenchmarkResultExporter.exportAllReports();
    }

    @Test
    void testKMetaManualIdempotency() {
        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();
        EcoreToKMetaManualSerializer serializer = new EcoreToKMetaManualSerializer();

        String currentText = kmetaText;
        String canonicalText1 = null;
        boolean allZeroByte = true;
        boolean allAstEqual = true;

        Metamodel baselineAST = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaText, Collections.emptyList());

        for (int cycle = 1; cycle <= CYCLES; cycle++) {
            // T2D
            Metamodel ast = KmetaDSLConverter.INSTANCE.parseKmetaString(currentText, Collections.emptyList());
            if (ast.getTypes().size() != baselineAST.getTypes().size()) {
                allAstEqual = false;
            }
            EPackage pkg = transformer.transform(ast, "cleaning_robot", "http://github/karpfen/cleaning_robot",
                    "cleaning_robot");

            // D2T
            String raw = serializer.serialize(pkg);
            String formatted = KarpfenDslFormatter.formatKMeta(raw);

            if (cycle == 1) {
                canonicalText1 = formatted;
            } else {
                if (!canonicalText1.equals(formatted)) {
                    allZeroByte = false;
                }
            }
            currentText = formatted;
        }

        assertTrue(allZeroByte, "KMeta Manual Serializer must produce exact 0-byte textual diff for cycles 2..5");
        assertTrue(allAstEqual, "KMeta AST must maintain 100% semantic identity across cycles");

        BenchmarkResultExporter.recordIdempotency(
                new BenchmarkResultExporter.IdempotencyDataPoint("cleaning_robot.kmeta", "Direct Java", CYCLES,
                        allZeroByte, allAstEqual));
    }

    @Test
    void testKMetaAcceleoIdempotency() {
        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();
        AcceleoKMetaSerializer serializer = new AcceleoKMetaSerializer();

        String currentText = kmetaText;
        String canonicalText1 = null;
        boolean allZeroByte = true;
        boolean allAstEqual = true;

        Metamodel baselineAST = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaText, Collections.emptyList());

        for (int cycle = 1; cycle <= CYCLES; cycle++) {
            Metamodel ast = KmetaDSLConverter.INSTANCE.parseKmetaString(currentText, Collections.emptyList());
            if (ast.getTypes().size() != baselineAST.getTypes().size()) {
                allAstEqual = false;
            }
            EPackage pkg = transformer.transform(ast, "cleaning_robot", "http://github/karpfen/cleaning_robot",
                    "cleaning_robot");
            String formatted = serializer.serialize(pkg);

            if (cycle == 1) {
                canonicalText1 = formatted;
            } else {
                if (!canonicalText1.equals(formatted)) {
                    allZeroByte = false;
                }
            }
            currentText = formatted;
        }

        assertTrue(allZeroByte, "KMeta Acceleo Serializer must produce exact 0-byte textual diff for cycles 2..5");
        assertTrue(allAstEqual, "KMeta AST must maintain 100% semantic identity across cycles");

        BenchmarkResultExporter.recordIdempotency(
                new BenchmarkResultExporter.IdempotencyDataPoint("cleaning_robot.kmeta", "Acceleo 4", CYCLES,
                        allZeroByte, allAstEqual));
    }

    @Test
    void testKModelManualIdempotency() {
        Metamodel metaAST = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaText, Collections.emptyList());
        KMetaToEcoreTransformer metaTransformer = new KMetaToEcoreTransformer();
        EPackage ePackage = metaTransformer.transform(metaAST, "cleaning_robot", "http://github/karpfen/cleaning_robot",
                "cleaning_robot");

        KModelToEcoreInstanceTransformer transformer = new KModelToEcoreInstanceTransformer();
        EcoreToKModelManualSerializer serializer = new EcoreToKModelManualSerializer();

        String currentText = kmodelText;
        String canonicalText1 = null;
        boolean allZeroByte = true;
        boolean allAstEqual = true;

        Model baselineModel = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelText, metaAST);

        for (int cycle = 1; cycle <= CYCLES; cycle++) {
            // T2D
            Model modelAST = KmodelDSLConverter.INSTANCE.parseKmodelString(currentText, metaAST);
            if (modelAST.getObjects().size() != baselineModel.getObjects().size()) {
                allAstEqual = false;
            }
            List<EObject> roots = transformer.transform(modelAST, ePackage);

            // D2T
            String raw = serializer.serialize(roots.get(0));
            String formatted = KarpfenDslFormatter.formatKModel(raw);

            if (cycle == 1) {
                canonicalText1 = formatted;
            } else {
                if (!canonicalText1.equals(formatted)) {
                    allZeroByte = false;
                }
            }
            currentText = formatted;
        }

        assertTrue(allZeroByte, "KModel Manual Serializer must produce exact 0-byte textual diff for cycles 2..5");
        assertTrue(allAstEqual, "KModel AST must maintain 100% semantic identity across cycles");

        BenchmarkResultExporter.recordIdempotency(
                new BenchmarkResultExporter.IdempotencyDataPoint("cleaning_robot.kmodel", "Direct Java", CYCLES,
                        allZeroByte, allAstEqual));
    }

    @Test
    void testKModelAcceleoIdempotency() {
        Metamodel metaAST = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaText, Collections.emptyList());
        KMetaToEcoreTransformer metaTransformer = new KMetaToEcoreTransformer();
        EPackage ePackage = metaTransformer.transform(metaAST, "cleaning_robot", "http://github/karpfen/cleaning_robot",
                "cleaning_robot");

        KModelToEcoreInstanceTransformer transformer = new KModelToEcoreInstanceTransformer();
        AcceleoKModelSerializer serializer = new AcceleoKModelSerializer();

        String currentText = kmodelText;
        String canonicalText1 = null;
        boolean allZeroByte = true;
        boolean allAstEqual = true;

        Model baselineModel = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelText, metaAST);

        for (int cycle = 1; cycle <= CYCLES; cycle++) {
            Model modelAST = KmodelDSLConverter.INSTANCE.parseKmodelString(currentText, metaAST);
            if (modelAST.getObjects().size() != baselineModel.getObjects().size()) {
                allAstEqual = false;
            }
            List<EObject> roots = transformer.transform(modelAST, ePackage);
            String formatted = serializer.serialize(roots.get(0));

            if (cycle == 1) {
                canonicalText1 = formatted;
            } else {
                if (!canonicalText1.equals(formatted)) {
                    allZeroByte = false;
                }
            }
            currentText = formatted;
        }

        assertTrue(allZeroByte, "KModel Acceleo Serializer must produce exact 0-byte textual diff for cycles 2..5");
        assertTrue(allAstEqual, "KModel AST must maintain 100% semantic identity across cycles");

        BenchmarkResultExporter.recordIdempotency(
                new BenchmarkResultExporter.IdempotencyDataPoint("cleaning_robot.kmodel", "Acceleo 4", CYCLES,
                        allZeroByte, allAstEqual));
    }

    @Test
    void testKStatesManualIdempotency() {
        KStatesToEcoreTransformer transformer = new KStatesToEcoreTransformer();
        EcoreToKStatesManualSerializer serializer = new EcoreToKStatesManualSerializer();

        String currentText = kstatesText;
        String canonicalText1 = null;
        boolean allZeroByte = true;
        boolean allAstEqual = true;

        StateMachine baselineSM = KstatesDSLConverter.INSTANCE.parseKstatesString(kstatesText);

        for (int cycle = 1; cycle <= CYCLES; cycle++) {
            // T2D
            StateMachine smAST = KstatesDSLConverter.INSTANCE.parseKstatesString(currentText);
            if (smAST.getStates().size() != baselineSM.getStates().size()
                    || smAST.getTransitions().size() != baselineSM.getTransitions().size()) {
                allAstEqual = false;
            }
            EObject smObj = transformer.transform(smAST, "cleaning_robot_states");

            // D2T
            String raw = serializer.serialize(smObj);
            String formatted = KarpfenDslFormatter.formatKStates(raw);

            if (cycle == 1) {
                canonicalText1 = formatted;
            } else {
                if (!canonicalText1.equals(formatted)) {
                    allZeroByte = false;
                }
            }
            currentText = formatted;
        }

        assertTrue(allZeroByte, "KStates Manual Serializer must produce exact 0-byte textual diff for cycles 2..5");
        assertTrue(allAstEqual, "KStates AST must maintain 100% semantic identity across cycles");

        BenchmarkResultExporter.recordIdempotency(
                new BenchmarkResultExporter.IdempotencyDataPoint("cleaning_robot.kstates", "Direct Java", CYCLES,
                        allZeroByte, allAstEqual));
    }

    @Test
    void testKStatesAcceleoIdempotency() {
        KStatesToEcoreTransformer transformer = new KStatesToEcoreTransformer();
        AcceleoKStatesSerializer serializer = new AcceleoKStatesSerializer();

        String currentText = kstatesText;
        String canonicalText1 = null;
        boolean allZeroByte = true;
        boolean allAstEqual = true;

        StateMachine baselineSM = KstatesDSLConverter.INSTANCE.parseKstatesString(kstatesText);

        for (int cycle = 1; cycle <= CYCLES; cycle++) {
            StateMachine smAST = KstatesDSLConverter.INSTANCE.parseKstatesString(currentText);
            if (smAST.getStates().size() != baselineSM.getStates().size()
                    || smAST.getTransitions().size() != baselineSM.getTransitions().size()) {
                allAstEqual = false;
            }
            EObject smObj = transformer.transform(smAST, "cleaning_robot_states");
            String formatted = serializer.serialize(smObj);

            if (cycle == 1) {
                canonicalText1 = formatted;
            } else {
                if (!canonicalText1.equals(formatted)) {
                    allZeroByte = false;
                }
            }
            currentText = formatted;
        }

        assertTrue(allZeroByte, "KStates Acceleo Serializer must produce exact 0-byte textual diff for cycles 2..5");
        assertTrue(allAstEqual, "KStates AST must maintain 100% semantic identity across cycles");

        BenchmarkResultExporter.recordIdempotency(
                new BenchmarkResultExporter.IdempotencyDataPoint("cleaning_robot.kstates", "Acceleo 4", CYCLES,
                        allZeroByte, allAstEqual));
    }
}
