package org.karpfen.serializer;

import org.eclipse.emf.ecore.EPackage;

public class AcceleoKMetaSerializer implements KMetaSerializer {

    public static final String MODULE_NAME = "templates::generateKMeta";
    public static final String TEMPLATE_NAME = "generateKMeta";
    public static final String OUTPUT_FILE = "output.kmeta";

    private final AcceleoRunner runner = new AcceleoRunner();

    @Override
    public String serialize(EPackage ePackage) {
        if (ePackage == null) {
            return "";
        }
        return runner.generateToString(MODULE_NAME, TEMPLATE_NAME, OUTPUT_FILE, ePackage);
    }

}
