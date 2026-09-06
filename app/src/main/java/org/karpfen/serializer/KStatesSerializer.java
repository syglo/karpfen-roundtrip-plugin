package org.karpfen.serializer;

import org.eclipse.emf.ecore.EObject;

/**
 * Common serializer contract for transforming dynamic EMF {@link EObject}
 * instance graphs into {@code .kstates} DSL source text.
 */
public interface KStatesSerializer {
    /**
     * Serializes a dynamic EMF instance root {@link EObject} into {@code .kstates}
     * DSL syntax.
     *
     * @param rootObject the root instance object
     * @return generated {@code .kstates} text
     */
    String serialize(EObject rootObject);
}
