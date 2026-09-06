package org.karpfen.serializer;

import org.eclipse.emf.ecore.EObject;
import org.karpfen.design.KarpfenLog;

/**
 * Template-driven serializer generating {@code .kstates} DSL source text using
 * Acceleo 4 code generation.
 */
public class AcceleoKStatesSerializer implements KStatesSerializer {

    public static final String MODULE_NAME = "templates::generateKStates";
    public static final String TEMPLATE_NAME = "generateKStates";
    public static final String OUTPUT_FILE = "output.kstates";

    private final AcceleoRunner runner = new AcceleoRunner();

    /**
     * Serializes the dynamic state machine {@link EObject} into {@code .kstates}
     * text via Acceleo templates.
     * {resources/templates/generateKStates.mtl}.
     *
     * @param smObject the root dynamic state machine object
     * @return generated {@code .kstates} text
     */
    @Override
    public String serialize(EObject smObject) {
        if (smObject == null) {
            return "";
        }
        try {
            return runner.generateToString(MODULE_NAME, TEMPLATE_NAME, OUTPUT_FILE, smObject);
        } catch (Throwable t) {
            KarpfenLog.warn("Acceleo KStates template serialization fallback invoked: " + t.getMessage());
            return new EcoreToKStatesManualSerializer().serialize(smObject);
        }
    }
}
