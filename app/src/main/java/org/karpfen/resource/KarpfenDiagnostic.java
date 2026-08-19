package org.karpfen.resource;

import org.eclipse.emf.ecore.resource.Resource;

public class KarpfenDiagnostic implements Resource.Diagnostic {

    private final String message;
    private final String location;
    private final int line;
    private final int column;

    // Reroutes EMF's Resource.getErrrors() to diagnostics instead of throwing
    // IOExceptions
    public KarpfenDiagnostic(String message, String location, int line, int column) {
        this.message = message != null ? message : "Unknown error";
        this.location = location != null ? location : "";
        this.line = Math.max(1, line);
        this.column = Math.max(0, column);
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public int getLine() {
        return line;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        return String.format("[%s:%d:%d] %s", location, line, column, message);
    }
}
