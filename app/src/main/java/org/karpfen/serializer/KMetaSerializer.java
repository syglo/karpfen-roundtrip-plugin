package org.karpfen.serializer;

import org.eclipse.emf.ecore.EPackage;

/**
 * Common serializer contract for transforming EMF {@link EPackage} metamodels
 * into {@code .kmeta} DSL source text.
 */
public interface KMetaSerializer {
    /**
     * Serializes an {@link EPackage} into {@code .kmeta} DSL syntax.
     *
     * @param ePackage the EMF package to serialize
     * @return generated {@code .kmeta} text
     */
    String serialize(EPackage ePackage);
}
