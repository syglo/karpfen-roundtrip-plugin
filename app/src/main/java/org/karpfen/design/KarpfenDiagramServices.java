package org.karpfen.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kmeta.KmetaLexer;
import kmeta.KmetaParser;
import kmodel.KmodelLexer;
import kmodel.KmodelParser;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

public class KarpfenDiagramServices {

    public static final String KARPFEN_URI = "https://github/karpfen/annotation";
    public static final String ID_FEATURE_NAME = "__id__";

    // ! KMeta visual projections - UML class diagramm

    public String getKMetaClassLabel(EClass clas) {
        if (clas == null || clas.getName() == null)
            return "Type";
        EPackage pkg = clas.getEPackage();
        if (pkg != null) {
            EAnnotation ann = pkg.getEAnnotation(KARPFEN_URI);
            if (ann != null && clas.getName().equals(ann.getDetails().get("rootClass"))) {
                return "<root> " + clas.getName();
            }
        }
        return clas.getName();
    }

    public String getKMetaAttributeLabel(EAttribute attr) {
        if (attr == null || attr.getName() == null)
            return "";
        String typeName = mapEcoreToKarpfenType(attr.getEAttributeType());
        if (attr.isMany() || attr.getUpperBound() == ETypedElement.UNBOUNDED_MULTIPLICITY) {
            return attr.getName() + " : list(\"" + typeName + "\")";
        }
        return attr.getName() + " : " + typeName;
    }

    public String getKMetaEdgeLabel(EReference ref) {
        if (ref == null || ref.getName() == null)
            return "";
        if (ref.isMany() || ref.getUpperBound() == ETypedElement.UNBOUNDED_MULTIPLICITY) {
            return ref.getName() + " [list]";
        }
        return ref.getName();
    }

    // ! KMeta ANTLR micro parser, input subsitution

