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
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.karpfen.transformer.KMetaToEcoreTransformer;

public class AcceleoServices {

    private static final String GENMODEL_URI = "https://eclipse/emf/GenModel";
    private static final String KARPFEN_URI = "https://github/karpfen/annotation";

    // !!! KMeta Services helpers for .mtl

    public List<EClass> getOrderedClasses(EPackage pkg) {
        if (pkg == null)
            return Collections.emptyList();

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
        if (c == null)
            return Collections.emptyList();
        List<EAttribute> result = new ArrayList<>();
        for (EAttribute attr : c.getEAttributes()) {
            if (!KMetaToEcoreTransformer.ID_FEATURE_NAME.equals(attr.getName())) {
                result.add(attr);
            }
        }
        return result;
    }

    public List<EReference> getContainmentReferences(EClass c) {
        if (c == null)
            return Collections.emptyList();
        List<EReference> result = new ArrayList<>();
        for (EReference ref : c.getEReferences()) {
            if (ref.isContainment()) {
                result.add(ref);
            }
        }
        return result;
    }

    public List<EReference> getAssociationReferences(EClass c) {
        if (c == null)
            return Collections.emptyList();
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
        if (c == null)
            return "";
        EAnnotation ann = c.getEAnnotation(GENMODEL_URI);
        if (ann != null && ann.getDetails().containsKey("documentation")) {
            return ann.getDetails().get("documentation");
        }
        return "";
    }

    public String mapDataType(EClassifier d) {
        if (d == null)
            return "string";
        String name = d.getName();
        if (name == null) {
            name = d.getInstanceClassName();
        }
        if (name == null)
            return "string";
        String lower = name.toLowerCase();
        if (lower.contains("double") || lower.contains("float") || lower.contains("int") || lower.contains("long")
                || lower.contains("number")) {
            return "number";
        }
        if (lower.contains("bool")) {
            return "boolean";
        }
        return "string";
    }

    // !!! KModel Services helpers for .mtl

