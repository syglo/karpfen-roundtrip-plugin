package org.karpfen.design;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.sirius.diagram.description.CenteringStyle;
import org.eclipse.sirius.diagram.ContainerLayout;
import org.eclipse.sirius.diagram.DiagramPackage;
import org.eclipse.sirius.diagram.EdgeArrows;
import org.eclipse.sirius.diagram.EdgeRouting;
import org.eclipse.sirius.diagram.LabelPosition;
import org.eclipse.sirius.diagram.LineStyle;
import org.eclipse.sirius.diagram.ResizeKind;
import org.eclipse.sirius.diagram.description.AdditionalLayer;
import org.eclipse.sirius.diagram.description.ContainerMapping;
import org.eclipse.sirius.diagram.description.DescriptionFactory;
import org.eclipse.sirius.diagram.description.DiagramDescription;
import org.eclipse.sirius.diagram.description.EdgeMapping;
import org.eclipse.sirius.diagram.description.Layer;
import org.eclipse.sirius.diagram.description.NodeMapping;
import org.eclipse.sirius.diagram.description.style.CenterLabelStyleDescription;
import org.eclipse.sirius.diagram.description.style.EdgeStyleDescription;
import org.eclipse.sirius.diagram.description.style.FlatContainerStyleDescription;
import org.eclipse.sirius.diagram.description.style.NodeStyleDescription;
import org.eclipse.sirius.diagram.description.style.StyleFactory;
import org.eclipse.sirius.diagram.description.style.StylePackage;
import org.eclipse.sirius.diagram.description.tool.ContainerCreationDescription;
import org.eclipse.sirius.diagram.description.tool.DeleteElementDescription;
import org.eclipse.sirius.diagram.description.tool.DirectEditLabel;
import org.eclipse.sirius.diagram.description.tool.EdgeCreationDescription;
import org.eclipse.sirius.diagram.description.tool.NodeCreationDescription;
import org.eclipse.sirius.diagram.description.tool.ToolSection;
import org.eclipse.sirius.viewpoint.LabelAlignment;
import org.eclipse.sirius.viewpoint.ViewpointPackage;
import org.eclipse.sirius.viewpoint.description.Group;
import org.eclipse.sirius.viewpoint.description.JavaExtension;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.eclipse.sirius.viewpoint.description.tool.ChangeContext;
import org.eclipse.sirius.viewpoint.description.tool.CreateInstance;
import org.eclipse.sirius.viewpoint.description.tool.EditMaskVariables;
import org.eclipse.sirius.viewpoint.description.tool.ElementDeleteVariable;
import org.eclipse.sirius.viewpoint.description.tool.InitEdgeCreationOperation;
import org.eclipse.sirius.viewpoint.description.tool.InitialNodeCreationOperation;
import org.eclipse.sirius.viewpoint.description.tool.InitialOperation;
import org.eclipse.sirius.viewpoint.description.tool.OperationAction;
import org.eclipse.sirius.viewpoint.description.tool.SetValue;

public class OdesignGenerator {

    public static final String RELATIVE_RESOURCE_PATH = "src/main/resources/description/karpfen.odesign";

    // Init EMF and Sirius dependencies to run them in runner class.
    public static void initStandalone() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("odesign",
                new XMIResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(
                Resource.Factory.Registry.DEFAULT_EXTENSION,
                new XMIResourceFactoryImpl());

        // ECore deps
        EPackage.Registry.INSTANCE.put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);

        // Sirius viewpoint
        EPackage.Registry.INSTANCE.put(org.eclipse.sirius.viewpoint.description.DescriptionPackage.eNS_URI,
                org.eclipse.sirius.viewpoint.description.DescriptionPackage.eINSTANCE);

