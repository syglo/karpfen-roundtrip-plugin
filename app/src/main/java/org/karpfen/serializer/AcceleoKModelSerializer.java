package org.karpfen.serializer;

import org.eclipse.emf.ecore.EObject;

public class AcceleoKModelSerializer implements KModelSerializer {

    public static final String MODULE_NAME = "templates::generateKModel";
    public static final String TEMPLATE_NAME = "generateKModel";
    public static final String OUTPUT_FILE = "output.kmodel";

    private final AcceleoRunner runner = new AcceleoRunner();

    @Override
    public String serialize(EObject rootObject) {
        if (rootObject == null) {
            return "";
        }
        return runner.generateToString(MODULE_NAME, TEMPLATE_NAME, OUTPUT_FILE, rootObject);
    }
}
