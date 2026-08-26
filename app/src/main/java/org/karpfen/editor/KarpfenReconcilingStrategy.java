package org.karpfen.editor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.karpfen.design.KarpfenLog;
import org.karpfen.resource.KarpfenProblemMarkerManager;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KmodelDSLConverter;
import dsl.textual.KstatesDSLConverter;
import meta.Metamodel;

public class KarpfenReconcilingStrategy implements IReconcilingStrategy, IReconcilingStrategyExtension {

    private IDocument document;
    private final KarpfenEditor editor;

    public KarpfenReconcilingStrategy(KarpfenEditor editor) {
        this.editor = editor;
    }

    @Override
    public void setProgressMonitor(IProgressMonitor monitor) {
    }

    @Override
    public void initialReconcile() {
        validate();
    }

    @Override
    public void setDocument(IDocument document) {
        this.document = document;
    }

    @Override
    public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
        validate();
    }

    @Override
    public void reconcile(IRegion partition) {
        validate();
    }

    private void validate() {
        if (document == null || editor == null)
            return;
        IEditorInput input = editor.getEditorInput();
        if (!(input instanceof IFileEditorInput fileInput))
            return;

        IFile file = fileInput.getFile();
        String content = document.get();
        String ext = file.getFileExtension() != null ? file.getFileExtension().toLowerCase() : "";
        URI uri = URI.createPlatformResourceURI(file.getFullPath().toString(), true);

        try {
            if ("kmeta".equals(ext)) {
                KmetaDSLConverter.INSTANCE.parseKmetaString(content, Collections.emptyList());
                KarpfenProblemMarkerManager.clearMarkers(uri);
            } else if ("kmodel".equals(ext)) {
                Metamodel metamodel = resolveCompanionMetamodel(file);
                if (metamodel == null) {
                    throw new IllegalArgumentException(
                            "Could not resolve corresponding .kmeta (Place a matching .kmeta file in the same folder).");
                }
                KmodelDSLConverter.INSTANCE.parseKmodelString(content, metamodel);
                KarpfenProblemMarkerManager.clearMarkers(uri);
            } else if ("kstates".equals(ext)) {
                KstatesDSLConverter.INSTANCE.parseKstatesString(content);
                KarpfenProblemMarkerManager.clearMarkers(uri);
            }

        } catch (Throwable t) {
            KarpfenProblemMarkerManager.reportError(uri, content, t);
            KarpfenLog.warn("Reconciler detected issue in " + file.getName() + ": " + t.getMessage());
        }
    }

    private Metamodel resolveCompanionMetamodel(IFile modelFile) {
        try {
            IContainer parent = modelFile.getParent();
            if (parent == null || !parent.exists())
                return null;

            String baseName = modelFile.getName().replace(".kmodel", "");
            IResource exact = parent.findMember(baseName + ".kmeta");
            if (exact instanceof IFile kmetaFile && kmetaFile.exists()) {
                return parseMetamodel(kmetaFile);
            }

            for (IResource member : parent.members()) {
                if (member instanceof IFile f && "kmeta".equalsIgnoreCase(f.getFileExtension())) {
                    return parseMetamodel(f);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Metamodel parseMetamodel(IFile kmetaFile) {
        try (InputStream in = kmetaFile.getContents(true)) {
            String kmetaContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaContent, Collections.emptyList());
        } catch (Throwable ignored) {
            return null;
        }
    }
}