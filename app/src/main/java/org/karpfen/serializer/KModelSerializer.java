package org.karpfen.serializer;

import org.eclipse.emf.ecore.EObject;

public interface KModelSerializer {
    String serialize(EObject rootObject);
}
