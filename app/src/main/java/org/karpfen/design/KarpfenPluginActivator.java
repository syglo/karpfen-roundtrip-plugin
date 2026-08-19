package org.karpfen.design;

import java.util.Set;

import org.eclipse.sirius.business.api.componentization.ViewpointRegistry;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.karpfen.resource.KarpfenResourceInitializer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleActivator;

public class KarpfenPluginActivator implements BundleActivator {

    public static final String PLUGIN_ID = "org.karpfen.roundtrip.plugin";
    private static Set<Viewpoint> viewpoints;

    @Override
    public void start(BundleContext context) throws Exception {
        // emf resource and epackage
        KarpfenResourceInitializer.init();

        // register .odesign specification model
        String odesignPath = PLUGIN_ID + "/description/karpfen.odesign";
        viewpoints = ViewpointRegistry.getInstance().registerFromPlugin(odesignPath);

        System.out.println("=================================================");
        System.out.println("[Karpfen] Sirius Viewpoints registered: " + (viewpoints != null ? viewpoints.size() : 0));
        if (viewpoints != null) {
            for (Viewpoint vp : viewpoints) {
                System.out.println("  -> Loaded Viewpoint: " + vp.getName() + " (" + vp.getLabel() + ")");
            }
        }
        System.out.println("=================================================");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (viewpoints != null) {
            for (Viewpoint vp : viewpoints) {
                ViewpointRegistry.getInstance().disposeFromPlugin(vp);
            }
            viewpoints.clear();
            viewpoints = null;
        }
    }
}
