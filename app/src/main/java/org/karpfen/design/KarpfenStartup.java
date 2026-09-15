package org.karpfen.design;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.sirius.business.api.componentization.ViewpointRegistry;
import org.eclipse.sirius.business.api.dialect.DialectManager;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.karpfen.resource.KarpfenResourceInitializer;

/**
 * Hot Reloading for .odesign file in workspace if present.
 * Early startup hook for the Karpfen Roundtrip Sirius plugin.
 * Handles viewpoint registration, live-reloading of workspace
 * {@code karpfen.odesign}
 * on edits/additions, and clean exit prompting when the workspace VSM is
 * deleted.
 */
public class KarpfenStartup implements IStartup {

    public static final String PLUGIN_ID = "org.karpfen.roundtrip.plugin";
    public static final String PLUGIN_ODESIGN_PATH = PLUGIN_ID + "/description/karpfen.odesign";
    public static final URI PLUGIN_ODESIGN_URI = URI.createPlatformPluginURI(PLUGIN_ODESIGN_PATH, true);

    private static volatile boolean listenerRegistered = false;
    private static final AtomicBoolean closePromptActive = new AtomicBoolean(false);
    private static final Object jobLock = new Object();
    private static volatile Job pendingChangeJob = null;

    @Override
    public void earlyStartup() {
        registerKarpfenViewpoints();
        setupWorkspaceChangeListener();
    }

    /**
     * Registers Karpfen viewpoints in the Sirius {@link ViewpointRegistry}.
     * If a local {@code karpfen.odesign} exists in the workspace, it takes
     * precedence and
     * redirects the plugin URI via {@link URIConverter#URI_MAP} so that open
     * diagrams
     * and editors seamlessly read the live workspace version.
     */
    public static synchronized void registerKarpfenViewpoints() {
        try {
            KarpfenResourceInitializer.init();

            ViewpointRegistry registry = ViewpointRegistry.getInstance();

            // Purge any stale workspace viewpoints that might be lingering in memory
            try {
                registry.registerFromWorkspace(Collections.emptySet());
            } catch (Throwable ignored) {
            }

            IFile workspaceOdesign = findWorkspaceOdesign();

            if (workspaceOdesign != null && workspaceOdesign.exists()) {
                String wsPath = workspaceOdesign.getFullPath().toString();
                URI wsUri = URI.createPlatformResourceURI(wsPath, true);
                KarpfenLog.info("[KarpfenStartup] Detected local workspace VSM: " + wsPath);

                // Map plugin URI to workspace URI so that all references .aird, registry,
                // session transparently resolve to the workspace file
                URIConverter.URI_MAP.put(PLUGIN_ODESIGN_URI, wsUri);

                boolean alreadyRegistered = false;
                for (Viewpoint vp : registry.getViewpoints()) {
                    if ("KarpfenViewpoint".equals(vp.getName()) && registry.isFromPlugin(vp)
                            && vp.eResource() != null) {
                        alreadyRegistered = true;
                        break;
                    }
                }

                if (!alreadyRegistered) {
                    registry.registerFromPlugin(PLUGIN_ODESIGN_PATH);
                } else {
                    registry.reloadAllFromPlugins();
                }

                DialectManager.INSTANCE.invalidateMappingCache();
                KarpfenLog
                        .info("[KarpfenStartup] Successfully registered/reloaded viewpoint from workspace: " + wsPath);
            } else {
                // Revert any workspace redirect
                boolean wasRedirected = URIConverter.URI_MAP.remove(PLUGIN_ODESIGN_URI) != null;

                boolean alreadyRegistered = false;
                for (Viewpoint vp : registry.getViewpoints()) {
                    if ("KarpfenViewpoint".equals(vp.getName()) && registry.isFromPlugin(vp)
                            && vp.eResource() != null) {
                        alreadyRegistered = true;
                        break;
                    }
                }

                if (!alreadyRegistered) {
                    registry.registerFromPlugin(PLUGIN_ODESIGN_PATH);
                } else if (wasRedirected) {
                    registry.reloadAllFromPlugins();
                }

                DialectManager.INSTANCE.invalidateMappingCache();
                KarpfenLog.info("[KarpfenStartup] Viewpoint registered from bundled plugin.");
            }
        } catch (Throwable e) {
            KarpfenLog.error("Failed to register viewpoint: " + e.getMessage(), e);
        }
    }

