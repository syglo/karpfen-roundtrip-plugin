package org.karpfen.editor;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class KarpfenColorManager {

    public static final RGB KEYWORD = new RGB(127, 0, 85);
    public static final RGB STRING = new RGB(42, 0, 255);
    public static final RGB NUMBER = new RGB(0, 128, 128);
    public static final RGB COMMENT = new RGB(63, 127, 95);
    public static final RGB IDENTIFIER = new RGB(0, 80, 160);
    public static final RGB DEFAULT = new RGB(0, 0, 0);

    private final Map<RGB, Color> colorTable = new HashMap<>();

    public Color getColor(RGB rgb) {
        Display display = Display.getCurrent();
        if (display == null) {
            display = Display.getDefault();
        }
        final Display targetDisplay = display;
        return colorTable.computeIfAbsent(rgb, r -> new Color(targetDisplay, r));
    }

    public void dispose() {
        for (Color color : colorTable.values()) {
            if (color != null && !color.isDisposed()) {
                color.dispose();
            }
        }
        colorTable.clear();
    }
}
