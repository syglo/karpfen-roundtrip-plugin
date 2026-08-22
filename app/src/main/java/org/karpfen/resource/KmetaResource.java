package org.karpfen.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
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

    @Override
    public EObject getEObject(String uriFragment) {
        if (uriFragment != null && !getContents().isEmpty() && getContents().get(0) instanceof EPackage pkg) {
            String name = uriFragment;
            if (name.startsWith("//")) {
                name = name.substring(2);
            } else if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (name.startsWith("@eClassifiers.")) {
                name = name.substring("@eClassifiers.".length());
            }
            EClassifier classifier = pkg.getEClassifier(name);
            if (classifier != null) {
                return classifier;
            }
        }
        return super.getEObject(uriFragment);
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
            getErrors().clear();
            getContents().clear();
            getContents().add(ePackage);

            if (getResourceSet() != null) {
                getResourceSet().getPackageRegistry().put(ePackage.getNsURI(), ePackage);
            }

            // clear problem markers
            KarpfenProblemMarkerManager.clearMarkers(getURI());

        } catch (Throwable t) {
            // Soft failure, record it to emf and problem marker
            int line = KarpfenProblemMarkerManager.findOffendingLine(t, content);
            String message = KarpfenProblemMarkerManager.formatUserMessage(t);

            getErrors().add(new KarpfenDiagnostic(message, getURI().toString(), line, 0));
            KarpfenProblemMarkerManager.reportError(getURI(), content, t);

            System.err.println("[Karpfen] Validation error in .kmeta: " + message);
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
            outputStream.flush();
        }
    }

    public Metamodel getParsedMetamodel() {
        return parsedMetamodel;
    }
}
