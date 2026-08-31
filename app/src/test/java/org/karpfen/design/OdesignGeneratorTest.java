package org.karpfen.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.sirius.diagram.description.CenteringStyle;
import org.eclipse.sirius.diagram.ContainerLayout;
import org.eclipse.sirius.diagram.EdgeRouting;
import org.eclipse.sirius.diagram.description.AdditionalLayer;
import org.eclipse.sirius.diagram.description.ContainerMapping;
import org.eclipse.sirius.diagram.description.DescriptionPackage;
import org.eclipse.sirius.diagram.description.DiagramDescription;
import org.eclipse.sirius.diagram.description.EdgeMapping;
import org.eclipse.sirius.diagram.description.NodeMapping;
import org.eclipse.sirius.diagram.description.style.FlatContainerStyleDescription;
import org.eclipse.sirius.viewpoint.ViewpointPackage;
import org.eclipse.sirius.viewpoint.description.Group;
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
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("odesign",
                new XMIResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(
                Resource.Factory.Registry.DEFAULT_EXTENSION,
                new XMIResourceFactoryImpl());

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
        OdesignGenerator.generateOdesign(ODESIGN_FILE);

        // EMF ResourceSet deserialization
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("odesign",
                new XMIResourceFactoryImpl());

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
        Viewpoint viewpoint = rootGroup.getOwnedViewpoints().get(0);
        assertEquals("KarpfenViewpoint", viewpoint.getName());
        assertEquals("Karpfen Visualizations", viewpoint.getLabel());
        assertEquals(2, viewpoint.getOwnedRepresentations().size(),
                "odesign must contain kmeta and kmodel viewpoints.");

        // KMeta
        DiagramDescription kmetaDiagram = (DiagramDescription) viewpoint.getOwnedRepresentations().stream()
                .filter(r -> r.getName().equals("KMetaClassDiagram")).findFirst().orElseThrow();
        assertEquals("ecore.EPackage", kmetaDiagram.getDomainClass());
        assertNotNull(kmetaDiagram.getDefaultLayer());
        assertEquals(1, kmetaDiagram.getDefaultLayer().getContainerMappings().size());
        assertEquals(3, kmetaDiagram.getDefaultLayer().getToolSections().size());

        ContainerMapping eClassNode = kmetaDiagram.getDefaultLayer().getContainerMappings().get(0);
        assertEquals("EClassNode", eClassNode.getName());
        assertEquals(ContainerLayout.LIST, eClassNode.getChildrenPresentation());
        assertTrue(eClassNode.getStyle() instanceof FlatContainerStyleDescription);
        FlatContainerStyleDescription classStyle = (FlatContainerStyleDescription) eClassNode.getStyle();
        assertEquals("14", classStyle.getWidthComputationExpression());
        assertEquals("4", classStyle.getHeightComputationExpression());

        assertEquals(1, eClassNode.getSubNodeMappings().size());
        NodeMapping attrNode = eClassNode.getSubNodeMappings().get(0);
        assertEquals("EAttributeNode", attrNode.getName());

        // Default Layer has 0 edges (edges live in switchable layers)
        assertEquals(0, kmetaDiagram.getDefaultLayer().getEdgeMappings().size(),
                "Default layer should hold 0 edges to allow clean switching");

        // Additional Layers for KMeta (Straight vs Manhattan)
        assertEquals(2, kmetaDiagram.getAdditionalLayers().size());

        AdditionalLayer kmetaStraightLayer = kmetaDiagram.getAdditionalLayers().stream()
                .filter(l -> l.getName().equals("StraightRoutingLayer")).findFirst().orElseThrow();
        assertTrue(kmetaStraightLayer.isActiveByDefault());
        assertTrue(kmetaStraightLayer.isOptional());
        assertEquals(2, kmetaStraightLayer.getEdgeMappings().size());
        EdgeMapping hasEdge = kmetaStraightLayer.getEdgeMappings().get(0);
        assertEquals(EdgeRouting.STRAIGHT_LITERAL, hasEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, hasEdge.getStyle().getEndsCentering());

        AdditionalLayer kmetaOrthoLayer = kmetaDiagram.getAdditionalLayers().stream()
                .filter(l -> l.getName().equals("OrthogonalRoutingLayer")).findFirst().orElseThrow();
        assertFalse(kmetaOrthoLayer.isActiveByDefault());
        assertTrue(kmetaOrthoLayer.isOptional());
        assertEquals(2, kmetaOrthoLayer.getEdgeMappings().size());
        EdgeMapping orthoHasEdge = kmetaOrthoLayer.getEdgeMappings().get(0);
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, orthoHasEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, orthoHasEdge.getStyle().getEndsCentering());

        // KModel
        DiagramDescription kmodelDiagram = (DiagramDescription) viewpoint.getOwnedRepresentations().stream()
                .filter(r -> r.getName().equals("KModelObjectDiagram")).findFirst().orElseThrow();
        assertEquals("ecore.EObject", kmodelDiagram.getDomainClass());
        assertNotNull(kmodelDiagram.getDefaultLayer());
        assertEquals(1, kmodelDiagram.getDefaultLayer().getContainerMappings().size());
        assertEquals(2, kmodelDiagram.getDefaultLayer().getToolSections().size());

        ContainerMapping eObjNode = kmodelDiagram.getDefaultLayer().getContainerMappings().get(0);
        assertEquals("EObjectNode", eObjNode.getName());
        assertEquals("aql:self.eAllContents()->including(self)", eObjNode.getSemanticCandidatesExpression());
        assertNotNull(eObjNode.getDeletionDescription());
        assertEquals(1, eObjNode.getSubNodeMappings().size());

        FlatContainerStyleDescription objStyle = (FlatContainerStyleDescription) eObjNode.getStyle();
        assertEquals("16", objStyle.getWidthComputationExpression());
        assertEquals("4", objStyle.getHeightComputationExpression());

        // Default Layer has 0 edges
        assertEquals(0, kmodelDiagram.getDefaultLayer().getEdgeMappings().size(),
                "Default layer should hold 0 edges to allow clean switching");

        // Additional Layers for KModel (Straight vs Manhattan)
        assertEquals(2, kmodelDiagram.getAdditionalLayers().size());

        AdditionalLayer kmodelStraightLayer = kmodelDiagram.getAdditionalLayers().stream()
                .filter(l -> l.getName().equals("StraightRoutingLayer")).findFirst().orElseThrow();
        assertTrue(kmodelStraightLayer.isActiveByDefault());
        assertTrue(kmodelStraightLayer.isOptional());
        assertEquals(2, kmodelStraightLayer.getEdgeMappings().size());
        EdgeMapping instHasEdge = kmodelStraightLayer.getEdgeMappings().get(0);
        assertEquals(EdgeRouting.STRAIGHT_LITERAL, instHasEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, instHasEdge.getStyle().getEndsCentering());

        AdditionalLayer kmodelOrthoLayer = kmodelDiagram.getAdditionalLayers().stream()
                .filter(l -> l.getName().equals("OrthogonalRoutingLayer")).findFirst().orElseThrow();
        assertFalse(kmodelOrthoLayer.isActiveByDefault());
        assertTrue(kmodelOrthoLayer.isOptional());
        assertEquals(2, kmodelOrthoLayer.getEdgeMappings().size());
        EdgeMapping orthoInstHasEdge = kmodelOrthoLayer.getEdgeMappings().get(0);
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, orthoInstHasEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, orthoInstHasEdge.getStyle().getEndsCentering());
    }

    @Test
    void testOdesignSave() throws IOException {
        File generatedFile = OdesignGenerator.generateToResources();
        assertNotNull(generatedFile, "Generated file should not be null");
        assertTrue(generatedFile.exists(), "karfpen.odesign exist in src/main/resources/description/");
        assertTrue(generatedFile.length() > 0, "karpfen.odesign not empty");
    }
}
