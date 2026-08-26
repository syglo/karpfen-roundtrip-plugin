package org.karpfen.editor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.editors.text.TextEditor;
import org.karpfen.serializer.KarpfenDslFormatter;

public class KarpfenEditor extends TextEditor {

    private final KarpfenColorManager colorManager;

    public KarpfenEditor() {
        super();
        this.colorManager = new KarpfenColorManager();
    }

    @Override
    protected void doSetInput(IEditorInput input) throws org.eclipse.core.runtime.CoreException {
        super.doSetInput(input);
        String name = input != null ? input.getName() : "kmeta";
        String ext = name.contains(".") ? name.substring(name.lastIndexOf(".") + 1) : "kmeta";
        setSourceViewerConfiguration(new KarpfenSourceViewerConfiguration(colorManager, ext, this));
    }

    @Override
    public void doSave(IProgressMonitor progressMonitor) {
        // force formatter
        if (getDocumentProvider() != null && getEditorInput() != null) {
            IDocument document = getDocumentProvider().getDocument(getEditorInput());
            if (document != null) {
                String raw = document.get();
                String formatted = KarpfenDslFormatter.formatAuto(raw, getEditorInput().getName());
                if (!raw.equals(formatted)) {
                    document.set(formatted);
                }
            }
        }
        super.doSave(progressMonitor != null ? progressMonitor : new NullProgressMonitor());

        // Refresh workspace container
        if (getEditorInput() instanceof IFileEditorInput fileInput) {
            IFile file = fileInput.getFile();
            try {
                file.refreshLocal(IResource.DEPTH_ZERO, progressMonitor);
                IContainer parent = file.getParent();
                if (parent != null) {
                    parent.refreshLocal(IResource.DEPTH_ONE, progressMonitor);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void dispose() {
        colorManager.dispose();
        super.dispose();
    }
}
