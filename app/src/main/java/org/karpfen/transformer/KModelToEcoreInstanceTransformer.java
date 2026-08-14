package org.karpfen.transformer;

import instance.ClassTypeAtomicPropertyObject;
import instance.ClassTypeListPropertyObject;
import instance.ClassTypePropertyObject;
import instance.DataObject;
import instance.Model;
import instance.ObjectReference;
import instance.SimpleAtomicPropertyObject;
import instance.SimpleListPropertyObject;
import instance.SimplePropertyObject;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KModelToEcoreInstanceTransformer {

    private final Map<String, EObject> eObjectMap = new HashMap<>();

    public List<EObject> transform(Model kModel, EPackage ePackage) {
        eObjectMap.clear();

        // Convert Karpfen DataObjects with EME EObject instances
        for (DataObject dataObject : kModel.getObjects()) {
            instantiateEObjects(dataObject, ePackage);
        }

        // Add to EObjects attributes and relationships
        for (DataObject dataObject : kModel.getObjects()) {
            populateEObjectFeatures(dataObject, ePackage);
        }

        // Collect root objects
        List<EObject> rootEObjects = new ArrayList<>();
        for (DataObject rootDataObject : kModel.getObjects()) {
            String key = getObjectKey(rootDataObject);
            EObject rootEObject = eObjectMap.get(key);
            if (rootEObject != null && rootEObject.eContainer() == null && !rootEObjects.contains(rootEObject)) {
                rootEObjects.add(rootEObject);
            }
        }

        return rootEObjects;
    }

    public void saveToXmiFile(List<EObject> rootObjects, EPackage ePackage, File outputFile) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
            .put("xmi", new XMIResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
            .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());

        resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);

        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        URI fileUri = URI.createFileURI(outputFile.getAbsolutePath());
        Resource resource = resourceSet.createResource(fileUri);

        resource.getContents().addAll(rootObjects);
        resource.save(Collections.emptyMap());
    }

    public void saveToXmiFile(EObject rootObject, EPackage ePackage, File outputFile) throws IOException {
        saveToXmiFile(Collections.singletonList(rootObject), ePackage, outputFile);
    }

    private void instantiateEObjects(DataObject dataObject, EPackage ePackage) {
        EClass eClass = (EClass) ePackage.getEClassifier(dataObject.getOfType().getName());
        if (eClass == null) {
            throw new IllegalArgumentException(
                "Metamodel EClass not found for type: " + dataObject.getOfType().getName()
            );
        }

        EObject eObject = ePackage.getEFactoryInstance().create(eClass);
        String key = getObjectKey(dataObject);
        eObjectMap.put(key, eObject);

        // Instantiate embedded has objects recursively
        for (ClassTypePropertyObject rel : dataObject.getRelations()) {
            if (rel.getPropertyType().getAssociationType() == meta.AssociationType.EMBEDDED) { // has
                // Simple
                if (rel instanceof ClassTypeAtomicPropertyObject atomicRel && atomicRel.getValue() != null) {
                    instantiateEObjects(atomicRel.getValue(), ePackage);
                } else if (rel instanceof ClassTypeListPropertyObject listRel) { // Lists
                    for (DataObject child : listRel.getValues()) {
                        instantiateEObjects(child, ePackage);
                    }
                }
            }
        }
    }

    private void populateEObjectFeatures(DataObject dataObject, EPackage ePackage) {
        String key = getObjectKey(dataObject);
        EObject eObject = eObjectMap.get(key);
        EClass eClass = eObject.eClass();

        // primitive properties
        for (SimplePropertyObject prop : dataObject.getProperties()) {
            EStructuralFeature feature = eClass.getEStructuralFeature(prop.getKey());
            if (feature != null) {
                if (prop instanceof SimpleAtomicPropertyObject atomic) { // primitive
                    eObject.eSet(feature, atomic.getValue());
                } else if (prop instanceof SimpleListPropertyObject listProp) { // list
                    @SuppressWarnings("unchecked")
                    List<Object> targetList = (List<Object>) eObject.eGet(feature);
                    targetList.clear();
                    targetList.addAll(listProp.getValues());
                }
            }
        }

        // relations has / knows
        // populate relations first
        for (ClassTypePropertyObject rel : dataObject.getRelations()) {
            EReference reference = (EReference) eClass.getEStructuralFeature(rel.getKey());
            if (reference != null) {
                if (rel instanceof ClassTypeAtomicPropertyObject atomicRel && atomicRel.getValue() != null) {
                    String targetKey = getObjectKey(atomicRel.getValue());
                    eObject.eSet(reference, eObjectMap.get(targetKey));
                } else if (rel instanceof ClassTypeListPropertyObject listRel) {
                    @SuppressWarnings("unchecked")
                    List<EObject> eList = (List<EObject>) eObject.eGet(reference);
                    eList.clear();
                    for (DataObject target : listRel.getValues()) {
                        String targetKey = getObjectKey(target);
                        EObject targetEObj = eObjectMap.get(targetKey);
                        if (targetEObj != null && !eList.contains(targetEObj)) {
                            eList.add(targetEObj);
                        }
                    }
                }
            }
        }

        // then traverse down has / children to populate their properties
        for (ClassTypePropertyObject rel : dataObject.getRelations()) {
            if (rel.getPropertyType().getAssociationType() == meta.AssociationType.EMBEDDED) {
                if (rel instanceof ClassTypeAtomicPropertyObject atomicRel && atomicRel.getValue() != null) {
                    populateEObjectFeatures(atomicRel.getValue(), ePackage);
                } else if (rel instanceof ClassTypeListPropertyObject listRel) {
                    for (DataObject child : listRel.getValues()) {
                        populateEObjectFeatures(child, ePackage);
                    }
                }
            }
        }
    }

    private String getObjectKey(DataObject dataObject) {
        return (dataObject.getId() != null && !dataObject.getId().isEmpty())
            ? dataObject.getId()
            : String.valueOf(System.identityHashCode(dataObject));
    }

    public Map<String, EObject> getEObjectMap() {
        return Collections.unmodifiableMap(eObjectMap);
    }
}
