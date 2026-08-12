package org.karpfen.transformer;

import dsl.textual.KmetaDSLConverter;
import meta.Metamodel;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class KMetaToEcoreTransformerTest {

    @Test
    void testKMetaToEcoreTransformation() {
        String kmetaCode = """
            type "Point" "A point in 2D space" {
                prop("x", "number")
                prop("y", "number")
            }
            type "Obstacle" "An obstacle in the room" {
                prop("tags", list("string"))
            }
            type "Robot" "A cleaning robot" {
                prop("speed", "number")
                has("position", "Point")
                knows("obstacles", list("Obstacle"))
            }
            """;
        
        Metamodel metamodel = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaCode, java.util.Collections.emptyList());
        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();

        EPackage ePackage = transformer.transform(metamodel, "robotdomain", "http://github/karpfen", "robotdomain");
        assertNotNull(ePackage);
        assertEquals("robotdomain", ePackage.getName());

        EClass robotClass = (EClass) ePackage.getEClassifier("Robot");
        assertNotNull(robotClass);
        assertEquals("A cleaning robot", robotClass.getEAnnotations().get(0).getDetails().get("documentation"));

        // Primitives
        EAttribute speedAttr = (EAttribute) robotClass.getEStructuralFeature("speed");
        assertNotNull(speedAttr);
        assertEquals(EcorePackage.Literals.EDOUBLE, speedAttr.getEAttributeType());
        assertEquals(1, speedAttr.getUpperBound());

        // List Primitives
        EClass obstacleClass = (EClass) ePackage.getEClassifier("Obstacle");
        EAttribute tagsAttr = (EAttribute) obstacleClass.getEStructuralFeature("tags");
        assertEquals(-1, tagsAttr.getUpperBound());

        // Embedded has
        EReference positionRef = (EReference) robotClass.getEStructuralFeature("position");
        assertNotNull(positionRef);
        assertTrue(positionRef.isContainment());
        assertEquals("Point", positionRef.getEReferenceType().getName());
        assertEquals(1, positionRef.getUpperBound());

        // knows
        EReference obstacleRef = (EReference) robotClass.getEStructuralFeature("obstacles");
        assertNotNull(obstacleRef);
        assertFalse(obstacleRef.isContainment());
        assertEquals("Obstacle", obstacleRef.getEReferenceType().getName());
        assertEquals(-1, obstacleRef.getUpperBound());
    }

    @Test
    void testFileSerialization(@TempDir Path tempDir) throws IOException {
        String kmetaFilePath = "../example/metamodel_dsl_example.kmeta";
        Metamodel metamodel = KmetaDSLConverter.INSTANCE.parseKmetaFile(kmetaFilePath);

        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();
        EPackage ePackage = transformer.transform(metamodel, "robotdomain", "http://github/karpfen", "robotdomain");

        File targetFile = tempDir.resolve("robotdomain.ecore").toFile();
        transformer.saveToEcoreFile(ePackage, targetFile);

        assertTrue(targetFile.exists());
        assertTrue(targetFile.length() > 0);
    }
}
