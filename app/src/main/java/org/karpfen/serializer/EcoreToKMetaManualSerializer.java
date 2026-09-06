package org.karpfen.serializer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcorePackage;
import org.karpfen.transformer.KMetaToEcoreTransformer;

/**
 * Manual programmatic Java serializer converting EMF {@link EPackage}
 * metamodels into {@code .kmeta} Karpfen DSL source text.
 * Without indentation or any formatting, because it is post-processed through
 * ANTLR-based Karpfen DSL formatter.
 * 
 * Emits type definitions, comments from GenModel documentation annotations,
 * primitive properties,
 * and containment ({@code has}) / non-containment ({@code knows}) reference
 * declarations with canonical ordering.
 */
public class EcoreToKMetaManualSerializer implements KMetaSerializer {

    private static final String GENMODEL_URI = "https://eclipse/emf/GenModel";
    private static final String KARPFEN_URI = "https://github/karpfen/annotation";

    /**
     * Serializes the given {@link EPackage} into Karpfen {@code .kmeta} DSL text.
     *
     * @param ePackage the EMF package to serialize
     * @return generated {@code .kmeta} source text
     */
    @Override
    public String serialize(EPackage ePackage) {
        if (ePackage == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        List<EClass> orderedClasses = getOrderedClasses(ePackage);

        for (int i = 0; i < orderedClasses.size(); i++) {
            EClass eClass = orderedClasses.get(i);
            serializeClass(eClass, sb);
            if (i < orderedClasses.size() - 1) {
                sb.append("\n\n");
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    private void serializeClass(EClass eClass, StringBuilder sb) {
        String comment = getDocumentation(eClass);
        sb.append(String.format("type \"%s\" \"%s\" {\n", eClass.getName(), comment));

        // properties
        for (EAttribute attr : eClass.getEAttributes()) {
            if (KMetaToEcoreTransformer.ID_FEATURE_NAME.equals(attr.getName())) {
                continue;
            }
            String typeName = mapDataType(attr.getEAttributeType());
            if (isMany(attr)) {
                sb.append(String.format("prop(\"%s\", list(\"%s\"))\n", attr.getName(), typeName));
            } else {
                sb.append(String.format("prop(\"%s\", \"%s\")\n", attr.getName(), typeName));
            }
        }

        // has
        for (EReference ref : eClass.getEReferences()) {
            if (ref.isContainment()) {
                String targetType = ref.getEType() != null ? ref.getEType().getName() : "EObject";
                if (isMany(ref)) {
                    sb.append(String.format("has(\"%s\", list(\"%s\"))\n", ref.getName(), targetType));
                } else {
                    sb.append(String.format("has(\"%s\", \"%s\")\n", ref.getName(), targetType));
                }
            }
        }

        // knows
        for (EReference ref : eClass.getEReferences()) {
            if (!ref.isContainment()) {
                String targetType = ref.getEType() != null ? ref.getEType().getName() : "EObject";
                if (isMany(ref)) {
                    sb.append(String.format("knows(\"%s\", list(\"%s\"))\n", ref.getName(), targetType));
                } else {
                    sb.append(String.format("knows(\"%s\", \"%s\")\n", ref.getName(), targetType));
                }
            }
        }

        sb.append("}");
    }

    private List<EClass> getOrderedClasses(EPackage pkg) {
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
            // place root class last
            ordered.add(rootClass);
        }
        return ordered;
    }

    private String getDocumentation(EClass eClass) {
        if (eClass == null)
            return "";
        EAnnotation ann = eClass.getEAnnotation(GENMODEL_URI);
        if (ann != null && ann.getDetails().containsKey("documentation")) {
            return ann.getDetails().get("documentation");
        }
        return "";
    }

    private String mapDataType(EClassifier d) {
        if (d == null)
            return "string";
        if (d == EcorePackage.Literals.EDOUBLE || d == EcorePackage.Literals.EFLOAT
                || d == EcorePackage.Literals.EINT || d == EcorePackage.Literals.ELONG
                || "EDouble".equalsIgnoreCase(d.getName()) || "EFloat".equalsIgnoreCase(d.getName())
                || "EInt".equalsIgnoreCase(d.getName()) || "ELong".equalsIgnoreCase(d.getName())
                || "number".equalsIgnoreCase(d.getName())) {
            return "number";
        }
        if (d == EcorePackage.Literals.EBOOLEAN || "EBoolean".equalsIgnoreCase(d.getName())
                || "boolean".equalsIgnoreCase(d.getName())) {
            return "boolean";
        }
        return "string";
    }

    private boolean isMany(EStructuralFeature feature) {
        return feature != null && (feature.isMany() || feature.getUpperBound() == ETypedElement.UNBOUNDED_MULTIPLICITY);
    }
}