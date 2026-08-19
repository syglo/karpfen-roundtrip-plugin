package org.karpfen.design;

import org.eclipse.sirius.business.api.componentization.ViewpointRegistry;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.eclipse.ui.IStartup;
import org.karpfen.resource.KarpfenResourceInitializer;

import java.util.Set;

public class KarpfenStartup implements IStartup {
    
    public static final String PLUGIN_ID = "org.karpfen.roundtrip.plugin";

    @Override
    public void earlyStartup() {
        try {
            // emf resource and epackage
            KarpfenResourceInitializer.init();

            String odesignPath = PLUGIN_ID + "/description/karpfen.odesign";
            Set<Viewpoint> viewpoints = ViewpointRegistry.getInstance().registerFromPlugin(odesignPath);

            System.out.println("=================================================");
            System.out.println("[Karpfen] Sirius Viewpoints registered via IStartup: " + (viewpoints != null ? viewpoints.size() : 0));
            if (viewpoints != null) {
                for (Viewpoint vp : viewpoints) {
                    System.out.println("  -> Loaded Viewpoint: " + vp.getName() + " (" + vp.getLabel() + ")");
                }
            }
            System.out.println("=================================================");
        } catch (Exception e) {
            System.err.println("[Karpfen] Failed to register viewpoint on startup: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
