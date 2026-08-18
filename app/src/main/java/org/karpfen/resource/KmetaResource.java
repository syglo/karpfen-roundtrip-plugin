package org.karpfen.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.karpfen.serializer.AcceleoKMetaSerializer;
import org.karpfen.serializer.EcoreToKMetaManualSerializer;
import org.karpfen.serializer.KMetaSerializer;
import org.karpfen.serializer.KarpfenDslFormatter;
import org.karpfen.serializer.SerializerMode;
import org.karpfen.transformer.KMetaToEcoreTransformer;

import dsl.textual.KmetaDSLConverter;
import meta.Metamodel;

public class KmetaResource extends ResourceImpl {

    public static SerializerMode ACTIVE_MODE = SerializerMode.ACCELEO_TEMPLATE;

    private Metamodel parsedMetamodel;

    public KmetaResource(URI uri) {
        super(uri);
    }

    // T2D
    @Override
    protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        try {
            // parse kmeta text dsl to karpfen ast metamodel
            this.parsedMetamodel = KmetaDSLConverter.INSTANCE.parseKmetaString(content, Collections.emptyList());

            // transform karpfen ast to EMF ecore
            KMetaToEcoreTransformer transformer = new KMetaToEcoreTransformer();
            String packageName = getURI().trimFileExtension().lastSegment();
            if (packageName == null || packageName.isBlank()) {
                packageName = "kmetaPackage";
            }

            String nsUri = "http://github/karpfen/" + packageName;
            EPackage ePackage = transformer.transform(this.parsedMetamodel, packageName, nsUri, packageName);

            // from super class
            getContents().clear();
            getContents().add(ePackage);

            if (getResourceSet() != null) {
                getResourceSet().getPackageRegistry().put(ePackage.getNsURI(), ePackage);
            }
        } catch (Exception e) {
            throw new IOException("[Karpfen] Failed to load .kmeta resource from: " + getURI(), e);
        }
    }

    // D2T
    @Override
    protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
        if (!getContents().isEmpty() && getContents().get(0) instanceof EPackage pkg) {
            KMetaSerializer serializer = (ACTIVE_MODE == SerializerMode.ACCELEO_TEMPLATE)
                ? new AcceleoKMetaSerializer()
                : new EcoreToKMetaManualSerializer();
            String generated = serializer.serialize(pkg);
            String formatted = KarpfenDslFormatter.formatKMeta(generated);
            outputStream.write(formatted.getBytes(StandardCharsets.UTF_8));
        }
    }

    public Metamodel getParsedMetamodel() {
        return parsedMetamodel;
    }
}
