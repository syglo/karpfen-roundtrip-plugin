package org.karpfen.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.sirius.diagram.description.CenteringStyle;
import org.eclipse.sirius.diagram.ContainerLayout;
import org.eclipse.sirius.diagram.EdgeRouting;
import org.eclipse.sirius.diagram.description.ContainerMapping;
import org.eclipse.sirius.diagram.description.DescriptionPackage;
import org.eclipse.sirius.diagram.description.DiagramDescription;
import org.eclipse.sirius.diagram.description.DoubleLayoutOption;
import org.eclipse.sirius.diagram.description.EdgeMapping;
import org.eclipse.sirius.diagram.description.EnumLayoutOption;
import org.eclipse.sirius.diagram.description.EnumSetLayoutOption;
import org.eclipse.sirius.diagram.description.LayoutOptionTarget;
import org.eclipse.sirius.diagram.description.NodeMapping;
import org.eclipse.sirius.diagram.description.style.FlatContainerStyleDescription;
import org.eclipse.sirius.viewpoint.ViewpointPackage;
import org.eclipse.sirius.viewpoint.description.Group;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.karpfen.serializer.EcoreToKStatesManualSerializer;
import org.karpfen.serializer.KarpfenDslFormatter;
import org.karpfen.transformer.KStatesToEcoreTransformer;

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

        File resFile = OdesignGenerator.generateToResources();
        assertTrue(resFile.exists(), "Generated resources .odesign file must exist.");

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

        assertTrue(resource.getContents().get(0) instanceof Group,
                "Root element must be an instance of Sirius Group.");
        Group rootGroup = (Group) resource.getContents().get(0);
        assertEquals("KarpfenGroup", rootGroup.getName());

        // Viewpoint exists
        Viewpoint viewpoint = rootGroup.getOwnedViewpoints().get(0);
        assertEquals("KarpfenViewpoint", viewpoint.getName());
        assertEquals("Karpfen Visualizations", viewpoint.getLabel());
        assertEquals(3, viewpoint.getOwnedRepresentations().size(),
                "odesign must contain kmeta, kmodel, and kstates representations.");

        // KMeta Representation
        DiagramDescription kmetaDiagram = (DiagramDescription) viewpoint.getOwnedRepresentations().stream()
                .filter(r -> r.getName().equals("KMetaClassDiagram")).findFirst().orElseThrow();
        assertEquals("ecore.EPackage", kmetaDiagram.getDomainClass());
        assertNotNull(kmetaDiagram.getLayout(), "KMeta diagram should have ELK layout configured");
        assertTrue(kmetaDiagram
                .getLayout() instanceof org.eclipse.sirius.diagram.description.CustomLayoutConfiguration);
        org.eclipse.sirius.diagram.description.CustomLayoutConfiguration elkLayout = (org.eclipse.sirius.diagram.description.CustomLayoutConfiguration) kmetaDiagram
                .getLayout();
        assertEquals("org.eclipse.elk.layered", elkLayout.getId());

        assertEquals(10, elkLayout.getLayoutOptions().size(),
                "Should have direction, edgeRouting (ORTHOGONAL), portAlignment, portPort, edgeEdge, nodeNode, layer spacing, fixedAlignment (BALANCED), nodeSize.constraints, and nodeSize.options");
        EnumLayoutOption dirOption = (EnumLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.direction")).findFirst().orElseThrow();
        assertEquals("DOWN", dirOption.getValue().getName());
        assertTrue(dirOption.getTargets().contains(LayoutOptionTarget.PARENT));

        EnumLayoutOption routingOption = (EnumLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.edgeRouting")).findFirst().orElseThrow();
        assertEquals("ORTHOGONAL", routingOption.getValue().getName());
        assertTrue(routingOption.getTargets().contains(LayoutOptionTarget.PARENT));

        EnumLayoutOption portAlignOption = (EnumLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.portAlignment.default")).findFirst()
                .orElseThrow();
        assertEquals("DISTRIBUTED", portAlignOption.getValue().getName());
        assertTrue(portAlignOption.getTargets().contains(LayoutOptionTarget.NODE));
        assertTrue(portAlignOption.getTargets().contains(LayoutOptionTarget.PARENT));

        DoubleLayoutOption portPortOption = (DoubleLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.spacing.portPort")).findFirst()
                .orElseThrow();
        assertEquals(16.0, portPortOption.getValue(), 0.001);
        assertTrue(portPortOption.getTargets().contains(LayoutOptionTarget.NODE));
        assertTrue(portPortOption.getTargets().contains(LayoutOptionTarget.PARENT));

        DoubleLayoutOption edgeEdgeOption = (DoubleLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.spacing.edgeEdge")).findFirst()
                .orElseThrow();
        assertEquals(10.0, edgeEdgeOption.getValue(), 0.001);
        assertTrue(edgeEdgeOption.getTargets().contains(LayoutOptionTarget.PARENT));

        DoubleLayoutOption nodeNodeOption = (DoubleLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.spacing.nodeNode")).findFirst()
                .orElseThrow();
        assertEquals(22.0, nodeNodeOption.getValue(), 0.001);
        assertTrue(nodeNodeOption.getTargets().contains(LayoutOptionTarget.PARENT));

        DoubleLayoutOption layerSpacingOption = (DoubleLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.layered.spacing.nodeNodeBetweenLayers"))
                .findFirst().orElseThrow();
        assertEquals(30.0, layerSpacingOption.getValue(), 0.001);
        assertTrue(layerSpacingOption.getTargets().contains(LayoutOptionTarget.PARENT));

        EnumLayoutOption fixedAlignOption = (EnumLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId()
                        .equals("org.eclipse.elk.layered.nodePlacement.bk.fixedAlignment"))
                .findFirst().orElseThrow();
        assertEquals("BALANCED", fixedAlignOption.getValue().getName());
        assertTrue(fixedAlignOption.getTargets().contains(LayoutOptionTarget.PARENT));

        EnumSetLayoutOption constraintsOption = (EnumSetLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.nodeSize.constraints")).findFirst()
                .orElseThrow();
        assertEquals("NODE_LABELS", constraintsOption.getValues().get(0).getName());
        assertTrue(constraintsOption.getTargets().contains(LayoutOptionTarget.NODE));
        assertTrue(constraintsOption.getTargets().contains(LayoutOptionTarget.PARENT));

        EnumSetLayoutOption sizeOptions = (EnumSetLayoutOption) elkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.nodeSize.options")).findFirst()
                .orElseThrow();
        assertEquals("DEFAULT_MINIMUM_SIZE", sizeOptions.getValues().get(0).getName());
        assertTrue(sizeOptions.getTargets().contains(LayoutOptionTarget.NODE));
        assertTrue(sizeOptions.getTargets().contains(LayoutOptionTarget.PARENT));

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
        assertEquals("aql:self.getKMetaDocumentation()", classStyle.getTooltipExpression());

        assertEquals(1, eClassNode.getSubNodeMappings().size());
        NodeMapping attrNode = eClassNode.getSubNodeMappings().get(0);
        assertEquals("EAttributeNode", attrNode.getName());

        // Default Layer has 2 Orthogonal edges
        assertEquals(2, kmetaDiagram.getDefaultLayer().getEdgeMappings().size(),
                "Default layer holds orthogonal composition and association edges");
        assertEquals(0, kmetaDiagram.getAdditionalLayers().size(),
                "No additional layers needed for single standard orthogonal routing");

        EdgeMapping hasEdge = kmetaDiagram.getDefaultLayer().getEdgeMappings().get(0);
        assertEquals("HasCompositionEdge", hasEdge.getName());
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, hasEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, hasEdge.getStyle().getEndsCentering());
        assertNotNull(hasEdge.getStyle().getCenterLabelStyleDescription());
        assertEquals("aql:self.name", hasEdge.getStyle().getCenterLabelStyleDescription().getLabelExpression());
        assertNotNull(hasEdge.getStyle().getEndLabelStyleDescription());
        assertEquals(
                "aql:self.getKMetaEdgeEndLabel()",
                hasEdge.getStyle().getEndLabelStyleDescription().getLabelExpression());

        EdgeMapping knowsEdge = kmetaDiagram.getDefaultLayer().getEdgeMappings().get(1);
        assertEquals("KnowsAssociationEdge", knowsEdge.getName());
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, knowsEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, knowsEdge.getStyle().getEndsCentering());
        assertNotNull(knowsEdge.getStyle().getCenterLabelStyleDescription());
        assertEquals("aql:self.name",
                knowsEdge.getStyle().getCenterLabelStyleDescription().getLabelExpression());
        assertNotNull(knowsEdge.getStyle().getEndLabelStyleDescription());
        assertEquals(
                "aql:self.getKMetaEdgeEndLabel()",
                knowsEdge.getStyle().getEndLabelStyleDescription().getLabelExpression());

        // KModel
        DiagramDescription kmodelDiagram = (DiagramDescription) viewpoint.getOwnedRepresentations().stream()
                .filter(r -> r.getName().equals("KModelObjectDiagram")).findFirst().orElseThrow();
        assertEquals("ecore.EObject", kmodelDiagram.getDomainClass());
        assertEquals("aql:self.isKModelRoot()", kmodelDiagram.getPreconditionExpression());
        assertNotNull(kmodelDiagram.getLayout(), "KModel diagram should have ELK layout configured");
        org.eclipse.sirius.diagram.description.CustomLayoutConfiguration kmodelElkLayout = (org.eclipse.sirius.diagram.description.CustomLayoutConfiguration) kmodelDiagram
                .getLayout();
        assertEquals("org.eclipse.elk.layered", kmodelElkLayout.getId());
        assertEquals(10, kmodelElkLayout.getLayoutOptions().size(),
                "KModel ELK layout should contain all 10 layout options matching KMeta");
        assertNotNull(kmodelDiagram.getDefaultLayer());
        assertEquals(1, kmodelDiagram.getDefaultLayer().getContainerMappings().size());
        assertEquals(2, kmodelDiagram.getDefaultLayer().getToolSections().size());

        ContainerMapping eObjNode = kmodelDiagram.getDefaultLayer().getContainerMappings().get(0);
        assertEquals("EObjectNode", eObjNode.getName());
        assertEquals("aql:self.eAllContents()->including(self)", eObjNode.getSemanticCandidatesExpression());
        assertNotNull(eObjNode.getDeletionDescription());
        assertEquals(1, eObjNode.getSubNodeMappings().size());
        assertEquals("aql:self.getSchemaAttributes()",
                eObjNode.getSubNodeMappings().get(0).getSemanticCandidatesExpression());

        FlatContainerStyleDescription objStyle = (FlatContainerStyleDescription) eObjNode.getStyle();
        assertEquals("0", objStyle.getWidthComputationExpression());
        assertEquals("0", objStyle.getHeightComputationExpression());

        // Default Layer has 2 Orthogonal edges
        assertEquals(2, kmodelDiagram.getDefaultLayer().getEdgeMappings().size(),
                "Default layer holds orthogonal containment and reference links");
        assertEquals(0, kmodelDiagram.getAdditionalLayers().size(),
                "No additional layers needed for KModel");

        EdgeMapping instHasEdge = kmodelDiagram.getDefaultLayer().getEdgeMappings().get(0);
        assertEquals("InstanceContainmentEdge", instHasEdge.getName());
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, instHasEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, instHasEdge.getStyle().getEndsCentering());

        EdgeMapping instKnowsEdge = kmodelDiagram.getDefaultLayer().getEdgeMappings().get(1);
        assertEquals("InstanceReferenceEdge", instKnowsEdge.getName());
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, instKnowsEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, instKnowsEdge.getStyle().getEndsCentering());

        // KStates
        DiagramDescription kstatesDiagram = (DiagramDescription) viewpoint.getOwnedRepresentations().stream()
                .filter(r -> r.getName().equals("KStatesDiagram")).findFirst().orElseThrow();
        assertEquals("ecore.EObject", kstatesDiagram.getDomainClass());
        assertEquals("aql:self.isKStatesRoot()", kstatesDiagram.getPreconditionExpression());
        assertNotNull(kstatesDiagram.getLayout(), "KStates diagram should have ELK layout configured");
        org.eclipse.sirius.diagram.description.CustomLayoutConfiguration kstatesElkLayout = (org.eclipse.sirius.diagram.description.CustomLayoutConfiguration) kstatesDiagram
                .getLayout();
        assertEquals("org.eclipse.elk.layered", kstatesElkLayout.getId());
        assertEquals(11, kstatesElkLayout.getLayoutOptions().size(),
                "KStates ELK layout should contain 11 layout options including hierarchyHandling");
        EnumLayoutOption hierOption = (EnumLayoutOption) kstatesElkLayout.getLayoutOptions().stream()
                .filter(o -> o.getId().equals("org.eclipse.elk.hierarchyHandling")).findFirst()
                .orElseThrow();
        assertEquals("INCLUDE_CHILDREN", hierOption.getValue().getName());
        assertTrue(hierOption.getTargets().contains(LayoutOptionTarget.PARENT));

        assertNotNull(kstatesDiagram.getDefaultLayer());
        assertEquals(1, kstatesDiagram.getDefaultLayer().getContainerMappings().size());
        assertEquals(2, kstatesDiagram.getDefaultLayer().getToolSections().size());

        ContainerMapping stateNode = kstatesDiagram.getDefaultLayer().getContainerMappings().get(0);
        assertEquals("StateNode", stateNode.getName());
        assertEquals("aql:self.states", stateNode.getSemanticCandidatesExpression());
        assertEquals(ContainerLayout.FREE_FORM, stateNode.getChildrenPresentation());
        assertTrue(stateNode.getStyle() instanceof FlatContainerStyleDescription);
        FlatContainerStyleDescription stateStyle = (FlatContainerStyleDescription) stateNode.getStyle();
        assertEquals("15", stateStyle.getWidthComputationExpression());
        assertEquals("4", stateStyle.getHeightComputationExpression());

        assertEquals(1, stateNode.getSubNodeMappings().size(), "InitialPseudostateNode");
        NodeMapping pseudoNode = stateNode.getSubNodeMappings().get(0);
        assertEquals("InitialPseudostateNode", pseudoNode.getName());
        assertEquals("ecore.EObject", pseudoNode.getDomainClass());
        assertTrue(pseudoNode
                .getStyle() instanceof org.eclipse.sirius.diagram.description.style.DotDescription);

        // SubContainer mappings: CompositeStateActions and SubStateNode
        assertEquals(2, stateNode.getSubContainerMappings().size(),
                "StateNode contains CompositeStateActions and SubStateNode");

        ContainerMapping compositeActionsNode = stateNode.getSubContainerMappings().get(0);
        assertEquals("CompositeStateActions", compositeActionsNode.getName());
        assertEquals("ecore.EObject", compositeActionsNode.getDomainClass());
        assertEquals(
                "aql:if (self.entryAction != null and self.entryAction != '') or (self.doAction != null and self.doAction != '') then Sequence{self} else Sequence{} endif",
                compositeActionsNode.getSemanticCandidatesExpression());
        assertEquals(ContainerLayout.LIST, compositeActionsNode.getChildrenPresentation());
        assertEquals(2, compositeActionsNode.getSubNodeMappings().size(),
                "EntryActionNode and DoActionNode in Actions compartment");
        NodeMapping entryNode = compositeActionsNode.getSubNodeMappings().get(0);
        assertEquals("EntryActionNode", entryNode.getName());
        NodeMapping doNode = compositeActionsNode.getSubNodeMappings().get(1);
        assertEquals("DoActionNode", doNode.getName());

        // Nested SubState container mapping
        ContainerMapping subStateNode = stateNode.getSubContainerMappings().get(1);
        assertEquals("SubStateNode", subStateNode.getName());
        assertEquals("aql:self.innerStates", subStateNode.getSemanticCandidatesExpression());
        assertEquals(ContainerLayout.LIST, subStateNode.getChildrenPresentation());
        assertTrue(subStateNode.getStyle() instanceof FlatContainerStyleDescription);
        FlatContainerStyleDescription subStateStyle = (FlatContainerStyleDescription) subStateNode.getStyle();
        assertEquals("aql:self.getStateHeaderLabel()", subStateStyle.getLabelExpression());
        assertEquals(2, subStateNode.getSubNodeMappings().size(), "SubState EntryActionNode and DoActionNode");

        // Default Layer has 2 Transition edges: TransitionEdge (external) and
        // InternalTransitionEdge (parent-to-child)
        assertEquals(2, kstatesDiagram.getDefaultLayer().getEdgeMappings().size(),
                "Default layer holds TransitionEdge and InternalTransitionEdge");
        assertEquals(0, kstatesDiagram.getAdditionalLayers().size(),
                "No additional layers needed for KStates");

        EdgeMapping transEdge = kstatesDiagram.getDefaultLayer().getEdgeMappings().get(0);
        assertEquals("TransitionEdge", transEdge.getName());
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, transEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, transEdge.getStyle().getEndsCentering());
        assertEquals(2, transEdge.getSourceMapping().size(), "Edge links StateNode and SubStateNode");
        assertEquals(2, transEdge.getTargetMapping().size(), "Edge links StateNode and SubStateNode");
        assertTrue(transEdge.getSourceMapping().contains(stateNode));
        assertTrue(transEdge.getSourceMapping().contains(subStateNode));
        assertTrue(transEdge.getTargetMapping().contains(stateNode));
        assertTrue(transEdge.getTargetMapping().contains(subStateNode));

        EdgeMapping internalTransEdge = kstatesDiagram.getDefaultLayer().getEdgeMappings().get(1);
        assertEquals("InternalTransitionEdge", internalTransEdge.getName());
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, internalTransEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, internalTransEdge.getStyle().getEndsCentering());
        assertEquals(2, internalTransEdge.getSourceMapping().size(),
                "Source anchored to CompositeStateActions and InitialPseudostateNode");
        assertEquals(1, internalTransEdge.getTargetMapping().size(), "Target anchored to SubStateNode");
        assertTrue(internalTransEdge.getSourceMapping().contains(compositeActionsNode));
        assertTrue(internalTransEdge.getSourceMapping().contains(pseudoNode));
        assertTrue(internalTransEdge.getTargetMapping().contains(subStateNode));
    }

    @Test
    void testKStatesLabelAndActionFormatting() {
        KarpfenDiagramServices services = new KarpfenDiagramServices();

        // transition guard
        EPackage statesPkg = EcoreFactory.eINSTANCE
                .createEPackage();
        statesPkg.setName("statesPkg");

        EClass transClass = EcoreFactory.eINSTANCE.createEClass();
        transClass.setName("Transition");
        EAttribute condAttr = EcoreFactory.eINSTANCE
                .createEAttribute();
        condAttr.setName("condition");
        condAttr.setEType(EcorePackage.Literals.ESTRING);
        transClass.getEStructuralFeatures().add(condAttr);
        statesPkg.getEClassifiers().add(transClass);

        EObject t1 = statesPkg.getEFactoryInstance().create(transClass);
        t1.eSet(condAttr, "VALUE(\"true\")");
        assertEquals("", services.getTransitionLabel(t1), "VALUE(\"true\") condition should be suppressed");

        EObject t2 = statesPkg.getEFactoryInstance().create(transClass);
        t2.eSet(condAttr, "true");
        assertEquals("", services.getTransitionLabel(t2));

        EObject t3 = statesPkg.getEFactoryInstance().create(transClass);
        t3.eSet(condAttr, "EVAL { return $(d_closest_obstacle) < 0.2 }");
        assertEquals("[EVAL { return d_closest_obstacle < 0.2 }]", services.getTransitionLabel(t3));

        EObject t4 = statesPkg.getEFactoryInstance().create(transClass);
        t4.eSet(condAttr, "EVENT(\"public\", \"start\")");
        assertEquals("EVENT(\"public\", \"start\")", services.getTransitionLabel(t4));

        // multti line action
        EClass stateClass = EcoreFactory.eINSTANCE.createEClass();
        stateClass.setName("State");
        EAttribute entryAttr = EcoreFactory.eINSTANCE
                .createEAttribute();
        entryAttr.setName("entryAction");
        entryAttr.setEType(EcorePackage.Literals.ESTRING);
        EAttribute doAttr = EcoreFactory.eINSTANCE
                .createEAttribute();
        doAttr.setName("doAction");
        doAttr.setEType(EcorePackage.Literals.ESTRING);
        stateClass.getEStructuralFeatures().add(entryAttr);
        stateClass.getEStructuralFeatures().add(doAttr);

        EAttribute stateNameAttr = EcoreFactory.eINSTANCE.createEAttribute();
        stateNameAttr.setName("name");
        stateNameAttr.setEType(EcorePackage.Literals.ESTRING);
        stateClass.getEStructuralFeatures().add(stateNameAttr);

        EReference innerStatesRef = EcoreFactory.eINSTANCE.createEReference();
        innerStatesRef.setName("innerStates");
        innerStatesRef.setEType(stateClass);
        innerStatesRef.setContainment(true);
        innerStatesRef.setUpperBound(-1);
        stateClass.getEStructuralFeatures().add(innerStatesRef);

        statesPkg.getEClassifiers().add(stateClass);

        EObject s1 = statesPkg.getEFactoryInstance().create(stateClass);
        s1.eSet(entryAttr, "EVENT(\"public\", \"start\")\nSET(\"speed\", 10.0)");
        s1.eSet(doAttr, "SET(\"d_closest_obstacle\", 0.0)");

        String entryLabel = services.getEntryLabel(s1);
        assertEquals("entry / EVENT(\"public\", \"start\")\nentry / SET(\"speed\", 10.0)", entryLabel);

        String doLabel = services.getDoLabel(s1);
        assertEquals("do / SET(\"d_closest_obstacle\", 0.0)", doLabel);

        // Hierarchical nested state names
        EObject topState = statesPkg.getEFactoryInstance().create(stateClass);
        topState.eSet(stateNameAttr, "observe");
        assertEquals("observe", services.getStateHeaderLabel(topState));

        EObject subState = statesPkg.getEFactoryInstance().create(stateClass);
        subState.eSet(stateNameAttr, "react to obstacle");
        @SuppressWarnings("unchecked")
        List<EObject> topInners = (List<EObject>) topState.eGet(innerStatesRef);
        topInners.add(subState);
        assertEquals("react to obstacle", services.getStateHeaderLabel(subState));

        EObject subSubState = statesPkg.getEFactoryInstance().create(stateClass);
        subSubState.eSet(stateNameAttr, "fine tune");
        @SuppressWarnings("unchecked")
        List<EObject> subInners = (List<EObject>) subState.eGet(innerStatesRef);
        subInners.add(subSubState);
        assertEquals("fine tune", services.getStateHeaderLabel(subSubState));
    }

    @Test
    void testRootRepresentationPredicates() {
        KarpfenDiagramServices services = new KarpfenDiagramServices();

        // kmeta epackage
        EPackage ePkg = EcoreFactory.eINSTANCE.createEPackage();
        ePkg.setName("testPkg");
        assertFalse(services.isKModelRoot(ePkg));
        assertFalse(services.isKStatesRoot(ePkg));

        // child eobject are blocked, only kmodel root is allowed
        EPackage modelPkg = EcoreFactory.eINSTANCE.createEPackage();
        modelPkg.setName("modelPkg");

        EClass roomClass = EcoreFactory.eINSTANCE.createEClass();
        roomClass.setName("Room");
        EClass robotClass = EcoreFactory.eINSTANCE.createEClass();
        robotClass.setName("Robot");

        EReference hasRobotRef = EcoreFactory.eINSTANCE
                .createEReference();
        hasRobotRef.setName("robot");
        hasRobotRef.setEType(robotClass);
        hasRobotRef.setContainment(true);
        roomClass.getEStructuralFeatures().add(hasRobotRef);

        modelPkg.getEClassifiers().add(roomClass);
        modelPkg.getEClassifiers().add(robotClass);

        EObject rootObj = modelPkg.getEFactoryInstance().create(roomClass);
        EObject childObj = modelPkg.getEFactoryInstance().create(robotClass);
        rootObj.eSet(hasRobotRef, childObj);

        // rootObj has eContainer == null -> valid KModel root, invalid KStates root
        assertTrue(services.isKModelRoot(rootObj));
        assertFalse(services.isKStatesRoot(rootObj));

        // childObj has eContainer != null -> invalid KModel root, invalid KStates root
        assertFalse(services.isKModelRoot(childObj));
        assertFalse(services.isKStatesRoot(childObj));

        // child eobject are blocked, only kstates root is allowed
        EPackage statesPkg = EcoreFactory.eINSTANCE
                .createEPackage();
        statesPkg.setName("statesPkg");

        EClass smClass = EcoreFactory.eINSTANCE.createEClass();
        smClass.setName("StateMachine");
        EClass stateClass = EcoreFactory.eINSTANCE.createEClass();
        stateClass.setName("State");

        EReference statesRef = EcoreFactory.eINSTANCE
                .createEReference();
        statesRef.setName("states");
        statesRef.setEType(stateClass);
        statesRef.setContainment(true);
        smClass.getEStructuralFeatures().add(statesRef);

        statesPkg.getEClassifiers().add(smClass);
        statesPkg.getEClassifiers().add(stateClass);

        EObject smObj = statesPkg.getEFactoryInstance().create(smClass);
        EObject stateObj = statesPkg.getEFactoryInstance().create(stateClass);
        smObj.eSet(statesRef, stateObj);

        // StateMachine root has eContainer == null -> valid KStates root, invalid
        // KModel root!
        assertTrue(services.isKStatesRoot(smObj));
        assertFalse(services.isKModelRoot(smObj));

        // State child has eContainer != null -> invalid KStates root, invalid KModel
        // root!
        assertFalse(services.isKStatesRoot(stateObj));
        assertFalse(services.isKModelRoot(stateObj));
    }

    @Test
    void testKStatesToolingAndDeletionSafety() {
        KarpfenDiagramServices services = new KarpfenDiagramServices();

        String kstatesSrc = """
                STATEMACHINE ATTACHED TO "Robot" {
                    STATES {
                        INITIAL STATE "idle" {
                            ENTRY {
                                EVENT("public", "start")
                            }
                            DO {
                                SET("speed", "0.0")
                            }
                        }
                        STATE "drive" {
                            ENTRY {
                                SET("speed", "10.0")
                            }
                        }
                        STATE "stop" { }
                    }

                    TRANSITIONS {
                        TRANSITION "idle" -> "drive" {
                            CONDITION {
                                EVAL { return true }
                            }
                        }
                        TRANSITION "drive" -> "stop" {
                            CONDITION {
                                EVAL { return $(d_closest_obstacle) < 0.2 }
                            }
                        }
                    }

                    MACROS {
                        MACRO "get_d_closest_obstacle" {
                            TAKES("object", reference("Robot"))
                            RETURNS("number")
                            DEFINITION {
                                EVAL {
                                    return 0.5
                                }
                            }
                        }
                    }
                }
                """;

        states.StateMachine sm = dsl.textual.KstatesDSLConverter.INSTANCE.parseKstatesString(kstatesSrc);
        KStatesToEcoreTransformer transformer = new KStatesToEcoreTransformer();
        EObject root = transformer.transform(sm, "robotPkg");

        // emf should have 3 states, 2 transitions, and 1 macro
        @SuppressWarnings("unchecked")
        java.util.List<EObject> statesList = (java.util.List<EObject>) root
                .eGet(root.eClass().getEStructuralFeature("states"));
        @SuppressWarnings("unchecked")
        java.util.List<EObject> transList = (java.util.List<EObject>) root
                .eGet(root.eClass().getEStructuralFeature("transitions"));
        @SuppressWarnings("unchecked")
        java.util.List<String> macrosList = (java.util.List<String>) root
                .eGet(root.eClass().getEStructuralFeature("macros"));

        assertEquals(3, statesList.size(), "3 states originally");
        assertEquals(2, transList.size(), "2 transitions originally");
        assertEquals(1, macrosList.size(), "1 macro originally");

        // delete transition idle and drive
        EObject t1 = transList.get(0);
        services.deleteTransition(t1);

        assertEquals(1, transList.size(), "1 transition remaining");
        assertEquals(3, statesList.size(), "all 3 states still intact");
        assertEquals(1, macrosList.size(), "macros must NEVER be touched when deleting a transition");

        // don't allow to delete root, to preserve macroses and transitions
        services.deleteTransition(root);
        services.deleteState(root);
        assertEquals(3, statesList.size(), "Root was not deleted by mistake");
        assertEquals(1, macrosList.size(), "Macros remain intact");

        // palette creation works
        EObject newState = services.createState(root);
        assertNotNull(newState);
        assertEquals(4, statesList.size(), "New state added");
        assertEquals("State_1", newState.eGet(newState.eClass().getEStructuralFeature("name")));

        // cascade delete not allowed
        EObject stopState = statesList.stream()
                .filter(s -> "stop".equals(s.eGet(s.eClass().getEStructuralFeature("name"))))
                .findFirst().orElseThrow();
        services.deleteState(stopState);

        assertEquals(3, statesList.size(), "3 states after deleting 'stop'");
        assertEquals(0, transList.size(), "Transitions to 'stop' should be cascade deleted");
        assertEquals(1, macrosList.size(), "Macros still 100% intact");

        // d2t check
        EcoreToKStatesManualSerializer serializer = new EcoreToKStatesManualSerializer();
        String generated = serializer.serialize(root);
        String formatted = KarpfenDslFormatter.formatKStates(generated);

        assertTrue(formatted.contains("MACRO \"get_d_closest_obstacle\""),
                "MACROS must be preserved in serialized text");
        assertTrue(formatted.contains("STATE \"idle\""), "State idle preserved");
        assertTrue(formatted.contains("STATE \"drive\""), "State drive preserved");
        assertTrue(formatted.contains("STATE \"State_1\""), "Newly created State_1 preserved");
        assertFalse(formatted.contains("STATE \"stop\""), "Deleted state stop must not be in output");

        // t2d check
        states.StateMachine roundtripSm = dsl.textual.KstatesDSLConverter.INSTANCE
                .parseKstatesString(formatted);
        assertNotNull(roundtripSm);
        assertEquals(3, roundtripSm.getStates().size());
        assertEquals(1, roundtripSm.getMacros().size());
    }

    @Test
    void testOdesignSave() throws IOException {
        File generatedFile = OdesignGenerator.generateToResources();
        assertNotNull(generatedFile, "Generated file should not be null");
        assertTrue(generatedFile.exists(), "karfpen.odesign exist in src/main/resources/description/");
        assertTrue(generatedFile.length() > 0, "karpfen.odesign not empty");
    }
}
