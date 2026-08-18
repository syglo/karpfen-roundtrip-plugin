package org.karpfen.serializer;

import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.karpfen.transformer.KMetaToEcoreTransformer;

// Manual serializer converts EMF Eobject graph into .kmodel text
public class EcoreToKModelManualSerializer implements KModelSerializer {

    @Override
    public String serialize(EObject rootObject) {
        if (rootObject == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        serializeObject(rootObject, sb);
        sb.append("\n");

        return sb.toString();
    }

    private void serializeObject(EObject obj, StringBuilder sb) {
        String id = getId(obj);
        String className = (obj.eClass() != null) ? obj.eClass().getName() : "";

        sb.append(String.format("make object \"%s\":\"%s\" {\n", id, className));

        EClass eClass = obj.eClass();
        if (eClass != null) {

            // Properties
            for (EAttribute attr : eClass.getEAllAttributes()) {
                if (KMetaToEcoreTransformer.ID_FEATURE_NAME.equals(attr.getName())) {
                    continue;
                }
                if (!obj.eIsSet(attr)) {
                    continue;
                }

                Object val = obj.eGet(attr);
                if (val != null) {
                    if (attr.isMany() && val instanceof List<?> list) {
                        for (Object item : list) {
                            if (item != null) {
                                sb.append(String.format("prop(\"%s\") -> \"%s\"\n", attr.getName(), item));
                            }
                        }
                    } else {
                        sb.append(String.format("prop(\"%s\") -> \"%s\"\n", attr.getName(), val));
                    }
                }
            }

            // Has
            for (EReference ref : eClass.getEAllReferences()) {
                if (!ref.isContainment() || !obj.eIsSet(ref)) {
                    continue;
                }

                Object childVal = obj.eGet(ref);
                if (childVal != null) {
                    if (ref.isMany() && childVal instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof EObject childObj) {
                                sb.append(String.format("has(\"%s\") -> ", ref.getName()));
                                serializeObject(childObj, sb);
                                sb.append("\n");
                            }
                        }
                    } else if (childVal instanceof EObject childObj) {
                        sb.append(String.format("has(\"%s\") -> ", ref.getName()));
                        serializeObject(childObj, sb);
                        sb.append("\n");
                    }
                }
            }

            // Knows
            for (EReference ref : eClass.getEAllReferences()) {
                if (ref.isContainment() || !obj.eIsSet(ref)) {
                    continue;
                }

                Object targetVal = obj.eGet(ref);
                if (targetVal != null) {
                    if (ref.isMany() && targetVal instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof EObject targetObj) {
                                sb.append(String.format("knows(\"%s\") -> \"%s\"\n", ref.getName(),
                                        getId(targetObj)));
                            }
                        }
                    } else if (targetVal instanceof EObject targetObj) {
                        sb.append(String.format("knows(\"%s\") -> \"%s\"\n", ref.getName(), getId(targetObj)));
                    }
                }
            }
        }

        sb.append("}");
    }

    private String getId(EObject obj) {
        if (obj == null || obj.eClass() == null) {
            return "_";
        }
        EStructuralFeature idFeature = obj.eClass().getEStructuralFeature(KMetaToEcoreTransformer.ID_FEATURE_NAME);
        if (idFeature != null) {
            Object val = obj.eGet(idFeature);
            if (val != null && !val.toString().isBlank()) {
                return val.toString().trim();
            }
        }
        return "_";
    }
}