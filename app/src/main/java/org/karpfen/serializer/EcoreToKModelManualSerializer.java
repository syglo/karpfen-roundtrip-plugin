package org.karpfen.serializer;

import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.karpfen.transformer.KMetaToEcoreTransformer;

/**
 * Manual programmatic Java serializer converting dynamic EMF {@link EObject}
 * instance graphs into {@code .kmodel} DSL text.
 * Without indentation or any formatting, because it is post-processed through
 * ANTLR-based Karpfen DSL formatter.
 * 
 * Recursively traverses object hierarchies, serializing properties via
 * {@code prop("...") -> "..."},
 * embedded containment relations via {@code has("...") -> make object ...}, and
 * cross-references via {@code knows("...") -> "id"}.
 */
public class EcoreToKModelManualSerializer implements KModelSerializer {

    /**
     * Serializes the dynamic {@link EObject} instance root into Karpfen
     * {@code .kmodel} DSL text.
     *
     * @param rootObject the root instance {@link EObject} to serialize
     * @return generated {@code .kmodel} source text
     */
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
        String className = getClassName(obj);

        sb.append(String.format("make object \"%s\":\"%s\" {\n", id, className));

        EClass eClass = resolveEClass(obj);
        if (eClass != null) {

            // Properties
            for (EAttribute attr : eClass.getEAllAttributes()) {
                if (KMetaToEcoreTransformer.ID_FEATURE_NAME.equals(attr.getName())) {
                    continue;
                }
                try {
                    EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(attr.getName());
                    if (targetFeat == null || !obj.eIsSet(targetFeat)) {
                        continue;
                    }

                    Object val = obj.eGet(targetFeat);
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
                } catch (Throwable ignored) {
                }
            }

            // Has
            for (EReference ref : eClass.getEAllReferences()) {
                if (!ref.isContainment()) {
                    continue;
                }
                try {
                    EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
                    if (targetFeat == null || !obj.eIsSet(targetFeat)) {
                        continue;
                    }

                    Object childVal = obj.eGet(targetFeat);
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
                } catch (Throwable ignored) {
                }
            }

            // Knows
            for (EReference ref : eClass.getEAllReferences()) {
                if (ref.isContainment()) {
                    continue;
                }
                try {
                    EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
                    if (targetFeat == null || !obj.eIsSet(targetFeat)) {
                        continue;
                    }

                    Object targetVal = obj.eGet(targetFeat);
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
                } catch (Throwable ignored) {
                }
            }
        }

        sb.append("}");
    }

    private EClass resolveEClass(EObject obj) {
        if (obj == null)
            return null;
        EClass eClass = obj.eClass();
        if (eClass != null && eClass.eIsProxy()) {
            EObject resolved = EcoreUtil.resolve(eClass, obj);
            if (resolved instanceof EClass resolvedClass && !resolvedClass.eIsProxy()) {
                return resolvedClass;
            }
        }
        return eClass;
    }

    private String getClassName(EObject obj) {
        EClass eClass = resolveEClass(obj);
        return (eClass != null && eClass.getName() != null) ? eClass.getName() : "Object";
    }

    private String getId(EObject obj) {
        if (obj == null)
            return "_";
        EClass eClass = resolveEClass(obj);
        if (eClass == null)
            return "_";
        EStructuralFeature idFeature = eClass.getEStructuralFeature(KMetaToEcoreTransformer.ID_FEATURE_NAME);
        if (idFeature != null) {
            try {
                if (obj.eIsSet(idFeature)) {
                    Object val = obj.eGet(idFeature);
                    if (val != null && !val.toString().isBlank()) {
                        return val.toString().trim();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return "_";
    }
}