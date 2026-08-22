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
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

public class KarpfenDiagramServices {

    public static final String KARPFEN_URI = "https://github/karpfen/annotation";
    public static final String ID_FEATURE_NAME = "__id__";

    // KMeta visuals helpers & direct edit

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
            return "prop(\"" + attr.getName() + "\", list(\"" + typeName + "\"))";
        }
        return "prop(\"" + attr.getName() + "\", \"" + typeName + "\")";
    }

    public String getKMetaEdgeLabel(EReference ref) {
        if (ref == null || ref.getName() == null)
            return "";
        String targetType = (ref.getEType() != null && ref.getEType().getName() != null) ? ref.getEType().getName()
                : "Type";
        String keyword = ref.isContainment() ? "has" : "knows";
        if (ref.isMany() || ref.getUpperBound() == ETypedElement.UNBOUNDED_MULTIPLICITY) {
            return keyword + "(\"" + ref.getName() + "\", list(\"" + targetType + "\"))";
        }
        return keyword + "(\"" + ref.getName() + "\", \"" + targetType + "\")";
    }

    public EClass editClassName(EClass clas, String input) {
        if (clas == null || input == null || input.isBlank())
            return clas;
        String raw = input.trim();
        String snippet;
        if (raw.startsWith("type")) {
            snippet = raw;
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n")) {
            snippet = "type \"" + raw.replace("\"", "") + "\" \"\" {}";
        } else {
            snippet = raw;
        }

        try {
            KmetaParser parser = createKmetaParser(snippet);
            KmetaParser.Type_definitionContext ctx = parser.type_definition();
            String name = unquote(ctx.STRING(0).getText());
            if (!name.isBlank()) {
                clas.setName(name);
            }
        } catch (ParseCancellationException e) {
            System.err.println("[Karpfen] Direct-Edit Invalid type name rejected: " + input);
        }
        return clas;
    }

    public EAttribute editKMetaAttribute(EAttribute attr, String input) {
        if (attr == null || input == null || input.isBlank())
            return attr;
        String raw = input.trim();
        String snippet = raw.startsWith("prop(") ? raw : "prop(" + raw + ")";

        try {
            KmetaParser parser = createKmetaParser(snippet);
            KmetaParser.Prop_ruleContext ctx = parser.prop_rule();

            String name = unquote(ctx.STRING().getText());
            boolean isList = ctx.rule_value().LIST() != null;
            String typeStr = unquote(ctx.rule_value().STRING().getText());

            attr.setName(name);
            attr.setUpperBound(isList ? ETypedElement.UNBOUNDED_MULTIPLICITY : 1);
            attr.setEType(mapKarpfenToEcoreType(typeStr));
        } catch (ParseCancellationException e) {
            System.err.println("[Karpfen] Direct-Edit Input rejected by KMeta prop_rule: " + input);
        }
        return attr;
    }

    public EReference editKMetaEdge(EReference ref, String input) {
        if (ref == null || input == null || input.isBlank())
            return ref;
        String raw = input.trim();

        try {
            if (ref.isContainment()) {
                String snippet = raw.startsWith("has(") ? raw : "has(" + raw + ")";
                KmetaParser parser = createKmetaParser(snippet);
                KmetaParser.Has_ruleContext ctx = parser.has_rule();

                String relName = unquote(ctx.STRING().getText());
                boolean isList = ctx.rule_value().LIST() != null;
                String targetType = unquote(ctx.rule_value().STRING().getText());

                ref.setName(relName);
                ref.setUpperBound(isList ? ETypedElement.UNBOUNDED_MULTIPLICITY : 1);
                updateReferenceTargetType(ref, targetType);
            } else {
                String snippet = raw.startsWith("knows(") ? raw : "knows(" + raw + ")";
                KmetaParser parser = createKmetaParser(snippet);
                KmetaParser.Knows_ruleContext ctx = parser.knows_rule();

                String relName = unquote(ctx.STRING().getText());
                boolean isList = ctx.rule_value().LIST() != null;
                String targetType = unquote(ctx.rule_value().STRING().getText());

                ref.setName(relName);
                ref.setUpperBound(isList ? ETypedElement.UNBOUNDED_MULTIPLICITY : 1);
                updateReferenceTargetType(ref, targetType);
            }
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

    // KModel visuals helpers & direct edit

    public String getObjectHeaderLabel(EObject self) {
        if (self == null)
            return "";
        EClass eClass = resolveEClass(self);
        String className = (eClass != null && eClass.getName() != null) ? eClass.getName() : "Object";

        EStructuralFeature idFeature = (eClass != null) ? eClass.getEStructuralFeature(ID_FEATURE_NAME) : null;
        Object idVal = (idFeature != null && self.eIsSet(idFeature)) ? self.eGet(idFeature) : null;
        String idStr = (idVal != null) ? idVal.toString().trim() : "";

        return "make object \"" + idStr + "\":\"" + className + "\"";
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
        if (attr == null || context == null)
            return "";
        EObject target = resolveSemanticTarget(context);
        if (target == null || !target.eIsSet(attr))
            return "";

        Object val = target.eGet(attr);
        if (val == null)
            return "";

        if (attr.isMany() && val instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                sb.append("prop(\"").append(attr.getName()).append("\") -> \"").append(list.get(i)).append("\"");
                if (i < list.size() - 1)
                    sb.append("\n");
            }
            return sb.toString();
        }
        return "prop(\"" + attr.getName() + "\") -> \"" + val.toString() + "\"";
    }

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
            snippet = raw;
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n")) {
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

    public EObject editKModelSlot(EAttribute attr, EObject container, String input) {
        return editKModelSlot(container, attr, input);
    }

    public EObject editKModelSlot(EObject container, EAttribute attr, String input) {
        if (container == null || attr == null || input == null || input.isBlank())
            return container;
        String raw = input.trim();
        String snippet;

        if (raw.startsWith("prop(") || raw.contains("->")) {
            snippet = raw;
        } else if (!raw.contains(" ") && !raw.contains("\t") && !raw.contains("\n")) {
            snippet = "prop(\"" + attr.getName() + "\") -> \"" + raw.replace("\"", "") + "\"";
        } else {
            snippet = raw;
        }

        try {
            KmodelParser parser = createKmodelParser(snippet);
            KmodelParser.Prop_statementContext ctx = parser.prop_statement();

            String propValue = unquote(ctx.STRING(1).getText());
            Object converted = convertStringToValue(propValue, attr.getEAttributeType());

            if (attr.isMany()) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) container.eGet(attr);
                list.add(converted);
            } else {
                container.eSet(attr, converted);
            }
        } catch (ParseCancellationException e) {
            System.err.println("[Karpfen] Direct-Edit Input rejected by KModel prop_statement: " + input);
        }
        return container;
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

    private EObject resolveSemanticTarget(EObject context) {
        if (context instanceof DSemanticDecorator decorator) {
            if (decorator.eContainer() instanceof DSemanticDecorator parentDecorator) {
                return parentDecorator.getTarget();
            }
            return decorator.getTarget();
        }
        return context;
    }

    private EClass resolveEClass(EObject obj) {
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