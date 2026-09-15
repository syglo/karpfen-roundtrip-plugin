package org.karpfen.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.eclipse.emf.ecore.resource.URIConverter;
import org.junit.jupiter.api.Test;

public class KarpfenStartupTest {

    @Test
    void testConstants() {
        assertEquals("org.karpfen.roundtrip.plugin", KarpfenStartup.PLUGIN_ID);
        assertEquals("org.karpfen.roundtrip.plugin/description/karpfen.odesign", KarpfenStartup.PLUGIN_ODESIGN_PATH);
        assertNotNull(KarpfenStartup.PLUGIN_ODESIGN_URI);
        assertEquals("platform:/plugin/org.karpfen.roundtrip.plugin/description/karpfen.odesign",
                KarpfenStartup.PLUGIN_ODESIGN_URI.toString());
    }

    @Test
    void testStartupExecution() {
        KarpfenStartup startup = new KarpfenStartup();
        assertDoesNotThrow(() -> startup.earlyStartup());
        assertDoesNotThrow(() -> KarpfenStartup.setupWorkspaceChangeListener());
        assertDoesNotThrow(() -> KarpfenStartup.registerKarpfenViewpoints());
        assertDoesNotThrow(() -> KarpfenStartup.refreshActiveSessions());
    }

    @Test
    void testUriMapFallback() {
        // Ensure no leftover workspace redirect in default state
        KarpfenStartup.registerKarpfenViewpoints();
        assertEquals(null, URIConverter.URI_MAP.get(KarpfenStartup.PLUGIN_ODESIGN_URI));
    }

    @Test
    void testUriMapRedirectionAndCleanup() {
        org.eclipse.emf.common.util.URI mockWsUri = org.eclipse.emf.common.util.URI
                .createPlatformResourceURI("/LiveTest/karpfen.odesign", true);
        URIConverter.URI_MAP.put(KarpfenStartup.PLUGIN_ODESIGN_URI, mockWsUri);
        assertEquals(mockWsUri, URIConverter.URI_MAP.get(KarpfenStartup.PLUGIN_ODESIGN_URI));

        // When no workspace resource is actually in the test workspace root,
        // registerKarpfenViewpoints
        // cleans up the redirection cleanly and restores fallback
        KarpfenStartup.registerKarpfenViewpoints();
        assertEquals(null, URIConverter.URI_MAP.get(KarpfenStartup.PLUGIN_ODESIGN_URI));
    }
}