    /**
     * Listens for changes to karpfen.odesign.
     * Uses a debounced background job 500ms delay to prevent crashes
     */
    public static synchronized void setupWorkspaceChangeListener() {
        if (listenerRegistered) {
            return;
        }

        try {
            if (ResourcesPlugin.getWorkspace() != null) {
                ResourcesPlugin.getWorkspace().addResourceChangeListener(event -> {
                    IResourceDelta delta = event.getDelta();
                    if (delta == null) {
                        return;
                    }

                    boolean[] affected = new boolean[1];
                    try {
                        delta.accept(d -> {
                            if (affected[0]) {
                                return false;
                            }
                            IResource res = d.getResource();
                            if (res != null && res.getType() == IResource.FILE
                                    && "karpfen.odesign".equalsIgnoreCase(res.getName())) {
                                affected[0] = true;
                                return false;
                            }
                            return true;
                        });
                    } catch (Throwable ignored) {
                    }

                    if (affected[0]) {
                        synchronized (jobLock) {
                            if (pendingChangeJob != null) {
                                pendingChangeJob.cancel();
                            }
                            pendingChangeJob = new Job("Karpfen VSM Workspace Change") {
                                @Override
                                protected IStatus run(IProgressMonitor monitor) {
                                    if (monitor.isCanceled()) {
                                        return Status.CANCEL_STATUS;
                                    }
                                    handleWorkspaceChangeAsync();
                                    return Status.OK_STATUS;
                                }
                            };
                            pendingChangeJob.setSystem(true);
                            pendingChangeJob.schedule(500);
                        }
                    }
                }, IResourceChangeEvent.POST_CHANGE);
                listenerRegistered = true;
            }
        } catch (Throwable t) {
            KarpfenLog.warn("[KarpfenStartup] Could not attach change listener: " + t.getMessage());
        }
    }

    private static void handleWorkspaceChangeAsync() {
        IFile wsFile = findWorkspaceOdesign();
        if (wsFile != null && wsFile.exists()) {
            // Local karpfen.odesign added or modified: live reload
            KarpfenLog.info("[KarpfenStartup] Workspace karpfen.odesign change detected. Reloading viewpoints...");
            registerKarpfenViewpoints();
        } else {
            // Local karpfen.odesign was deleted from workspace: exit eclipse and reload
            // manually
            if (closePromptActive.compareAndSet(false, true)) {
                try {
                    if (PlatformUI.isWorkbenchRunning() && PlatformUI.getWorkbench().getDisplay() != null) {
                        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
                            try {
                                if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null
                                        && PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell() != null) {
                                    MessageDialog.openInformation(
                                            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                                            "Karpfen Plugin Reload",
                                            "The local 'karpfen.odesign' was deleted from the workspace.\n\n"
                                                    + "Please restart Eclipse manually.");

                                    PlatformUI.getWorkbench().close();
                                }
                            } finally {
                                closePromptActive.set(false);
                            }
                        });
                    } else {
                        closePromptActive.set(false);
                    }
                } catch (Throwable t) {
                    closePromptActive.set(false);
                }
            }
        }
    }

    private static IFile findWorkspaceOdesign() {
        IFile[] result = new IFile[1];
        try {
            if (ResourcesPlugin.getWorkspace() != null && ResourcesPlugin.getWorkspace().getRoot() != null) {
                ResourcesPlugin.getWorkspace().getRoot().accept(resource -> {
                    if (result[0] != null) {
                        return false;
                    }
                    if (resource.getType() == IResource.FILE
                            && "karpfen.odesign".equalsIgnoreCase(resource.getName())) {
                        result[0] = (IFile) resource;
                        return false;
                    }
                    return true;
                });
            }
        } catch (Throwable ignored) {
        }
        return result[0];
    }

    public static void refreshActiveSessions() {
    }
}