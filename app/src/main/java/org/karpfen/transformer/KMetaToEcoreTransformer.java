package org.karpfen.transformer;

import meta.*;

import org.eclipse.emf.ecore.*;

import java.util.HashMap;
import java.util.Map;

public class KMetaToEcoreTransformer {

    private final EcoreFactory factory = EcoreFactory.eINSTANCE;

    public EPackage transform(Metamodel kMetamodel, String packageName, String nsUri, String nsPrefix) {
        EPackage ePackage = factory.createEPackage();
        ePackage.setName(packageName);
        ePackage.setNsURI(nsUri);
        ePackage.setNsPrefix(nsPrefix);

        Map<String, EClass> eClassMap = new HashMap<>();

        // EClass - ClassTypes
        for (ClassType classType : kMetamodel.getTypes()) {
            EClass eClass = factory.createEClass();
            eClass.setName(classType.getName());
            ePackage.getEClassifiers().add(eClass);
            eClassMap.put(classType.getName(), eClass);
        }

        // EAttributes and EReferences
        for (ClassType classType : kMetamodel.getTypes()) {
            EClass eClass = eClassMap.get(classType.getName());

            // Map prop -> EAttribute
            for (SimpleProperty prop : classType.getSimpleProperties()) {
                EAttribute attribute = factory.createEAttribute();
                attribute.setName(prop.getKey());
                attribute.setEType(mapDataType(prop.getPropertyType()));
                attribute.setUpperBound(prop.isList() ? ETypedElement.UNBOUNDED_MULTIPLICITY : 1);
                eClass.getEStructuralFeatures().add(attribute);
            }
        }

        return ePackage;
    }

    // Helper
    private EDataType mapDataType(SimplePropertyType type) {
        return switch (type) {
            case NUMBER -> EcorePackage.Literals.EDOUBLE;
            case BOOLEAN -> EcorePackage.Literals.EBOOLEAN;
            case STRING -> EcorePackage.Literals.ESTRING;
        };
    }
}
