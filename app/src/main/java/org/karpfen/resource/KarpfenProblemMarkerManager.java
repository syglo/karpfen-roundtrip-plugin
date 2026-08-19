package org.karpfen.resource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;

public class KarpfenProblemMarkerManager {

    public static final String MARKER_TYPE = IMarker.PROBLEM;
    private static final Pattern LINE_COL_PATTERN = Pattern.compile("line (\\d+):(\\d+)");
    private static final Pattern LINE_ALT_PATTERN = Pattern.compile("at line (\\d+)");

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

    public static void reportError(URI uri, Throwable ex) {
        IFile file = getWorkspaceFile(uri);
        if (file == null || !file.exists()) {
            return;
        }

        int line = extractLineNumber(ex);
        String rawMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        String userFriendlyMessage = formatUserMessage(rawMessage);

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

    public static int extractLineNumber(Throwable ex) {
        if (ex == null || ex.getMessage() == null)
            return 1;
        String msg = ex.getMessage();

        Matcher m = LINE_COL_PATTERN.matcher(msg);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }

        Matcher mAlt = LINE_ALT_PATTERN.matcher(msg);
        if (mAlt.find()) {
            return Integer.parseInt(mAlt.group(1));
        }

        return 1;
    }

    private static String formatUserMessage(String raw) {
        if (raw.contains("Unknown rule value type for prop")) {
            return raw.replace("java.lang.IllegalArgumentException: ", "")
                    + " (Supported types: 'string', 'number', 'boolean', or list('...'))";
        }
        if (raw.contains("Unknown class type")) {
            return raw.replace("java.lang.IllegalArgumentException: ", "")
                    + " (Check if this class is declared in the corresponding .kmeta file)";
        }
        if (raw.contains("Unknown property")) {
            return raw.replace("java.lang.IllegalArgumentException: ", "")
                    + " (Property is not declared on the target class in .kmeta)";
        }
        return raw;
    }
}
