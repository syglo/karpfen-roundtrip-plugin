package org.karpfen.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;

public final class KarpfenProblemMarkerManager {

    public static final String MARKER_TYPE = IMarker.PROBLEM;

    private static final Pattern LINE_COL_PATTERN = Pattern.compile("line (\\d+):(\\d+)");
    private static final Pattern LINE_ALT_PATTERN = Pattern.compile("at line (\\d+)");
    private static final Pattern SINGLE_QUOTED = Pattern.compile("'([^']+)'");
    private static final Pattern DOUBLE_QUOTED = Pattern.compile("\"([^\"]+)\"");

    // Parses exceptions and reroutes them to readable eclipse workspace problem
    // markers window
    private KarpfenProblemMarkerManager() {

    }

    public static void clearMarkers(URI uri) {
        IFile file = getWorkspaceFile(uri);
        if (file != null && file.exists()) {
            try {
                file.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
            } catch (CoreException ignored) {
            }
        }
    }

    public static void reportError(URI uri, String sourceContent, Throwable ex) {
        IFile file = getWorkspaceFile(uri);
        if (file == null || !file.exists()) {
            return;
        }

        int line = findOffendingLine(ex, sourceContent);
        String userFriendlyMessage = formatUserMessage(ex);

        try {
            clearMarkers(uri);
            IMarker marker = file.createMarker(MARKER_TYPE);
            marker.setAttribute(IMarker.MESSAGE, userFriendlyMessage);
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
            marker.setAttribute(IMarker.LINE_NUMBER, line);
            marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
        } catch (CoreException ignored) {
        }
    }

    public static IFile getWorkspaceFile(URI uri) {
        if (uri == null || !uri.isPlatformResource()) {
            return null;
        }
        try {
            String platformPath = uri.toPlatformString(true);
            return ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(platformPath));
        } catch (Throwable t) {
            return null;
        }
    }

    public static int findOffendingLine(Throwable ex, String content) {
        if (ex == null)
            return 1;
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();

        // apply regex for "line 4:12" or "at line 4"
        Matcher m1 = LINE_COL_PATTERN.matcher(msg);
        if (m1.find()) {
            return Integer.parseInt(m1.group(1));
        }

        Matcher m2 = LINE_ALT_PATTERN.matcher(msg);
        if (m2.find()) {
            return Integer.parseInt(m2.group(1));
        }

        if (content == null || content.isBlank()) {
            return 1;
        }

        // we get keywords from error messages
        List<String> tokensToFind = new ArrayList<>();
        Matcher sqMatcher = SINGLE_QUOTED.matcher(msg);
        while (sqMatcher.find()) {
            tokensToFind.add(sqMatcher.group(1));
        }
        Matcher dqMatcher = DOUBLE_QUOTED.matcher(msg);
        while (dqMatcher.find()) {
            tokensToFind.add(dqMatcher.group(1));
        }

        // list all lines and locate line with problme
        String[] lines = content.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String lineText = lines[i];
            for (String token : tokensToFind) {
                if (!token.isBlank() && (lineText.contains("\"" + token + "\"") || lineText.contains("'" + token + "'")
                        || lineText.contains(token))) {
                    return i + 1;
                }
            }
        }

        // catch NullPointerException from ANTLR visitor
        if (ex instanceof NullPointerException || msg.contains("NullPointerException") || msg.contains("STRING()")) {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if ((line.startsWith("prop(") || line.startsWith("has(") || line.startsWith("knows("))
                        && (!line.contains("\"") || countOccurrences(line, '"') < 4)) {
                    return i + 1;
                }
            }
        }

        return 1;
    }

    public static String formatUserMessage(Throwable ex) {
        if (ex == null)
            return "Unknown validation error";

        String raw = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        raw = raw.replace("java.lang.IllegalArgumentException: ", "")
                .replace("java.lang.NullPointerException: ", "")
                .replace("java.lang.NumberFormatException: ", "");

        // "" and null ast
        if (ex instanceof NullPointerException || raw.contains("NullPointerException") || raw.contains("STRING()")
                || raw.contains("TerminalNode.getText()")) {
            return "Syntax Error: Ensure all identifiers, property names, and types are enclosed in double quotes (e.g., prop(\"name\", \"string\")).";
        }

        // unknown value in type from .kmeta
        if (raw.contains("Unknown rule value type for prop")) {
            return raw + " (Supported primitive types: \"string\", \"number\", \"boolean\", or list(\"...\"))";
        }

        // mismatch in .kmodel, property doesn't exist in .kmeta
        if (raw.contains("Unknown property")) {
            return "Metamodel Mismatch: " + raw + " (Property is not declared in the corresponding .kmeta schema).";
        }

        // mismatch in .kmodel, class type doesn't exist in .kmeta
        if (raw.contains("Unknown class type")) {
            return "Metamodel Mismatch: " + raw + " (Class is not defined in the corresponding .kmeta schema).";
        }

        // type mismatch string as number
        if (raw.contains("For input string:")) {
            return "Type Mismatch: " + raw + " (Expected a valid numeric literal conforming to the metamodel).";
        }

        // antrl lexr token recognition errors
        if (raw.contains("token recognition error at:")) {
            return "Lexical Error: " + raw;
        }

        // for everything else, antlr bad input / syntax errors
        if (raw.contains("mismatched input") || raw.contains("no viable alternative")) {
            return "Syntax Error: " + raw;
        }

        // .kmeta doesn't exist for this .kmodel
        if (raw.contains("Could not resolve corresponding .kmeta")) {
            return "Metamodel Resolution Error: " + raw + " (Place a matching .kmeta file in the same folder).";
        }

        return raw;
    }

    private static int countOccurrences(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch)
                count++;
        }
        return count;
    }
}