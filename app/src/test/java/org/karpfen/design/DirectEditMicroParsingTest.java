package org.karpfen.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DirectEditMicroParsingTest {

    private KarpfenDiagramServices services;
    private EcoreFactory factory;

    @BeforeEach
    void setUp() {
        services = new KarpfenDiagramServices();
        factory = EcoreFactory.eINSTANCE;
    }

    @Test
    void testKMetaClassMicroParsing() {
        EPackage pkg = factory.createEPackage();
        EClass clas = factory.createEClass();
        clas.setName("Room");
        pkg.getEClassifiers().add(clas);

        EAnnotation ann = factory.createEAnnotation();
        ann.setSource(KarpfenDiagramServices.KARPFEN_URI);
        ann.getDetails().put("rootClass", "Room");
        pkg.getEAnnotations().add(ann);

        assertEquals("<root> Room", services.getKMetaClassLabel(clas));

        services.editClassName(clas, "<root> LivingRoom");
        assertEquals("LivingRoom", clas.getName());
        assertEquals("LivingRoom", ann.getDetails().get("rootClass"));
        assertEquals("<root> LivingRoom", services.getKMetaClassLabel(clas));

        services.editClassName(clas, "Obstacle");
        assertEquals("Obstacle", clas.getName());

        services.editClassName(clas, "type \"Unclosed_Quote");
        assertEquals("Obstacle", clas.getName());
    }

    @Test
    void testKMetaAttributeMicroParsing() {
        EAttribute attr = factory.createEAttribute();
        attr.setName("temp");
        attr.setEType(EcorePackage.Literals.ESTRING);
        attr.setUpperBound(1);

        services.editKMetaAttribute(attr, "speed : number");
        assertEquals("speed", attr.getName());
        assertEquals(EcorePackage.Literals.EDOUBLE, attr.getEAttributeType());
        assertEquals(1, attr.getUpperBound());
        assertEquals("speed : number", services.getKMetaAttributeLabel(attr));

        services.editKMetaAttribute(attr, "speed [list]");
        assertEquals("speed", attr.getName());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, attr.getUpperBound());
        assertEquals("speed : list(\"number\")", services.getKMetaAttributeLabel(attr));

        services.editKMetaAttribute(attr, "speed");
        assertEquals("speed", attr.getName());
        assertEquals(1, attr.getUpperBound());
        assertEquals("speed : number", services.getKMetaAttributeLabel(attr));

        services.editKMetaAttribute(attr, "tags : list(\"string\")");
        assertEquals("tags", attr.getName());
        assertEquals(EcorePackage.Literals.ESTRING, attr.getEAttributeType());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, attr.getUpperBound());
        assertEquals("tags : list(\"string\")", services.getKMetaAttributeLabel(attr));

        services.editKMetaAttribute(attr, "invalid_unquoted_syntax without brackets");
        assertEquals("tags", attr.getName());
    }

    @Test
    void testKMetaEdgeMicroParsing() {
        EPackage pkg = factory.createEPackage();
        EClass robotClass = factory.createEClass();
        robotClass.setName("Robot");
        EClass pointClass = factory.createEClass();
        pointClass.setName("Point");
        EClass wallClass = factory.createEClass();
        wallClass.setName("Wall");
        pkg.getEClassifiers().add(robotClass);
        pkg.getEClassifiers().add(pointClass);
        pkg.getEClassifiers().add(wallClass);

        EReference hasRef = factory.createEReference();
        hasRef.setName("pos");
        hasRef.setContainment(true);
        hasRef.setEType(pointClass);
        hasRef.setUpperBound(1);
        robotClass.getEStructuralFeatures().add(hasRef);

        assertEquals("pos", services.getKMetaEdgeLabel(hasRef));
        assertEquals("1", services.getKMetaEdgeEndLabel(hasRef));

        // Convert scalar -> list using Karpfen [list]
        services.editKMetaEdge(hasRef, "pos [list]");
        assertEquals("pos", hasRef.getName());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, hasRef.getUpperBound());
        assertEquals("pos", services.getKMetaEdgeLabel(hasRef));
        assertEquals("*", services.getKMetaEdgeEndLabel(hasRef));

        // Convert list -> scalar
        services.editKMetaEdge(hasRef, "pos");
        assertEquals("pos", hasRef.getName());
        assertEquals(1, hasRef.getUpperBound());
        assertEquals("pos", services.getKMetaEdgeLabel(hasRef));
        assertEquals("1", services.getKMetaEdgeEndLabel(hasRef));

        // Convert scalar -> list using UML 2.5 [*] notation
        services.editKMetaEdge(hasRef, "pos[*]");
        assertEquals("pos", hasRef.getName());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, hasRef.getUpperBound());
        assertEquals("pos", services.getKMetaEdgeLabel(hasRef));
        assertEquals("*", services.getKMetaEdgeEndLabel(hasRef));

        services.editKMetaEdge(hasRef, "position : Point");
        assertEquals("position", hasRef.getName());
        assertEquals(pointClass, hasRef.getEType());
        assertEquals(1, hasRef.getUpperBound());
        assertEquals("1", services.getKMetaEdgeEndLabel(hasRef));

        // Direct-edit end label with raw multiplicity tokens
        services.editKMetaEdge(hasRef, "*");
        assertEquals("position", hasRef.getName());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, hasRef.getUpperBound());
        assertEquals("*", services.getKMetaEdgeEndLabel(hasRef));

        services.editKMetaEdge(hasRef, "1");
        assertEquals("position", hasRef.getName());
        assertEquals(1, hasRef.getUpperBound());
        assertEquals("1", services.getKMetaEdgeEndLabel(hasRef));

        // Direct-edit middle label with trailing multiplicity suffix
        // e.g. pos *, pos 1, pos [1]
        services.editKMetaEdge(hasRef, "pos *");
        assertEquals("pos", hasRef.getName());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, hasRef.getUpperBound());
        assertEquals("pos", services.getKMetaEdgeLabel(hasRef));
        assertEquals("*", services.getKMetaEdgeEndLabel(hasRef));

        services.editKMetaEdge(hasRef, "pos 1");
        assertEquals("pos", hasRef.getName());
        assertEquals(1, hasRef.getUpperBound());
        assertEquals("pos", services.getKMetaEdgeLabel(hasRef));
        assertEquals("1", services.getKMetaEdgeEndLabel(hasRef));

        services.editKMetaEdge(hasRef, "pos [1]");
        assertEquals("pos", hasRef.getName());
        assertEquals(1, hasRef.getUpperBound());
        assertEquals("1", services.getKMetaEdgeEndLabel(hasRef));

        services.editKMetaEdge(hasRef, "pos [list]");
        assertEquals("pos", hasRef.getName());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, hasRef.getUpperBound());
        assertEquals("*", services.getKMetaEdgeEndLabel(hasRef));

        services.editKMetaEdge(hasRef, "position : Point *");
        assertEquals("position", hasRef.getName());
        assertEquals(pointClass, hasRef.getEType());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, hasRef.getUpperBound());
        assertEquals("position", services.getKMetaEdgeLabel(hasRef));
        assertEquals("*", services.getKMetaEdgeEndLabel(hasRef));

        services.editKMetaEdge(hasRef, "position : Point 1");
        assertEquals("position", hasRef.getName());
        assertEquals(pointClass, hasRef.getEType());
        assertEquals(1, hasRef.getUpperBound());
        assertEquals("position", services.getKMetaEdgeLabel(hasRef));
        assertEquals("1", services.getKMetaEdgeEndLabel(hasRef));

        EReference knowsRef = factory.createEReference();
        knowsRef.setName("walls");
        knowsRef.setContainment(false);
        knowsRef.setUpperBound(ETypedElement.UNBOUNDED_MULTIPLICITY);
        knowsRef.setEType(wallClass);
        robotClass.getEStructuralFeatures().add(knowsRef);

        assertEquals("walls", services.getKMetaEdgeLabel(knowsRef));
        assertEquals("*", services.getKMetaEdgeEndLabel(knowsRef));

        services.editKMetaEdge(knowsRef, "wall");
        assertEquals("wall", knowsRef.getName());
        assertEquals(1, knowsRef.getUpperBound());
        assertEquals("wall", services.getKMetaEdgeLabel(knowsRef));
        assertEquals("0..1", services.getKMetaEdgeEndLabel(knowsRef));

        services.editKMetaEdge(knowsRef, "knows(unclosed syntax");
        assertEquals("wall", knowsRef.getName());
    }

    @Test
    void testKModelObjectHeaderMicroParsing() {
        EClass robotClass = factory.createEClass();
        robotClass.setName("Robot");

        EAttribute idAttr = factory.createEAttribute();
        idAttr.setName(KarpfenDiagramServices.ID_FEATURE_NAME);
        idAttr.setEType(EcorePackage.Literals.ESTRING);
        robotClass.getEStructuralFeatures().add(idAttr);

        EPackage pkg = factory.createEPackage();
        pkg.getEClassifiers().add(robotClass);

        EObject robotObj = pkg.getEFactoryInstance().create(robotClass);
        robotObj.eSet(idAttr, "turtle");

        assertEquals("turtle : Robot", services.getObjectHeaderLabel(robotObj));

        services.editKModelObjectHeader(robotObj, "turtle_v2 : Robot");
        assertEquals("turtle_v2", robotObj.eGet(idAttr));
        assertEquals("turtle_v2 : Robot", services.getObjectHeaderLabel(robotObj));

        services.editKModelObjectHeader(robotObj, "turtle_final");
        assertEquals("turtle_final", robotObj.eGet(idAttr));

        services.editKModelObjectHeader(robotObj, "make object \"turtle_dsl\":\"Robot\"");
        assertEquals("turtle_dsl", robotObj.eGet(idAttr));

        services.editKModelObjectHeader(robotObj, "make object unclosed");
        assertEquals("turtle_dsl", robotObj.eGet(idAttr));
    }

    @Test
    void testKModelSlotMicroParsingAndUnset() {
        EClass robotClass = factory.createEClass();
        robotClass.setName("Robot");

        EAttribute speedAttr = factory.createEAttribute();
        speedAttr.setName("speed");
        speedAttr.setEType(EcorePackage.Literals.EDOUBLE);
        robotClass.getEStructuralFeatures().add(speedAttr);

        EPackage pkg = factory.createEPackage();
        pkg.getEClassifiers().add(robotClass);

        EObject robotObj = pkg.getEFactoryInstance().create(robotClass);
        robotObj.eSet(speedAttr, 1.0);

        List<EAttribute> attrs = services.getPopulatedAttributes(robotObj);
        assertEquals(1, attrs.size());
        EAttribute attr = attrs.get(0);
        assertEquals("speed = 1.0", services.getKModelSlotLabel(attr, robotObj));

        services.editKModelSlot(robotObj, attr, "speed = 2.5");
        assertEquals(2.5, (Double) robotObj.eGet(speedAttr));
        assertEquals("speed = 2.5", services.getKModelSlotLabel(attr, robotObj));

        // Test unsetting slot when user types 'speed = <unset>'
        services.editKModelSlot(robotObj, attr, "speed = <unset>");
        assertFalse(robotObj.eIsSet(speedAttr));
        assertEquals("speed = <unset>", services.getKModelSlotLabel(attr, robotObj));

        // Test assigning value back from unset
        services.editKModelSlot(robotObj, attr, "4.5");
        assertTrue(robotObj.eIsSet(speedAttr));
        assertEquals(4.5, (Double) robotObj.eGet(speedAttr));
        assertEquals("speed = 4.5", services.getKModelSlotLabel(attr, robotObj));

        // Test unsetting slot when user types bare '<unset>'
        services.editKModelSlot(robotObj, attr, "<unset>");
        assertFalse(robotObj.eIsSet(speedAttr));
        assertEquals("speed = <unset>", services.getKModelSlotLabel(attr, robotObj));
    }

    @Test
    void testKModelMultiFeatureCommaDirectEditAndReconciliation() {
        EPackage pkg = factory.createEPackage();
        pkg.setName("roomdomain");

        EClass robotClass = factory.createEClass();
        robotClass.setName("Robot");
        pkg.getEClassifiers().add(robotClass);

        EClass wallClass = factory.createEClass();
        wallClass.setName("Wall");
        pkg.getEClassifiers().add(wallClass);

        EClass obstacleClass = factory.createEClass();
        obstacleClass.setName("Obstacle");
        pkg.getEClassifiers().add(obstacleClass);

        // ID attributes
        for (org.eclipse.emf.ecore.EClassifier c : pkg.getEClassifiers()) {
            if (c instanceof EClass ec) {
                EAttribute id = factory.createEAttribute();
                id.setName(KarpfenDiagramServices.ID_FEATURE_NAME);
                id.setEType(EcorePackage.Literals.ESTRING);
                ec.getEStructuralFeatures().add(id);
            }
        }

        // Robot knows closest_wall (scalar Wall) and walls (list Wall)
        EReference knowsClosestWall = factory.createEReference();
        knowsClosestWall.setName("closest_wall");
        knowsClosestWall.setContainment(false);
        knowsClosestWall.setUpperBound(1);
        knowsClosestWall.setEType(wallClass);
        robotClass.getEStructuralFeatures().add(knowsClosestWall);

        EReference knowsWalls = factory.createEReference();
        knowsWalls.setName("walls");
        knowsWalls.setContainment(false);
        knowsWalls.setUpperBound(ETypedElement.UNBOUNDED_MULTIPLICITY);
        knowsWalls.setEType(wallClass);
        robotClass.getEStructuralFeatures().add(knowsWalls);

        // Robot knows closest_obstacle (scalar Obstacle) and obstacles (list Obstacle)
        EReference knowsClosestObstacle = factory.createEReference();
        knowsClosestObstacle.setName("closest_obstacle");
        knowsClosestObstacle.setContainment(false);
        knowsClosestObstacle.setUpperBound(1);
        knowsClosestObstacle.setEType(obstacleClass);
        robotClass.getEStructuralFeatures().add(knowsClosestObstacle);

        EReference knowsObstacles = factory.createEReference();
        knowsObstacles.setName("obstacles");
        knowsObstacles.setContainment(false);
        knowsObstacles.setUpperBound(ETypedElement.UNBOUNDED_MULTIPLICITY);
        knowsObstacles.setEType(obstacleClass);
        robotClass.getEStructuralFeatures().add(knowsObstacles);

        EObject robot = pkg.getEFactoryInstance().create(robotClass);
        robot.eSet(robotClass.getEStructuralFeature(KarpfenDiagramServices.ID_FEATURE_NAME), "turtle");

        EObject wall1 = pkg.getEFactoryInstance().create(wallClass);
        wall1.eSet(wallClass.getEStructuralFeature(KarpfenDiagramServices.ID_FEATURE_NAME), "wall_top");

        EObject chair = pkg.getEFactoryInstance().create(obstacleClass);
        chair.eSet(obstacleClass.getEStructuralFeature(KarpfenDiagramServices.ID_FEATURE_NAME), "chair");

        // Robot linking to Wall - scalar closest_wall
        services.createInstanceLink(robot, wall1, false);
        assertSame(wall1, robot.eGet(knowsClosestWall));
        assertEquals("closest_wall", services.getInstanceReferenceLabel(robot, wall1));

        // Direct-Edit bind multi features (sirius problem) on Wall - closest_wall,
        // walls
        services.editInstanceEdge(robot, wall1, "closest_wall, walls", false);
        assertTrue(robot.eIsSet(knowsClosestWall));
        assertSame(wall1, robot.eGet(knowsClosestWall));
        @SuppressWarnings("unchecked")
        List<EObject> wallsList = (List<EObject>) robot.eGet(knowsWalls);
        assertTrue(wallsList.contains(wall1));
        assertEquals("closest_wall, walls", services.getInstanceReferenceLabel(robot, wall1));

        // Double link on Obstacle - closest_obstacle, obstacles
        services.createInstanceLink(robot, chair, false);
        services.editInstanceEdge(robot, chair, "closest_obstacle, obstacles", false);
        assertTrue(robot.eIsSet(knowsClosestObstacle));
        assertSame(chair, robot.eGet(knowsClosestObstacle));
        @SuppressWarnings("unchecked")
        List<EObject> obsList = (List<EObject>) robot.eGet(knowsObstacles);
        assertTrue(obsList.contains(chair));
        assertEquals("closest_obstacle, obstacles", services.getInstanceReferenceLabel(robot, chair));

        // Type safety when linking, when renaming edge on walls to "obstacles"
        // throws errors, expecting obstacle instead of wall
        // Must reject to prevent corruption of graphical model diagram
        services.editInstanceEdge(robot, wall1, "obstacles", false);
        assertTrue(robot.eIsSet(knowsClosestWall));
        assertTrue(wallsList.contains(wall1));
        assertFalse(obsList.contains(wall1));
        assertEquals("closest_wall, walls", services.getInstanceReferenceLabel(robot, wall1));

        // Must reject edit to prevent model type mismatch corruption
        // Type safety when renaming edge from chair (Obstacle) to walls
        // must reject, expects wall not obstacle
        services.editInstanceEdge(robot, chair, "walls", false);
        assertTrue(robot.eIsSet(knowsClosestObstacle));
        assertTrue(obsList.contains(chair));
        assertFalse(wallsList.contains(chair));
        assertEquals("closest_obstacle, obstacles", services.getInstanceReferenceLabel(robot, chair));

        // Direct-Edit rebind to single feature "walls"
        // From "closest_wall, walls" to just "walls"
        services.editInstanceEdge(robot, wall1, "walls", false);
        assertFalse(robot.eIsSet(knowsClosestWall));
        assertTrue(wallsList.contains(wall1));
        assertEquals("walls", services.getInstanceReferenceLabel(robot, wall1));

        // Direct-Edit to "<unset>" should clear all links to wall
        services.editInstanceEdge(robot, wall1, "<unset>", false);
        assertFalse(robot.eIsSet(knowsClosestWall));
        assertFalse(wallsList.contains(wall1));
        assertEquals("knows", services.getInstanceReferenceLabel(robot, wall1));

        // Deleting of multiple edge with multi features
        services.editInstanceEdge(robot, wall1, "closest_wall, walls", false);
        assertEquals("closest_wall, walls", services.getInstanceReferenceLabel(robot, wall1));
        services.deleteInstanceLink(robot, wall1, false);
        assertFalse(robot.eIsSet(knowsClosestWall));
        assertFalse(wallsList.contains(wall1));
    }

    @Test
    void testKMetaDocumentationLookup() {
        EClass robotClass = factory.createEClass();
        robotClass.setName("Robot");

        // Initially no documentation
        assertEquals("", services.getKMetaDocumentation(robotClass));

        // Add GenModel documentation annotation
        org.eclipse.emf.ecore.EAnnotation ann = factory.createEAnnotation();
        ann.setSource("https://eclipse/emf/GenModel");
        ann.getDetails().put("documentation", "A cleaning robot that navigates autonomously.");
        robotClass.getEAnnotations().add(ann);

        assertEquals("A cleaning robot that navigates autonomously.", services.getKMetaDocumentation(robotClass));
    }

    @Test
    void testKStatesEditStateName() {
        EClass stateClass = factory.createEClass();
        stateClass.setName("State");
        EAttribute nameAttr = factory.createEAttribute();
        nameAttr.setName("name");
        nameAttr.setEType(EcorePackage.Literals.ESTRING);
        stateClass.getEStructuralFeatures().add(nameAttr);

        EAttribute initAttr = factory.createEAttribute();
        initAttr.setName("isInitial");
        initAttr.setEType(EcorePackage.Literals.EBOOLEAN);
        stateClass.getEStructuralFeatures().add(initAttr);

        EPackage pkg = factory.createEPackage();
        pkg.getEClassifiers().add(stateClass);

        EObject state = pkg.getEFactoryInstance().create(stateClass);
        state.eSet(nameAttr, "obstacle_state");

        services.editStateName(state, "react to obstacle");
        assertEquals("react to obstacle", state.eGet(nameAttr));

        services.editStateName(state, "<initial> drive fast");
        assertEquals("drive fast", state.eGet(nameAttr));
        assertTrue((Boolean) state.eGet(initAttr));
    }
}