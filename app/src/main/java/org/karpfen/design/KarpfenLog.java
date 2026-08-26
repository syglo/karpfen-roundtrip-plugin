package org.karpfen.design;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public final class KarpfenLog {

    public static final String PLUGIN_ID = "org.karpfen.roundtrip.plugin";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private KarpfenLog() {
    }

    public static void trace(String category, String message) {
        info("[" + category + "] " + message);
    }

    public static void info(String message) {
        log(IStatus.INFO, message, null);
    }

    public static void warn(String message) {
        log(IStatus.WARNING, message, null);
    }

    public static void error(String message) {
        log(IStatus.ERROR, message, null);
    }

    public static void error(String message, Throwable throwable) {
        log(IStatus.ERROR, message, throwable);
    }

    private static void log(int severity, String message, Throwable throwable) {
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        String thread = Thread.currentThread().getName();
        String prefix = switch (severity) {
            case IStatus.ERROR -> String.format("[%s][%s][Karpfen ERROR] ", timestamp, thread);
            case IStatus.WARNING -> String.format("[%s][%s][Karpfen WARN]  ", timestamp, thread);
            default -> String.format("[%s][%s][Karpfen INFO]  ", timestamp, thread);
        };

        String fullMessage = prefix + (message != null ? message : "");

        if (severity == IStatus.ERROR) {
            System.err.println(fullMessage);
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        } else {
            System.out.println(fullMessage);
            if (throwable != null) {
                throwable.printStackTrace(System.out);
            }
        }

        try {
            Bundle bundle = FrameworkUtil.getBundle(KarpfenLog.class);
            if (bundle != null) {
                ILog log = Platform.getLog(bundle);
                if (log != null) {
                    log.log(new Status(severity, PLUGIN_ID, message != null ? message : "", throwable));
                }
            }
        } catch (Throwable ignored) {
        }
    }
}