package org.karpfen.transformer;

import meta.*;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class KMetaToEcoreTransformer {

    private static final String GENMODEL_ANNOTATION_URI = "https://eclipse/emf/GenModel";
    private static final String KARPFEN_ANNOTATION_URI = "https://github/karpfen/annotation";

    private final EcoreFactory factory = EcoreFactory.eINSTANCE;

    public static final String ID_FEATURE_NAME = "__id__";

    // Lookup caches T2M D2T
    private final Map<String, EClass> eClassMap = new HashMap<>();
    private final Map<String, EStructuralFeature> featureMap = new HashMap<>();

    public EPackage transform(Metamodel kMetamodel, String packageName, String nsUri, String nsPrefix) {
        eClassMap.clear();
        featureMap.clear();

        EPackage ePackage = factory.createEPackage();
        ePackage.setName(packageName);
        ePackage.setNsURI(nsUri);
        ePackage.setNsPrefix(nsPrefix);

        // Mark designated root class (annotation in EPackage)
        if (kMetamodel.getRootClass() != null) {
            EAnnotation rootAnnotation = factory.createEAnnotation();
            rootAnnotation.setSource(KARPFEN_ANNOTATION_URI);
            rootAnnotation.getDetails().put("rootClass", kMetamodel.getRootClass().getName());
            ePackage.getEAnnotations().add(rootAnnotation);
        }

        // ClassTypes to EClass
        for (ClassType classType : kMetamodel.getTypes()) {
            EClass eClass = factory.createEClass();
            eClass.setName(classType.getName());

            // !! VERY IMPORTANT fix for kmodel, to have ability create new View KModel
            // Diagramm
            eClass.getESuperTypes().add(EcorePackage.Literals.EOBJECT);

            // Synthetic instance identifier attributes (helps later)
            EAttribute idAttr = factory.createEAttribute();
            idAttr.setName(ID_FEATURE_NAME);
            idAttr.setEType(EcorePackage.Literals.ESTRING);
            idAttr.setID(true); // EMF unique intrinsic ID
            idAttr.setDefaultValue("");
            idAttr.setUnsettable(true);
            eClass.getEStructuralFeatures().add(idAttr);

            // Map doc comments to EMF GenModel annotations - visual tooltips in IDE
            if (classType.getComment() != null && !classType.getComment().isBlank()) {
                addDocumentationAnnotation(eClass, classType.getComment());
            }

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
                attribute.setUnsettable(true);

                eClass.getEStructuralFeatures().add(attribute);
                featureMap.put(classType.getName() + "." + prop.getKey(), attribute);
            }

            // Map has / knows -> EReference
            for (ClassTypeProperty rel : classType.getObjectProperties()) {
                EReference reference = factory.createEReference();
                reference.setName(rel.getKey());

                EClass targetClass = eClassMap.get(rel.getReference().getClassTypeName());
                if (targetClass == null) {
                    throw new IllegalArgumentException(
                            "Target class '" + rel.getReference().getClassTypeName() + "' not found in metamodel.");
                }
                reference.setEType(targetClass);

                // if true then has = EMBEDDED, if false then knows = LINK
                boolean isContainment = (rel.getAssociationType() == AssociationType.EMBEDDED); // or LINK
                reference.setContainment(isContainment);
                reference.setUpperBound(rel.isList() ? ETypedElement.UNBOUNDED_MULTIPLICITY : 1);

                eClass.getEStructuralFeatures().add(reference);
                featureMap.put(classType.getName() + "." + rel.getKey(), reference);
            }
        }

        return ePackage;
    }

    public void saveToEcoreFile(EPackage ePackage, File outputFile) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());

        URI fileUri = URI.createFileURI(outputFile.getAbsolutePath());
        Resource resource = resourceSet.createResource(fileUri);
        resource.getContents().add(ePackage);

        resource.save(Collections.emptyMap());
    }

    // Helpers
    private EDataType mapDataType(SimplePropertyType type) {
        return switch (type) {
            case NUMBER -> EcorePackage.Literals.EDOUBLE;
            case BOOLEAN -> EcorePackage.Literals.EBOOLEAN;
            case STRING -> EcorePackage.Literals.ESTRING;
        };
    }

    private void addDocumentationAnnotation(EClass eClass, String docString) {
        EAnnotation annotation = factory.createEAnnotation();
        annotation.setSource(GENMODEL_ANNOTATION_URI);
        annotation.getDetails().put("documentation", docString);
        eClass.getEAnnotations().add(annotation);
    }

    public Map<String, EClass> getEClassMap() {
        return Collections.unmodifiableMap(eClassMap);
    }

    public Map<String, EStructuralFeature> getFeatureMap() {
        return Collections.unmodifiableMap(featureMap);
    }
}
