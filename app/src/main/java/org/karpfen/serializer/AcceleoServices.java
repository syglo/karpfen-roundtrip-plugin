package org.karpfen.serializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.karpfen.transformer.KMetaToEcoreTransformer;

public class AcceleoServices {

    private static final String GENMODEL_URI = "https://eclipse/emf/GenModel";
    private static final String KARPFEN_URI = "https://github/karpfen/annotation";

    // !!! KMeta Services helpers for .mtl

    public List<EClass> getOrderedClasses(EPackage pkg) {
        if (pkg == null) return Collections.emptyList();

        String rootClassName = null;
        EAnnotation rootAnn = pkg.getEAnnotation(KARPFEN_URI);
        if (rootAnn != null) {
            rootClassName = rootAnn.getDetails().get("rootClass");
        }

        List<EClass> regularClasses = new ArrayList<>();
        EClass rootClass = null;

        for (EClassifier classifier : pkg.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                if (rootClassName != null && rootClassName.equals(eClass.getName())) {
                    rootClass = eClass;
                } else {
                    regularClasses.add(eClass);
                }
            }
        }

        List<EClass> ordered = new ArrayList<>(regularClasses);
        if (rootClass != null) {
            ordered.add(rootClass);
        }
        return ordered;
    }

    public List<EAttribute> getAttributes(EClass c) {
        if (c == null) return Collections.emptyList();
        List<EAttribute> result = new ArrayList<>();
        for (EAttribute attr : c.getEAttributes()) {
            if (!KMetaToEcoreTransformer.ID_FEATURE_NAME.equals(attr.getName())) {
                result.add(attr);
            }
        }
        return result;
    }

    public List<EReference> getContainmentReferences(EClass c) {
        if (c == null) return Collections.emptyList();
        List<EReference> result = new ArrayList<>();
        for (EReference ref : c.getEReferences()) {
            if (ref.isContainment()) {
                result.add(ref);
            }
        }
        return result;
    }

    public List<EReference> getAssociationReferences(EClass c) {
        if (c == null) return Collections.emptyList();
        List<EReference> result = new ArrayList<>();
        for (EReference ref : c.getEReferences()) {
            if (!ref.isContainment()) {
                result.add(ref);
            }
        }
        return result;
    }

    public boolean isMany(EStructuralFeature feature) {
        return feature != null && feature.isMany();
    }

    public String getDocumentation(EClass c) {
        if (c == null) return "";
        EAnnotation ann = c.getEAnnotation(GENMODEL_URI);
        if (ann != null && ann.getDetails().containsKey("documentation")) {
            return ann.getDetails().get("documentation");
        }
        return "";
    }

    public String mapDataType(EClassifier d) {
        if (d == null) return "string";
        if (d == EcorePackage.Literals.EDOUBLE || d == EcorePackage.Literals.EFLOAT
                || d == EcorePackage.Literals.EINT || d == EcorePackage.Literals.ELONG
                || "EDouble".equalsIgnoreCase(d.getName()) || "EFloat".equalsIgnoreCase(d.getName())
                || "EInt".equalsIgnoreCase(d.getName()) || "ELong".equalsIgnoreCase(d.getName())
                || "number".equalsIgnoreCase(d.getName())) {
            return "number";
        }
        if (d == EcorePackage.Literals.EBOOLEAN || "EBoolean".equalsIgnoreCase(d.getName()) || "boolean".equalsIgnoreCase(d.getName())) {
            return "boolean";
        }
        return "string";
    }

    // !!! KModel Services helpers for .mtl

    public String getId(EObject obj) {
        if (obj == null) return "_";
        EStructuralFeature idFeature = obj.eClass().getEStructuralFeature(KMetaToEcoreTransformer.ID_FEATURE_NAME);
        if (idFeature != null) {
            Object val = obj.eGet(idFeature);
            if (val != null && !val.toString().isBlank()) {
                return val.toString().trim();
            }
        }
        return "_";
    }

    public String getClassName(EObject obj) {
        return (obj != null && obj.eClass() != null) ? obj.eClass().getName() : "";
    }

    public List<EAttribute> getAllAttributes(EObject obj) {
        if (obj == null || obj.eClass() == null) return Collections.emptyList();
        List<EAttribute> result = new ArrayList<>();
        for (EAttribute attr : obj.eClass().getEAllAttributes()) {
            if (!KMetaToEcoreTransformer.ID_FEATURE_NAME.equals(attr.getName()) && obj.eIsSet(attr)) {
                result.add(attr);
            }
        }
        return result;
    }

    public List<EReference> getAllContainmentReferences(EObject obj) {
        if (obj == null || obj.eClass() == null) return Collections.emptyList();
        List<EReference> result = new ArrayList<>();
        for (EReference ref : obj.eClass().getEAllReferences()) {
            if (ref.isContainment() && obj.eIsSet(ref)) {
                result.add(ref);
            }
        }
        return result;
    }

    public List<EReference> getAllAssociationReferences(EObject obj) {
        if (obj == null || obj.eClass() == null) return Collections.emptyList();
        List<EReference> result = new ArrayList<>();
        for (EReference ref : obj.eClass().getEAllReferences()) {
            if (!ref.isContainment() && obj.eIsSet(ref)) {
                result.add(ref);
            }
        }
        return result;
    }

    public List<String> getAttributeValues(EObject obj, EAttribute attr) {
        if (obj == null || attr == null || !obj.eIsSet(attr)) return Collections.emptyList();
        Object rawVal = obj.eGet(attr);
        if (rawVal == null) return Collections.emptyList();

        List<String> values = new ArrayList<>();
        if (attr.isMany()) {
            if (rawVal instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) values.add(item.toString());
                }
            }
        } else {
            values.add(rawVal.toString());
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    public List<EObject> getContainedChildren(EObject obj, EReference ref) {
        if (obj == null || ref == null || !ref.isContainment() || !obj.eIsSet(ref)) return Collections.emptyList();
        Object rawVal = obj.eGet(ref);
        if (rawVal == null) return Collections.emptyList();

        if (ref.isMany()) {
            if (rawVal instanceof List<?> list) {
                return (List<EObject>) list;
            }
            return Collections.emptyList();
        } else if (rawVal instanceof EObject child) {
            return Collections.singletonList(child);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<EObject> getReferenceTargets(EObject obj, EReference ref) {
        if (obj == null || ref == null || ref.isContainment() || !obj.eIsSet(ref)) return Collections.emptyList();
        Object rawVal = obj.eGet(ref);
        if (rawVal == null) return Collections.emptyList();

        if (ref.isMany()) {
            if (rawVal instanceof List<?> list) {
                return (List<EObject>) list;
            }
            return Collections.emptyList();
        } else if (rawVal instanceof EObject target) {
            return Collections.singletonList(target);
        }
        return Collections.emptyList();
    }
}