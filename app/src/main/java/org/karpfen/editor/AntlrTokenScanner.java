package org.karpfen.editor;

import kmeta.KmetaLexer;
import kmodel.KmodelLexer;
import kstates.KstatesLexer;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.swt.SWT;

public class AntlrTokenScanner implements ITokenScanner {

    private final KarpfenColorManager colorManager;
    private final String fileExtension;

    private Lexer lexer;
    private Token currentToken;
    private int rangeOffset;

    public AntlrTokenScanner(KarpfenColorManager colorManager, String fileExtension) {
        this.colorManager = colorManager;
        this.fileExtension = fileExtension != null ? fileExtension.toLowerCase().replace(".", "").trim() : "kmeta";
    }

    @Override
    public void setRange(IDocument document, int offset, int length) {
        this.rangeOffset = offset;
        try {
            String text = document.get(offset, length);
            this.lexer = createLexer(text);
        } catch (Exception e) {
            this.lexer = null;
        }
    }

    @Override
    public IToken nextToken() {
        if (lexer == null) {
            return org.eclipse.jface.text.rules.Token.EOF;
        }

        try {
            currentToken = lexer.nextToken();
            if (currentToken == null || currentToken.getType() == Token.EOF) {
                return org.eclipse.jface.text.rules.Token.EOF;
            }

            return mapTokenToAttribute(currentToken.getType());
        } catch (Exception e) {
            return org.eclipse.jface.text.rules.Token.EOF;
        }
    }

    @Override
    public int getTokenOffset() {
        return currentToken != null ? (rangeOffset + currentToken.getStartIndex()) : rangeOffset;
    }

    @Override
    public int getTokenLength() {
        return currentToken != null ? (currentToken.getStopIndex() - currentToken.getStartIndex() + 1) : 0;
    }

    private Lexer createLexer(String text) {
        return switch (fileExtension) {
            case "kmeta" -> new KmetaLexer(CharStreams.fromString(text));
            case "kmodel" -> new KmodelLexer(CharStreams.fromString(text));
            default -> new KstatesLexer(CharStreams.fromString(text));
        };
    }

    private IToken mapTokenToAttribute(int tokenType) {
        TextAttribute textAttribute = switch (fileExtension) {
            case "kmeta" -> mapKMetaToken(tokenType);
            case "kmodel" -> mapKmodelToken(tokenType);
            case "kstates" -> mapKstatesToken(tokenType);
            default -> new TextAttribute(colorManager.getColor(KarpfenColorManager.DEFAULT));
        };
        return new org.eclipse.jface.text.rules.Token(textAttribute);
    }

    private TextAttribute mapKMetaToken(int tokenType) {
        return switch (tokenType) {
            case KmetaLexer.TYPE, KmetaLexer.PROP, KmetaLexer.HAS,
                    KmetaLexer.KNOWS, KmetaLexer.LIST ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.KEYWORD), null, SWT.BOLD);

            case KmetaLexer.STRING ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.STRING));

            default ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.DEFAULT));
        };
    }

    private TextAttribute mapKmodelToken(int tokenType) {
        return switch (tokenType) {
            case KmodelLexer.MAKE, KmodelLexer.OBJECT, KmodelLexer.PROP,
                    KmodelLexer.HAS, KmodelLexer.KNOWS ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.KEYWORD), null, SWT.BOLD);

            case KmodelLexer.STRING ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.STRING));

            default ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.DEFAULT));
        };
    }

    private TextAttribute mapKstatesToken(int tokenType) {
        return switch (tokenType) {
            case KstatesLexer.STATEMACHINE, KstatesLexer.ATTACHED, KstatesLexer.TO,
                    KstatesLexer.STATES, KstatesLexer.STATE, KstatesLexer.INITIAL,
                    KstatesLexer.ENTRY, KstatesLexer.DO, KstatesLexer.TRANSITIONS,
                    KstatesLexer.TRANSITION, KstatesLexer.NOT, KstatesLexer.LOOPING,
                    KstatesLexer.CONDITION, KstatesLexer.MACROS, KstatesLexer.MACRO ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.KEYWORD), null, SWT.BOLD);

            case KstatesLexer.STRING ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.STRING));

            default ->
                new TextAttribute(colorManager.getColor(KarpfenColorManager.DEFAULT));
        };
    }
}
