package org.karpfen.serializer;

import org.eclipse.emf.ecore.EPackage;
import org.karpfen.design.KarpfenLog;

/**
 * Template-driven serializer generating {@code .kmeta} DSL source text using
 * Acceleo 4 code generation.
 */
public class AcceleoKMetaSerializer implements KMetaSerializer {

    public static final String MODULE_NAME = "templates::generateKMeta";
    public static final String TEMPLATE_NAME = "generateKMeta";
    public static final String OUTPUT_FILE = "output.kmeta";

    private final AcceleoRunner runner = new AcceleoRunner();

    /**
     * Serializes the {@link EPackage} containing KMeta EMF model into karpfen
     * {@code .kmeta} text with Acceleo template
     * {resources/templates/generateKMeta.mt}.
     *
     * @param ePackage the KMeta EMF package to serialize
     * @return generated {@code .kmeta} text as string, or empty string if @param
     *         ePackage is null
     */
    @Override
    public String serialize(EPackage ePackage) {
        if (ePackage == null) {
            return "";
        }
        try {
            return runner.generateToString(MODULE_NAME, TEMPLATE_NAME, OUTPUT_FILE, ePackage);
        } catch (Throwable t) {
            KarpfenLog.warn("Acceleo template serialization fallback invoked: " + t.getMessage());
            return new EcoreToKMetaManualSerializer().serialize(ePackage);
        }
    }
}
