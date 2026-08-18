package org.karpfen.serializer;

import org.eclipse.emf.ecore.EPackage;

public interface KMetaSerializer {
    String serialize(EPackage ePackage);
}
