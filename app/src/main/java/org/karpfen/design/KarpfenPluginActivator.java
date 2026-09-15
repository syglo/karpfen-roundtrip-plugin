package org.karpfen.design;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class KarpfenPluginActivator implements BundleActivator {

    public static final String PLUGIN_ID = "org.karpfen.roundtrip.plugin";

    @Override
    public void start(BundleContext context) throws Exception {
        // Double activations now activates trhough KarpfenStartup
        KarpfenStartup.registerKarpfenViewpoints();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
    }
}