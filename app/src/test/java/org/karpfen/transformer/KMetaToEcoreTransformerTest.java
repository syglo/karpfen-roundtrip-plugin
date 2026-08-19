package org.karpfen.transformer;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KmodelDSLConverter;
import instance.Model;
import meta.Metamodel;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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
        String kmodelFilePath = "../example/model_dsl_example.kmodel";

        Metamodel metamodel = KmetaDSLConverter.INSTANCE.parseKmetaFile(kmetaFilePath);
        assertNotNull(metamodel, "Karpfen Metamodel AST must not be null");

        Model model = KmodelDSLConverter.INSTANCE.parseKmodelFile(kmodelFilePath, metamodel);
        assertNotNull(model, "Karpfen Metamodel instance AST must not be null");

        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();
        EPackage ePackage = transformer.transform(metamodel, "roomdomain", "http://github/karpfen", "roomdomain");

        // Tmp dir local to project
        File outputDir = new File("build/test-outputs");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File targetFile = new File(outputDir, "roomdomain.ecore");
        transformer.saveToEcoreFile(ePackage, targetFile);
        assertTrue(targetFile.exists() && targetFile.length() > 0);

        // Transform model to emf eobjects and serialize .xmi instance
        KModelToEcoreInstanceTransformer instanceTransformer = new KModelToEcoreInstanceTransformer();
        List<EObject> rootObjects = instanceTransformer.transform(model, ePackage);
        assertEquals(1, rootObjects.size(), "Room APB 2101 should be single root object");

        File xmiTargetFile = new File(outputDir, "roomdomain_instance.xmi");
        instanceTransformer.saveToXmiFile(rootObjects, ePackage, xmiTargetFile);

        assertTrue(xmiTargetFile.exists(), "Target .xmi file should exists");
        assertTrue(xmiTargetFile.length() > 0, "Serialize .xmi should not be empty");

        System.out.println("[Karpfen] Metamodel .ecore generated at: " + targetFile.getAbsolutePath());
        System.out.println("[Karpfen] Instance .xmi generated at: " + xmiTargetFile.getAbsolutePath());
    }
}
