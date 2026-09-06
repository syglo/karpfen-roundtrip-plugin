package org.karpfen.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.karpfen.design.KarpfenLog;
import org.karpfen.serializer.AcceleoKStatesSerializer;
import org.karpfen.serializer.EcoreToKStatesManualSerializer;
import org.karpfen.serializer.KStatesSerializer;
import org.karpfen.serializer.KarpfenDslFormatter;
import org.karpfen.serializer.SerializerMode;
import org.karpfen.transformer.KStatesToEcoreTransformer;

import dsl.textual.KstatesDSLConverter;
import states.StateMachine;

/**
 * Binds {@code .kstates} Karpfend text files extensions/associations in Eclipse
 * IDE.
 * EMF {@link org.eclipse.emf.ecore.resource.Resource} implementation for
 * Karpfen Behavioral State Machine files ({@code .kstates}).
 * 
 * Transforms textual state machines into dynamic behavioral statechart graphs
 * during {@link #doLoad(InputStream, Map)},
 * and serializes state machine changes back to {@code .kstates} DSL text during
 * {@link #doSave(OutputStream, Map)}.
 */
public class KstatesResource extends ResourceImpl {

    public static SerializerMode ACTIVE_MODE = SerializerMode.ACCELEO_TEMPLATE;

    private StateMachine parsedStateMachine;
    private EPackage statePackage;

    /**
     * Creates a new {@link KstatesResource} for the specified URI.
     *
     * @param uri URI of the {@code .kstates} resource
     */
    public KstatesResource(URI uri) {
        super(uri);
        KarpfenResourceInitializer.init();
    }

    // T2D
    @Override
    protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        try {
            // parse kstates text dsl to karpfen ast state machine
            this.parsedStateMachine = KstatesDSLConverter.INSTANCE.parseKstatesString(content);

            String smName = getURI().trimFileExtension().lastSegment();
            if (smName == null || smName.isBlank()) {
                smName = "kstatesMachine";
            }

            // transform karpfen ast to EMF ecore behavioral graph
            KStatesToEcoreTransformer transformer = new KStatesToEcoreTransformer();
            EObject rootStateMachine = transformer.transform(this.parsedStateMachine, smName);
            this.statePackage = transformer.getStatePackage();

            if (this.statePackage != null) {
                EPackage.Registry.INSTANCE.put(this.statePackage.getNsURI(), this.statePackage);
                if (getResourceSet() != null) {
                    getResourceSet().getPackageRegistry().put(this.statePackage.getNsURI(), this.statePackage);
                }
            }

            getErrors().clear();
            getContents().clear();
            getContents().add(rootStateMachine);

            KarpfenProblemMarkerManager.clearMarkers(getURI());

        } catch (Throwable t) {
            int line = KarpfenProblemMarkerManager.findOffendingLine(t, content);
            String message = KarpfenProblemMarkerManager.formatUserMessage(t);

            getErrors().clear();
            getContents().clear();
            getErrors().add(new KarpfenDiagnostic(message, getURI().toString(), line, 0));
            KarpfenProblemMarkerManager.reportError(getURI(), content, t);

            KarpfenLog.warn("Validation error in .kstates: " + message);
        }
    }

    // D2T
    @Override
    protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
        if (!getContents().isEmpty() && getContents().get(0) instanceof EObject smObj) {
            KStatesSerializer serializer = (ACTIVE_MODE == SerializerMode.ACCELEO_TEMPLATE)
                    ? new AcceleoKStatesSerializer()
                    : new EcoreToKStatesManualSerializer();
            String generated = serializer.serialize(smObj);
            String formatted = KarpfenDslFormatter.formatKStates(generated);
            outputStream.write(formatted.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            try {
                this.parsedStateMachine = KstatesDSLConverter.INSTANCE.parseKstatesString(formatted);
            } catch (Throwable t) {
                KarpfenLog.warn("Could not update in-memory parsedStateMachine after doSave: " + t.getMessage());
            }

            refreshWorkspaceAfterSave();
        }
    }

    private void refreshWorkspaceAfterSave() {
        if (getURI() != null && getURI().isPlatformResource()) {
            try {
                String platformPath = getURI().toPlatformString(true);
                IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(platformPath));
                if (file.exists() && file.getParent() != null) {
                    file.getParent().refreshLocal(IResource.DEPTH_ONE, null);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public StateMachine getParsedStateMachine() {
        return parsedStateMachine;
    }

    public EPackage getStatePackage() {
        return statePackage;
    }
}
