package org.karpfen.serializer;

import org.eclipse.emf.ecore.EObject;
import org.karpfen.design.KarpfenLog;

public class AcceleoKStatesSerializer {

    public static final String MODULE_NAME = "templates::generateKStates";
    public static final String TEMPLATE_NAME = "generateKStates";
    public static final String OUTPUT_FILE = "output.kstates";

    private final AcceleoRunner runner = new AcceleoRunner();

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
