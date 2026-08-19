package org.karpfen.editor;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.jface.text.IDocument;
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
        super.doSave(progressMonitor);
    }

    @Override
    public void dispose() {
        colorManager.dispose();
        super.dispose();
    }
}
