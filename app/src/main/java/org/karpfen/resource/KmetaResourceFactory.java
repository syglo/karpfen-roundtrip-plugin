package org.karpfen.resource;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;

public class KmetaResourceFactory extends ResourceFactoryImpl {

    @Override
    public Resource createResource(URI uri) {
        return new KmetaResource(uri);
    }
}
