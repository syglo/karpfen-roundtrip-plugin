package org.karpfen.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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
import org.eclipse.emf.common.util.URI;
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
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.diagram.DEdge;
import org.eclipse.sirius.diagram.EdgeTarget;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

/**
 * Core Eclipse Sirius diagram services provider helpers for Karpfen DSL visual
 * representations.
 * Implements direct-editing micro-parsers using ANTLR4 to allow inline textual
 * editing of
 * diagram elements, label computation for UML class diagrams, object graphs,
 * and statecharts,
 * and palette tool operations (create/delete link, create/delete node).
 */
public class KarpfenDiagramServices {

    public static final String KARPFEN_URI = "https://github/karpfen/annotation";
    public static final String ID_FEATURE_NAME = "__id__";

    private static final Map<EAttribute, EObject> ACTIVE_SLOT_TARGETS = Collections
            .synchronizedMap(new WeakHashMap<>());
    private static volatile EObject lastRenderedEObject = null;

    private record SourceTargetPair(EObject source, EObject target) {
    }

    // Helpers to fix sirius view representation for root objects through sirius
    // precondition expression.

    /**
     * Sirius precondition checker determining if an {@link EObject} is a valid root
     * candidate for KModel diagrams.
     *
     * @param self context semantic element or view decorator
     * @return true if the element represents a valid KModel root object
     */
    public boolean isKModelRoot(EObject self) {
        if (self == null)
            return false;
        EObject target = resolveSemanticTarget(self);
        if (target == null)
            return false;
        if (target.eContainer() != null)
            return false;
        if (target instanceof EPackage)
            return false;

        String className = target.eClass() != null ? target.eClass().getName() : "";
        if ("StateMachine".equals(className) || "State".equals(className) || "Transition".equals(className)) {
            return false;
        }
        return true;
    }

    /**
     * Sirius precondition checker determining if an {@link EObject} is a root
     * StateMachine. Used to filter out EObjects that are not root StateMachine,
     * like State, and Transition types.
     *
     * @param self context semantic element or view decorator
     * @return true if the element is a root StateMachine
     */
    public boolean isKStatesRoot(EObject self) {
        if (self == null)
            return false;
        EObject target = resolveSemanticTarget(self);
        if (target == null)
            return false;
        if (target.eContainer() != null)
            return false;
        return target.eClass() != null && "StateMachine".equals(target.eClass().getName());
    }

    // ! KMeta visual projections - UML class diagramm

    /**
     * Computes the display label for an {@link EClass} in a KMeta diagram,
     * appending {@code <root>} if marked.
     *
     * @param clas the metamodel class
     * @return formatted class header label
     */
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

    /**
     * Computes the display label for an {@link EAttribute} in a KMeta diagram (e.g.
     * {@code name : string}).
     *
     * @param attr the metamodel attribute
     * @return formatted attribute label
     */
    public String getKMetaAttributeLabel(EAttribute attr) {
        if (attr == null || attr.getName() == null)
            return "";
        String typeName = mapEcoreToKarpfenType(attr.getEAttributeType());
        if (attr.isMany() || attr.getUpperBound() == ETypedElement.UNBOUNDED_MULTIPLICITY) {
            return attr.getName() + " : list(\"" + typeName + "\")";
        }
        return attr.getName() + " : " + typeName;
    }

    /**
     * Computes the display label for an {@link EReference} edge in a KMeta diagram
     * representing the role name.
     *
     * @param ref the metamodel reference
     * @return formatted reference label
     */
    public String getKMetaEdgeLabel(EReference ref) {
        if (ref == null || ref.getName() == null)
            return "";
        return ref.getName();
    }

    /**
     * Computes the UML multiplicity for end label in an {@link EReference} edge
     * (e.g. {@code *}, {@code 1}, or {@code 0..1}).
     *
     * @param ref the metamodel reference
     * @return multiplicity end label
     */
    public String getKMetaEdgeEndLabel(EReference ref) {
        if (ref == null)
            return "";
        if (ref.isMany() || ref.getUpperBound() == ETypedElement.UNBOUNDED_MULTIPLICITY) {
            return "*";
        }
        return ref.isContainment() ? "1" : "0..1";
    }

    /**
     * Retrieves the documentation string from stored in the GenModel EAnnotation of
     * an {@link EObject} -> EClass/EStructuralFeature or
     * outputs an empty string if not present.
     *
     * @param elem the metamodel element
     * @return documentation text or empty string
     */
    public String getKMetaDocumentation(EObject elem) {
        if (elem instanceof org.eclipse.emf.ecore.EModelElement modelElem) {
            EAnnotation ann = modelElem.getEAnnotation("https://eclipse/emf/GenModel");
            if (ann == null) {
                ann = modelElem.getEAnnotation("http://www.eclipse.org/emf/2002/GenModel");
            }
            if (ann != null && ann.getDetails().containsKey("documentation")) {
                String doc = ann.getDetails().get("documentation");
                return doc != null ? doc : "";
            }
        }
        return "";
    }

    // ! KMeta ANTLR micro parser, input subsitution

    /**
     * Direct-editing micro-parser for modifying an {@link EClass} name from inline
     * diagram input.
     *
     * @param clas  target class to rename
     * @param input user-typed text snippet
     * @return updated {@link EClass}
     */
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

    /**
     * Direct-editing micro-parser for updating an {@link EAttribute} from inline
     * diagram input.
     * Parses property declarations (e.g. {@code prop("x", "number")} or shorthand
     * {@code x: number}),
     * updating attribute name, multiplicity, and data type.
     *
     * @param attr  target attribute to edit
     * @param input user-typed text snippet
     * @return updated {@link EAttribute}
     */
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