    public String getId(EObject obj) {
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

    public String getClassName(EObject obj) {
        if (obj == null)
            return "";
        EClass eClass = resolveEClass(obj);
        return (eClass != null && eClass.getName() != null) ? eClass.getName() : "";
    }

    public List<EAttribute> getAllAttributes(EObject obj) {
        if (obj == null)
            return Collections.emptyList();
        EClass eClass = resolveEClass(obj);
        if (eClass == null)
            return Collections.emptyList();

        List<EAttribute> result = new ArrayList<>();
        for (EAttribute attr : eClass.getEAllAttributes()) {
            if (!KMetaToEcoreTransformer.ID_FEATURE_NAME.equals(attr.getName())) {
                try {
                    EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(attr.getName());
                    if (targetFeat != null && obj.eIsSet(targetFeat)) {
                        result.add(attr);
                    }
                } catch (Throwable ignored) {
                    try {
                        EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(attr.getName());
                        if (targetFeat != null && obj.eGet(targetFeat) != null) {
                            result.add(attr);
                        }
                    } catch (Throwable ignored2) {
                    }
                }
            }
        }
        return result;
    }

    public List<EReference> getAllContainmentReferences(EObject obj) {
        if (obj == null)
            return Collections.emptyList();
        EClass eClass = resolveEClass(obj);
        if (eClass == null)
            return Collections.emptyList();

        List<EReference> result = new ArrayList<>();
        for (EReference ref : eClass.getEAllReferences()) {
            if (ref.isContainment()) {
                try {
                    EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
                    if (targetFeat != null && obj.eIsSet(targetFeat)) {
                        result.add(ref);
                    }
                } catch (Throwable ignored) {
                    try {
                        EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
                        if (targetFeat != null && obj.eGet(targetFeat) != null) {
                            result.add(ref);
                        }
                    } catch (Throwable ignored2) {
                    }
                }
            }
        }
        return result;
    }

    public List<EReference> getAllAssociationReferences(EObject obj) {
        if (obj == null)
            return Collections.emptyList();
        EClass eClass = resolveEClass(obj);
        if (eClass == null)
            return Collections.emptyList();

        List<EReference> result = new ArrayList<>();
        for (EReference ref : eClass.getEAllReferences()) {
            if (!ref.isContainment()) {
                try {
                    EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
                    if (targetFeat != null && obj.eIsSet(targetFeat)) {
                        result.add(ref);
                    }
                } catch (Throwable ignored) {
                    try {
                        EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
                        if (targetFeat != null && obj.eGet(targetFeat) != null) {
                            result.add(ref);
                        }
                    } catch (Throwable ignored2) {
                    }
                }
            }
        }
        return result;
    }

    public List<String> getAttributeValues(EObject obj, EAttribute attr) {
        if (obj == null || attr == null)
            return Collections.emptyList();
        Object rawVal = null;
        try {
            EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(attr.getName());
            if (targetFeat != null && obj.eIsSet(targetFeat)) {
                rawVal = obj.eGet(targetFeat);
            }
        } catch (Throwable ignored) {
            try {
                EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(attr.getName());
                if (targetFeat != null) {
                    rawVal = obj.eGet(targetFeat);
                }
            } catch (Throwable ignored2) {
            }
        }
        if (rawVal == null)
            return Collections.emptyList();

        List<String> values = new ArrayList<>();
        if (attr.isMany() && rawVal instanceof List<?> list) {
            for (Object item : list) {
                if (item != null)
                    values.add(item.toString());
            }
        } else {
            values.add(rawVal.toString());
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    public List<EObject> getContainedChildren(EObject obj, EReference ref) {
        if (obj == null || ref == null || !ref.isContainment())
            return Collections.emptyList();
        Object rawVal = null;
        try {
            EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
            if (targetFeat != null && obj.eIsSet(targetFeat)) {
                rawVal = obj.eGet(targetFeat);
            }
        } catch (Throwable ignored) {
            try {
                EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
                if (targetFeat != null) {
                    rawVal = obj.eGet(targetFeat);
                }
            } catch (Throwable ignored2) {
            }
        }
        if (rawVal == null)
            return Collections.emptyList();

        if (ref.isMany() && rawVal instanceof List<?> list) {
            return (List<EObject>) list;
        } else if (rawVal instanceof EObject child) {
            return Collections.singletonList(child);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<EObject> getReferenceTargets(EObject obj, EReference ref) {
        if (obj == null || ref == null || ref.isContainment())
            return Collections.emptyList();
        Object rawVal = null;
        try {
            EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
            if (targetFeat != null && obj.eIsSet(targetFeat)) {
                rawVal = obj.eGet(targetFeat);
            }
        } catch (Throwable ignored) {
            try {
                EStructuralFeature targetFeat = obj.eClass().getEStructuralFeature(ref.getName());
                if (targetFeat != null) {
                    rawVal = obj.eGet(targetFeat);
                }
            } catch (Throwable ignored2) {
            }
        }
        if (rawVal == null)
            return Collections.emptyList();

        if (ref.isMany() && rawVal instanceof List<?> list) {
            return (List<EObject>) list;
        } else if (rawVal instanceof EObject target) {
            return Collections.singletonList(target);
        }
        return Collections.emptyList();
    }

    // !!! KStates Services helpers for .mtl

    public String getSmAttachedClass(EObject sm) {
        if (sm == null)
            return "Object";
        EStructuralFeature feat = sm.eClass().getEStructuralFeature("attachedToClass");
        if (feat != null && sm.eIsSet(feat)) {
            Object val = sm.eGet(feat);
            if (val != null)
                return val.toString();
        }
        return "Object";
    }

    @SuppressWarnings("unchecked")
    public List<EObject> getSmStates(EObject sm) {
        if (sm == null)
            return Collections.emptyList();
        EStructuralFeature feat = sm.eClass().getEStructuralFeature("states");
        if (feat != null && sm.eIsSet(feat)) {
            Object val = sm.eGet(feat);
            if (val instanceof List<?> list) {
                return (List<EObject>) list;
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<EObject> getSmTransitions(EObject sm) {
        if (sm == null)
            return Collections.emptyList();
        EStructuralFeature feat = sm.eClass().getEStructuralFeature("transitions");
        if (feat != null && sm.eIsSet(feat)) {
            Object val = sm.eGet(feat);
            if (val instanceof List<?> list) {
                return (List<EObject>) list;
            }
        }
        return Collections.emptyList();
    }

    public boolean hasMacros(EObject sm) {
        return !getSmMacros(sm).isEmpty();
    }

    @SuppressWarnings("unchecked")
    public List<String> getSmMacros(EObject sm) {
        if (sm == null)
            return Collections.emptyList();
        EStructuralFeature feat = sm.eClass().getEStructuralFeature("macros");
        if (feat != null && sm.eIsSet(feat)) {
            Object val = sm.eGet(feat);
            if (val instanceof List<?> list) {
                return (List<String>) list;
            }
        }
        return Collections.emptyList();
    }

    public String getStateName(EObject state) {
        if (state == null)
            return "State";
        EStructuralFeature feat = state.eClass().getEStructuralFeature("name");
        if (feat != null && state.eIsSet(feat)) {
            Object val = state.eGet(feat);
            if (val != null)
                return val.toString();
        }
        return "State";
    }

    public boolean isInitialState(EObject state) {
        if (state == null)
            return false;
        EStructuralFeature feat = state.eClass().getEStructuralFeature("isInitial");
        if (feat != null && state.eIsSet(feat)) {
            Object val = state.eGet(feat);
            if (val instanceof Boolean b)
                return b;
        }
        return false;
    }

    public boolean hasEntryAction(EObject state) {
        if (state == null)
            return false;
        EStructuralFeature feat = state.eClass().getEStructuralFeature("entryAction");
        if (feat != null && state.eIsSet(feat)) {
            Object val = state.eGet(feat);
            return val != null && !val.toString().isBlank();
        }
        return false;
    }

    public String getStateEntryAction(EObject state) {
        if (state == null)
            return "";
        EStructuralFeature feat = state.eClass().getEStructuralFeature("entryAction");
        if (feat != null && state.eIsSet(feat)) {
            Object val = state.eGet(feat);
            if (val != null)
                return val.toString();
        }
        return "";
    }

    public boolean hasDoAction(EObject state) {
        if (state == null)
            return false;
        EStructuralFeature feat = state.eClass().getEStructuralFeature("doAction");
        if (feat != null && state.eIsSet(feat)) {
            Object val = state.eGet(feat);
            return val != null && !val.toString().isBlank();
        }
        return false;
    }

    public String getStateDoAction(EObject state) {
        if (state == null)
            return "";
        EStructuralFeature feat = state.eClass().getEStructuralFeature("doAction");
        if (feat != null && state.eIsSet(feat)) {
            Object val = state.eGet(feat);
            if (val != null)
                return val.toString();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public List<EObject> getInnerStates(EObject state) {
        if (state == null)
            return Collections.emptyList();
        EStructuralFeature feat = state.eClass().getEStructuralFeature("innerStates");
        if (feat != null && state.eIsSet(feat)) {
            Object val = state.eGet(feat);
            if (val instanceof List<?> list) {
                return (List<EObject>) list;
            }
        }
        return Collections.emptyList();
    }

    public String getSourceStateName(EObject trans) {
        if (trans == null)
            return "_";
        EStructuralFeature feat = trans.eClass().getEStructuralFeature("sourceState");
        if (feat != null && trans.eIsSet(feat)) {
            Object val = trans.eGet(feat);
            if (val instanceof EObject src) {
                return getStateName(src);
            }
        }
        return "_";
    }

    public String getTargetStateName(EObject trans) {
        if (trans == null)
            return "_";
        EStructuralFeature feat = trans.eClass().getEStructuralFeature("targetState");
        if (feat != null && trans.eIsSet(feat)) {
            Object val = trans.eGet(feat);
            if (val instanceof EObject tgt) {
                return getStateName(tgt);
            }
        }
        return "_";
    }

    public boolean isNotLooping(EObject trans) {
        if (trans == null)
            return false;
        EStructuralFeature feat = trans.eClass().getEStructuralFeature("notLooping");
        if (feat != null && trans.eIsSet(feat)) {
            Object val = trans.eGet(feat);
            if (val instanceof Boolean b)
                return b;
        }
        return false;
    }

    public boolean hasCondition(EObject trans) {
        if (trans == null)
            return false;
        EStructuralFeature feat = trans.eClass().getEStructuralFeature("condition");
        if (feat != null && trans.eIsSet(feat)) {
            Object val = trans.eGet(feat);
            return val != null && !val.toString().isBlank();
        }
        return false;
    }

    public String getTransitionCondition(EObject trans) {
        if (trans == null)
            return "";
        EStructuralFeature feat = trans.eClass().getEStructuralFeature("condition");
        if (feat != null && trans.eIsSet(feat)) {
            Object val = trans.eGet(feat);
            if (val != null)
                return val.toString();
        }
        return "";
    }

    public String indentLines(String text, String indent) {
        if (text == null || text.isBlank())
            return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.isBlank()) {
                sb.append(indent).append(line.trim());
            }
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String indentMacro(String macroText, String indent) {
        if (macroText == null || macroText.isBlank())
            return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = macroText.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.isBlank()) {
                sb.append(indent).append(line);
            }
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String getFormattedMacrosBlock(EObject sm, String indent) {
        List<String> macros = getSmMacros(sm);
        if (macros.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indent).append("MACROS {\n");
        for (String m : macros) {
            sb.append(indentMacro(m, indent.concat("    "))).append("\n");
        }
        sb.append(indent).append("}");
        return sb.toString();
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
            return null;
        }
        return eClass;
    }
}