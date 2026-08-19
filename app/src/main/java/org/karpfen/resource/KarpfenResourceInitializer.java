package org.karpfen.resource;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public final class KarpfenResourceInitializer {

    private static boolean initialized = false;

    private KarpfenResourceInitializer() {

    }

    public static synchronized void init() {
        if (initialized)
            return;

        // register xmi, ecore
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("ecore", new XMIResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());

        // register dsls .kmeta, .kmodel, .kstates
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("kmeta", new KmetaResourceFactory());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("kmodel", new KmodelResourceFactory());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("kstates", new KstatesResourceFactory());

        // register ecore
        EPackage.Registry.INSTANCE.put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);

        initialized = true;
    }
}