    /**
     * Direct-editing micro-parser for updating an {@link EReference} edge from
     * inline diagram input.
     * Parses {@code has(...)} and {@code knows(...)} relation rules, updating
     * reference name,
     * target type, and list multiplicity.
     *
     * @param ref   target reference edge to edit
     * @param input user-typed text snippet
     * @return updated {@link EReference}
     */
    public EReference editKMetaEdge(EReference ref, String input) {
        KarpfenLog.trace("DirectEdit-KMeta", "editKMetaEdge called for ref=" + (ref != null ? ref.getName() : "null")
                + " with input=[" + input + "]");
        if (ref == null || input == null || input.isBlank())
            return ref;
        String raw = input.trim();

        // Direct-edit handling when user edits end label multiplicity directly
        if (raw.equals("*") || raw.equals("[*]") || raw.equals("[list]") || raw.equalsIgnoreCase("list")
                || raw.equals("0..*") || raw.equals("[0..*]") || raw.equals("[]")) {
            ref.setUpperBound(ETypedElement.UNBOUNDED_MULTIPLICITY);
            markTargetResourceDirty(ref);
            KarpfenLog.trace("DirectEdit-KMeta", "Updated ref " + ref.getName() + " multiplicity to *");
            return ref;
        } else if (raw.equals("1") || raw.equals("[1]") || raw.equals("0..1") || raw.equals("[0..1]")
                || raw.equalsIgnoreCase("scalar") || raw.equalsIgnoreCase("single")) {
            ref.setUpperBound(1);
            markTargetResourceDirty(ref);
            KarpfenLog.trace("DirectEdit-KMeta", "Updated ref " + ref.getName() + " multiplicity to 1");
            return ref;
        }

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
            } else if (hasListSuffix(typePart)) {
                String cleanType = stripListSuffix(typePart);
                snippet = String.format("%s(\"%s\", list(\"%s\"))", kw, name,
                        cleanType.isEmpty() ? existingTarget : cleanType);
            } else if (hasScalarSuffix(typePart)) {
                String cleanType = stripScalarSuffix(typePart);
                snippet = String.format("%s(\"%s\", \"%s\")", kw, name,
                        cleanType.isEmpty() ? existingTarget : cleanType);
            } else {
                String cleanedType = typePart.replace("\"", "").trim();
                snippet = cleanedType.contains(" ") || cleanedType.isEmpty() ? raw
                        : String.format("%s(\"%s\", \"%s\")", kw, name, cleanedType);
            }
        } else if (hasListSuffix(raw)) {
            String name = stripListSuffix(raw);
            snippet = name.contains(" ") || name.isEmpty() ? raw
                    : String.format("%s(\"%s\", list(\"%s\"))", kw, name, existingTarget);
        } else if (hasScalarSuffix(raw)) {
            String name = stripScalarSuffix(raw);
            snippet = name.contains(" ") || name.isEmpty() ? raw
                    : String.format("%s(\"%s\", \"%s\")", kw, name, existingTarget);
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

    /**
     * Toggles the designated root class annotation on the containing
     * {@link EPackage}.
     *
     * @param clas the metamodel class to toggle as root
     * @return the class
     */
    @SuppressWarnings("unlikely-arg-type")
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

    /**
     * Computes the header label for an instance object (e.g.
     * {@code id : ClassName}).
     *
     * @param self context instance object or view decorator
     * @return formatted instance header string
     */
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

    /**
     * Returns all non-identity schema attributes defined on the instance's class.
     *
     * @param self context instance object
     * @return list of schema {@link EAttribute}s
     */
    public List<EAttribute> getSchemaAttributes(EObject self) {
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
            if (!ID_FEATURE_NAME.equals(attr.getName())) {
                result.add(attr);
                ACTIVE_SLOT_TARGETS.put(attr, target);
                lastRenderedEObject = target;
            }
        }
        return result;
    }

    /**
     * Returns all schema attributes that currently have values set on the instance
     * object.
     *
     * @param self context instance object
     * @return list of populated {@link EAttribute}s
     */
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
                    ACTIVE_SLOT_TARGETS.put(attr, target);
                    lastRenderedEObject = target;
                }
            } catch (Throwable ignored) {
                try {
                    EStructuralFeature feature = target.eClass().getEStructuralFeature(attr.getName());
                    if (feature != null && target.eGet(feature) != null) {
                        result.add(attr);
                        ACTIVE_SLOT_TARGETS.put(attr, target);
                        lastRenderedEObject = target;
                    }
                } catch (Throwable ignored2) {
                }
            }
        }
        return result;
    }

    /**
     * Computes the display slot label for an attribute on a target instance (e.g.
     * {@code feature = value}).
     *
     * @param attr    the attribute slot definition
     * @param context the target instance context
     * @return formatted slot string or {@code feature = <unset>}
     */
    public String getKModelSlotLabel(EAttribute attr, EObject context) {
        if (attr == null)
            return "";
        EObject target = resolveSemanticTarget(context);
        if (target != null && !(target instanceof EAttribute)) {
            ACTIVE_SLOT_TARGETS.put(attr, target);
            lastRenderedEObject = target;
        } else {
            target = resolveSlotTargetInstance(attr);
        }

        if (target == null)
            return attr.getName() + " = <unset>";

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
        boolean isSet = false;
        try {
            if (target.eIsSet(feature)) {
                val = target.eGet(feature);
                isSet = true;
            }
        } catch (Throwable t1) {
            try {
                val = target.eGet(feature);
                isSet = (val != null);
            } catch (Throwable ignored) {
            }
        }

        if (!isSet || val == null) {
            return featName + " = <unset>";
        }

        if (feature.isMany() && val instanceof List<?> list) {
            if (list.isEmpty()) {
                return featName + " = []";
            }
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

    /**
     * Overloaded slot label provider with swapped parameter order for Sirius AQL
     * expressions.
     *
     * @param context the target instance context
     * @param attr    the attribute slot definition
     * @return formatted slot string
     */
    public String getKModelSlotLabel(EObject context, EAttribute attr) {
        return getKModelSlotLabel(attr, context);
    }

    /**
     * Resolves display label for a containment link between instance nodes.
     *
     * @param self         source instance
     * @param viewOrTarget target instance or view decorator
     * @return containment feature name(s) or "has"
     */
    public String getInstanceContainmentLabel(EObject self, EObject viewOrTarget) {
        return resolveInstanceEdgeLabel(self, viewOrTarget, true);
    }

    /**
     * Resolves display label for a cross-reference link between instance nodes.
     *
     * @param self         source instance
     * @param viewOrTarget target instance or view decorator
     * @return reference feature name(s) or "knows"
     */
    public String getInstanceReferenceLabel(EObject self, EObject viewOrTarget) {
        return resolveInstanceEdgeLabel(self, viewOrTarget, false);
    }

    /**
     * Computes the edge label between two instance endpoints based on active EMF
     * references.
     *
     * @param self          source element
     * @param viewOrTarget  target element or view
     * @param isContainment true for containment, false for cross-references
     * @return joined feature names or default fallback keyword
     */
    public String resolveInstanceEdgeLabel(EObject self, EObject viewOrTarget, boolean isContainment) {
        SourceTargetPair pair = resolveEndpoints(self, viewOrTarget);
        EObject src = pair.source();
        EObject tgt = pair.target();
        if (src == null || tgt == null || src.eClass() == null) {
            return isContainment ? "has" : "knows";
        }

        List<String> matchedNames = new ArrayList<>();

        // Collect all scalar references linking src -> tgt
        for (EReference ref : src.eClass().getEAllReferences()) {
            if (ref.isContainment() == isContainment && !ref.isMany()) {
                try {
                    if (src.eIsSet(ref) && src.eGet(ref) == tgt) {
                        matchedNames.add(ref.getName());
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // Collect all list collection references containing tgt
        for (EReference ref : src.eClass().getEAllReferences()) {
            if (ref.isContainment() == isContainment && ref.isMany()) {
                try {
                    Object val = src.eGet(ref);
                    if (val instanceof List<?> list && list.contains(tgt)) {
                        matchedNames.add(ref.getName());
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        if (!matchedNames.isEmpty()) {
            return String.join(", ", matchedNames);
        }

        return isContainment ? "has" : "knows";
    }

    // ! KModel Direct Editing & Micro-Parsing

    /**
     * Direct-editing micro-parser for updating instance object ID headers (e.g.
     * {@code "myId":"MyType"}).
     *
     * @param self  context instance object
     * @param input user-typed text snippet
     * @return updated instance {@link EObject}
     */
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

    /**
     * Direct-editing entry point for updating a slot attribute value from inline
     * diagram input.
     *
     * @param attr    target slot attribute
     * @param context context instance element or view decorator
     * @param input   user-typed text snippet (e.g. {@code prop("x") -> "10"} or
     *                {@code x = 10} or raw {@code 10})
     * @return updated attribute definition
     */
    public EObject editKModelSlotValue(EAttribute attr, EObject context, String input) {
        KarpfenLog.trace("DirectEdit-KModel", "editKModelSlotValue called with attr="
                + (attr != null ? attr.getName() : "null") + ", input=[" + input + "]");
        EObject target = resolveSemanticTarget(context);
        if (target == null || target instanceof EAttribute) {
            target = resolveSlotTargetInstance(attr);
        }
        if (target != null && attr != null) {
            editKModelSlot(target, attr, input);
        } else {
            KarpfenLog.warn("editKModelSlotValue failed: target=" + target + ", attr=" + attr);
        }
        return attr;
    }

    /**
     * Overloaded direct-editing slot value updater with swapped parameter order for
     * Sirius AQL expressions.
     *
     * @param context context instance element or view decorator
     * @param attr    target slot attribute
     * @param input   user-typed text snippet
     * @return updated attribute definition
     */
    public EObject editKModelSlotValue(EObject context, EAttribute attr, String input) {
        return editKModelSlotValue(attr, context, input);
    }

    /**
     * Executes micro-parsing and value assignment for an instance slot feature.
     * Converts text values to the appropriate Ecore primitive type and supports
     * {@code <unset>} commands.
     *
     * @param container instance container object
     * @param attr      attribute slot definition
     * @param input     user-typed text value
     * @return modified container instance
     */
    public EObject editKModelSlot(EObject container, EAttribute attr, String input) {
        KarpfenLog.trace("DirectEdit-KModel", "editKModelSlot executing on container=" + container + ", attr="
                + (attr != null ? attr.getName() : "null") + ", input=[" + input + "]");
        if (container == null || attr == null || input == null || input.isBlank())
            return container;
        EObject target = resolveSemanticTarget(container);
        if (target == null || target instanceof EAttribute) {
            target = resolveSlotTargetInstance(attr);
        }
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

        EStructuralFeature actualFeat = target.eClass().getEStructuralFeature(targetAttr.getName());
        if (actualFeat == null) {
            actualFeat = targetAttr;
        }

        String cleanProp = propValue != null ? unquote(propValue).trim() : "";
        if ("<unset>".equalsIgnoreCase(cleanProp) || "unset".equalsIgnoreCase(cleanProp)
                || "<unset>".equalsIgnoreCase(raw) || "unset".equalsIgnoreCase(raw)
                || cleanProp.isEmpty()) {
            target.eUnset(actualFeat);
            markTargetResourceDirty(target);
            KarpfenLog.info("[DirectEdit-KModel] Unset feature " + actualFeat.getName() + " on " + target);
            return target;
        }

        try {
            Object converted = convertStringToValue(propValue, targetAttr.getEAttributeType());
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

    /**
     * Direct-editing micro-parser for reconciling instance relationships from typed
     * edge labels.
     * Validates type compatibility of requested reference features and
     * assigns/clears relations accordingly.
     *
     * @param self          source instance node
     * @param viewOrTarget  target instance node or view decorator
     * @param input         comma-separated list of reference feature names
     * @param isContainment true for containment edges ({@code has}), false for
     *                      references ({@code knows})
     * @return updated source {@link EObject}
     */
    public EObject editInstanceEdge(EObject self, Object viewOrTarget, String input, boolean isContainment) {
        EObject viewObj = null;
        if (viewOrTarget instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof EObject eo) {
                viewObj = eo;
            }
        } else if (viewOrTarget instanceof EObject eo) {
            viewObj = eo;
        }

        SourceTargetPair pair = resolveEndpoints(self, viewObj);
        EObject src = pair.source();
        EObject tgt = pair.target();
        if (src == null || tgt == null || src.eClass() == null || tgt.eClass() == null || input == null)
            return src;

        String raw = input.trim();
        List<String> targetRefNames = new ArrayList<>();
        if (!raw.isBlank() && !"<unset>".equalsIgnoreCase(raw) && !"unset".equalsIgnoreCase(raw)) {
            String[] parts = raw.split(",");
            for (String part : parts) {
                String cleaned = unquote(part.trim().replace("[ref]", "").replace("[list]", "").trim());
                if (!cleaned.isBlank()) {
                    targetRefNames.add(cleaned.toLowerCase());
                }
            }
        }

        // Validate that requested references exist AND are type-compatible with the
        // target object (resolving proxies)
        List<EReference> validTargetRefs = new ArrayList<>();
        for (String requestedName : targetRefNames) {
            for (EReference ref : src.eClass().getEAllReferences()) {
                if (ref.isContainment() == isContainment && ref.getName().equalsIgnoreCase(requestedName)) {
                    if (isReferenceTypeCompatible(ref, tgt, src)) {
                        validTargetRefs.add(ref);
                    } else {
                        String expectedType = getEClassName(ref.getEType(), src);
                        String actualType = getClassName(tgt);
                        KarpfenLog.warn("Type mismatch on direct edit: Reference '" + ref.getName() + "' on "
                                + getClassName(src) + " expects type " + expectedType
                                + " but target is of type " + actualType);
                    }
                    break;
                }
            }
        }

        // If user entered feature names but NONE of them are type-compatible, reject
        // edit to prevent model corruption
        if (!targetRefNames.isEmpty() && validTargetRefs.isEmpty()) {
            KarpfenLog.warn("Direct-Edit rejected: None of the specified reference features " + targetRefNames
                    + " are type-compatible with target " + getClassName(tgt));
            return src;
        }

        boolean modified = false;

        // Reconcile references matching isContainment
        for (EReference ref : src.eClass().getEAllReferences()) {
            if (ref.isContainment() == isContainment) {
                boolean shouldContain = validTargetRefs.contains(ref);
                if (ref.isMany()) {
                    Object val = src.eGet(ref);
                    if (val instanceof List<?> list) {
                        @SuppressWarnings("unchecked")
                        List<EObject> mList = (List<EObject>) list;
                        if (shouldContain) {
                            if (!mList.contains(tgt)) {
                                mList.add(tgt);
                                modified = true;
                            }
                        } else {
                            if (mList.remove(tgt)) {
                                modified = true;
                            }
                        }
                    }
                } else {
                    if (shouldContain) {
                        if (!src.eIsSet(ref) || src.eGet(ref) != tgt) {
                            src.eSet(ref, tgt);
                            modified = true;
                        }
                    } else {
                        if (src.eIsSet(ref) && src.eGet(ref) == tgt) {
                            src.eUnset(ref);
                            modified = true;
                        }
                    }
                }
            }
        }

        if (modified) {
            markTargetResourceDirty(src);
            KarpfenLog.info(
                    "[DirectEdit-KModel] Reconciled edge " + src + " -> " + tgt + " with features: " + targetRefNames);
        }
        return src;
    }

    /**
     * Palette tool operation creating a containment or cross-reference link between
     * two instance nodes.
     *
     * @param source        source instance node
     * @param target        target instance node
     * @param isContainment true for containment ({@code has}), false for reference
     *                      ({@code knows})
     * @return modified source instance
     */
    public EObject createInstanceLink(EObject source, EObject target, boolean isContainment) {
        EObject src = resolveSemanticTarget(source);
        EObject tgt = resolveSemanticTarget(target);
        if (src == null || tgt == null || src.eClass() == null || tgt.eClass() == null)
            return src;

        List<EReference> matchingRefs = new ArrayList<>();
        for (EReference ref : src.eClass().getEAllReferences()) {
            if (ref.isContainment() == isContainment && isReferenceTypeCompatible(ref, tgt, src)) {
                matchingRefs.add(ref);
            }
        }

        if (matchingRefs.isEmpty()) {
            KarpfenLog.warn("No compatible " + (isContainment ? "containment" : "reference") + " feature on "
                    + getClassName(src) + " for " + getClassName(tgt));
            return src;
        }

        // Smart precedence: Unset scalar, List reference, Overwrite scalar
        EReference chosen = null;
        for (EReference ref : matchingRefs) {
            if (!ref.isMany() && !src.eIsSet(ref)) {
                chosen = ref;
                break;
            }
        }
        if (chosen == null) {
            for (EReference ref : matchingRefs) {
                if (ref.isMany()) {
                    chosen = ref;
                    break;
                }
            }
        }
        if (chosen == null) {
            chosen = matchingRefs.get(0);
        }

        if (chosen.isMany()) {
            @SuppressWarnings("unchecked")
            List<EObject> list = (List<EObject>) src.eGet(chosen);
            if (!list.contains(tgt)) {
                list.add(tgt);
            }
        } else {
            src.eSet(chosen, tgt);
        }

        markTargetResourceDirty(src);
        KarpfenLog.info("[Palette-KModel] Linked " + src + " -> " + tgt + " via " + chosen.getName());
        return src;
    }

    // KModel delete operations

    /**
     * Palette tool operation deleting a relationship link between two instance
     * nodes.
     *
     * @param self          source instance node
     * @param viewOrTarget  target instance or view decorator
     * @param isContainment true for containment, false for cross-references
     * @return modified source instance
     */
    public EObject deleteInstanceLink(EObject self, Object viewOrTarget, boolean isContainment) {
        EObject viewObj = null;
        if (viewOrTarget instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof EObject eo) {
                viewObj = eo;
            }
        } else if (viewOrTarget instanceof EObject eo) {
            viewObj = eo;
        }

        SourceTargetPair pair = resolveEndpoints(self, viewObj);
        EObject src = pair.source();
        EObject tgt = pair.target();
        if (src == null || tgt == null || src.eClass() == null) {
            KarpfenLog.warn("deleteInstanceLink could not resolve endpoints: src=" + src + ", tgt=" + tgt);
            return src;
        }

        boolean modified = false;
        for (EReference ref : src.eClass().getEAllReferences()) {
            if (ref.isContainment() == isContainment) {
                if (ref.isMany()) {
                    Object val = src.eGet(ref);
                    if (val instanceof List<?> list && list.contains(tgt)) {
                        list.remove(tgt);
                        modified = true;
                        KarpfenLog.info(
                                "[Delete-KModel] Removed link " + src + " -> " + tgt + " from list " + ref.getName());
                    }
                } else if (src.eIsSet(ref) && src.eGet(ref) == tgt) {
                    src.eUnset(ref);
                    modified = true;
                    KarpfenLog.info("[Delete-KModel] Unset link " + src + " -> " + tgt + " on scalar " + ref.getName());
                }
            }
        }

        if (modified) {
            markTargetResourceDirty(src);
        }
        return src;
    }

    /**
     * Palette/context menu tool deleting an instance {@link EObject} from its
     * container or resource.
     *
     * @param self instance element to delete
     * @return parent container or target element
     */
    public EObject deleteKModelObject(EObject self) {
        EObject target = resolveSemanticTarget(self);
        if (target == null)
            return self;

        EObject container = target.eContainer();
        EReference contFeature = target.eContainmentFeature();

        if (container != null && contFeature != null) {
            if (contFeature.isMany()) {
                @SuppressWarnings("unchecked")
                List<EObject> list = (List<EObject>) container.eGet(contFeature);
                list.remove(target);
            } else {
                container.eUnset(contFeature);
            }
            markTargetResourceDirty(container);
        } else if (target.eResource() != null) {
            target.eResource().getContents().remove(target);
            markTargetResourceDirty(target);
        }

        EcoreUtil.delete(target, true);
        KarpfenLog.info("[Delete-KModel] Deleted object " + target);
        return container != null ? container : target;
    }

    // ! KStates visual projections - Statechart diagram

    /**
     * Computes the header label for a state (e.g. {@code <initial> StateName} or
     * {@code StateName}).
     *
     * @param state the state {@link EObject}
     * @return formatted state header label
     */
    public String getStateHeaderLabel(EObject state) {
        if (state == null)
            return "State";
        EStructuralFeature initFeat = state.eClass().getEStructuralFeature("isInitial");
        Boolean isInit = (initFeat != null && state.eIsSet(initFeat)) ? (Boolean) state.eGet(initFeat) : false;

        EStructuralFeature nameFeat = state.eClass().getEStructuralFeature("name");
        String name = (nameFeat != null && state.eIsSet(nameFeat)) ? (String) state.eGet(nameFeat) : "State";

        return (isInit != null && isInit ? "<initial> " : "") + name;
    }

    /**
     * Computes the display label for a state's {@code entry} action block.
     *
     * @param state the state {@link EObject}
     * @return formatted entry action string (e.g. {@code entry / EVENT(...)})
     */
    public String getEntryLabel(EObject state) {
        if (state == null)
            return "";
        EStructuralFeature feat = state.eClass().getEStructuralFeature("entryAction");
        if (feat != null && state.eIsSet(feat)) {
            String val = (String) state.eGet(feat);
            if (val != null && !val.isBlank()) {
                StringBuilder sb = new StringBuilder();
                for (String line : val.split("\\R")) {
                    String trimmed = line.trim();
                    if (!trimmed.isBlank() && !trimmed.startsWith("//")) {
                        if (!sb.isEmpty())
                            sb.append("\n");
                        sb.append("entry / ").append(trimmed);
                    }
                }
                return sb.toString();
            }
        }
        return "";
    }

    /**
     * Computes the display label for a state's {@code do} action block.
     *
     * @param state the state {@link EObject}
     * @return formatted do action string (e.g. {@code do / SET(...)})
     */
    public String getDoLabel(EObject state) {
        if (state == null)
            return "";
        EStructuralFeature feat = state.eClass().getEStructuralFeature("doAction");
        if (feat != null && state.eIsSet(feat)) {
            String val = (String) state.eGet(feat);
            if (val != null && !val.isBlank()) {
                StringBuilder sb = new StringBuilder();
                for (String line : val.split("\\R")) {
                    String trimmed = line.trim();
                    if (!trimmed.isBlank() && !trimmed.startsWith("//")) {
                        if (!sb.isEmpty())
                            sb.append("\n");
                        sb.append("do / ").append(trimmed);
                    }
                }
                return sb.toString();
            }
        }
        return "";
    }

    /**
     * Computes the display label for a transition edge (guard conditions, events,
     * values).
     *
     * @param transition the transition {@link EObject}
     * @return formatted transition guard label (e.g. {@code [condition]} or
     *         {@code EVENT(...)})
     */
    public String getTransitionLabel(EObject transition) {
        if (transition == null)
            return "";
        EStructuralFeature condFeat = transition.eClass().getEStructuralFeature("condition");
        String cond = (condFeat != null && transition.eIsSet(condFeat)) ? (String) transition.eGet(condFeat) : "";

        if (cond == null || cond.isBlank()) {
            return "";
        }

        String clean = cond.trim();
        if ("VALUE(\"true\")".equalsIgnoreCase(clean) || "true".equalsIgnoreCase(clean)
                || "\"true\"".equalsIgnoreCase(clean)) {
            return "";
        }

        // Strip macro and variable wrappers $(...)
        clean = clean.replace("$(", "").replace(")", "");
        if (clean.startsWith("EVENT(\"public\",")) {
            String eventName = clean.replace("EVENT(\"public\",", "").replace(")", "").replace("\"", "").trim();
            return "EVENT(\"public\", \"" + eventName + "\")";
        }

        return "[" + clean + "]";
    }

    // ! KStates Direct Editing & Operations

    /**
     * Direct-editing micro-parser for updating state names and initial state
     * markers.
     *
     * @param state the state {@link EObject}
     * @param input user-typed text snippet
     * @return updated state {@link EObject}
     */
    public EObject editStateName(EObject state, String input) {
        KarpfenLog.trace("DirectEdit-KStates", "editStateName called with input=[" + input + "]");
        if (state == null || input == null || input.isBlank())
            return state;
        String raw = input.trim();
        boolean makeInitial = raw.startsWith("<initial>") || raw.startsWith("INITIAL");
        String cleanName = raw.replace("<initial>", "").replace("INITIAL", "").replace("STATE", "")
                .replace("\"", "").trim();

        EStructuralFeature nameFeat = state.eClass().getEStructuralFeature("name");
        if (nameFeat != null) {
            state.eSet(nameFeat, cleanName);
        }

        EStructuralFeature initFeat = state.eClass().getEStructuralFeature("isInitial");
        if (initFeat != null && makeInitial) {
            state.eSet(initFeat, true);
        }

        markTargetResourceDirty(state);
        return state;
    }

    /**
     * Direct-editing micro-parser for updating transition guard conditions and
     * {@code NOT LOOPING} directives.
     *
     * @param transition the transition {@link EObject}
     * @param input      user-typed guard text snippet
     * @return updated transition {@link EObject}
     */
    public EObject editTransitionGuard(EObject transition, String input) {
        KarpfenLog.trace("DirectEdit-KStates", "editTransitionGuard called with input=[" + input + "]");
        if (transition == null || input == null)
            return transition;

        String raw = input.trim();
        EStructuralFeature condFeat = transition.eClass().getEStructuralFeature("condition");
        EStructuralFeature loopFeat = transition.eClass().getEStructuralFeature("notLooping");

        if ("<unset>".equalsIgnoreCase(raw) || "unset".equalsIgnoreCase(raw) || raw.isBlank()) {
            if (condFeat != null)
                transition.eUnset(condFeat);
            if (loopFeat != null)
                transition.eSet(loopFeat, false);
        } else {
            boolean isNotLooping = raw.toUpperCase().contains("NOT LOOPING");
            String cleanCond = raw.replace("[NOT LOOPING]", "").replace("NOT LOOPING", "")
                    .replace("[", "").replace("]", "").trim();

            if (loopFeat != null) {
                transition.eSet(loopFeat, isNotLooping);
            }
            if (condFeat != null) {
                transition.eSet(condFeat, cleanCond);
            }
        }

        markTargetResourceDirty(transition);
        return transition;
    }

    /**
     * Toggles the initial state flag on a state {@link EObject}.
     *
     * @param state target state
     * @return updated state
     */
    public EObject toggleInitialState(EObject state) {
        if (state == null)
            return state;
        EStructuralFeature initFeat = state.eClass().getEStructuralFeature("isInitial");
        if (initFeat != null) {
            Boolean current = (Boolean) state.eGet(initFeat);
            state.eSet(initFeat, current == null || !current);
            markTargetResourceDirty(state);
        }
        return state;
    }

    /**
     * Palette tool operation creating a transition edge between source and target
     * states.
     *
     * @param source source state
     * @param target target state
     * @return source state
     */
    public EObject createTransitionLink(EObject source, EObject target) {
        EObject src = resolveSemanticTarget(source);
        EObject tgt = resolveSemanticTarget(target);
        if (src == null || tgt == null)
            return source;

        // Traverse to root StateMachine
        EObject root = src;
        while (root.eContainer() != null) {
            root = root.eContainer();
        }

        EStructuralFeature transFeat = root.eClass().getEStructuralFeature("transitions");
        if (transFeat instanceof EReference transRef && transRef.getEType() instanceof EClass transClass) {
            EObject newTrans = transClass.getEPackage().getEFactoryInstance().create(transClass);
            newTrans.eSet(transClass.getEStructuralFeature("sourceState"), src);
            newTrans.eSet(transClass.getEStructuralFeature("targetState"), tgt);

            String srcName = (String) src.eGet(src.eClass().getEStructuralFeature("name"));
            String tgtName = (String) tgt.eGet(tgt.eClass().getEStructuralFeature("name"));
            newTrans.eSet(transClass.getEStructuralFeature("name"), srcName + " -> " + tgtName);

            @SuppressWarnings("unchecked")
            List<EObject> list = (List<EObject>) root.eGet(transFeat);
            list.add(newTrans);
            markTargetResourceDirty(root);
            KarpfenLog.info("[Palette-KStates] Created transition: " + srcName + " -> " + tgtName);
        }
        return source;
    }

    /**
     * Palette tool operation creating a new state node in the state machine.
     *
     * @param container context state machine container
     * @return newly created state {@link EObject}
     */
    public EObject createState(EObject container) {
        EObject target = resolveSemanticTarget(container);
        if (target == null)
            return container;

        EObject root = target;
        while (root.eContainer() != null) {
            root = root.eContainer();
        }

        EStructuralFeature statesFeat = root.eClass().getEStructuralFeature("states");
        if (statesFeat instanceof EReference statesRef && statesRef.getEType() instanceof EClass stateClass) {
            EObject newState = stateClass.getEPackage().getEFactoryInstance().create(stateClass);
            @SuppressWarnings("unchecked")
            List<EObject> statesList = (List<EObject>) root.eGet(statesFeat);
            int idx = 1;
            java.util.Set<String> existing = new java.util.HashSet<>();
            if (statesList != null) {
                for (EObject s : statesList) {
                    String n = (String) s.eGet(s.eClass().getEStructuralFeature("name"));
                    if (n != null)
                        existing.add(n);
                }
            }
            while (existing.contains("State_" + idx)) {
                idx++;
            }
            String finalName = "State_" + idx;
            newState.eSet(stateClass.getEStructuralFeature("name"), finalName);
            newState.eSet(stateClass.getEStructuralFeature("isInitial"), statesList == null || statesList.isEmpty());
            if (statesList != null) {
                statesList.add(newState);
            }
            markTargetResourceDirty(root);
            KarpfenLog.info("[Palette-KStates] Created new state: " + finalName);
            return newState;
        }
        return target;
    }

    /**
     * Palette/context menu tool deleting a state and all connected transitions
     * from the state machine.
     *
     * @param self state element to delete
     * @return parent container or root element
     */
    public EObject deleteState(EObject self) {
        if (self == null)
            return null;
        EObject target = resolveSemanticTarget(self);
        if (target == null)
            return self;

        // Safety to prevent accidental deletion of the entire state machine
        if (target.eClass() != null && "StateMachine".equalsIgnoreCase(target.eClass().getName())) {
            KarpfenLog.warn("[Delete-KStates] Refused to delete root StateMachine as a state!");
            return target;
        }

        EObject root = target;
        while (root.eContainer() != null) {
            root = root.eContainer();
        }

        // Remove transitions referencing this state
        EStructuralFeature transFeat = root.eClass().getEStructuralFeature("transitions");
        if (transFeat != null) {
            @SuppressWarnings("unchecked")
            List<EObject> transList = (List<EObject>) root.eGet(transFeat);
            if (transList != null) {
                transList.removeIf(t -> {
                    Object s = t.eGet(t.eClass().getEStructuralFeature("sourceState"));
                    Object d = t.eGet(t.eClass().getEStructuralFeature("targetState"));
                    return s == target || d == target;
                });
            }
        }

        EObject container = target.eContainer();
        EReference contFeature = target.eContainmentFeature();
        if (container != null && contFeature != null) {
            if (contFeature.isMany()) {
                @SuppressWarnings("unchecked")
                List<EObject> list = (List<EObject>) container.eGet(contFeature);
                if (list != null) {
                    list.remove(target);
                }
            } else {
                container.eUnset(contFeature);
            }
            markTargetResourceDirty(container);
        } else {
            markTargetResourceDirty(root);
        }

        EcoreUtil.delete(target, true);
        KarpfenLog.info("[Delete-KStates] Deleted state " + target);
        return container != null ? container : root;
    }

    /**
     * Palette/context menu tool deleting a transition edge from the state
     * machine.
     *
     * @param self transition element to delete
     * @return parent container or root element
     */
    public EObject deleteTransition(EObject self) {
        if (self == null)
            return null;
        EObject target = resolveSemanticTarget(self);
        if (target == null)
            return self;

        // Safety to prevent accidental deletion of the entire state machine
        if (target.eClass() != null && "StateMachine".equalsIgnoreCase(target.eClass().getName())) {
            KarpfenLog.warn("[Delete-KStates] Refused to delete root StateMachine as a transition!");
            return target;
        }

        EObject root = target;
        while (root.eContainer() != null) {
            root = root.eContainer();
        }

        EStructuralFeature transFeat = root.eClass().getEStructuralFeature("transitions");
        if (transFeat != null) {
            @SuppressWarnings("unchecked")
            List<EObject> transList = (List<EObject>) root.eGet(transFeat);
            if (transList != null) {
                transList.remove(target);
            }
        }

        EObject container = target.eContainer();
        EReference contFeature = target.eContainmentFeature();
        if (container != null && contFeature != null) {
            if (contFeature.isMany()) {
                @SuppressWarnings("unchecked")
                List<EObject> list = (List<EObject>) container.eGet(contFeature);
                if (list != null) {
                    list.remove(target);
                }
            } else {
                container.eUnset(contFeature);
            }
            markTargetResourceDirty(container);
        } else {
            markTargetResourceDirty(root);
        }

        EcoreUtil.delete(target, true);
        KarpfenLog.info("[Delete-KStates] Deleted transition " + target);
        return container != null ? container : root;
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

    // ANTLR parser factories for input validations

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

    private String getEClassName(EClassifier classifier, EObject context) {
        if (classifier == null)
            return "EObject";
        if (classifier.eIsProxy()) {
            if (context != null) {
                EObject resolved = EcoreUtil.resolve(classifier, context);
                if (resolved instanceof EClassifier rc && !rc.eIsProxy() && rc.getName() != null) {
                    return rc.getName();
                }
            }
            if (classifier instanceof InternalEObject internalEObject) {
                URI proxyUri = internalEObject.eProxyURI();
                if (proxyUri != null && proxyUri.fragment() != null) {
                    String frag = proxyUri.fragment();
                    int lastSlash = frag.lastIndexOf('/');
                    String name = (lastSlash >= 0) ? frag.substring(lastSlash + 1) : frag;
                    name = name.replace("#", "").trim();
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            }
        }
        String name = classifier.getName();
        return (name != null && !name.isBlank()) ? name : "EObject";
    }

    private boolean isReferenceTypeCompatible(EReference ref, EObject tgt, EObject src) {
        if (ref == null || tgt == null)
            return false;
        EClassifier expectedClassifier = ref.getEType();
        if (expectedClassifier == null)
            return true;

        String expectedTypeName = getEClassName(expectedClassifier, src);
        String actualTypeName = (tgt.eClass() != null) ? getEClassName(tgt.eClass(), tgt) : getClassName(tgt);

        if ("EObject".equalsIgnoreCase(expectedTypeName) || "EObject".equalsIgnoreCase(actualTypeName)) {
            return true;
        }

        if (expectedTypeName.equalsIgnoreCase(actualTypeName)) {
            return true;
        }

        EClass expectedClass = resolveEClass(expectedClassifier);
        EClass actualClass = resolveEClass(tgt.eClass());
        if (expectedClass != null && actualClass != null && !expectedClass.eIsProxy() && !actualClass.eIsProxy()) {
            if (expectedClass.isSuperTypeOf(actualClass)) {
                return true;
            }
        }

        return false;
    }

    private SourceTargetPair resolveEndpoints(EObject obj1, EObject obj2) {
        EObject src = null;
        EObject tgt = null;

        DEdge edge = null;
        if (obj1 instanceof DEdge d1) {
            edge = d1;
        } else if (obj2 instanceof DEdge d2) {
            edge = d2;
        }

        if (edge != null) {
            EdgeTarget sNode = edge.getSourceNode();
            if (sNode instanceof DSemanticDecorator dec) {
                src = resolveSemanticTarget(dec.getTarget());
            }
            EdgeTarget tNode = edge.getTargetNode();
            if (tNode instanceof DSemanticDecorator dec) {
                tgt = resolveSemanticTarget(dec.getTarget());
            }
            if (src == null && edge.getTarget() != null) {
                src = resolveSemanticTarget(edge.getTarget());
            }
        }

        if (src == null) {
            src = resolveSemanticTarget(obj1);
        }
        if (tgt == null) {
            tgt = resolveSemanticTarget(obj2);
        }

        return new SourceTargetPair(src, tgt);
    }

    private EObject resolveSlotTargetInstance(EAttribute attr) {
        if (attr == null)
            return lastRenderedEObject;
        EObject target = ACTIVE_SLOT_TARGETS.get(attr);
        if (target != null)
            return target;

        EClass containingClass = attr.getEContainingClass();
        if (containingClass != null && containingClass.eResource() != null
                && containingClass.eResource().getResourceSet() != null) {
            for (Resource res : containingClass.eResource().getResourceSet().getResources()) {
                TreeIterator<EObject> all = res.getAllContents();
                while (all.hasNext()) {
                    EObject obj = all.next();
                    if (obj.eClass() != null && obj.eClass().getName() != null
                            && obj.eClass().getName().equals(containingClass.getName())) {
                        return obj;
                    }
                }
            }
        }
        return lastRenderedEObject;
    }

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
        EClass eClass = (obj instanceof EClass ec) ? ec : obj.eClass();
        if (eClass != null && eClass.eIsProxy()) {
            EObject resolved = EcoreUtil.resolve(eClass, obj);
            if (resolved instanceof EClass resolvedClass && !resolvedClass.eIsProxy()) {
                return resolvedClass;
            }
        }
        return eClass;
    }

    private String getClassName(EObject obj) {
        if (obj == null)
            return "Object";
        if (obj instanceof EClass ec) {
            return getEClassName(ec, null);
        }
        EClass eClass = resolveEClass(obj);
        if (eClass != null) {
            return getEClassName(eClass, obj);
        }
        return "Object";
    }

    private void updateReferenceTargetType(EReference ref, String targetTypeName) {
        if (ref.getEContainingClass() != null && ref.getEContainingClass().getEPackage() != null) {
            EClassifier targetClassifier = ref.getEContainingClass().getEPackage().getEClassifier(targetTypeName);
            if (targetClassifier instanceof EClass targetEClass) {
                ref.setEType(targetEClass);
            }
        }
    }

    private boolean hasListSuffix(String str) {
        if (str == null)
            return false;
        String s = str.trim();
        return s.endsWith(" [list]") || s.endsWith("[list]") || s.endsWith(" [*]") || s.endsWith("[*]")
                || s.endsWith(" [0..*]") || s.endsWith("[0..*]") || s.endsWith(" []") || s.endsWith("[]")
                || s.endsWith(" *") || (s.endsWith("*") && s.length() > 1) || s.endsWith(" list")
                || s.endsWith(" 0..*");
    }

    private boolean hasScalarSuffix(String str) {
        if (str == null)
            return false;
        String s = str.trim();
        return s.endsWith(" [1]") || s.endsWith("[1]") || s.endsWith(" 1")
                || s.endsWith(" [0..1]") || s.endsWith("[0..1]") || s.endsWith(" 0..1")
                || s.endsWith(" [single]") || s.endsWith("[single]") || s.endsWith(" single");
    }

    private String stripListSuffix(String str) {
        if (str == null)
            return "";
        String s = str.trim();
        String[] suffixes = { " [list]", "[list]", " [*]", "[*]", " [0..*]", "[0..*]", " []", "[]", " *", "*", " list",
                " 0..*" };
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) {
                return s.substring(0, s.length() - suffix.length()).trim().replace("\"", "");
            }
        }
        return s.trim().replace("\"", "");
    }

    private String stripScalarSuffix(String str) {
        if (str == null)
            return "";
        String s = str.trim();
        String[] suffixes = { " [1]", "[1]", " 1", " [0..1]", "[0..1]", " 0..1", " [single]", "[single]", " single" };
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) {
                return s.substring(0, s.length() - suffix.length()).trim().replace("\"", "");
            }
        }
        return s.trim().replace("\"", "");
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