    public EClass editClassName(EClass clas, String input) {
        KarpfenLog.trace("DirectEdit-KMeta", "editClassName called for class="
                + (clas != null ? clas.getName() : "null") + " with input=[" + input + "]");
        if (clas == null || input == null || input.isBlank())
            return clas;
        String raw = input.trim();
        String oldName = clas.getName();

        if (raw.startsWith("<root>")) {
            raw = raw.substring("<root>".length()).trim();
        }

        String snippet = raw.startsWith("type") ? raw
                : (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n") && !raw.contains("(")
                        && !raw.contains(")")) ? "type \"" + raw.replace("\"", "") + "\" \"\" {}" : raw;

        try {
            KmetaParser parser = createKmetaParser(snippet);
            KmetaParser.Type_definitionContext ctx = parser.type_definition();
            String newName = unquote(ctx.STRING(0).getText());
            if (!newName.isBlank()) {
                clas.setName(newName);

                EPackage pkg = clas.getEPackage();
                if (pkg != null) {
                    EAnnotation ann = pkg.getEAnnotation(KARPFEN_URI);
                    if (ann != null && oldName != null && oldName.equals(ann.getDetails().get("rootClass"))) {
                        ann.getDetails().put("rootClass", newName);
                    }
                }
                markTargetResourceDirty(clas);
                KarpfenLog.trace("DirectEdit-KMeta", "Renamed type to: " + newName);
            }
        } catch (ParseCancellationException e) {
            KarpfenLog.warn("Direct-Edit Invalid type rejected: " + input);
        }
        return clas;
    }

    public EAttribute editKMetaAttribute(EAttribute attr, String input) {
        KarpfenLog.trace("DirectEdit-KMeta", "editKMetaAttribute called for attr="
                + (attr != null ? attr.getName() : "null") + " with input=[" + input + "]");
        if (attr == null || input == null || input.isBlank())
            return attr;
        String raw = input.trim();
        String currentType = mapEcoreToKarpfenType(attr.getEAttributeType());
        String snippet;

        if (raw.startsWith("prop(")) {
            snippet = raw;
        } else if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            String name = parts[0].trim().replace("\"", "");
            String typePart = parts[1].trim();

            if (name.contains(" ") || name.isEmpty()) {
                snippet = raw;
            } else if (typePart.startsWith("list(") && typePart.endsWith(")")) {
                String inner = typePart.substring(5, typePart.length() - 1).trim().replace("\"", "");
                snippet = inner.contains(" ") || inner.isEmpty() ? raw
                        : String.format("prop(\"%s\", list(\"%s\"))", name, inner);
            } else if (typePart.equalsIgnoreCase("list") || typePart.endsWith("[]") || typePart.endsWith("[list]")) {
                String inner = typePart.replace("[list]", "").replace("[]", "").replace("list", "").trim().replace("\"",
                        "");
                snippet = String.format("prop(\"%s\", list(\"%s\"))", name, inner.isEmpty() ? currentType : inner);
            } else {
                String cleanedType = typePart.replace("\"", "").trim();
                snippet = cleanedType.contains(" ") || cleanedType.isEmpty() ? raw
                        : String.format("prop(\"%s\", \"%s\")", name, cleanedType);
            }
        } else if (raw.endsWith("[list]") || raw.endsWith("[]")) {
            String name = raw.replace("[list]", "").replace("[]", "").trim().replace("\"", "");
            snippet = name.contains(" ") || name.isEmpty() ? raw
                    : String.format("prop(\"%s\", list(\"%s\"))", name, currentType);
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n") && !raw.contains("(")
                && !raw.contains(")")) {
            snippet = String.format("prop(\"%s\", \"%s\")", raw.replace("\"", ""), currentType);
        } else {
            snippet = raw;
        }

        try {
            KmetaParser parser = createKmetaParser(snippet);
            KmetaParser.Prop_ruleContext ctx = parser.prop_rule();

            String name = unquote(ctx.STRING().getText());
            boolean isList = ctx.rule_value().LIST() != null;
            String typeStr = unquote(ctx.rule_value().STRING().getText());

            attr.setName(name);
            attr.setUpperBound(isList ? ETypedElement.UNBOUNDED_MULTIPLICITY : 1);
            attr.setEType(mapKarpfenToEcoreType(typeStr));
            markTargetResourceDirty(attr);
            KarpfenLog.trace("DirectEdit-KMeta",
                    "Updated attr: name=" + name + ", type=" + typeStr + ", isList=" + isList);
        } catch (ParseCancellationException e) {
            KarpfenLog.warn("Direct-Edit Input rejected by KMeta prop_rule: " + input);
        }
        return attr;
    }

    public EReference editKMetaEdge(EReference ref, String input) {
        KarpfenLog.trace("DirectEdit-KMeta", "editKMetaEdge called for ref=" + (ref != null ? ref.getName() : "null")
                + " with input=[" + input + "]");
        if (ref == null || input == null || input.isBlank())
            return ref;
        String raw = input.trim();
        String kw = ref.isContainment() ? "has" : "knows";
        String existingTarget = (ref.getEType() != null && ref.getEType().getName() != null) ? ref.getEType().getName()
                : "Type";
        String snippet;

        if (raw.startsWith("has(") || raw.startsWith("knows(")) {
            snippet = raw;
        } else if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            String name = parts[0].trim().replace("\"", "");
            String typePart = parts[1].trim();
            if (name.contains(" ") || name.isEmpty()) {
                snippet = raw;
            } else if (typePart.startsWith("list(") && typePart.endsWith(")")) {
                String inner = typePart.substring(5, typePart.length() - 1).trim().replace("\"", "");
                snippet = inner.contains(" ") || inner.isEmpty() ? raw
                        : String.format("%s(\"%s\", list(\"%s\"))", kw, name, inner);
            } else if (typePart.endsWith("[list]") || typePart.endsWith("[*]") || typePart.endsWith("[0..*]")
                    || typePart.endsWith("[]")) {
                String inner = typePart.substring(0, typePart.indexOf('[')).trim().replace("\"", "");
                snippet = String.format("%s(\"%s\", list(\"%s\"))", kw, name, inner.isEmpty() ? existingTarget : inner);
            } else {
                String cleanedType = typePart.replace("\"", "").trim();
                snippet = cleanedType.contains(" ") || cleanedType.isEmpty() ? raw
                        : String.format("%s(\"%s\", \"%s\")", kw, name, cleanedType);
            }
        } else if (raw.endsWith("[list]") || raw.endsWith("[*]") || raw.endsWith("[0..*]") || raw.endsWith("[]")) {
            String name = raw.substring(0, raw.indexOf('[')).trim().replace("\"", "");
            snippet = name.contains(" ") || name.isEmpty() ? raw
                    : String.format("%s(\"%s\", list(\"%s\"))", kw, name, existingTarget);
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n") && !raw.contains("(")
                && !raw.contains(")")) {
            snippet = String.format("%s(\"%s\", \"%s\")", kw, raw.replace("\"", ""), existingTarget);
        } else {
            snippet = raw;
        }

        try {
            String targetTypeStr;
            boolean isList;
            String refName;

            if (ref.isContainment()) {
                KmetaParser parser = createKmetaParser(snippet);
                KmetaParser.Has_ruleContext ctx = parser.has_rule();
                refName = unquote(ctx.STRING().getText());
                isList = ctx.rule_value().LIST() != null;
                targetTypeStr = unquote(ctx.rule_value().STRING().getText());
            } else {
                KmetaParser parser = createKmetaParser(snippet);
                KmetaParser.Knows_ruleContext ctx = parser.knows_rule();
                refName = unquote(ctx.STRING().getText());
                isList = ctx.rule_value().LIST() != null;
                targetTypeStr = unquote(ctx.rule_value().STRING().getText());
            }

            ref.setName(refName);
            ref.setUpperBound(isList ? ETypedElement.UNBOUNDED_MULTIPLICITY : 1);
            updateReferenceTargetType(ref, targetTypeStr);
            markTargetResourceDirty(ref);
            KarpfenLog.trace("DirectEdit-KMeta",
                    "Updated ref: name=" + refName + ", targetType=" + targetTypeStr + ", isList=" + isList);
        } catch (ParseCancellationException e) {
            KarpfenLog.warn("Direct-Edit Input rejected by KMeta relation rule: " + input);
        }
        return ref;
    }

    public EObject toggleRootClass(EClass clas) {
        if (clas == null || clas.getEPackage() == null)
            return clas;
        EPackage pkg = clas.getEPackage();
        EAnnotation ann = pkg.getEAnnotation(KARPFEN_URI);
        if (ann == null) {
            ann = EcoreFactory.eINSTANCE.createEAnnotation();
            ann.setSource(KARPFEN_URI);
            pkg.getEAnnotations().add(ann);
        }
        String currentRoot = ann.getDetails().get("rootClass");
        if (clas.getName().equals(currentRoot)) {
            ann.getDetails().remove("rootClass");
        } else {
            ann.getDetails().put("rootClass", clas.getName());
        }
        markTargetResourceDirty(clas);
        return clas;
    }

    // ! KModel visual projections - UML object diagramm

    public String getObjectHeaderLabel(EObject self) {
        if (self == null)
            return "";
        EObject target = resolveSemanticTarget(self);
        EClass eClass = resolveEClass(target);
        String className = (eClass != null && eClass.getName() != null) ? eClass.getName() : "Object";

        EStructuralFeature idFeature = (eClass != null) ? eClass.getEStructuralFeature(ID_FEATURE_NAME) : null;
        Object idVal = null;
        if (idFeature != null) {
            try {
                if (target.eIsSet(idFeature)) {
                    idVal = target.eGet(idFeature);
                }
            } catch (Throwable ignored) {
            }
        }
        String idStr = (idVal != null && !idVal.toString().isBlank()) ? idVal.toString().trim() : "_";
        return idStr + " : " + className;
    }

    public List<EAttribute> getPopulatedAttributes(EObject self) {
        if (self == null)
            return Collections.emptyList();
        EObject target = resolveSemanticTarget(self);
        if (target == null)
            return Collections.emptyList();
        EClass eClass = resolveEClass(target);
        if (eClass == null || eClass.eIsProxy())
            return Collections.emptyList();

        List<EAttribute> result = new ArrayList<>();
        for (EAttribute attr : eClass.getEAllAttributes()) {
            if (ID_FEATURE_NAME.equals(attr.getName())) {
                continue;
            }
            try {
                EStructuralFeature feature = target.eClass().getEStructuralFeature(attr.getName());
                if (feature != null && target.eIsSet(feature)) {
                    result.add(attr);
                }
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    public String getKModelSlotLabel(EAttribute attr, EObject context) {
        if (attr == null)
            return "";
        EObject target = resolveSemanticTarget(context);
        if (target == null)
            return attr.getName() + " = ";

        String featName = attr.getName();
        if (featName == null)
            return "";

        EClass targetClass = resolveEClass(target);
        EStructuralFeature feature = (targetClass != null) ? targetClass.getEStructuralFeature(featName) : null;
        if (feature == null && target.eClass() != null) {
            feature = target.eClass().getEStructuralFeature(featName);
        }
        if (feature == null) {
            feature = attr;
        }

        Object val = null;
        try {
            if (target.eIsSet(feature)) {
                val = target.eGet(feature);
            }
        } catch (Throwable t1) {
            try {
                val = target.eGet(feature);
            } catch (Throwable ignored) {
            }
        }

        if (val == null) {
            return featName + " = ";
        }

        if (feature.isMany() && val instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                sb.append(featName).append(" = ").append(list.get(i));
                if (i < list.size() - 1)
                    sb.append("\n");
            }
            return sb.toString();
        }
        return featName + " = " + val.toString();
    }

    public String getKModelSlotLabel(EObject context, EAttribute attr) {
        return getKModelSlotLabel(attr, context);
    }

    // ! KMeta ANTLR micro parser, input subsitution

    public EObject editKModelObjectHeader(EObject self, String input) {
        KarpfenLog.trace("DirectEdit-KModel", "editKModelObjectHeader called with input=[" + input + "]");
        if (self == null || input == null || input.isBlank())
            return self;
        EObject target = resolveSemanticTarget(self);
        if (target == null)
            return self;

        String raw = input.trim();
        String snippet;

        if (raw.startsWith("make object")) {
            snippet = raw.substring("make object".length()).trim();
        } else if (raw.startsWith("make")) {
            snippet = raw.substring("make".length()).trim();
        } else if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            String id = parts[0].trim().replace("\"", "");
            String cls = parts[1].trim().replace("\"", "");
            if (id.contains(" ") || cls.contains(" ") || cls.isEmpty()) {
                snippet = raw;
            } else {
                snippet = String.format("\"%s\":\"%s\"", id, cls);
            }
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n") && !raw.contains("(")
                && !raw.contains(")")) {
            snippet = "\"" + raw.replace("\"", "") + "\":\"" + getClassName(target) + "\"";
        } else {
            snippet = raw;
        }

        try {
            KmodelParser parser = createKmodelParser(snippet);
            KmodelParser.Object_signatureContext ctx = parser.object_signature();

            String newId = unquote(ctx.children.get(0).getText());
            EClass eClass = resolveEClass(target);
            if (eClass != null) {
                EStructuralFeature idFeature = eClass.getEStructuralFeature(ID_FEATURE_NAME);
                if (idFeature != null) {
                    target.eSet(idFeature, newId);
                    markTargetResourceDirty(target);
                    KarpfenLog.trace("DirectEdit-KModel", "Updated object id to: " + newId);
                }
            }
        } catch (ParseCancellationException e) {
            KarpfenLog.warn("Direct-Edit Invalid object header rejected: " + input);
        }
        return target;
    }

    public EObject editKModelSlotValue(EAttribute attr, EObject context, String input) {
        KarpfenLog.trace("DirectEdit-KModel", "editKModelSlotValue called with attr="
                + (attr != null ? attr.getName() : "null") + ", input=[" + input + "]");
        EObject target = resolveSemanticTarget(context);
        if (target != null && attr != null) {
            editKModelSlot(target, attr, input);
        } else {
            KarpfenLog.warn("editKModelSlotValue failed: target=" + target + ", attr=" + attr);
        }
        return attr;
    }

    public EObject editKModelSlotValue(EObject context, EAttribute attr, String input) {
        return editKModelSlotValue(attr, context, input);
    }

    public EObject editKModelSlot(EObject container, EAttribute attr, String input) {
        KarpfenLog.trace("DirectEdit-KModel", "editKModelSlot executing on container=" + container + ", attr="
                + (attr != null ? attr.getName() : "null") + ", input=[" + input + "]");
        if (container == null || attr == null || input == null || input.isBlank())
            return container;
        EObject target = resolveSemanticTarget(container);
        if (target == null) {
            KarpfenLog.warn("editKModelSlot: resolveSemanticTarget returned null for container=" + container);
            return container;
        }

        String raw = input.trim();
        String featureName = attr.getName();
        String propValue = raw;

        if (raw.startsWith("prop(") && raw.contains("->")) {
            try {
                KmodelParser parser = createKmodelParser(raw);
                KmodelParser.Prop_statementContext ctx = parser.prop_statement();
                featureName = unquote(ctx.STRING(0).getText());
                propValue = unquote(ctx.STRING(1).getText());
            } catch (ParseCancellationException e) {
                KarpfenLog.warn("Direct-Edit Input rejected by KModel prop_statement: " + input);
                return container;
            }
        } else if (raw.contains("->")) {
            String[] parts = raw.split("->", 2);
            String k = unquote(parts[0].replace("prop(", "").replace(")", "").trim());
            if (!k.isEmpty())
                featureName = k;
            propValue = unquote(parts[1].trim());
        } else if (raw.contains("=")) {
            String[] parts = raw.split("=", 2);
            String k = unquote(parts[0].trim());
            if (!k.isEmpty())
                featureName = k;
            propValue = unquote(parts[1].trim());
        } else if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            String k = unquote(parts[0].trim());
            if (!k.isEmpty())
                featureName = k;
            propValue = unquote(parts[1].trim());
        } else {
            propValue = unquote(raw);
        }

        EClass targetClass = resolveEClass(target);
        if (targetClass == null) {
            targetClass = target.eClass();
        }
        if (targetClass == null)
            return container;

        EAttribute targetAttr = null;
        EStructuralFeature feature = targetClass.getEStructuralFeature(featureName);
        if (feature instanceof EAttribute ea) {
            targetAttr = ea;
        } else {
            for (EAttribute a : targetClass.getEAllAttributes()) {
                if (featureName.equals(a.getName())) {
                    targetAttr = a;
                    break;
                }
            }
        }
        if (targetAttr == null && target.eClass() != null) {
            for (EAttribute a : target.eClass().getEAllAttributes()) {
                if (featureName.equals(a.getName())) {
                    targetAttr = a;
                    break;
                }
            }
        }
        if (targetAttr == null) {
            targetAttr = attr;
        }

        try {
            Object converted = convertStringToValue(propValue, targetAttr.getEAttributeType());
            EStructuralFeature actualFeat = target.eClass().getEStructuralFeature(targetAttr.getName());
            if (actualFeat == null) {
                actualFeat = targetAttr;
            }

            if (actualFeat.isMany()) {
                Object rawList = target.eGet(actualFeat);
                if (rawList instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<Object> mList = (List<Object>) list;
                    mList.clear();
                    mList.add(converted);
                }
            } else {
                target.eSet(actualFeat, converted);
            }
            markTargetResourceDirty(target);
            KarpfenLog
                    .info("[DirectEdit-KModel] Successfully set " + featureName + " = " + converted + " on " + target);
        } catch (Throwable t) {
            KarpfenLog.warn("Direct-Edit conversion failed for " + featureName + " value [" + propValue + "]: "
                    + t.getMessage());
        }

        return target;
    }

    public EObject editKModelSlot(EAttribute attr, EObject container, String input) {
        return editKModelSlot(container, attr, input);
    }

    // KModel create operations palette

    public EObject createNewKModelObject(EObject self, EObject container) {
        EObject root = resolveSemanticTarget(container != null ? container : self);
        if (root == null || root.eClass() == null || root.eClass().getEPackage() == null)
            return root;

        EPackage pkg = root.eClass().getEPackage();
        EClass targetClass = null;
        for (EClassifier classifier : pkg.getEClassifiers()) {
            if (classifier instanceof EClass ec && !ec.isAbstract() && !ec.getName().equals(root.eClass().getName())) {
                targetClass = ec;
                break;
            }
        }
        if (targetClass == null) {
            targetClass = root.eClass();
        }

        EObject newInstance = EcoreUtil.create(targetClass);
        EStructuralFeature idFeat = targetClass.getEStructuralFeature(ID_FEATURE_NAME);
        if (idFeat != null) {
            String generatedId = targetClass.getName().toLowerCase() + "_" + (System.currentTimeMillis() % 1000);
            newInstance.eSet(idFeat, generatedId);
        }

        boolean attached = false;
        for (EReference ref : root.eClass().getEAllContainments()) {
            if (ref.getEReferenceType().isSuperTypeOf(targetClass)) {
                if (ref.isMany()) {
                    @SuppressWarnings("unchecked")
                    List<EObject> list = (List<EObject>) root.eGet(ref);
                    list.add(newInstance);
                } else if (root.eGet(ref) == null) {
                    root.eSet(ref, newInstance);
                }
                attached = true;
                break;
            }
        }

        if (!attached && root.eResource() != null) {
            root.eResource().getContents().add(newInstance);
        }

        markTargetResourceDirty(root);
        KarpfenLog.info("[Palette-KModel] Created new instance of " + targetClass.getName() + " on " + root);
        return newInstance;
    }

    public EObject createInstanceLink(EObject source, EObject target, boolean isContainment) {
        EObject src = resolveSemanticTarget(source);
        EObject tgt = resolveSemanticTarget(target);
        if (src == null || tgt == null || src.eClass() == null || tgt.eClass() == null)
            return src;

        for (EReference ref : src.eClass().getEAllReferences()) {
            if (ref.isContainment() == isContainment && ref.getEReferenceType().isSuperTypeOf(tgt.eClass())) {
                if (ref.isMany()) {
                    @SuppressWarnings("unchecked")
                    List<EObject> list = (List<EObject>) src.eGet(ref);
                    if (!list.contains(tgt)) {
                        list.add(tgt);
                    }
                } else {
                    src.eSet(ref, tgt);
                }
                markTargetResourceDirty(src);
                KarpfenLog.info("[Palette-KModel] Linked " + src + " -> " + tgt + " via " + ref.getName());
                break;
            }
        }
        return src;
    }

    // Hack to mark open files dirty, required for synchronization

    private void markTargetResourceDirty(EObject context) {
        if (context == null)
            return;
        Resource directRes = context.eResource();
        if (directRes == null && context instanceof EAttribute attr && attr.getEContainingClass() != null) {
            directRes = attr.getEContainingClass().eResource();
        }
        if (directRes != null) {
            directRes.setModified(true);
            KarpfenLog.trace("DirtyHook", "Flagged target resource as dirty: " + directRes.getURI());
        }
    }

    // ANTRL parser factories for input validations

    private KmetaParser createKmetaParser(String snippet) {
        CharStream stream = CharStreams.fromString(snippet);
        KmetaLexer lexer = new KmetaLexer(stream);
        lexer.removeErrorListeners();

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        KmetaParser parser = new KmetaParser(tokens);
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        return parser;
    }

    private KmodelParser createKmodelParser(String snippet) {
        CharStream stream = CharStreams.fromString(snippet);
        KmodelLexer lexer = new KmodelLexer(stream);
        lexer.removeErrorListeners();

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        KmodelParser parser = new KmodelParser(tokens);
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        return parser;
    }

    // Helpers

    private EObject resolveSemanticTarget(EObject context) {
        if (context instanceof DSemanticDecorator decorator) {
            EObject target = decorator.getTarget();
            if (target != null && !(target instanceof EAttribute) && !(target instanceof EReference)
                    && !(target instanceof EPackage)) {
                return target;
            }
            EObject curr = decorator.eContainer();
            while (curr != null) {
                if (curr instanceof DSemanticDecorator parentDec) {
                    EObject parentTarget = parentDec.getTarget();
                    if (parentTarget != null && !(parentTarget instanceof EAttribute)
                            && !(parentTarget instanceof EReference) && !(parentTarget instanceof EPackage)) {
                        return parentTarget;
                    }
                }
                curr = curr.eContainer();
            }
            return target;
        }
        return context;
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

    private void updateReferenceTargetType(EReference ref, String targetTypeName) {
        if (ref.getEContainingClass() != null && ref.getEContainingClass().getEPackage() != null) {
            EClassifier targetClassifier = ref.getEContainingClass().getEPackage().getEClassifier(targetTypeName);
            if (targetClassifier instanceof EClass targetEClass) {
                ref.setEType(targetEClass);
            }
        }
    }

    private Object convertStringToValue(String val, EClassifier classifier) {
        if (classifier == null || val == null)
            return val;
        String cleanVal = unquote(val);
        String name = classifier.getName();
        if (name == null) {
            name = classifier.getInstanceClassName();
        }
        String lower = name != null ? name.toLowerCase() : "";

        if (lower.contains("double") || lower.contains("float") || lower.contains("number")
                || classifier == EcorePackage.Literals.EDOUBLE || classifier == EcorePackage.Literals.EFLOAT) {
            return Double.parseDouble(cleanVal);
        }
        if (lower.contains("int") || lower.contains("long")
                || classifier == EcorePackage.Literals.EINT || classifier == EcorePackage.Literals.ELONG) {
            return Integer.parseInt(cleanVal);
        }
        if (lower.contains("bool") || classifier == EcorePackage.Literals.EBOOLEAN) {
            return Boolean.parseBoolean(cleanVal);
        }
        return cleanVal;
    }

    private String mapEcoreToKarpfenType(EClassifier classifier) {
        if (classifier == null)
            return "string";
        String name = classifier.getName();
        if (name == null) {
            name = classifier.getInstanceClassName();
        }
        if (name == null)
            return "string";
        String lower = name.toLowerCase();
        if (lower.contains("double") || lower.contains("float") || lower.contains("int") || lower.contains("long")
                || lower.contains("number")) {
            return "number";
        }
        if (lower.contains("bool"))
            return "boolean";
        return "string";
    }

    private EClassifier mapKarpfenToEcoreType(String karpfenType) {
        String lower = (karpfenType != null) ? karpfenType.toLowerCase().trim() : "string";
        if ("number".equals(lower))
            return EcorePackage.Literals.EDOUBLE;
        if ("boolean".equals(lower))
            return EcorePackage.Literals.EBOOLEAN;
        return EcorePackage.Literals.ESTRING;
    }

    private String unquote(String text) {
        if (text == null)
            return "";
        return text.trim().replaceAll("^[\"']|[\"']$", "");
    }
}