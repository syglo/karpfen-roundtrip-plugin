package org.karpfen.design;

import java.util.Set;

import org.eclipse.sirius.business.api.componentization.ViewpointRegistry;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.eclipse.ui.IStartup;
import org.karpfen.resource.KarpfenResourceInitializer;

public class KarpfenStartup implements IStartup {

    public static final String PLUGIN_ID = "org.karpfen.roundtrip.plugin";

    @Override
    public void earlyStartup() {
        registerKarpfenViewpoints();
    }

    public static synchronized void registerKarpfenViewpoints() {
        try {
            // emf resource and epackage
            KarpfenResourceInitializer.init();

            for (Viewpoint vp : ViewpointRegistry.getInstance().getViewpoints()) {
                if ("KarpfenViewpoint".equals(vp.getName())) {
                    return;
                }
            }

            String odesignPath = PLUGIN_ID + "/description/karpfen.odesign";
            Set<Viewpoint> viewpoints = ViewpointRegistry.getInstance().registerFromPlugin(odesignPath);

            KarpfenLog.info("Sirius Viewpoints registered: " + (viewpoints != null ? viewpoints.size() : 0));
            if (viewpoints != null) {
                for (Viewpoint vp : viewpoints) {
                    KarpfenLog.info("  -> Loaded Viewpoint: " + vp.getName() + " (" + vp.getLabel() + ")");
                }
            }
        } catch (Exception e) {
            KarpfenLog.error("Failed to register viewpoint: " + e.getMessage(), e);
        }
    }
}