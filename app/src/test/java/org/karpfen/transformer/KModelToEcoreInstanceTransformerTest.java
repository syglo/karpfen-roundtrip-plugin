package org.karpfen.transformer;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KmodelDSLConverter;
import instance.Model;
import meta.Metamodel;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KModelToEcoreInstanceTransformerTest {
    
    private static KMetaToEcoreTransformer metaTransformer;
    private static KModelToEcoreInstanceTransformer instanceTransformer;

    @BeforeAll
    static void setUp() {
        metaTransformer = new KMetaToEcoreTransformer();
        instanceTransformer = new KModelToEcoreInstanceTransformer();
    }

    @Test
    void testKModelTransformationInMemory() {
        String kmetaCode = """
            type "Point" "2D Point" {
                prop("x", "number")
                prop("y", "number")
            }
            type "Target" "Target object" {
                prop("name", "string")
            }
            type "Agent" "Agent entity" {
                prop("speed", "number")
                has("pos", "Point")
                knows("target", "Target")
            }
            type "World" "Root World Container" {
                has("target", "Target")
                has("agent", "Agent")
            }
            """;

        String kmodelCode = """
            make object "w1":"World" {
                has("target") -> make object "t1":"Target" {
                    prop("name") -> "BaseStation"
                }
                has("agent") -> make object "a1":"Agent" {
                    prop("speed") -> "2.5"
                    has("pos") -> make object "p1":"Point" {
                        prop("x") -> "1.0"
                        prop("y") -> "4.0"
                    }
                    knows("target") -> "t1"
                }
            }
            """;

        Metamodel metamodel = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaCode, Collections.emptyList());
        Model model = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelCode, metamodel);
        EPackage ePackage = metaTransformer.transform(metamodel, "agentdomain", "http://github/karpfen", "agent");

        // Transform kmodel
        List<EObject> rootObjects = instanceTransformer.transform(model, ePackage);
        assertNotNull(rootObjects);
        assertEquals(1, rootObjects.size(), "Karpfen KModel starts with only single root object. Only w1 World is expected");

        // Single root world
        EObject world = rootObjects.get(0);
        assertEquals("World", world.eClass().getName());

        // World has target
        EObject target = (EObject) world.eGet(world.eClass().getEStructuralFeature("target"));
        assertNotNull(target);
        assertEquals("BaseStation", target.eGet(target.eClass().getEStructuralFeature("name")));

        // a1 agent and nested p1 point
        EObject agent = (EObject) world.eGet(world.eClass().getEStructuralFeature("agent"));
        assertNotNull(agent);
        assertEquals(2.5, (Double) agent.eGet(agent.eClass().getEStructuralFeature("speed")));

        EObject pos = (EObject) agent.eGet(agent.eClass().getEStructuralFeature("pos"));
        assertNotNull(pos);
        assertEquals("Point", pos.eClass().getName());
        assertEquals(1.0, (Double) pos.eGet(pos.eClass().getEStructuralFeature("x")));
        assertEquals(4.0, (Double) pos.eGet(pos.eClass().getEStructuralFeature("y")));
        assertSame(agent, pos.eContainer(), "Point 'p1' container must be Agent 'a1'");

        // cross reference link knows target t1
        EObject resolvedTarget = (EObject) agent.eGet(agent.eClass().getEStructuralFeature("target"));
        assertNotNull(resolvedTarget);
        assertSame(target, resolvedTarget, "Agent's target reference must point directly to Target 't1'");
    }

    @Test
    void testXmiFileSerializationAndReload(@TempDir Path tempDir) throws IOException {
        String kmetaCode = """
            type "Item" "An inventory item" {
                prop("name", "string")
                prop("quantity", "number")
            }
            """;

        String kmodelCode = """
            make object "item1":"Item" {
                prop("name") -> "A113"
                prop("quantity") -> "42.0"
            }
            """;

        Metamodel metamodel = KmetaDSLConverter.INSTANCE.parseKmetaString(kmetaCode, Collections.emptyList());
        Model model = KmodelDSLConverter.INSTANCE.parseKmodelString(kmodelCode, metamodel);
        EPackage ePackage = metaTransformer.transform(metamodel, "itemdomain", "http://github/karpfen", "item");

        List<EObject> rootObjects = instanceTransformer.transform(model, ePackage);
        File xmiOutputFile = tempDir.resolve("items.xmi").toFile();

        // Serialize to file
        instanceTransformer.saveToXmiFile(rootObjects, ePackage, xmiOutputFile);
        assertTrue(xmiOutputFile.exists());
        assertTrue(xmiOutputFile.length() > 0);

        // Deserialization verification
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        rs.getPackageRegistry().put(ePackage.getNsURI(), ePackage);

        Resource resource = rs.getResource(URI.createFileURI(xmiOutputFile.getAbsolutePath()), true);
        resource.load(Collections.emptyMap());

        assertEquals(1, resource.getContents().size());
        EObject loadedItem = resource.getContents().get(0);
        assertEquals("A113", loadedItem.eGet(loadedItem.eClass().getEStructuralFeature("name")));
        assertEquals(42.0, loadedItem.eGet(loadedItem.eClass().getEStructuralFeature("quantity")));
    }
}
