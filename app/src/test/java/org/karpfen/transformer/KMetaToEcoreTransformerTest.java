package org.karpfen.transformer;

import dsl.textual.KmetaDSLConverter;
import meta.Metamodel;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KMetaToEcoreTransformerTest {

    @Test
    void testKMetaToEcoreTransformation() {
        String kmetaCode = """
            type "Point" "A point in 2D space" {
                prop("x", "number")
                prop("y", "number")
            }
            type "Robot" "A cleaning robot" {
                prop("speed", "number")
                has("position", "Point")
            }
            """;
        
        Metamodel metamodel = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaCode, java.util.Collections.emptyList());
        KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();

        EPackage ePackage = transformer.transform(metamodel, "robotdomain", "http://github/karpfen", "robotdomain");

        assertNotNull(ePackage);
        EClass robotClass = (EClass) ePackage.getEClassifier("Robot");
        assertNotNull(robotClass);

        EReference positionRef = (EReference) robotClass.getEStructuralFeature("position");
        assertNotNull(positionRef);
        assertTrue(positionRef.isContainment());
        assertEquals("Point", positionRef.getEReferenceType().getName());
    }
}
