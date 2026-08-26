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
import org.eclipse.sirius.diagram.ContainerLayout;
import org.eclipse.sirius.diagram.DiagramPackage;
import org.eclipse.sirius.diagram.EdgeArrows;
import org.eclipse.sirius.diagram.LabelPosition;
import org.eclipse.sirius.diagram.LineStyle;
import org.eclipse.sirius.diagram.ResizeKind;
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
        eClassNode.setStyle(classStyle);
        kmetaDefaultLayer.getContainerMappings().add(eClassNode);

        // Class attributes subnodes - EAttribute in EClass
        NodeMapping attributeNode = DescriptionFactory.eINSTANCE.createNodeMapping();
        attributeNode.setName("EAttributeNode");
        attributeNode.setDomainClass("ecore.EAttribute");
        attributeNode.setSemanticCandidatesExpression("aql:self.eAttributes->select(a | a.name != '__id__')");

        NodeStyleDescription attrStyle = StyleFactory.eINSTANCE.createSquareDescription();
        attrStyle.setLabelExpression("aql:self.getKMetaAttributeLabel()");
        attrStyle.setShowIcon(false);
        attrStyle.setLabelAlignment(LabelAlignment.LEFT);
        attrStyle.setLabelPosition(LabelPosition.NODE_LITERAL);
        attrStyle.setBorderSizeComputationExpression("0");
        attrStyle.setResizeKind(ResizeKind.NONE_LITERAL);
        attributeNode.setStyle(attrStyle);
        eClassNode.getSubNodeMappings().add(attributeNode);

        // Edge has - composition reference
        EdgeMapping kmetaHasEdge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        kmetaHasEdge.setName("HasCompositionEdge");
        kmetaHasEdge.setDomainClass("ecore.EReference");
        kmetaHasEdge.setUseDomainElement(true);
        kmetaHasEdge.setSemanticCandidatesExpression(
                "aql:self.eClassifiers->filter(ecore::EClass).eStructuralFeatures->filter(ecore::EReference)->select(r | r.containment)");
        kmetaHasEdge.getSourceMapping().add(eClassNode);
        kmetaHasEdge.getTargetMapping().add(eClassNode);
        kmetaHasEdge.setSourceFinderExpression("aql:self.eContainingClass");
        kmetaHasEdge.setTargetFinderExpression("aql:self.eType");

        EdgeStyleDescription hasEdgeStyle = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        hasEdgeStyle.setLineStyle(LineStyle.SOLID_LITERAL);
        hasEdgeStyle.setSourceArrow(EdgeArrows.FILL_DIAMOND_LITERAL);
        hasEdgeStyle.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        hasEdgeStyle.setSizeComputationExpression("1");
        CenterLabelStyleDescription kmetaHasLabelStyle = StyleFactory.eINSTANCE
                .createCenterLabelStyleDescription();
        kmetaHasLabelStyle.setLabelExpression("aql:self.getKMetaEdgeLabel()");
        kmetaHasLabelStyle.setShowIcon(false);
        hasEdgeStyle.setCenterLabelStyleDescription(kmetaHasLabelStyle);
        kmetaHasEdge.setStyle(hasEdgeStyle);
        kmetaDefaultLayer.getEdgeMappings().add(kmetaHasEdge);

        // Edge knows - association reference
        EdgeMapping kmetaKnowsEdge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        kmetaKnowsEdge.setName("KnowsAssociationEdge");
        kmetaKnowsEdge.setDomainClass("ecore.EReference");
        kmetaKnowsEdge.setUseDomainElement(true);
        kmetaKnowsEdge.setSemanticCandidatesExpression(
                "aql:self.eClassifiers->filter(ecore::EClass).eStructuralFeatures->filter(ecore::EReference)->select(r | not(r.containment))");
        kmetaKnowsEdge.getSourceMapping().add(eClassNode);
        kmetaKnowsEdge.getTargetMapping().add(eClassNode);
        kmetaKnowsEdge.setSourceFinderExpression("aql:self.eContainingClass");
        kmetaKnowsEdge.setTargetFinderExpression("aql:self.eType");

        EdgeStyleDescription knowsEdgeStyle = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        knowsEdgeStyle.setLineStyle(LineStyle.DASH_LITERAL);
        knowsEdgeStyle.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        knowsEdgeStyle.setSizeComputationExpression("1");
        CenterLabelStyleDescription kmetaKnowsLabelStyle = StyleFactory.eINSTANCE
                .createCenterLabelStyleDescription();
        kmetaKnowsLabelStyle.setLabelExpression("aql:self.getKMetaEdgeLabel()");
        kmetaKnowsLabelStyle.setShowIcon(false);
        knowsEdgeStyle.setCenterLabelStyleDescription(kmetaKnowsLabelStyle);
        kmetaKnowsEdge.setStyle(knowsEdgeStyle);
        kmetaDefaultLayer.getEdgeMappings().add(kmetaKnowsEdge);

        // KMETA TOOLS
        buildKMetaToolSections(kmetaDefaultLayer, eClassNode, attributeNode, kmetaHasEdge, kmetaKnowsEdge);

        ///////
        // !!! KModel object diagram
        ///////
        DiagramDescription kmodelDiagram = DescriptionFactory.eINSTANCE.createDiagramDescription();
        kmodelDiagram.setName("KModelObjectDiagram");
        kmodelDiagram.setLabel("KModel Object Diagram");
        kmodelDiagram.setDomainClass("ecore.EObject");
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
        eObjectNode.setStyle(objStyle);
        kmodelDefaultLayer.getContainerMappings().add(eObjectNode);

        // Slot Subnode Mapping: uses (view) context to resolve parent dynamic EObject
        NodeMapping slotNode = DescriptionFactory.eINSTANCE.createNodeMapping();
        slotNode.setName("EAttributeSlotNode");
        slotNode.setDomainClass("ecore.EAttribute");
        slotNode.setSemanticCandidatesExpression("aql:self.getPopulatedAttributes()");

        NodeStyleDescription slotStyle = StyleFactory.eINSTANCE.createSquareDescription();
        slotStyle.setLabelExpression("aql:self.getKModelSlotLabel(view)");
        slotStyle.setShowIcon(true);
        slotStyle.setLabelAlignment(LabelAlignment.LEFT);
        slotStyle.setLabelPosition(LabelPosition.NODE_LITERAL);
        slotStyle.setBorderSizeComputationExpression("0");
        slotStyle.setResizeKind(ResizeKind.NONE_LITERAL);
        slotNode.setStyle(slotStyle);
        eObjectNode.getSubNodeMappings().add(slotNode);

        // Edge has
        EdgeMapping instanceHasEdge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        instanceHasEdge.setName("InstanceContainmentEdge");
        instanceHasEdge.setUseDomainElement(false);
        instanceHasEdge.getSourceMapping().add(eObjectNode);
        instanceHasEdge.getTargetMapping().add(eObjectNode);
        instanceHasEdge.setTargetFinderExpression("aql:self.eContents()");

        EdgeStyleDescription instanceHasStyle = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        instanceHasStyle.setLineStyle(LineStyle.SOLID_LITERAL);
        instanceHasStyle.setSourceArrow(EdgeArrows.FILL_DIAMOND_LITERAL);
        instanceHasStyle.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        instanceHasStyle.setSizeComputationExpression("1");
        instanceHasEdge.setStyle(instanceHasStyle);
        kmodelDefaultLayer.getEdgeMappings().add(instanceHasEdge);

        // Edge knows
        EdgeMapping instanceKnowsEdge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        instanceKnowsEdge.setName("InstanceReferenceEdge");
        instanceKnowsEdge.setUseDomainElement(false);
        instanceKnowsEdge.getSourceMapping().add(eObjectNode);
        instanceKnowsEdge.getTargetMapping().add(eObjectNode);
        instanceKnowsEdge.setTargetFinderExpression("aql:self.eCrossReferences()");

        EdgeStyleDescription instanceKnowsStyle = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        instanceKnowsStyle.setLineStyle(LineStyle.DASH_LITERAL);
        instanceKnowsStyle.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        instanceKnowsStyle.setSizeComputationExpression("1");
        instanceKnowsEdge.setStyle(instanceKnowsStyle);
        kmodelDefaultLayer.getEdgeMappings().add(instanceKnowsEdge);

        // KModel TOOLS
        buildKModelToolSections(kmodelDefaultLayer, eObjectNode, slotNode, instanceHasEdge, instanceKnowsEdge);

        // Save as .odesign XMI
        ResourceSet resourceSet = new ResourceSetImpl();
        URI fileUri = URI.createFileURI(outputFile.getAbsolutePath());
        Resource resource = resourceSet.createResource(fileUri);
        resource.getContents().add(group);
        resource.save(Collections.emptyMap());

        KarpfenLog.info("Successfully generated .odesign at: " + outputFile.getAbsolutePath());
    }

    private static void applyDirectEditMask(DirectEditLabel directEditTool,
            org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf) {
        EditMaskVariables mask = vtf.createEditMaskVariables();
        mask.setMask("{0}");
        directEditTool.setMask(mask);
    }

    private static void buildKMetaToolSections(Layer defaultLayer,
            ContainerMapping eClassNode, NodeMapping attributeNode,
            EdgeMapping hasEdge, EdgeMapping knowsEdge) {

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

        relSection.getOwnedTools().add(createReferenceTool(hasEdge, "has (1:1 embedded)", "has_", true, 1));
        relSection.getOwnedTools()
                .add(createReferenceTool(hasEdge, "has (1:N embedded list)", "has_list_", true, -1));
        relSection.getOwnedTools().add(createReferenceTool(knowsEdge, "knows (1:1 link)", "knows_", false, 1));
        relSection.getOwnedTools()
                .add(createReferenceTool(knowsEdge, "knows (1:N link list)", "knows_list_", false, -1));
    }

    private static void buildKModelToolSections(Layer defaultLayer, ContainerMapping eObjectNode,
            NodeMapping slotNode, EdgeMapping instanceHasEdge, EdgeMapping instanceKnowsEdge) {
        org.eclipse.sirius.diagram.description.tool.ToolFactory dtf = org.eclipse.sirius.diagram.description.tool.ToolFactory.eINSTANCE;
        org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf = org.eclipse.sirius.viewpoint.description.tool.ToolFactory.eINSTANCE;

        // Direct Edit Section
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

        // Object Creation Palette Section
        ToolSection creationSection = dtf.createToolSection();
        creationSection.setName("ObjectsSection");
        creationSection.setLabel("Objects");
        defaultLayer.getToolSections().add(creationSection);

        ContainerCreationDescription createObjectTool = dtf.createContainerCreationDescription();
        createObjectTool.setName("CreateObjectTool");
        createObjectTool.setLabel("New Object Instance");
        createObjectTool.getContainerMappings().add(eObjectNode);

        InitialNodeCreationOperation createObjOp = vtf.createInitialNodeCreationOperation();
        ChangeContext createObjCtx = vtf.createChangeContext();
        createObjCtx.setBrowseExpression("aql:self.createNewKModelObject(container)");
        createObjOp.setFirstModelOperations(createObjCtx);
        createObjectTool.setInitialOperation(createObjOp);
        creationSection.getOwnedTools().add(createObjectTool);

        // Object Relationships Palette Section
        ToolSection linkSection = dtf.createToolSection();
        linkSection.setName("InstanceLinksSection");
        linkSection.setLabel("Links");
        defaultLayer.getToolSections().add(linkSection);

        EdgeCreationDescription createHasLinkTool = dtf.createEdgeCreationDescription();
        createHasLinkTool.setName("CreateHasLink");
        createHasLinkTool.setLabel("has (Containment Link)");
        createHasLinkTool.getEdgeMappings().add(instanceHasEdge);
        InitEdgeCreationOperation hasLinkOp = vtf.createInitEdgeCreationOperation();
        ChangeContext hasLinkCtx = vtf.createChangeContext();
        hasLinkCtx.setBrowseExpression("aql:source.createInstanceLink(target, true)");
        hasLinkOp.setFirstModelOperations(hasLinkCtx);
        createHasLinkTool.setInitialOperation(hasLinkOp);
        linkSection.getOwnedTools().add(createHasLinkTool);

        EdgeCreationDescription createKnowsLinkTool = dtf.createEdgeCreationDescription();
        createKnowsLinkTool.setName("CreateKnowsLink");
        createKnowsLinkTool.setLabel("knows (Reference Link)");
        createKnowsLinkTool.getEdgeMappings().add(instanceKnowsEdge);
        InitEdgeCreationOperation knowsLinkOp = vtf.createInitEdgeCreationOperation();
        ChangeContext knowsLinkCtx = vtf.createChangeContext();
        knowsLinkCtx.setBrowseExpression("aql:source.createInstanceLink(target, false)");
        knowsLinkOp.setFirstModelOperations(knowsLinkCtx);
        createKnowsLinkTool.setInitialOperation(knowsLinkOp);
        linkSection.getOwnedTools().add(createKnowsLinkTool);
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

    private static EdgeCreationDescription createReferenceTool(EdgeMapping edgeMapping, String label, String prefix,
            boolean isContainment, int upperBound) {
        org.eclipse.sirius.diagram.description.tool.ToolFactory dtf = org.eclipse.sirius.diagram.description.tool.ToolFactory.eINSTANCE;
        org.eclipse.sirius.viewpoint.description.tool.ToolFactory vtf = org.eclipse.sirius.viewpoint.description.tool.ToolFactory.eINSTANCE;

        EdgeCreationDescription tool = dtf.createEdgeCreationDescription();
        tool.setName(label.replace(" ", "").replace("(", "").replace(")", "").replace(":", "").replace("-",
                ""));
        tool.setLabel(label);
        tool.getEdgeMappings().add(edgeMapping);

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
