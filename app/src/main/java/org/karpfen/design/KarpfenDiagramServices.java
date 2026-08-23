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
import org.eclipse.emf.common.util.TreeIterator;
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
        if (clas == null || input == null || input.isBlank())
            return clas;
        String raw = input.trim();
        String oldName = clas.getName();

        if (raw.startsWith("<root>")) {
            raw = raw.substring("<root>".length()).trim();
        }

        String snippet;
        if (raw.startsWith("type")) {
            snippet = raw;
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n") && !raw.contains("(")
                && !raw.contains(")")) {
            snippet = "type \"" + raw.replace("\"", "") + "\" \"\" {}";
        } else {
            snippet = raw;
        }

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
            }
        } catch (ParseCancellationException e) {
            System.err.println("[Karpfen] Direct-Edit Invalid type rejected: " + input);
        }
        return clas;
    }

    public EAttribute editKMetaAttribute(EAttribute attr, String input) {
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
                if (inner.contains(" ") || inner.isEmpty()) {
                    snippet = raw;
                } else {
                    snippet = String.format("prop(\"%s\", list(\"%s\"))", name, inner);
                }
            } else if (typePart.equalsIgnoreCase("list") || typePart.endsWith("[]") || typePart.endsWith("[list]")) {
                String inner = typePart.replace("[list]", "").replace("[]", "").replace("list", "").trim().replace("\"",
                        "");
                snippet = String.format("prop(\"%s\", list(\"%s\"))", name, inner.isEmpty() ? currentType : inner);
            } else {
                String cleanedType = typePart.replace("\"", "").trim();
                if (cleanedType.contains(" ") || cleanedType.isEmpty()) {
                    snippet = raw;
                } else {
                    snippet = String.format("prop(\"%s\", \"%s\")", name, cleanedType);
                }
            }
        } else if (raw.endsWith("[list]") || raw.endsWith("[]")) {
            String name = raw.replace("[list]", "").replace("[]", "").trim().replace("\"", "");
            if (name.contains(" ") || name.isEmpty()) {
                snippet = raw;
            } else {
                snippet = String.format("prop(\"%s\", list(\"%s\"))", name, currentType);
            }
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n") && !raw.contains("(")
                && !raw.contains(")")) {
            snippet = String.format("prop(\"%s\", \"%s\")", raw.replace("\"", ""), currentType);
        } else {
            snippet = raw;
        }

        try {
            boolean wasMany = attr.isMany();
            KmetaParser parser = createKmetaParser(snippet);
            KmetaParser.Prop_ruleContext ctx = parser.prop_rule();

            String name = unquote(ctx.STRING().getText());
            boolean isList = ctx.rule_value().LIST() != null;
            String typeStr = unquote(ctx.rule_value().STRING().getText());

            attr.setName(name);
            attr.setUpperBound(isList ? ETypedElement.UNBOUNDED_MULTIPLICITY : 1);
            attr.setEType(mapKarpfenToEcoreType(typeStr));
            synchronizeFeatureMultiplicity(attr, wasMany, isList);
        } catch (ParseCancellationException e) {
            System.err.println("[Karpfen] Direct-Edit Input rejected by KMeta prop_rule: " + input);
        }
        return attr;
    }

    public EReference editKMetaEdge(EReference ref, String input) {
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
                if (inner.contains(" ") || inner.isEmpty()) {
                    snippet = raw;
                } else {
                    snippet = String.format("%s(\"%s\", list(\"%s\"))", kw, name, inner);
                }
            } else if (typePart.endsWith("[list]") || typePart.endsWith("[*]") || typePart.endsWith("[0..*]")
                    || typePart.endsWith("[]")) {
                String inner = typePart.substring(0, typePart.indexOf('[')).trim().replace("\"", "");
                snippet = String.format("%s(\"%s\", list(\"%s\"))", kw, name, inner.isEmpty() ? existingTarget : inner);
            } else {
                String cleanedType = typePart.replace("\"", "").trim();
                if (cleanedType.contains(" ") || cleanedType.isEmpty()) {
                    snippet = raw;
                } else {
                    snippet = String.format("%s(\"%s\", \"%s\")", kw, name, cleanedType);
                }
            }
        } else if (raw.endsWith("[list]") || raw.endsWith("[*]") || raw.endsWith("[0..*]") || raw.endsWith("[]")) {
            String name = raw.substring(0, raw.indexOf('[')).trim().replace("\"", "");
            if (name.contains(" ") || name.isEmpty()) {
                snippet = raw;
            } else {
                snippet = String.format("%s(\"%s\", list(\"%s\"))", kw, name, existingTarget);
            }
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n") && !raw.contains("(")
                && !raw.contains(")")) {
            snippet = String.format("%s(\"%s\", \"%s\")", kw, raw.replace("\"", ""), existingTarget);
        } else {
            snippet = raw;
        }

        try {
            boolean wasMany = ref.isMany();
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
            synchronizeFeatureMultiplicity(ref, wasMany, isList);
        } catch (ParseCancellationException e) {
            System.err.println("[Karpfen] Direct-Edit Input rejected by KMeta relation rule: " + input);
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
        return clas;
    }

    // ! KModel visual projections - UML object diagramm

    public String getObjectHeaderLabel(EObject self) {
        if (self == null)
            return "";
        EClass eClass = resolveEClass(self);
        String className = (eClass != null && eClass.getName() != null) ? eClass.getName() : "Object";

        EStructuralFeature idFeature = (eClass != null) ? eClass.getEStructuralFeature(ID_FEATURE_NAME) : null;
        Object idVal = (idFeature != null && self.eIsSet(idFeature)) ? self.eGet(idFeature) : null;
        String idStr = (idVal != null && !idVal.toString().isBlank()) ? idVal.toString().trim() : "_";

        return idStr + " : " + className;
    }

    public List<EAttribute> getPopulatedAttributes(EObject self) {
        if (self == null)
            return Collections.emptyList();
        EClass eClass = resolveEClass(self);
        if (eClass == null)
            return Collections.emptyList();

        List<EAttribute> result = new ArrayList<>();
        for (EAttribute attr : eClass.getEAllAttributes()) {
            if (!ID_FEATURE_NAME.equals(attr.getName()) && self.eIsSet(attr)) {
                result.add(attr);
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

        EClass targetClass = resolveEClass(target);
        EStructuralFeature feature = (targetClass != null) ? targetClass.getEStructuralFeature(attr.getName()) : attr;
        if (feature == null) {
            feature = attr;
        }

        Object val = null;
        try {
            if (target.eIsSet(feature)) {
                val = target.eGet(feature);
            }
        } catch (Throwable t) {
            if (targetClass != null) {
                for (EAttribute a : targetClass.getEAllAttributes()) {
                    if (a.getName() != null && a.getName().equals(attr.getName())) {
                        try {
                            if (target.eIsSet(a)) {
                                val = target.eGet(a);
                            }
                        } catch (Throwable ignored) {
                        }
                        break;
                    }
                }
            }
        }

        if (val == null) {
            return attr.getName() + " = ";
        }

        if (feature.isMany() && val instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                sb.append(feature.getName()).append(" = ").append(list.get(i));
                if (i < list.size() - 1)
                    sb.append("\n");
            }
            return sb.toString();
        }
        return feature.getName() + " = " + val.toString();
    }

    public String getKModelSlotLabel(EObject context, EAttribute attr) {
        return getKModelSlotLabel(attr, context);
    }

    // ! KMeta ANTLR micro parser, input subsitution

    public EObject editKModelObjectHeader(EObject self, String input) {
        if (self == null || input == null || input.isBlank())
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
            snippet = "\"" + raw.replace("\"", "") + "\":\"" + getClassName(self) + "\"";
        } else {
            snippet = raw;
        }

        try {
            KmodelParser parser = createKmodelParser(snippet);
            KmodelParser.Object_signatureContext ctx = parser.object_signature();

            String newId = unquote(ctx.children.get(0).getText());
            EClass eClass = resolveEClass(self);
            if (eClass != null) {
                EStructuralFeature idFeature = eClass.getEStructuralFeature(ID_FEATURE_NAME);
                if (idFeature != null) {
                    self.eSet(idFeature, newId);
                }
            }
        } catch (ParseCancellationException e) {
            System.err.println("[Karpfen] Direct-Edit Invalid object header rejected: " + input);
        }
        return self;
    }

    public EObject editKModelSlotValue(EAttribute attr, EObject context, String input) {
        EObject target = resolveSemanticTarget(context);
        return editKModelSlot(target, attr, input);
    }

    public EObject editKModelSlot(EObject container, EAttribute attr, String input) {
        if (container == null || attr == null || input == null || input.isBlank())
            return container;
        EObject target = resolveSemanticTarget(container);
        if (target == null)
            return container;

        EClass targetClass = resolveEClass(target);
        EStructuralFeature feature = (targetClass != null) ? targetClass.getEStructuralFeature(attr.getName()) : attr;
        if (!(feature instanceof EAttribute targetAttr)) {
            return container;
        }

        String raw = input.trim();
        String snippet;

        if (raw.startsWith("prop(") || raw.contains("->")) {
            snippet = raw;
        } else if (raw.contains("=")) {
            String[] parts = raw.split("=", 2);
            String key = parts[0].trim().replace("\"", "");
            String val = parts[1].trim().replace("\"", "");
            if (key.contains(" ") || key.isEmpty()) {
                snippet = raw;
            } else {
                snippet = String.format("prop(\"%s\") -> \"%s\"", key, val);
            }
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n") && !raw.contains("(")
                && !raw.contains(")")) {
            snippet = String.format("prop(\"%s\") -> \"%s\"", targetAttr.getName(), raw.replace("\"", ""));
        } else {
            snippet = raw;
        }

        try {
            KmodelParser parser = createKmodelParser(snippet);
            KmodelParser.Prop_statementContext ctx = parser.prop_statement();

            String propValue = unquote(ctx.STRING(1).getText());
            Object converted = convertStringToValue(propValue, targetAttr.getEAttributeType());

            if (targetAttr.isMany()) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) target.eGet(targetAttr);
                list.add(converted);
            } else {
                target.eSet(targetAttr, converted);
            }
        } catch (ParseCancellationException e) {
            System.err.println("[Karpfen] Direct-Edit Input rejected by KModel prop_statement: " + input);
        }
        return container;
    }

    public EObject editKModelSlot(EAttribute attr, EObject container, String input) {
        return editKModelSlot(container, attr, input);
    }

    // ANTLR micro-parse validator for label edits

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

    // helpers

    private void synchronizeFeatureMultiplicity(EStructuralFeature feature, boolean wasMany, boolean isMany) {
        if (wasMany == isMany || feature.getEContainingClass() == null) {
            return;
        }
        EClass containingClass = feature.getEContainingClass();
        EPackage pkg = containingClass.getEPackage();
        if (pkg == null || pkg.eResource() == null || pkg.eResource().getResourceSet() == null) {
            return;
        }

        for (Resource res : pkg.eResource().getResourceSet().getResources()) {
            TreeIterator<EObject> allContents = res.getAllContents();
            while (allContents.hasNext()) {
                EObject obj = allContents.next();
                if (obj.eClass() == containingClass || containingClass.isSuperTypeOf(obj.eClass())) {
                    if (obj.eIsSet(feature)) {
                        try {
                            if (isMany) {
                                Object val = obj.eGet(feature);
                                if (val != null && !(val instanceof List<?>)) {
                                    obj.eUnset(feature);
                                    @SuppressWarnings("unchecked")
                                    List<Object> list = (List<Object>) obj.eGet(feature);
                                    list.add(val);
                                }
                            } else {
                                Object val = obj.eGet(feature);
                                if (val instanceof List<?> list) {
                                    Object first = list.isEmpty() ? null : list.get(0);
                                    obj.eUnset(feature);
                                    if (first != null) {
                                        obj.eSet(feature, first);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                            obj.eUnset(feature);
                        }
                    }
                }
            }
        }
    }

    private EObject resolveSemanticTarget(EObject context) {
        if (context instanceof DSemanticDecorator decorator) {
            EObject target = decorator.getTarget();
            if (target != null && !(target instanceof EAttribute) && !(target instanceof EReference)) {
                return target;
            }
            if (decorator.eContainer() instanceof DSemanticDecorator parentDecorator) {
                return parentDecorator.getTarget();
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
        String name = classifier.getName().toLowerCase();
        if (name.contains("double") || name.contains("float") || name.contains("number")) {
            return Double.parseDouble(val);
        }
        if (name.contains("int") || name.contains("long")) {
            return Integer.parseInt(val);
        }
        if (name.contains("bool")) {
            return Boolean.parseBoolean(val);
        }
        return val;
    }

    private String mapEcoreToKarpfenType(EClassifier classifier) {
        if (classifier == null)
            return "string";
        String name = classifier.getName();
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