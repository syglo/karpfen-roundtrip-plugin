package org.karpfen.editor;

import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.karpfen.resource.KarpfenProblemMarkerManager;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KstatesDSLConverter;

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

        try {
            if ("kmeta".equals(ext)) {
                KmetaDSLConverter.INSTANCE.parseKmetaString(content, Collections.emptyList());
            } else if ("kstates".equals(ext)) {
                KstatesDSLConverter.INSTANCE.parseKstatesString(content);
            }
            KarpfenProblemMarkerManager.clearMarkers(
                    org.eclipse.emf.common.util.URI.createPlatformResourceURI(file.getFullPath().toString(), true));
        } catch (Throwable t) {
            KarpfenProblemMarkerManager.reportError(
                    org.eclipse.emf.common.util.URI.createPlatformResourceURI(file.getFullPath().toString(), true), t);
        }
    }
}
