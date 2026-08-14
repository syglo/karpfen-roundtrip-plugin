package org.karpfen.transformer;

import dsl.textual.KmetaDSLConverter;
import meta.Metamodel;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class KMetaToEcoreTransformerTest {

    private static KMetaToEcoreTransformer transformer;

    @BeforeAll
    static void setUp() {
        transformer = new KMetaToEcoreTransformer();
    }

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
                prop("active", "boolean")
                has("position", "Point")
                knows("obstacles", list("Obstacle"))
            }
            """;
        
        Metamodel metamodel = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaCode, java.util.Collections.emptyList());
        assertNotNull(metamodel, "Metamodel AST should parse successfully");

        EPackage ePackage = transformer.transform(metamodel, "robotdomain", "http://github/karpfen", "robotdomain");
        assertNotNull(ePackage);
        assertEquals("robotdomain", ePackage.getName());
        assertEquals(3, ePackage.getEClassifiers().size());

        // EClass check
        EClass robotClass = (EClass) ePackage.getEClassifier("Robot");
        assertNotNull(robotClass);
        EAnnotation docAnnotation = robotClass.getEAnnotation("https://eclipse/emf/GenModel");
        assertNotNull(docAnnotation);
        assertEquals("A cleaning robot", docAnnotation.getDetails().get("documentation"));

        // Primitives
        EAttribute speedAttr = (EAttribute) robotClass.getEStructuralFeature("speed");
        assertNotNull(speedAttr);
        assertEquals(EcorePackage.Literals.EDOUBLE, speedAttr.getEAttributeType());
        assertEquals(1, speedAttr.getUpperBound());

        EAttribute activeAttr = (EAttribute) robotClass.getEStructuralFeature("active");
        assertNotNull(activeAttr);
        assertEquals(EcorePackage.Literals.EBOOLEAN, activeAttr.getEAttributeType());

        // List Primitives
        EClass obstacleClass = (EClass) ePackage.getEClassifier("Obstacle");
        EAttribute tagsAttr = (EAttribute) obstacleClass.getEStructuralFeature("tags");
        assertNotNull(tagsAttr);
        assertEquals(EcorePackage.Literals.ESTRING, tagsAttr.getEAttributeType());
        assertEquals(-1, tagsAttr.getUpperBound(), "list() should have unbounded multiplicity -1");

        // Embedded has
        EReference positionRef = (EReference) robotClass.getEStructuralFeature("position");
        assertNotNull(positionRef);
        assertTrue(positionRef.isContainment(), "has relationship must be mapped to containment reference");
        assertEquals("Point", positionRef.getEReferenceType().getName());
        assertEquals(1, positionRef.getUpperBound());

        // knows
        EReference obstacleRef = (EReference) robotClass.getEStructuralFeature("obstacles");
        assertNotNull(obstacleRef);
        assertFalse(obstacleRef.isContainment(), "knows relationship must be mapped to noncontainment reference");
        assertEquals("Obstacle", obstacleRef.getEReferenceType().getName());
        assertEquals(-1, obstacleRef.getUpperBound(), "list() unbounded multiplicity -1");
    }

    @Test
    void testEcoreFileSerialization() throws IOException {
        String kmetaFilePath = "../example/metamodel_dsl_example.kmeta";
        Metamodel metamodel = KmetaDSLConverter.INSTANCE.parseKmetaFile(kmetaFilePath);

        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();
        EPackage ePackage = transformer.transform(metamodel, "robotdomain", "http://github/karpfen", "robotdomain");

        // Tmp dir local to project
        File outputDir = new File("build/test-outputs");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File targetFile = new File(outputDir, "robotdomain.ecore");
        transformer.saveToEcoreFile(ePackage, targetFile);

        assertTrue(targetFile.exists());
        assertTrue(targetFile.length() > 0);

        System.out.println("File serialization tmp artifact: " + targetFile.getAbsolutePath());
    }
}
