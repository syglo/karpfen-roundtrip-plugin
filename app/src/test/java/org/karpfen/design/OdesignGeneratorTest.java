package org.karpfen.design;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.sirius.diagram.description.ContainerMapping;
import org.eclipse.sirius.diagram.description.DescriptionPackage;
import org.eclipse.sirius.diagram.description.DiagramDescription;
import org.eclipse.sirius.viewpoint.ViewpointPackage;
import org.eclipse.sirius.viewpoint.description.Group;
import org.eclipse.sirius.viewpoint.description.RepresentationDescription;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OdesignGeneratorTest {

    private static final File OUTPUT_DIR = new File("build/test-outputs");
    private static final File ODESIGN_FILE = new File(OUTPUT_DIR, "karpfen.odesign");

    @BeforeAll
    static void setUp() {
        if (!OUTPUT_DIR.exists()) {
            OUTPUT_DIR.mkdirs();
        }
    }

    @BeforeAll
    static void initEMF() {
        // Tests are failing without EMF and Sirius dependencies...
        // Register the .odesign / .xmi file extension with the XMI resource factory
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
            .put("odesign", new XMIResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
            .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());

        // Register Sirius EPackages
        EPackage.Registry.INSTANCE.put(DescriptionPackage.eNS_URI, DescriptionPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(ViewpointPackage.eNS_URI, ViewpointPackage.eINSTANCE);
    }

    @Test
    void testOdesignGenerationFile() throws IOException {
        OdesignGenerator.generateOdesign(ODESIGN_FILE);

        assertTrue(ODESIGN_FILE.exists(), "Generated .odesign file must exist.");
        assertTrue(ODESIGN_FILE.length() > 0, "Generated .odesign must not be empty.");

        System.out.println("Generated .odesign file at: " + ODESIGN_FILE.getAbsolutePath());
    }

    @Test
    void testOdesignAST() throws IOException {
        // Ensure file exists
        if (!ODESIGN_FILE.exists()) {
            OdesignGenerator.generateOdesign(ODESIGN_FILE);
        }

        // EMF ResourceSet deserialization
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("odesign", new XMIResourceFactoryImpl());

        URI fileUri = URI.createFileURI(ODESIGN_FILE.getAbsolutePath());
        Resource resource = resourceSet.getResource(fileUri, true);
        resource.load(Collections.emptyMap());

        // Root Group exists
        assertNotNull(resource, "EMF Resource should load successfully.");
        assertEquals(1, resource.getContents().size(), "Resource must contain exactly one root Group element.");
        
        assertTrue(resource.getContents().get(0) instanceof Group, "Root element must be an instance of Sirius Group.");
        Group rootGroup = (Group) resource.getContents().get(0);
        assertEquals("KarpfenGroup", rootGroup.getName());

        // Viewpoint exists
        assertEquals(1, rootGroup.getOwnedViewpoints().size(), "Group must contain one Viewpoint.");
        Viewpoint viewpoint = rootGroup.getOwnedViewpoints().get(0);
        assertEquals("KarpfenViewpoint", viewpoint.getName());
        assertEquals("Karpfen Visualizations", viewpoint.getLabel());

        // Sirius Diagram Description for KMeta Class Diagram
        assertEquals(1, viewpoint.getOwnedRepresentations().size(), "Viewpoint must have one Representation.");
        RepresentationDescription representation = viewpoint.getOwnedRepresentations().get(0);
        assertTrue(representation instanceof DiagramDescription, "Representation must be a DiagramDescription.");
        
        DiagramDescription kmetaDiagram = (DiagramDescription) representation;
        assertEquals("KMetaClassDiagram", kmetaDiagram.getName());
        assertEquals("ecore.EPackage", kmetaDiagram.getDomainClass());

        // Check Node / Container Mapping
        assertEquals(1, kmetaDiagram.getContainerMappings().size(), "Diagram must contain one container mapping.");
        ContainerMapping eClassNode = kmetaDiagram.getContainerMappings().get(0);
        assertEquals("EClassNode", eClassNode.getName());
        assertEquals("ecore.EClass", eClassNode.getDomainClass());
        assertEquals("aql:self.eClassifiers->filter(ecore::EClass)", eClassNode.getSemanticCandidatesExpression());
        assertNotNull(eClassNode.getStyle(), "Container style must be configured.");
    }
}