        // Sirius diagram and style
        EPackage.Registry.INSTANCE.put(ViewpointPackage.eNS_URI, ViewpointPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(org.eclipse.sirius.diagram.description.DescriptionPackage.eNS_URI,
                org.eclipse.sirius.diagram.description.DescriptionPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(DiagramPackage.eNS_URI, DiagramPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(StylePackage.eNS_URI, StylePackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(org.eclipse.sirius.diagram.description.tool.ToolPackage.eNS_URI,
                org.eclipse.sirius.diagram.description.tool.ToolPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(org.eclipse.sirius.viewpoint.description.tool.ToolPackage.eNS_URI,
                org.eclipse.sirius.viewpoint.description.tool.ToolPackage.eINSTANCE);
    }

    // Save odesign in src/main/resources/ to be packaged in .jar
    public static File generateToResources() throws IOException {
        initStandalone();

        Path targetPath = Paths.get(RELATIVE_RESOURCE_PATH);
        if (!targetPath.toFile().exists()
                && Paths.get("app", RELATIVE_RESOURCE_PATH).getParent().toFile().exists()) {
            targetPath = Paths.get("app", RELATIVE_RESOURCE_PATH);
        }

        File targetFile = targetPath.toFile();
        generateOdesign(targetFile);
        return targetFile;
    }

    // Generates .odesign AST for DSLs in Sirius IDE
    public static void generateOdesign(File outputFile) throws IOException {
        initStandalone();

        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Root group and viewpoint
        Group group = org.eclipse.sirius.viewpoint.description.DescriptionFactory.eINSTANCE.createGroup();
        group.setName("KarpfenGroup");

        Viewpoint viewpoint = org.eclipse.sirius.viewpoint.description.DescriptionFactory.eINSTANCE
                .createViewpoint();
        viewpoint.setName("KarpfenViewpoint");
        viewpoint.setLabel("Karpfen Visualizations");
        group.getOwnedViewpoints().add(viewpoint);

        // Register java services in sirius viewpoint
        // required to create visuals for object diagram
        JavaExtension javaExt = org.eclipse.sirius.viewpoint.description.DescriptionFactory.eINSTANCE
                .createJavaExtension();
        javaExt.setQualifiedClassName("org.karpfen.design.KarpfenDiagramServices");
        viewpoint.getOwnedJavaExtensions().add(javaExt);

        ///////
        // !!! KMeta class diagram
        ///////
        DiagramDescription kmetaDiagram = DescriptionFactory.eINSTANCE.createDiagramDescription();
        kmetaDiagram.setName("KMetaClassDiagram");
        kmetaDiagram.setLabel("KMeta Class Diagram");
        kmetaDiagram.setDomainClass("ecore.EPackage");
        kmetaDiagram.getMetamodel().add(EcorePackage.eINSTANCE);
        viewpoint.getOwnedRepresentations().add(kmetaDiagram);

        Layer kmetaDefaultLayer = DescriptionFactory.eINSTANCE.createLayer();
        kmetaDefaultLayer.setName("Default");
        kmetaDefaultLayer.setLabel("Default");
        kmetaDiagram.setDefaultLayer(kmetaDefaultLayer);

        // Class container node - EClass
        ContainerMapping eClassNode = DescriptionFactory.eINSTANCE.createContainerMapping();
        eClassNode.setName("EClassNode");
        eClassNode.setDomainClass("ecore.EClass");
        eClassNode.setSemanticCandidatesExpression("aql:self.eClassifiers->filter(ecore::EClass)");
        eClassNode.setChildrenPresentation(ContainerLayout.LIST);

        FlatContainerStyleDescription classStyle = StyleFactory.eINSTANCE.createFlatContainerStyleDescription();
        classStyle.setLabelExpression("aql:self.getKMetaClassLabel()");
        classStyle.setShowIcon(true);
        classStyle.setBorderSizeComputationExpression("1");
        classStyle.setWidthComputationExpression("14");
        classStyle.setHeightComputationExpression("4");
        eClassNode.setStyle(classStyle);
        kmetaDefaultLayer.getContainerMappings().add(eClassNode);

        // Class attributes subnodes - EAttribute in EClass
        NodeMapping attributeNode = DescriptionFactory.eINSTANCE.createNodeMapping();
        attributeNode.setName("EAttributeNode");
        attributeNode.setDomainClass("ecore.EAttribute");
        attributeNode.setSemanticCandidatesExpression("aql:self.eAttributes->select(a | a.name != '__id__')");

        NodeStyleDescription attrStyle = StyleFactory.eINSTANCE.createSquareDescription();
        attrStyle.setLabelExpression("aql:self.getKMetaAttributeLabel()");
        attrStyle.setShowIcon(true);
        attrStyle.setLabelAlignment(LabelAlignment.LEFT);
        attrStyle.setLabelPosition(LabelPosition.NODE_LITERAL);
        attrStyle.setBorderSizeComputationExpression("0");
        attrStyle.setResizeKind(ResizeKind.NONE_LITERAL);
        attributeNode.setStyle(attrStyle);
        eClassNode.getSubNodeMappings().add(attributeNode);

        // Straight Routing Layer
        AdditionalLayer kmetaStraightLayer = DescriptionFactory.eINSTANCE.createAdditionalLayer();
        kmetaStraightLayer.setName("StraightRoutingLayer");
        kmetaStraightLayer.setLabel("Direct (Straight) Routing");
        kmetaStraightLayer.setActiveByDefault(true);
        kmetaStraightLayer.setOptional(true);

        EdgeMapping kmetaHasEdge = createKmetaEdgeMapping("HasCompositionEdge", true, eClassNode,
                EdgeRouting.STRAIGHT_LITERAL);
        kmetaStraightLayer.getEdgeMappings().add(kmetaHasEdge);

        EdgeMapping kmetaKnowsEdge = createKmetaEdgeMapping("KnowsAssociationEdge", false, eClassNode,
                EdgeRouting.STRAIGHT_LITERAL);
        kmetaStraightLayer.getEdgeMappings().add(kmetaKnowsEdge);
        kmetaDiagram.getAdditionalLayers().add(kmetaStraightLayer);

        // Orthogonal Routing Layer
        AdditionalLayer kmetaOrthoLayer = DescriptionFactory.eINSTANCE.createAdditionalLayer();
        kmetaOrthoLayer.setName("OrthogonalRoutingLayer");
        kmetaOrthoLayer.setLabel("Orthogonal (Manhattan) Routing");
        kmetaOrthoLayer.setActiveByDefault(false);
        kmetaOrthoLayer.setOptional(true);

        EdgeMapping orthoKmetaHasEdge = createKmetaEdgeMapping("OrthoHasCompositionEdge", true, eClassNode,
                EdgeRouting.MANHATTAN_LITERAL);
        kmetaOrthoLayer.getEdgeMappings().add(orthoKmetaHasEdge);

        EdgeMapping orthoKmetaKnowsEdge = createKmetaEdgeMapping("OrthoKnowsAssociationEdge", false, eClassNode,
                EdgeRouting.MANHATTAN_LITERAL);
        kmetaOrthoLayer.getEdgeMappings().add(orthoKmetaKnowsEdge);
        kmetaDiagram.getAdditionalLayers().add(kmetaOrthoLayer);

        // KMETA TOOLS
        buildKMetaToolSections(kmetaDefaultLayer, eClassNode, attributeNode,
                kmetaHasEdge, kmetaKnowsEdge, orthoKmetaHasEdge, orthoKmetaKnowsEdge);

        ///////
        // !!! KModel object diagram
        ///////
        DiagramDescription kmodelDiagram = DescriptionFactory.eINSTANCE.createDiagramDescription();
        kmodelDiagram.setName("KModelObjectDiagram");
        kmodelDiagram.setLabel("KModel Object Diagram");
        kmodelDiagram.setDomainClass("ecore.EObject");
        kmodelDiagram.setPreconditionExpression("aql:self.isKModelRoot()");
        kmodelDiagram.getMetamodel().add(EcorePackage.eINSTANCE);
        viewpoint.getOwnedRepresentations().add(kmodelDiagram);

        Layer kmodelDefaultLayer = DescriptionFactory.eINSTANCE.createLayer();
        kmodelDefaultLayer.setName("Default");
        kmodelDefaultLayer.setLabel("Default");
        kmodelDiagram.setDefaultLayer(kmodelDefaultLayer);

        // Object container node
        ContainerMapping eObjectNode = DescriptionFactory.eINSTANCE.createContainerMapping();
        eObjectNode.setName("EObjectNode");
        eObjectNode.setDomainClass("ecore.EObject");
        eObjectNode.setSemanticCandidatesExpression("aql:self.eAllContents()->including(self)");
        eObjectNode.setChildrenPresentation(ContainerLayout.LIST);

        FlatContainerStyleDescription objStyle = StyleFactory.eINSTANCE.createFlatContainerStyleDescription();
        objStyle.setLabelExpression("aql:self.getObjectHeaderLabel()");
        objStyle.setLabelAlignment(LabelAlignment.LEFT);
        objStyle.setShowIcon(true);
        objStyle.setBorderSizeComputationExpression("1");
        objStyle.setWidthComputationExpression("16");
        objStyle.setHeightComputationExpression("4");
        eObjectNode.setStyle(objStyle);
        kmodelDefaultLayer.getContainerMappings().add(eObjectNode);

        // Slot Subnode Mapping: displays all schema attributes with value or <unset>
        NodeMapping slotNode = DescriptionFactory.eINSTANCE.createNodeMapping();
        slotNode.setName("EAttributeSlotNode");
        slotNode.setDomainClass("ecore.EAttribute");
        slotNode.setSemanticCandidatesExpression("aql:self.getSchemaAttributes()");

        NodeStyleDescription slotStyle = StyleFactory.eINSTANCE.createSquareDescription();
        slotStyle.setLabelExpression("aql:self.getKModelSlotLabel(view)");
        slotStyle.setShowIcon(true);
        slotStyle.setLabelAlignment(LabelAlignment.LEFT);
        slotStyle.setLabelPosition(LabelPosition.NODE_LITERAL);
        slotStyle.setBorderSizeComputationExpression("0");
        slotStyle.setResizeKind(ResizeKind.NONE_LITERAL);
        slotNode.setStyle(slotStyle);
        eObjectNode.getSubNodeMappings().add(slotNode);

        // Straight Routing Layer
        AdditionalLayer kmodelStraightLayer = DescriptionFactory.eINSTANCE.createAdditionalLayer();
        kmodelStraightLayer.setName("StraightRoutingLayer");
        kmodelStraightLayer.setLabel("Direct (Straight) Routing");
        kmodelStraightLayer.setActiveByDefault(true);
        kmodelStraightLayer.setOptional(true);

        EdgeMapping instanceHasEdge = createKmodelEdgeMapping("InstanceContainmentEdge", true, eObjectNode,
                EdgeRouting.STRAIGHT_LITERAL);
        kmodelStraightLayer.getEdgeMappings().add(instanceHasEdge);

        EdgeMapping instanceKnowsEdge = createKmodelEdgeMapping("InstanceReferenceEdge", false, eObjectNode,
                EdgeRouting.STRAIGHT_LITERAL);
        kmodelStraightLayer.getEdgeMappings().add(instanceKnowsEdge);
        kmodelDiagram.getAdditionalLayers().add(kmodelStraightLayer);

        // Orthogonal Routing Layer
        AdditionalLayer kmodelOrthoLayer = DescriptionFactory.eINSTANCE.createAdditionalLayer();
        kmodelOrthoLayer.setName("OrthogonalRoutingLayer");
        kmodelOrthoLayer.setLabel("Orthogonal (Manhattan) Routing");
        kmodelOrthoLayer.setActiveByDefault(false);
        kmodelOrthoLayer.setOptional(true);

        EdgeMapping orthoInstanceHasEdge = createKmodelEdgeMapping("OrthoInstanceContainmentEdge", true,
                eObjectNode, EdgeRouting.MANHATTAN_LITERAL);
        kmodelOrthoLayer.getEdgeMappings().add(orthoInstanceHasEdge);

        EdgeMapping orthoInstanceKnowsEdge = createKmodelEdgeMapping("OrthoInstanceReferenceEdge", false,
                eObjectNode, EdgeRouting.MANHATTAN_LITERAL);
        kmodelOrthoLayer.getEdgeMappings().add(orthoInstanceKnowsEdge);
        kmodelDiagram.getAdditionalLayers().add(kmodelOrthoLayer);

        // KModel TOOLS
        buildKModelToolSections(kmodelDefaultLayer, eObjectNode, slotNode,
                instanceHasEdge, instanceKnowsEdge, orthoInstanceHasEdge, orthoInstanceKnowsEdge);

        ///////
        // !!! KStates statechart diagram
        ///////
        DiagramDescription kstatesDiagram = DescriptionFactory.eINSTANCE.createDiagramDescription();
        kstatesDiagram.setName("KStatesDiagram");
        kstatesDiagram.setLabel("KStates State Diagram");
        kstatesDiagram.setDomainClass("ecore.EObject");
        kstatesDiagram.setPreconditionExpression("aql:self.isKStatesRoot()");
        kstatesDiagram.getMetamodel().add(EcorePackage.eINSTANCE);
        viewpoint.getOwnedRepresentations().add(kstatesDiagram);

        Layer kstatesDefaultLayer = DescriptionFactory.eINSTANCE.createLayer();
        kstatesDefaultLayer.setName("Default");
        kstatesDefaultLayer.setLabel("Default");
        kstatesDiagram.setDefaultLayer(kstatesDefaultLayer);

        // State container node, list header, entry, do, sub-states
        ContainerMapping stateNode = DescriptionFactory.eINSTANCE.createContainerMapping();
        stateNode.setName("StateNode");
        stateNode.setDomainClass("ecore.EObject");
        stateNode.setSemanticCandidatesExpression(
                "aql:self.eAllContents()->including(self)->filter(ecore::EObject)->select(o | o.eClass().name == 'State')");
        stateNode.setChildrenPresentation(ContainerLayout.LIST);

        FlatContainerStyleDescription stateStyle = StyleFactory.eINSTANCE.createFlatContainerStyleDescription();
        stateStyle.setLabelExpression("aql:self.getStateHeaderLabel()");
        stateStyle.setLabelAlignment(LabelAlignment.LEFT);
        stateStyle.setShowIcon(true);
        stateStyle.setBorderSizeComputationExpression("1");
        stateStyle.setWidthComputationExpression("18");
        stateStyle.setHeightComputationExpression("5");
        stateNode.setStyle(stateStyle);
        kstatesDefaultLayer.getContainerMappings().add(stateNode);

        // ENTRY action subnode
        NodeMapping entryActionNode = DescriptionFactory.eINSTANCE.createNodeMapping();
        entryActionNode.setName("EntryActionNode");
        entryActionNode.setDomainClass("ecore.EObject");
        entryActionNode.setSemanticCandidatesExpression(
                "aql:if self.entryAction != null and self.entryAction != '' then Sequence{self} else Sequence{} endif");

        NodeStyleDescription entryStyle = StyleFactory.eINSTANCE.createSquareDescription();
        entryStyle.setLabelExpression("aql:self.getEntryLabel()");
        entryStyle.setShowIcon(false);
        entryStyle.setLabelAlignment(LabelAlignment.LEFT);
        entryStyle.setLabelPosition(LabelPosition.NODE_LITERAL);
        entryStyle.setBorderSizeComputationExpression("0");
        entryStyle.setResizeKind(ResizeKind.NONE_LITERAL);
        entryActionNode.setStyle(entryStyle);
        stateNode.getSubNodeMappings().add(entryActionNode);

        // DO action subnode
        NodeMapping doActionNode = DescriptionFactory.eINSTANCE.createNodeMapping();
        doActionNode.setName("DoActionNode");
        doActionNode.setDomainClass("ecore.EObject");
        doActionNode.setSemanticCandidatesExpression(
                "aql:if self.doAction != null and self.doAction != '' then Sequence{self} else Sequence{} endif");

        NodeStyleDescription doStyle = StyleFactory.eINSTANCE.createSquareDescription();
        doStyle.setLabelExpression("aql:self.getDoLabel()");
        doStyle.setShowIcon(false);
        doStyle.setLabelAlignment(LabelAlignment.LEFT);
        doStyle.setLabelPosition(LabelPosition.NODE_LITERAL);
        doStyle.setBorderSizeComputationExpression("0");
        doStyle.setResizeKind(ResizeKind.NONE_LITERAL);
        doActionNode.setStyle(doStyle);
        stateNode.getSubNodeMappings().add(doActionNode);

        // Straight Routing Layer for Transitions
        AdditionalLayer kstatesStraightLayer = DescriptionFactory.eINSTANCE.createAdditionalLayer();
        kstatesStraightLayer.setName("StraightRoutingLayer");
        kstatesStraightLayer.setLabel("Direct (Straight) Routing");
        kstatesStraightLayer.setActiveByDefault(true);
        kstatesStraightLayer.setOptional(true);

        EdgeMapping transitionEdge = createKstatesTransitionEdgeMapping("TransitionEdge", stateNode,
                EdgeRouting.STRAIGHT_LITERAL);
        kstatesStraightLayer.getEdgeMappings().add(transitionEdge);
        kstatesDiagram.getAdditionalLayers().add(kstatesStraightLayer);

        // Orthogonal Routing Layer for Transitions
        AdditionalLayer kstatesOrthoLayer = DescriptionFactory.eINSTANCE.createAdditionalLayer();
        kstatesOrthoLayer.setName("OrthogonalRoutingLayer");
        kstatesOrthoLayer.setLabel("Orthogonal (Manhattan) Routing");
        kstatesOrthoLayer.setActiveByDefault(false);
        kstatesOrthoLayer.setOptional(true);

        EdgeMapping orthoTransitionEdge = createKstatesTransitionEdgeMapping("OrthoTransitionEdge", stateNode,
                EdgeRouting.MANHATTAN_LITERAL);
        kstatesOrthoLayer.getEdgeMappings().add(orthoTransitionEdge);
        kstatesDiagram.getAdditionalLayers().add(kstatesOrthoLayer);

        // KStates TOOLS
        buildKStatesToolSections(kstatesDefaultLayer, stateNode, transitionEdge, orthoTransitionEdge);

        // Save as .odesign XMI
        ResourceSet resourceSet = new ResourceSetImpl();
        URI fileUri = URI.createFileURI(outputFile.getAbsolutePath());
        Resource resource = resourceSet.createResource(fileUri);
        resource.getContents().add(group);
        resource.save(Collections.emptyMap());

        KarpfenLog.info("Successfully generated .odesign at: " + outputFile.getAbsolutePath());
    }

    private static EdgeMapping createKmetaEdgeMapping(String name, boolean isContainment,
            ContainerMapping eClassNode, EdgeRouting routing) {
        EdgeMapping edge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        edge.setName(name);
        edge.setDomainClass("ecore.EReference");
        edge.setUseDomainElement(true);
        edge.setSemanticCandidatesExpression(isContainment
                ? "aql:self.eClassifiers->filter(ecore::EClass).eStructuralFeatures->filter(ecore::EReference)->select(r | r.containment)"
                : "aql:self.eClassifiers->filter(ecore::EClass).eStructuralFeatures->filter(ecore::EReference)->select(r | not(r.containment))");
        edge.getSourceMapping().add(eClassNode);
        edge.getTargetMapping().add(eClassNode);
        edge.setSourceFinderExpression("aql:self.eContainingClass");
        edge.setTargetFinderExpression("aql:self.eType");

        EdgeStyleDescription style = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        style.setLineStyle(isContainment ? LineStyle.SOLID_LITERAL : LineStyle.DASH_LITERAL);
        if (isContainment) {
            style.setSourceArrow(EdgeArrows.FILL_DIAMOND_LITERAL);
        }
        style.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        style.setSizeComputationExpression("1");
        style.setRoutingStyle(routing);
        style.setEndsCentering(CenteringStyle.NONE);

        CenterLabelStyleDescription labelStyle = StyleFactory.eINSTANCE.createCenterLabelStyleDescription();
        labelStyle.setLabelExpression("aql:self.getKMetaEdgeLabel()");
        labelStyle.setShowIcon(false);
        style.setCenterLabelStyleDescription(labelStyle);
        edge.setStyle(style);
        return edge;
    }

    private static EdgeMapping createKmodelEdgeMapping(String name, boolean isContainment,
            ContainerMapping eObjectNode, EdgeRouting routing) {
        EdgeMapping edge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        edge.setName(name);
        edge.setUseDomainElement(false);
        edge.getSourceMapping().add(eObjectNode);
        edge.getTargetMapping().add(eObjectNode);
        edge.setTargetFinderExpression(isContainment ? "aql:self.eContents()" : "aql:self.eCrossReferences()");

        EdgeStyleDescription style = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        style.setLineStyle(isContainment ? LineStyle.SOLID_LITERAL : LineStyle.DASH_LITERAL);
        if (isContainment) {
            style.setSourceArrow(EdgeArrows.FILL_DIAMOND_LITERAL);
        }
        style.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        style.setSizeComputationExpression("1");
        style.setRoutingStyle(routing);
        style.setEndsCentering(CenteringStyle.NONE);

        CenterLabelStyleDescription labelStyle = StyleFactory.eINSTANCE.createCenterLabelStyleDescription();
        labelStyle.setLabelExpression(isContainment
                ? "aql:self.getInstanceContainmentLabel(view)"
                : "aql:self.getInstanceReferenceLabel(view)");
        labelStyle.setShowIcon(false);
        style.setCenterLabelStyleDescription(labelStyle);
        edge.setStyle(style);
        return edge;
    }

    private static EdgeMapping createKstatesTransitionEdgeMapping(String name, ContainerMapping stateNode,
            EdgeRouting routing) {
        EdgeMapping edge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        edge.setName(name);
        edge.setDomainClass("ecore.EObject");
        edge.setUseDomainElement(true);
        edge.setSemanticCandidatesExpression(
                "aql:self.eAllContents()->including(self)->filter(ecore::EObject)->select(o | o.eClass().name == 'Transition')");
        edge.getSourceMapping().add(stateNode);
        edge.getTargetMapping().add(stateNode);
        edge.setSourceFinderExpression("aql:self.sourceState");
        edge.setTargetFinderExpression("aql:self.targetState");

        EdgeStyleDescription style = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        style.setLineStyle(LineStyle.SOLID_LITERAL);
        style.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        style.setSizeComputationExpression("1");
        style.setRoutingStyle(routing);
        style.setEndsCentering(CenteringStyle.NONE);

        CenterLabelStyleDescription labelStyle = StyleFactory.eINSTANCE.createCenterLabelStyleDescription();
        labelStyle.setLabelExpression("aql:self.getTransitionLabel()");
        labelStyle.setShowIcon(false);
        style.setCenterLabelStyleDescription(labelStyle);
        edge.setStyle(style);
        return edge;
    }

    private static void applyDirectEditMask(DirectEditLabel directEditTool,
            org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf) {
        EditMaskVariables mask = vtf.createEditMaskVariables();
        mask.setMask("{0}");
        directEditTool.setMask(mask);
    }

    private static void buildKMetaToolSections(Layer defaultLayer,
            ContainerMapping eClassNode, NodeMapping attributeNode,
            EdgeMapping hasEdge, EdgeMapping knowsEdge,
            EdgeMapping orthoHasEdge, EdgeMapping orthoKnowsEdge) {

        // alias for different toolfactroies
        org.eclipse.sirius.diagram.description.tool.ToolFactory dtf = org.eclipse.sirius.diagram.description.tool.ToolFactory.eINSTANCE;
        org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf = org.eclipse.sirius.viewpoint.description.tool.ToolFactory.eINSTANCE;

        // main section for tools/pallete group
        ToolSection typesSection = dtf.createToolSection();
        typesSection.setName("TypesSection");
        typesSection.setLabel("Types");
        defaultLayer.getToolSections().add(typesSection);

        // Tool - create new type EClass
        ContainerCreationDescription createClassTool = dtf.createContainerCreationDescription();
        createClassTool.setName("CreateTypeTool");
        createClassTool.setLabel("New Type");
        createClassTool.getContainerMappings().add(eClassNode);

        InitialNodeCreationOperation classOp = vtf.createInitialNodeCreationOperation();
        ChangeContext classCtx = vtf.createChangeContext();
        classCtx.setBrowseExpression("aql:self");

        CreateInstance createClassInst = vtf.createCreateInstance();
        createClassInst.setTypeName("ecore.EClass");
        createClassInst.setReferenceName("eClassifiers");
        createClassInst.setVariableName("newClass");

        SetValue setNameVal = vtf.createSetValue();
        setNameVal.setFeatureName("name");
        setNameVal.setValueExpression("aql:'NewType'");

        SetValue setSuperType = vtf.createSetValue();
        setSuperType.setFeatureName("eSuperTypes");
        setSuperType.setValueExpression("aql:ecore::EObject");

        createClassInst.getSubModelOperations().add(setNameVal);
        createClassInst.getSubModelOperations().add(setSuperType);
        classCtx.getSubModelOperations().add(createClassInst);
        classOp.setFirstModelOperations(classCtx);
        createClassTool.setInitialOperation(classOp);
        typesSection.getOwnedTools().add(createClassTool);

        OperationAction toggleRootAction = vtf.createOperationAction();
        toggleRootAction.setName("ToggleRootAction");
        toggleRootAction.setLabel("Toggle Root Type");
        InitialOperation rootOp = vtf.createInitialOperation();
        ChangeContext rootCtx = vtf.createChangeContext();
        rootCtx.setBrowseExpression("aql:self.toggleRootClass()");
        rootOp.setFirstModelOperations(rootCtx);
        toggleRootAction.setInitialOperation(rootOp);
        typesSection.getOwnedTools().add(toggleRootAction);

        DirectEditLabel editClassName = dtf.createDirectEditLabel();
        editClassName.setName("EditTypeName");
        applyDirectEditMask(editClassName, vtf);
        InitialOperation editClassOp = vtf.createInitialOperation();
        ChangeContext editClassCtx = vtf.createChangeContext();
        editClassCtx.setBrowseExpression("aql:self.editClassName(arg0)");
        editClassOp.setFirstModelOperations(editClassCtx);
        editClassName.setInitialOperation(editClassOp);
        eClassNode.setLabelDirectEdit(editClassName);
        typesSection.getOwnedTools().add(editClassName);

        DirectEditLabel editAttr = dtf.createDirectEditLabel();
        editAttr.setName("EditAttributeDSL");
        applyDirectEditMask(editAttr, vtf);
        InitialOperation editAttrOp = vtf.createInitialOperation();
        ChangeContext editAttrCtx = vtf.createChangeContext();
        editAttrCtx.setBrowseExpression("aql:self.editKMetaAttribute(arg0)");
        editAttrOp.setFirstModelOperations(editAttrCtx);
        editAttr.setInitialOperation(editAttrOp);
        attributeNode.setLabelDirectEdit(editAttr);
        typesSection.getOwnedTools().add(editAttr);

        DirectEditLabel editHas = dtf.createDirectEditLabel();
        editHas.setName("EditHasEdge");
        applyDirectEditMask(editHas, vtf);
        InitialOperation editHasOp = vtf.createInitialOperation();
        ChangeContext editHasCtx = vtf.createChangeContext();
        editHasCtx.setBrowseExpression("aql:self.editKMetaEdge(arg0)");
        editHasOp.setFirstModelOperations(editHasCtx);
        editHas.setInitialOperation(editHasOp);
        hasEdge.setLabelDirectEdit(editHas);
        orthoHasEdge.setLabelDirectEdit(editHas);
        typesSection.getOwnedTools().add(editHas);

        DirectEditLabel editKnows = dtf.createDirectEditLabel();
        editKnows.setName("EditKnowsEdge");
        applyDirectEditMask(editKnows, vtf);
        InitialOperation editKnowsOp = vtf.createInitialOperation();
        ChangeContext editKnowsCtx = vtf.createChangeContext();
        editKnowsCtx.setBrowseExpression("aql:self.editKMetaEdge(arg0)");
        editKnowsOp.setFirstModelOperations(editKnowsCtx);
        editKnows.setInitialOperation(editKnowsOp);
        knowsEdge.setLabelDirectEdit(editKnows);
        orthoKnowsEdge.setLabelDirectEdit(editKnows);
        typesSection.getOwnedTools().add(editKnows);

        ToolSection propSection = dtf.createToolSection();
        propSection.setName("PropertiesSection");
        propSection.setLabel("Properties (prop)");
        defaultLayer.getToolSections().add(propSection);

        propSection.getOwnedTools()
                .add(createPropertyTool(attributeNode, "String Property", "strProp", "ecore::EString",
                        1));
        propSection.getOwnedTools()
                .add(createPropertyTool(attributeNode, "Number Property", "numProp", "ecore::EDouble",
                        1));
        propSection.getOwnedTools()
                .add(createPropertyTool(attributeNode, "Boolean Property", "flag", "ecore::EBoolean",
                        1));
        propSection.getOwnedTools()
                .add(createPropertyTool(attributeNode, "List Property", "items", "ecore::EString", -1));

        ToolSection relSection = dtf.createToolSection();
        relSection.setName("RelationsSection");
        relSection.setLabel("Relationships");
        defaultLayer.getToolSections().add(relSection);

        relSection.getOwnedTools()
                .add(createReferenceTool(hasEdge, orthoHasEdge, "has (1:1 embedded)", "has_", true, 1));
        relSection.getOwnedTools()
                .add(createReferenceTool(hasEdge, orthoHasEdge, "has (1:N embedded list)", "has_list_",
                        true, -1));
        relSection.getOwnedTools().add(
                createReferenceTool(knowsEdge, orthoKnowsEdge, "knows (1:1 link)", "knows_", false, 1));
        relSection.getOwnedTools()
                .add(createReferenceTool(knowsEdge, orthoKnowsEdge, "knows (1:N link list)",
                        "knows_list_", false, -1));
    }

    private static void buildKModelToolSections(Layer defaultLayer, ContainerMapping eObjectNode,
            NodeMapping slotNode, EdgeMapping instanceHasEdge, EdgeMapping instanceKnowsEdge,
            EdgeMapping orthoInstanceHasEdge, EdgeMapping orthoInstanceKnowsEdge) {
        org.eclipse.sirius.diagram.description.tool.ToolFactory dtf = org.eclipse.sirius.diagram.description.tool.ToolFactory.eINSTANCE;
        org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf = org.eclipse.sirius.viewpoint.description.tool.ToolFactory.eINSTANCE;

        // Direct Edit & Management Section
        ToolSection modelSection = dtf.createToolSection();
        modelSection.setName("ModelSection");
        modelSection.setLabel("Model Operations");
        defaultLayer.getToolSections().add(modelSection);

        DirectEditLabel editObjectHeader = dtf.createDirectEditLabel();
        editObjectHeader.setName("EditObjectHeader");
        applyDirectEditMask(editObjectHeader, vtf);
        InitialOperation editHeaderOp = vtf.createInitialOperation();
        ChangeContext editHeaderCtx = vtf.createChangeContext();
        editHeaderCtx.setBrowseExpression("aql:self.editKModelObjectHeader(arg0)");
        editHeaderOp.setFirstModelOperations(editHeaderCtx);
        editObjectHeader.setInitialOperation(editHeaderOp);
        eObjectNode.setLabelDirectEdit(editObjectHeader);
        modelSection.getOwnedTools().add(editObjectHeader);

        DirectEditLabel editSlot = dtf.createDirectEditLabel();
        editSlot.setName("EditSlotValue");
        applyDirectEditMask(editSlot, vtf);
        InitialOperation editSlotOp = vtf.createInitialOperation();
        ChangeContext editSlotCtx = vtf.createChangeContext();
        editSlotCtx.setBrowseExpression("aql:self.editKModelSlotValue(view, arg0)");
        editSlotOp.setFirstModelOperations(editSlotCtx);
        editSlot.setInitialOperation(editSlotOp);
        slotNode.setLabelDirectEdit(editSlot);
        modelSection.getOwnedTools().add(editSlot);

        // Edge direct-edit tools passing 'view' (the DEdge instance)
        DirectEditLabel editHasEdge = dtf.createDirectEditLabel();
        editHasEdge.setName("EditInstanceHasEdge");
        applyDirectEditMask(editHasEdge, vtf);
        InitialOperation editHasEdgeOp = vtf.createInitialOperation();
        ChangeContext editHasEdgeCtx = vtf.createChangeContext();
        editHasEdgeCtx.setBrowseExpression("aql:self.editInstanceEdge(view, arg0, true)");
        editHasEdgeOp.setFirstModelOperations(editHasEdgeCtx);
        editHasEdge.setInitialOperation(editHasEdgeOp);
        instanceHasEdge.setLabelDirectEdit(editHasEdge);
        orthoInstanceHasEdge.setLabelDirectEdit(editHasEdge);
        modelSection.getOwnedTools().add(editHasEdge);

        DirectEditLabel editKnowsEdge = dtf.createDirectEditLabel();
        editKnowsEdge.setName("EditInstanceKnowsEdge");
        applyDirectEditMask(editKnowsEdge, vtf);
        InitialOperation editKnowsEdgeOp = vtf.createInitialOperation();
        ChangeContext editKnowsEdgeCtx = vtf.createChangeContext();
        editKnowsEdgeCtx.setBrowseExpression("aql:self.editInstanceEdge(view, arg0, false)");
        editKnowsEdgeOp.setFirstModelOperations(editKnowsEdgeCtx);
        editKnowsEdge.setInitialOperation(editKnowsEdgeOp);
        instanceKnowsEdge.setLabelDirectEdit(editKnowsEdge);
        orthoInstanceKnowsEdge.setLabelDirectEdit(editKnowsEdge);
        modelSection.getOwnedTools().add(editKnowsEdge);

        // Deletion tools (Slots are managed via <unset> direct-edit)
        DeleteElementDescription delObject = dtf.createDeleteElementDescription();
        delObject.setName("DeleteObjectTool");
        ElementDeleteVariable delObjElem = vtf.createElementDeleteVariable();
        delObjElem.setName("element");
        delObject.setElement(delObjElem);
        ElementDeleteVariable delObjElemView = vtf.createElementDeleteVariable();
        delObjElemView.setName("elementView");
        delObject.setElementView(delObjElemView);

        InitialOperation delObjOp = vtf.createInitialOperation();
        ChangeContext delObjCtx = vtf.createChangeContext();
        delObjCtx.setBrowseExpression("aql:self.deleteKModelObject()");
        delObjOp.setFirstModelOperations(delObjCtx);
        delObject.setInitialOperation(delObjOp);
        eObjectNode.setDeletionDescription(delObject);
        modelSection.getOwnedTools().add(delObject);

        DeleteElementDescription delHasLink = dtf.createDeleteElementDescription();
        delHasLink.setName("DeleteHasLinkTool");
        ElementDeleteVariable delHasElem = vtf.createElementDeleteVariable();
        delHasElem.setName("element");
        delHasLink.setElement(delHasElem);
        ElementDeleteVariable delHasElemView = vtf.createElementDeleteVariable();
        delHasElemView.setName("elementView");
        delHasLink.setElementView(delHasElemView);

        InitialOperation delHasLinkOp = vtf.createInitialOperation();
        ChangeContext delHasLinkCtx = vtf.createChangeContext();
        delHasLinkCtx.setBrowseExpression("aql:self.deleteInstanceLink(elementView, true)");
        delHasLinkOp.setFirstModelOperations(delHasLinkCtx);
        delHasLink.setInitialOperation(delHasLinkOp);
        instanceHasEdge.setDeletionDescription(delHasLink);
        orthoInstanceHasEdge.setDeletionDescription(delHasLink);
        modelSection.getOwnedTools().add(delHasLink);

        DeleteElementDescription delKnowsLink = dtf.createDeleteElementDescription();
        delKnowsLink.setName("DeleteKnowsLinkTool");
        ElementDeleteVariable delKnowsElem = vtf.createElementDeleteVariable();
        delKnowsElem.setName("element");
        delKnowsLink.setElement(delKnowsElem);
        ElementDeleteVariable delKnowsElemView = vtf.createElementDeleteVariable();
        delKnowsElemView.setName("elementView");
        delKnowsLink.setElementView(delKnowsElemView);

        InitialOperation delKnowsLinkOp = vtf.createInitialOperation();
        ChangeContext delKnowsLinkCtx = vtf.createChangeContext();
        delKnowsLinkCtx.setBrowseExpression("aql:self.deleteInstanceLink(elementView, false)");
        delKnowsLinkOp.setFirstModelOperations(delKnowsLinkCtx);
        delKnowsLink.setInitialOperation(delKnowsLinkOp);
        instanceKnowsEdge.setDeletionDescription(delKnowsLink);
        orthoInstanceKnowsEdge.setDeletionDescription(delKnowsLink);
        modelSection.getOwnedTools().add(delKnowsLink);

        // Object Relationships Palette Section
        ToolSection linkSection = dtf.createToolSection();
        linkSection.setName("InstanceLinksSection");
        linkSection.setLabel("Links");
        defaultLayer.getToolSections().add(linkSection);

        EdgeCreationDescription createKnowsLinkTool = dtf.createEdgeCreationDescription();
        createKnowsLinkTool.setName("CreateKnowsLink");
        createKnowsLinkTool.setLabel("knows (Reference Link)");
        createKnowsLinkTool.getEdgeMappings().add(instanceKnowsEdge);
        createKnowsLinkTool.getEdgeMappings().add(orthoInstanceKnowsEdge);
        InitEdgeCreationOperation knowsLinkOp = vtf.createInitEdgeCreationOperation();
        ChangeContext knowsLinkCtx = vtf.createChangeContext();
        knowsLinkCtx.setBrowseExpression("aql:source.createInstanceLink(target, false)");
        knowsLinkOp.setFirstModelOperations(knowsLinkCtx);
        createKnowsLinkTool.setInitialOperation(knowsLinkOp);
        linkSection.getOwnedTools().add(createKnowsLinkTool);

        EdgeCreationDescription createHasLinkTool = dtf.createEdgeCreationDescription();
        createHasLinkTool.setName("CreateHasLink");
        createHasLinkTool.setLabel("has (Containment Link)");
        createHasLinkTool.getEdgeMappings().add(instanceHasEdge);
        createHasLinkTool.getEdgeMappings().add(orthoInstanceHasEdge);
        InitEdgeCreationOperation hasLinkOp = vtf.createInitEdgeCreationOperation();
        ChangeContext hasLinkCtx = vtf.createChangeContext();
        hasLinkCtx.setBrowseExpression("aql:source.createInstanceLink(target, true)");
        hasLinkOp.setFirstModelOperations(hasLinkCtx);
        createHasLinkTool.setInitialOperation(hasLinkOp);
        linkSection.getOwnedTools().add(createHasLinkTool);
    }

    private static void buildKStatesToolSections(Layer defaultLayer, ContainerMapping stateNode,
            EdgeMapping transitionEdge, EdgeMapping orthoTransitionEdge) {
        org.eclipse.sirius.diagram.description.tool.ToolFactory dtf = org.eclipse.sirius.diagram.description.tool.ToolFactory.eINSTANCE;
        org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf = org.eclipse.sirius.viewpoint.description.tool.ToolFactory.eINSTANCE;

        // States Management Section
        ToolSection statesSection = dtf.createToolSection();
        statesSection.setName("StatesSection");
        statesSection.setLabel("States");
        defaultLayer.getToolSections().add(statesSection);

        ContainerCreationDescription createStateTool = dtf.createContainerCreationDescription();
        createStateTool.setName("CreateStateTool");
        createStateTool.setLabel("New State");
        createStateTool.getContainerMappings().add(stateNode);

        InitialNodeCreationOperation stateOp = vtf.createInitialNodeCreationOperation();
        ChangeContext stateCtx = vtf.createChangeContext();
        stateCtx.setBrowseExpression("aql:self.createState()");
        stateOp.setFirstModelOperations(stateCtx);
        createStateTool.setInitialOperation(stateOp);
        statesSection.getOwnedTools().add(createStateTool);

        OperationAction toggleInitialAction = vtf.createOperationAction();
        toggleInitialAction.setName("ToggleInitialStateAction");
        toggleInitialAction.setLabel("Toggle Initial State");
        InitialOperation initOp = vtf.createInitialOperation();
        ChangeContext initCtx = vtf.createChangeContext();
        initCtx.setBrowseExpression("aql:self.toggleInitialState()");
        initOp.setFirstModelOperations(initCtx);
        toggleInitialAction.setInitialOperation(initOp);
        statesSection.getOwnedTools().add(toggleInitialAction);

        DirectEditLabel editStateName = dtf.createDirectEditLabel();
        editStateName.setName("EditStateName");
        applyDirectEditMask(editStateName, vtf);
        InitialOperation editStateOp = vtf.createInitialOperation();
        ChangeContext editStateCtx = vtf.createChangeContext();
        editStateCtx.setBrowseExpression("aql:self.editStateName(arg0)");
        editStateOp.setFirstModelOperations(editStateCtx);
        editStateName.setInitialOperation(editStateOp);
        stateNode.setLabelDirectEdit(editStateName);
        statesSection.getOwnedTools().add(editStateName);

        DeleteElementDescription delState = dtf.createDeleteElementDescription();
        delState.setName("DeleteStateTool");
        ElementDeleteVariable delStateElem = vtf.createElementDeleteVariable();
        delStateElem.setName("element");
        delState.setElement(delStateElem);
        InitialOperation delStateOp = vtf.createInitialOperation();
        ChangeContext delStateCtx = vtf.createChangeContext();
        delStateCtx.setBrowseExpression("aql:element.deleteState()");
        delStateOp.setFirstModelOperations(delStateCtx);
        delState.setInitialOperation(delStateOp);
        stateNode.setDeletionDescription(delState);
        statesSection.getOwnedTools().add(delState);

        // Transitions Management Section
        ToolSection transitionsSection = dtf.createToolSection();
        transitionsSection.setName("TransitionsSection");
        transitionsSection.setLabel("Transitions");
        defaultLayer.getToolSections().add(transitionsSection);

        EdgeCreationDescription createTransTool = dtf.createEdgeCreationDescription();
        createTransTool.setName("CreateTransition");
        createTransTool.setLabel("Transition");
        createTransTool.getEdgeMappings().add(transitionEdge);
        createTransTool.getEdgeMappings().add(orthoTransitionEdge);
        InitEdgeCreationOperation transLinkOp = vtf.createInitEdgeCreationOperation();
        ChangeContext transLinkCtx = vtf.createChangeContext();
        transLinkCtx.setBrowseExpression("aql:source.createTransitionLink(target)");
        transLinkOp.setFirstModelOperations(transLinkCtx);
        createTransTool.setInitialOperation(transLinkOp);
        transitionsSection.getOwnedTools().add(createTransTool);

        DirectEditLabel editGuard = dtf.createDirectEditLabel();
        editGuard.setName("EditTransitionGuard");
        applyDirectEditMask(editGuard, vtf);
        InitialOperation editGuardOp = vtf.createInitialOperation();
        ChangeContext editGuardCtx = vtf.createChangeContext();
        editGuardCtx.setBrowseExpression("aql:self.editTransitionGuard(arg0)");
        editGuardOp.setFirstModelOperations(editGuardCtx);
        editGuard.setInitialOperation(editGuardOp);
        transitionEdge.setLabelDirectEdit(editGuard);
        orthoTransitionEdge.setLabelDirectEdit(editGuard);
        transitionsSection.getOwnedTools().add(editGuard);

        DeleteElementDescription delTrans = dtf.createDeleteElementDescription();
        delTrans.setName("DeleteTransitionTool");
        ElementDeleteVariable delTransElem = vtf.createElementDeleteVariable();
        delTransElem.setName("element");
        delTrans.setElement(delTransElem);
        InitialOperation delTransOp = vtf.createInitialOperation();
        ChangeContext delTransCtx = vtf.createChangeContext();
        delTransCtx.setBrowseExpression("aql:element.deleteTransition()");
        delTransOp.setFirstModelOperations(delTransCtx);
        delTrans.setInitialOperation(delTransOp);
        transitionEdge.setDeletionDescription(delTrans);
        orthoTransitionEdge.setDeletionDescription(delTrans);
        transitionsSection.getOwnedTools().add(delTrans);
    }

    private static NodeCreationDescription createPropertyTool(NodeMapping attributeNode, String label,
            String defaultName, String ecoreType, int upperBound) {
        org.eclipse.sirius.diagram.description.tool.ToolFactory dtf = org.eclipse.sirius.diagram.description.tool.ToolFactory.eINSTANCE;
        org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf = org.eclipse.sirius.viewpoint.description.tool.ToolFactory.eINSTANCE;

        NodeCreationDescription tool = dtf.createNodeCreationDescription();
        tool.setName(label.replace(" ", "").replace("(", "").replace(")", "").replace(":", ""));
        tool.setLabel(label);
        tool.getNodeMappings().add(attributeNode);

        InitialNodeCreationOperation op = vtf.createInitialNodeCreationOperation();
        ChangeContext ctx = vtf.createChangeContext();
        ctx.setBrowseExpression("aql:container");

        CreateInstance createAttr = vtf.createCreateInstance();
        createAttr.setTypeName("ecore.EAttribute");
        createAttr.setReferenceName("eStructuralFeatures");
        createAttr.setVariableName("newAttr");

        SetValue setName = vtf.createSetValue();
        setName.setFeatureName("name");
        setName.setValueExpression("aql:'" + defaultName + "'");

        SetValue setType = vtf.createSetValue();
        setType.setFeatureName("eType");
        setType.setValueExpression("aql:" + ecoreType);

        SetValue setBound = vtf.createSetValue();
        setBound.setFeatureName("upperBound");
        setBound.setValueExpression("aql:" + upperBound);

        createAttr.getSubModelOperations().add(setName);
        createAttr.getSubModelOperations().add(setType);
        createAttr.getSubModelOperations().add(setBound);
        ctx.getSubModelOperations().add(createAttr);
        op.setFirstModelOperations(ctx);
        tool.setInitialOperation(op);
        return tool;
    }

    private static EdgeCreationDescription createReferenceTool(EdgeMapping straightEdge, EdgeMapping orthoEdge,
            String label, String prefix, boolean isContainment, int upperBound) {
        org.eclipse.sirius.diagram.description.tool.ToolFactory dtf = org.eclipse.sirius.diagram.description.tool.ToolFactory.eINSTANCE;
        org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf = org.eclipse.sirius.viewpoint.description.tool.ToolFactory.eINSTANCE;

        EdgeCreationDescription tool = dtf.createEdgeCreationDescription();
        tool.setName(label.replace(" ", "").replace("(", "").replace(")", "").replace(":", "").replace("-",
                ""));
        tool.setLabel(label);
        tool.getEdgeMappings().add(straightEdge);
        tool.getEdgeMappings().add(orthoEdge);

        InitEdgeCreationOperation op = vtf.createInitEdgeCreationOperation();
        ChangeContext ctx = vtf.createChangeContext();
        ctx.setBrowseExpression("aql:source");

        CreateInstance createRef = vtf.createCreateInstance();
        createRef.setTypeName("ecore.EReference");
        createRef.setReferenceName("eStructuralFeatures");
        createRef.setVariableName("newRef");

        SetValue setName = vtf.createSetValue();
        setName.setFeatureName("name");
        setName.setValueExpression("aql:'" + prefix + "' + target.name.toLowerFirst()");

        SetValue setType = vtf.createSetValue();
        setType.setFeatureName("eType");
        setType.setValueExpression("aql:target");

        SetValue setContainment = vtf.createSetValue();
        setContainment.setFeatureName("containment");
        setContainment.setValueExpression("aql:" + isContainment);

        SetValue setBound = vtf.createSetValue();
        setBound.setFeatureName("upperBound");
        setBound.setValueExpression("aql:" + upperBound);

        createRef.getSubModelOperations().add(setName);
        createRef.getSubModelOperations().add(setType);
        createRef.getSubModelOperations().add(setContainment);
        createRef.getSubModelOperations().add(setBound);
        ctx.getSubModelOperations().add(createRef);
        op.setFirstModelOperations(ctx);
        tool.setInitialOperation(op);
        return tool;
    }

    public static void main(String[] args) {
        try {
            File result = generateToResources();
            KarpfenLog.info("Generation completed: " + result.getAbsolutePath());
        } catch (Exception e) {
            KarpfenLog.error("Failed to generate .odesign model: " + e.getMessage(), e);
            System.exit(1);
        }
    }
}
