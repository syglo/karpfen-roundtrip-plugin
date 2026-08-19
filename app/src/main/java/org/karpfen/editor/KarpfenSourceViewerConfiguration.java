package org.karpfen.editor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.formatter.ContentFormatter;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

public class KarpfenSourceViewerConfiguration extends TextSourceViewerConfiguration {
    
    private final KarpfenColorManager colorManager;
    private final String fileExtension;

    public KarpfenSourceViewerConfiguration(KarpfenColorManager colorManager, String fileExtension) {
        this.colorManager = colorManager;
        this.fileExtension = fileExtension;
    }

    @Override
    public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
        PresentationReconciler reconciler = new PresentationReconciler();
        DefaultDamagerRepairer dr = new DefaultDamagerRepairer(new AntlrTokenScanner(colorManager, fileExtension));
        reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
        reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
        return reconciler;
    }

    @Override
    public IContentFormatter getContentFormatter(ISourceViewer sourceViewer) {
        ContentFormatter formatter = new ContentFormatter();
        KarpfenFormattingStrategy strategy = new KarpfenFormattingStrategy(fileExtension);
        formatter.setFormattingStrategy(strategy, IDocument.DEFAULT_CONTENT_TYPE);
        formatter.enablePartitionAwareFormatting(false);
        return formatter;
    }
}
