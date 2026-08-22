package org.karpfen.design;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

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
        EClass clas = factory.createEClass();
        clas.setName("OldClass");

        services.editClassName(clas, "type \"Robot\" \"\" {}");
        assertEquals("Robot", clas.getName());

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

        services.editKMetaAttribute(attr, "prop(\"speed\", \"number\")");
        assertEquals("speed", attr.getName());
        assertEquals(EcorePackage.Literals.EDOUBLE, attr.getEAttributeType());
        assertEquals(1, attr.getUpperBound());

        services.editKMetaAttribute(attr, "prop(\"logs\", list(\"string\"))");
        assertEquals("logs", attr.getName());
        assertEquals(EcorePackage.Literals.ESTRING, attr.getEAttributeType());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, attr.getUpperBound());

        services.editKMetaAttribute(attr, "invalid_unquoted_syntax without brackets");
        assertEquals("logs", attr.getName());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, attr.getUpperBound());
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
        robotClass.getEStructuralFeatures().add(hasRef);

        services.editKMetaEdge(hasRef, "has(\"position\", \"Point\")");
        assertEquals("position", hasRef.getName());
        assertEquals(pointClass, hasRef.getEType());
        assertEquals(1, hasRef.getUpperBound());

        EReference knowsRef = factory.createEReference();
        knowsRef.setName("w");
        knowsRef.setContainment(false);
        robotClass.getEStructuralFeatures().add(knowsRef);

        services.editKMetaEdge(knowsRef, "knows(\"walls\", list(\"Wall\"))");
        assertEquals("walls", knowsRef.getName());
        assertEquals(wallClass, knowsRef.getEType());
        assertEquals(ETypedElement.UNBOUNDED_MULTIPLICITY, knowsRef.getUpperBound());

        services.editKMetaEdge(knowsRef, "knows(unclosed syntax");
        assertEquals("walls", knowsRef.getName());
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

        services.editKModelObjectHeader(robotObj, "\"turtle_v2\":\"Robot\"");
        assertEquals("turtle_v2", robotObj.eGet(idAttr));

        services.editKModelObjectHeader(robotObj, "turtle_final");
        assertEquals("turtle_final", robotObj.eGet(idAttr));

        services.editKModelObjectHeader(robotObj, "make object unclosed");
        assertEquals("turtle_final", robotObj.eGet(idAttr));
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
        assertEquals("prop(\"speed\") -> \"1.0\"", services.getKModelSlotLabel(attr, robotObj));

        services.editKModelSlot(robotObj, attr, "prop(\"speed\") -> \"2.5\"");
        assertEquals(2.5, (Double) robotObj.eGet(speedAttr));
        assertEquals("prop(\"speed\") -> \"2.5\"", services.getKModelSlotLabel(attr, robotObj));

        services.editKModelSlot(robotObj, attr, "3.0");
        assertEquals(3.0, (Double) robotObj.eGet(speedAttr));

        services.editKModelSlot(robotObj, attr, "prop(speed -> broken");
        assertEquals(3.0, (Double) robotObj.eGet(speedAttr));
    }
}