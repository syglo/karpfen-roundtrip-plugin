package org.karpfen.resource;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class KarpfenResourceTest {

    @BeforeAll
    static void setUp() {
        KarpfenResourceInitializer.init();
    }

    @Test
    void testDirectKmetaResourceLoading() throws IOException {
        File kmetaFile = new File("../example/metamodel_dsl_example.kmeta");
        assertTrue(kmetaFile.exists(), "Example .kmeta file must exist");

        ResourceSet resourceSet = new ResourceSetImpl();
        URI fileUri = URI.createFileURI(kmetaFile.getAbsolutePath());
        Resource resource = resourceSet.getResource(fileUri, true);
        resource.load(Collections.emptyMap());

        assertNotNull(resource);
        assertInstanceOf(KmetaResource.class, resource);
        assertEquals(1, resource.getContents().size());

        EPackage ePackage = (EPackage) resource.getContents().get(0);
        assertEquals(5, ePackage.getEClassifiers().size(), "Point, TwoDObject, Obstacle, Robot, Room");
        assertNotNull(ePackage.getEClassifier("Robot"));
    }

    @Test
    void testDirectKmodelResourceLoading() throws IOException {
        File kmetaFile = new File("../example/metamodel_dsl_example.kmeta");
        File kmodelFile = new File("../example/model_dsl_example.kmodel");

        ResourceSet resourceSet = new ResourceSetImpl();

        // Load .kmeta
        Resource metaResource = resourceSet.getResource(URI.createFileURI(kmetaFile.getAbsolutePath()), true);
        metaResource.load(Collections.emptyMap());

        // Load .kmodel
        Resource modelResource = resourceSet.getResource(URI.createFileURI(kmodelFile.getAbsolutePath()), true);
        modelResource.load(Collections.emptyMap());

        assertNotNull(modelResource);
        assertInstanceOf(KmodelResource.class, modelResource);
        assertEquals(1, modelResource.getContents().size(), "Root Room object expected");

        EObject rootRoom = modelResource.getContents().get(0);
        assertEquals("Room", rootRoom.eClass().getName());
        assertEquals("APB 2101", rootRoom.eGet(rootRoom.eClass().getEStructuralFeature("__id__")));

        EObject robot = (EObject) rootRoom.eGet(rootRoom.eClass().getEStructuralFeature("robot"));
        assertNotNull(robot);
        assertEquals("turtle", robot.eGet(robot.eClass().getEStructuralFeature("__id__")));
    }

    @Test
    void testDirectKstatesResourceLoading() throws IOException {
        File kstatesFile = new File("../example/statemachine_full_example/cleaning_robot.kstates");
        assertTrue(kstatesFile.exists(), "Example .kstates file must exist");

        ResourceSet resourceSet = new ResourceSetImpl();
        URI fileUri = URI.createFileURI(kstatesFile.getAbsolutePath());
        Resource resource = resourceSet.getResource(fileUri, true);
        resource.load(Collections.emptyMap());

        assertNotNull(resource);
        assertInstanceOf(KstatesResource.class, resource);
        assertEquals(1, resource.getContents().size());

        EObject rootSm = resource.getContents().get(0);
        assertNotNull(rootSm);
        assertEquals("StateMachine", rootSm.eClass().getName());
        assertEquals("Robot", rootSm.eGet(rootSm.eClass().getEStructuralFeature("attachedToClass")));
    }
}
