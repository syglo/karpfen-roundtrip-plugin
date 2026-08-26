package org.karpfen.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.karpfen.resource.KarpfenResourceInitializer;
import org.karpfen.transformer.KMetaToEcoreTransformer;
import org.karpfen.transformer.KModelToEcoreInstanceTransformer;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KmodelDSLConverter;
import instance.Model;
import meta.Metamodel;

public class AcceleoSerializationTest {

    private static File kmetaFile;
    private static File kmodelFile;

    @BeforeAll
    static void setUp() {
        KarpfenResourceInitializer.init();

        Path base = Path.of("").toAbsolutePath();
        kmetaFile = base.resolve("../example/statemachine_full_example/cleaning_robot.kmeta").normalize()
                .toFile();
        kmodelFile = base.resolve("../example/statemachine_full_example/cleaning_robot.kmodel").normalize()
                .toFile();

        assertTrue(kmetaFile.exists(), "cleaning_robot.kmeta should exist: " + kmetaFile.getAbsolutePath());
        assertTrue(kmodelFile.exists(), "cleaning_robot.kmodel should exist: " + kmodelFile.getAbsolutePath());
    }

    @Test
    void testKMetaRoundtrip() throws IOException {
        String originalKMetaText = Files.readString(kmetaFile.toPath(), StandardCharsets.UTF_8);

        // text -> karpfen ast
        Metamodel originalAST = KmetaDSLConverter.INSTANCE.parseKmetaString(originalKMetaText,
                Collections.emptyList());
        assertEquals(6, originalAST.getTypes().size(), "Vector, TwoDObject, Obstacle, Wall, Robot, Room");

        // karpfen ast -> EMF
        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();
        EPackage ePackage = transformer.transform(originalAST, "cleaning_robot",
                "http://github/karpfen/cleaning_robot",
                "cleaning_robot");

        // EMF acceleo -> text
        AcceleoKMetaSerializer acceleoSerializer = new AcceleoKMetaSerializer();
        String generatedKMeta = acceleoSerializer.serialize(ePackage);
        assertNotNull(generatedKMeta);
        assertFalse(generatedKMeta.isBlank());

        // debug in build/test-outputs
        Path testOutputDir = Path.of("build/test-outputs");
        Files.createDirectories(testOutputDir);
        Files.writeString(testOutputDir.resolve("acceleo_cleaning_robot.kmeta"), generatedKMeta,
                StandardCharsets.UTF_8);

        // load acceleo generated text and compare asts
        Metamodel reparsedMetamodel = KmetaDSLConverter.INSTANCE.parseKmetaString(generatedKMeta,
                Collections.emptyList());
        assertEquals(originalAST.getTypes().size(), reparsedMetamodel.getTypes().size());
        assertEquals("Room", reparsedMetamodel.getRootClass().getName());

        assertNotNull(reparsedMetamodel.getTypeByName("Robot"));
        // props - log, d_closest_obstacle, d_closest_wall
        assertEquals(3, reparsedMetamodel.getTypeByName("Robot").getSimpleProperties().size());
        // has/knows
        assertEquals(6, reparsedMetamodel.getTypeByName("Robot").getObjectProperties().size());
    }

    @Test
    void testKModelRoundtrip() throws IOException {
        String originalKMetaText = Files.readString(kmetaFile.toPath(), StandardCharsets.UTF_8);
        String originalKModelText = Files.readString(kmodelFile.toPath(), StandardCharsets.UTF_8);

        // text -> karpfen ast
        Metamodel metaAST = KmetaDSLConverter.INSTANCE.parseKmetaString(originalKMetaText,
                Collections.emptyList());
        Model originalModelAST = KmodelDSLConverter.INSTANCE.parseKmodelString(originalKModelText, metaAST);

        // karpfen ast -> EMF
        KMetaToEcoreTransformer metaTransformer = new KMetaToEcoreTransformer();
        EPackage ePackage = metaTransformer.transform(metaAST, "cleaning_robot",
                "http://github/karpfen/cleaning_robot",
                "cleaning_robot");

        KModelToEcoreInstanceTransformer instanceTransformer = new KModelToEcoreInstanceTransformer();
        List<EObject> rootObjects = instanceTransformer.transform(originalModelAST, ePackage);
        assertEquals(1, rootObjects.size());

        // EMF acceleo -> text
        AcceleoKModelSerializer acceleoSerializer = new AcceleoKModelSerializer();
        String generatedKModel = acceleoSerializer.serialize(rootObjects.get(0));
        assertNotNull(generatedKModel);
        assertFalse(generatedKModel.isBlank());

        // debug in build/test-outputs
        Path testOutputDir = Path.of("build/test-outputs");
        Files.createDirectories(testOutputDir);
        Files.writeString(testOutputDir.resolve("acceleo_cleaning_robot.kmodel"), generatedKModel,
                StandardCharsets.UTF_8);

        // load acceleo generated text and compare asts
        Model reparsedModel = KmodelDSLConverter.INSTANCE.parseKmodelString(generatedKModel, metaAST);
        assertEquals(1, reparsedModel.getObjects().size());
        assertEquals("APB 2101", reparsedModel.getObjects().get(0).getId());
        assertEquals("Room", reparsedModel.getObjects().get(0).getOfType().getName());
    }
}