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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KModelToEcoreInstanceTransformer {

    private final Map<String, EObject> eObjectMap = new HashMap<>();

    public Resource transformAndSave(Model kModel, EPackage ePackage, File outputFile) throws IOException {
        eObjectMap.clear();

        // Convert Karpfen DataObjects with EME EObject instances
        for (DataObject dataObject : kModel.getObjects()) {
            instantiateEObjects(dataObject, ePackage);
        }

        // Add to EObjects attributes and relationships
        for (DataObject dataObject : kModel.getObjects()) {
            populateEObjectFeatures(dataObject, ePackage);
        }

        // Serialize to XMI
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
            .put("xmi", new XMIResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);

        URI fileUri = URI.createFileURI(outputFile.getAbsolutePath());
        Resource resource = resourceSet.createResource(fileUri);

        // Add root objects to it
        for (DataObject rootDataObject : kModel.getObjects()) {
            EObject rootEObject = eObjectMap.get(rootDataObject.getId());
            if (rootEObject != null && rootEObject.eContainer() == null) {
                resource.getContents().add(rootEObject);
            }
        }

        resource.save(Collections.emptyMap());
        return resource;
    }

    private void instantiateEObjects(DataObject dataObject, EPackage ePackage) {
        EClass eClass = (EClass) ePackage.getEClassifier(dataObject.getOfType().getName());
        EObject eObject = ePackage.getEFactoryInstance().create(eClass);

        String key = dataObject.getId().isEmpty() ? String.valueOf(dataObject.hashCode()) : dataObject.getId();
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
        String key = dataObject.getId().isEmpty() ? String.valueOf(dataObject.hashCode()) : dataObject.getId();
        EObject eObject = eObjectMap.get(key);
        EClass eClass = eObject.eClass();

        // primitive properties
        for (SimplePropertyObject prop : dataObject.getProperties()) {
            EStructuralFeature feature = eClass.getEStructuralFeature(prop.getKey());
            if (feature != null) {
                if (prop instanceof SimpleAtomicPropertyObject atomic) { // primitive
                    eObject.eSet(feature, atomic.getValue());
                } else if (prop instanceof SimpleListPropertyObject listProp) { // list
                    eObject.eSet(feature, listProp.getValues());
                }
            }
        }

        // relations has / knows
        for (ClassTypePropertyObject rel : dataObject.getRelations()) {
            EReference reference = (EReference) eClass.getEStructuralFeature(rel.getKey());
            if (reference != null) {
                if (rel instanceof ClassTypeAtomicPropertyObject atomicRel && atomicRel.getValue() != null) {
                    String targetKey = atomicRel.getValue().getId().isEmpty() ?
                        String.valueOf(atomicRel.getValue().hashCode()) : atomicRel.getValue().getId();
                    
                    eObject.eSet(reference, eObjectMap.get(targetKey));
                } else if (rel instanceof ClassTypeListPropertyObject listRel) {
                    @SuppressWarnings("unchecked")
                    List<EObject> eList = (List<EObject>) eObject.eGet(reference);
                    for (DataObject target : listRel.getValues()) {
                        String targetKey = target.getId().isEmpty() ?
                            String.valueOf(target.hashCode()) : target.getId();
                        
                        eList.add(eObjectMap.get(targetKey));
                    }
                }
            }

        }
    }
}
