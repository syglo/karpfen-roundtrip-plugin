package org.karpfen.design;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EObject;
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

        // Convert scalar -> list
        services.editKMetaEdge(hasRef, "pos [list]");
        assertEquals("pos", hasRef.getName());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, hasRef.getUpperBound());
        assertEquals("pos [list]", services.getKMetaEdgeLabel(hasRef));

        // Convert list -> scalar
        services.editKMetaEdge(hasRef, "pos");
        assertEquals("pos", hasRef.getName());
        assertEquals(1, hasRef.getUpperBound());
        assertEquals("pos", services.getKMetaEdgeLabel(hasRef));

        services.editKMetaEdge(hasRef, "position : Point");
        assertEquals("position", hasRef.getName());
        assertEquals(pointClass, hasRef.getEType());
        assertEquals(1, hasRef.getUpperBound());

        EReference knowsRef = factory.createEReference();
        knowsRef.setName("walls");
        knowsRef.setContainment(false);
        knowsRef.setUpperBound(ETypedElement.UNBOUNDED_MULTIPLICITY);
        knowsRef.setEType(wallClass);
        robotClass.getEStructuralFeatures().add(knowsRef);

        assertEquals("walls [list]", services.getKMetaEdgeLabel(knowsRef));

        services.editKMetaEdge(knowsRef, "wall");
        assertEquals("wall", knowsRef.getName());
        assertEquals(1, knowsRef.getUpperBound());
        assertEquals("wall", services.getKMetaEdgeLabel(knowsRef));

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
    void testKModelSlotMicroParsing() {
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

        services.editKModelSlot(robotObj, attr, "3.0");
        assertEquals(3.0, (Double) robotObj.eGet(speedAttr));
        assertEquals("speed = 3.0", services.getKModelSlotLabel(attr, robotObj));

        services.editKModelSlot(robotObj, attr, "prop(\"speed\") -> \"4.5\"");
        assertEquals(4.5, (Double) robotObj.eGet(speedAttr));
        assertEquals("speed = 4.5", services.getKModelSlotLabel(attr, robotObj));

        services.editKModelSlot(robotObj, attr, "prop(speed -> broken");
        assertEquals(4.5, (Double) robotObj.eGet(speedAttr));
    }
}