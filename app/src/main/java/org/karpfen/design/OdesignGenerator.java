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
import org.eclipse.sirius.diagram.description.NodeMapping;
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
import org.eclipse.sirius.viewpoint.description.tool.InitEdgeCreationOperation;
import org.eclipse.sirius.viewpoint.description.tool.InitialNodeCreationOperation;
import org.eclipse.sirius.viewpoint.description.tool.InitialOperation;
import org.eclipse.sirius.viewpoint.description.tool.SetValue;

public class OdesignGenerator {

    public static final String RELATIVE_RESOURCE_PATH = "src/main/resources/description/karpfen.odesign";

    // Init EMF and Sirius dependencies to run them in runner class.
    public static void initStandalone() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("odesign", new XMIResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());

        // ECore deps
        EPackage.Registry.INSTANCE.put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);

        // Sirius viewpoint
        EPackage.Registry.INSTANCE.put(
                org.eclipse.sirius.viewpoint.description.DescriptionPackage.eNS_URI,
                org.eclipse.sirius.viewpoint.description.DescriptionPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(ViewpointPackage.eNS_URI, ViewpointPackage.eINSTANCE);

        // Sirius diagram and style
        EPackage.Registry.INSTANCE.put(
                org.eclipse.sirius.diagram.description.DescriptionPackage.eNS_URI,
                org.eclipse.sirius.diagram.description.DescriptionPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(DiagramPackage.eNS_URI, DiagramPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(StylePackage.eNS_URI, StylePackage.eINSTANCE);
    }

    // Save odesign in src/main/resources/ to be packaged in .jar
    public static File generateToResources() throws IOException {
        initStandalone();

        Path targetPath = Paths.get(RELATIVE_RESOURCE_PATH);
        if (!targetPath.toFile().exists() && Paths.get("app", RELATIVE_RESOURCE_PATH).getParent().toFile().exists()) {
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

        Viewpoint viewpoint = org.eclipse.sirius.viewpoint.description.DescriptionFactory.eINSTANCE.createViewpoint();
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
        kmetaDiagram.getMetamodel().add(EcorePackage.eINSTANCE); // aql requires it
        viewpoint.getOwnedRepresentations().add(kmetaDiagram);

        // Class container node - EClass
        ContainerMapping eClassNode = DescriptionFactory.eINSTANCE.createContainerMapping();
        eClassNode.setName("EClassNode");
        eClassNode.setDomainClass("ecore.EClass");
        eClassNode.setSemanticCandidatesExpression("aql:self.eClassifiers->filter(ecore::EClass)");
        eClassNode.setChildrenPresentation(ContainerLayout.LIST);

        FlatContainerStyleDescription classStyle = StyleFactory.eINSTANCE.createFlatContainerStyleDescription();
        classStyle.setLabelExpression("aql:self.name");
        classStyle.setShowIcon(true);
        classStyle.setBorderSizeComputationExpression("1");
        eClassNode.setStyle(classStyle);

        kmetaDiagram.getContainerMappings().add(eClassNode); // save

        // Class attributes subnodes - EAttribute in EClass
        NodeMapping attributeNode = DescriptionFactory.eINSTANCE.createNodeMapping();
        attributeNode.setName("EAttributeNode");
        attributeNode.setDomainClass("ecore.EAttribute");
        attributeNode.setSemanticCandidatesExpression("aql:self.eAttributes");

        NodeStyleDescription attrStyle = StyleFactory.eINSTANCE.createSquareDescription();
        attrStyle.setLabelExpression(
                "aql:self.name + ' : ' + if self.eType <> null then self.eType.name else 'EString' endif");
        attrStyle.setShowIcon(true);
        attrStyle.setLabelAlignment(LabelAlignment.LEFT);
        attrStyle.setLabelPosition(LabelPosition.NODE_LITERAL);
        attrStyle.setBorderSizeComputationExpression("0");
        attrStyle.setResizeKind(ResizeKind.NONE_LITERAL);
        attributeNode.setStyle(attrStyle);
        eClassNode.getSubNodeMappings().add(attributeNode);

        // Edge has - composition reference
        EdgeMapping hasEdge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        hasEdge.setName("HasCompositionEdge");
        hasEdge.setDomainClass("ecore.EReference");
        hasEdge.setUseDomainElement(true);
        hasEdge.setSemanticCandidatesExpression(
                "aql:self.eClassifiers->filter(ecore::EClass).eStructuralFeatures->filter(ecore::EReference)->select(r | r.containment)");
        hasEdge.getSourceMapping().add(eClassNode);
        hasEdge.getTargetMapping().add(eClassNode);
        hasEdge.setSourceFinderExpression("aql:self.eContainingClass");
        hasEdge.setTargetFinderExpression("aql:self.eType");

        EdgeStyleDescription hasEdgeStyle = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        hasEdgeStyle.setLineStyle(LineStyle.SOLID_LITERAL);
        hasEdgeStyle.setSourceArrow(EdgeArrows.FILL_DIAMOND_LITERAL);
        hasEdgeStyle.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        hasEdgeStyle.setSizeComputationExpression("1");
        hasEdge.setStyle(hasEdgeStyle);

        kmetaDiagram.getEdgeMappings().add(hasEdge); // save

        // Edge knows - association reference
        EdgeMapping knowsEdge = DescriptionFactory.eINSTANCE.createEdgeMapping();
        knowsEdge.setName("KnowsAssociationEdge");
        knowsEdge.setDomainClass("ecore.EReference");
        knowsEdge.setUseDomainElement(true);
        knowsEdge.setSemanticCandidatesExpression(
                "aql:self.eClassifiers->filter(ecore::EClass).eStructuralFeatures->filter(ecore::EReference)->select(r | not r.containment)");
        knowsEdge.getSourceMapping().add(eClassNode);
        knowsEdge.getTargetMapping().add(eClassNode);
        knowsEdge.setSourceFinderExpression("aql:self.eContainingClass");
        knowsEdge.setTargetFinderExpression("aql:self.eType");

        EdgeStyleDescription knowsEdgeStyle = StyleFactory.eINSTANCE.createEdgeStyleDescription();
        knowsEdgeStyle.setLineStyle(LineStyle.DASH_LITERAL);
        knowsEdgeStyle.setTargetArrow(EdgeArrows.INPUT_ARROW_LITERAL);
        knowsEdgeStyle.setSizeComputationExpression("1");
        knowsEdge.setStyle(knowsEdgeStyle);

        kmetaDiagram.getEdgeMappings().add(knowsEdge); // save

        // Custom tools for .kmeta in eclipse
        addKMetaModelingTools(kmetaDiagram, eClassNode, attributeNode, hasEdge, knowsEdge);

        ///////
        // !!! KModel object diagram
        ///////
        DiagramDescription kmodelDiagram = DescriptionFactory.eINSTANCE.createDiagramDescription();
        kmodelDiagram.setName("KModelObjectDiagram");
        kmodelDiagram.setLabel("KModel Object Diagram");
        kmodelDiagram.setDomainClass("ecore.EObject");
        kmodelDiagram.setPreconditionExpression("aql:self.eContainer() = null");
        kmodelDiagram.getMetamodel().add(EcorePackage.eINSTANCE);
        viewpoint.getOwnedRepresentations().add(kmodelDiagram);

        // Object container node
        ContainerMapping eObjectNode = DescriptionFactory.eINSTANCE.createContainerMapping();
        eObjectNode.setName("EObjectNode");
        eObjectNode.setDomainClass("ecore.EObject");
        eObjectNode.setSemanticCandidatesExpression("aql:self.eAllContents()->including(self)");
        eObjectNode.setChildrenPresentation(ContainerLayout.LIST);

        FlatContainerStyleDescription objStyle = StyleFactory.eINSTANCE.createFlatContainerStyleDescription();
        objStyle.setLabelExpression("aql:self.getObjectLabel()");
        objStyle.setLabelAlignment(LabelAlignment.LEFT);
        objStyle.setShowIcon(true);
        objStyle.setBorderSizeComputationExpression("1");
        eObjectNode.setStyle(objStyle);
        kmodelDiagram.getContainerMappings().add(eObjectNode);

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
        kmodelDiagram.getEdgeMappings().add(instanceHasEdge);

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
        kmodelDiagram.getEdgeMappings().add(instanceKnowsEdge);

        // Save as .odesign XMI
        ResourceSet resourceSet = new ResourceSetImpl();
        URI fileUri = URI.createFileURI(outputFile.getAbsolutePath());
        Resource resource = resourceSet.createResource(fileUri);
        resource.getContents().add(group);
        resource.save(Collections.emptyMap());

        System.out.println("Successfully generated .odesign at: " + outputFile.getAbsolutePath());
    }

    // https://wiki.eclipse.org/Sirius/Tutorials/AdvancedTutorial
    private static void addKMetaModelingTools(DiagramDescription kmetaDiagram,
            ContainerMapping eClassNode,
            NodeMapping attributeNode,
            EdgeMapping hasEdge,
            EdgeMapping knowsEdge) {

        // alias for different toolfactroies
        org.eclipse.sirius.diagram.description.tool.ToolFactory diagramToolFactory = org.eclipse.sirius.diagram.description.tool.ToolFactory.eINSTANCE;
        org.eclipse.sirius.viewpoint.description.tool.ToolFactory viewpointToolFactory = org.eclipse.sirius.viewpoint.description.tool.ToolFactory.eINSTANCE;

        // main section for tools/pallete group
        ToolSection toolSection = diagramToolFactory.createToolSection();
        toolSection.setName("KMetaModelingTools");
        toolSection.setLabel("KMeta Metamodel Entities");
        kmetaDiagram.setToolSection(toolSection);

        // Tool - create new type EClass
        ContainerCreationDescription createClassTool = diagramToolFactory.createContainerCreationDescription();
        createClassTool.setName("CreateClassTool");
        createClassTool.setLabel("New Type");
        createClassTool.getContainerMappings().add(eClassNode);

        InitialNodeCreationOperation classInitOp = viewpointToolFactory.createInitialNodeCreationOperation();
        ChangeContext classChangeContext = viewpointToolFactory.createChangeContext();
        classChangeContext.setBrowseExpression("aql:self");

        CreateInstance createEClass = viewpointToolFactory.createCreateInstance();
        createEClass.setTypeName("ecore.EClass");
        createEClass.setReferenceName("eClassifiers");
        createEClass.setVariableName("newClass");

        SetValue setClassName = viewpointToolFactory.createSetValue();
        setClassName.setFeatureName("name");
        setClassName.setValueExpression("aql:'NewType'");

        createEClass.getSubModelOperations().add(setClassName);
        classChangeContext.getSubModelOperations().add(createEClass);
        classInitOp.setFirstModelOperations(classChangeContext);
        createClassTool.setInitialOperation(classInitOp);
        toolSection.getOwnedTools().add(createClassTool);

        // Tool - create new property EAttr for EClass
        NodeCreationDescription createAttrTool = diagramToolFactory.createNodeCreationDescription();
        createAttrTool.setName("CreateAttributeTool");
        createAttrTool.setLabel("New Property (prop)");
        createAttrTool.getNodeMappings().add(attributeNode);

        InitialNodeCreationOperation attrInitOp = viewpointToolFactory.createInitialNodeCreationOperation();
        ChangeContext attrChangeContext = viewpointToolFactory.createChangeContext();
        attrChangeContext.setBrowseExpression("aql:container");

        CreateInstance createEAttribute = viewpointToolFactory.createCreateInstance();
        createEAttribute.setTypeName("ecore.EAttribute");
        createEAttribute.setReferenceName("eStructuralFeatures");
        createEAttribute.setVariableName("newAttr");

        SetValue setAttrName = viewpointToolFactory.createSetValue();
        setAttrName.setFeatureName("name");
        setAttrName.setValueExpression("aql:'newProp'");

        SetValue setAttrType = viewpointToolFactory.createSetValue();
        setAttrType.setFeatureName("eType");
        setAttrType.setValueExpression("aql:ecore::EString");

        createEAttribute.getSubModelOperations().add(setAttrName);
        createEAttribute.getSubModelOperations().add(setAttrType);
        attrChangeContext.getSubModelOperations().add(createEAttribute);
        attrInitOp.setFirstModelOperations(attrChangeContext);
        createAttrTool.setInitialOperation(attrInitOp);
        toolSection.getOwnedTools().add(createAttrTool);

        // Tool - direct edit visually name of class
        DirectEditLabel editClassNameTool = diagramToolFactory.createDirectEditLabel();
        editClassNameTool.setName("EditClassNameTool");

        InitialOperation editNameOp = viewpointToolFactory.createInitialOperation();
        SetValue applyNameVal = viewpointToolFactory.createSetValue();
        applyNameVal.setFeatureName("name");
        applyNameVal.setValueExpression("aql:arg0");
        editNameOp.setFirstModelOperations(applyNameVal);
        editClassNameTool.setInitialOperation(editNameOp);

        eClassNode.setLabelDirectEdit(editClassNameTool);
        toolSection.getOwnedTools().add(editClassNameTool);

        // Tool - direct edit visually attribute of class
        DirectEditLabel editAttrNameTool = diagramToolFactory.createDirectEditLabel();
        editAttrNameTool.setName("EditAttributeNameTool");

        InitialOperation editAttrOp = viewpointToolFactory.createInitialOperation();
        SetValue applyAttrNameVal = viewpointToolFactory.createSetValue();
        applyAttrNameVal.setFeatureName("name");
        applyAttrNameVal.setValueExpression("aql:arg0");
        editAttrOp.setFirstModelOperations(applyAttrNameVal);
        editAttrNameTool.setInitialOperation(editAttrOp);

        attributeNode.setLabelDirectEdit(editAttrNameTool);
        toolSection.getOwnedTools().add(editAttrNameTool);

        // Tool - create composition edge has
        EdgeCreationDescription createHasEdge = diagramToolFactory.createEdgeCreationDescription();
        createHasEdge.setName("CreateHasEdge");
        createHasEdge.setLabel("has (Composition)");
        createHasEdge.getEdgeMappings().add(hasEdge);

        InitEdgeCreationOperation hasEdgeOp = viewpointToolFactory.createInitEdgeCreationOperation();
        ChangeContext sourceContext = viewpointToolFactory.createChangeContext();
        sourceContext.setBrowseExpression("aql:source");

        CreateInstance createContainmentRef = viewpointToolFactory.createCreateInstance();
        createContainmentRef.setTypeName("ecore.EReference");
        createContainmentRef.setReferenceName("eStructuralFeatures");
        createContainmentRef.setVariableName("newRef");

        SetValue setRefName = viewpointToolFactory.createSetValue();
        setRefName.setFeatureName("name");
        setRefName.setValueExpression("aql:'has_' + target.name.toLowerFirst()");

        SetValue setRefType = viewpointToolFactory.createSetValue();
        setRefType.setFeatureName("eType");
        setRefType.setValueExpression("aql:target");

        SetValue setContainment = viewpointToolFactory.createSetValue();
        setContainment.setFeatureName("containment");
        setContainment.setValueExpression("aql:true");

        createContainmentRef.getSubModelOperations().add(setRefName);
        createContainmentRef.getSubModelOperations().add(setRefType);
        createContainmentRef.getSubModelOperations().add(setContainment);
        sourceContext.getSubModelOperations().add(createContainmentRef);
        hasEdgeOp.setFirstModelOperations(sourceContext);
        createHasEdge.setInitialOperation(hasEdgeOp);
        toolSection.getOwnedTools().add(createHasEdge);

        // Tool - create association edge knows
        EdgeCreationDescription createKnowsEdge = diagramToolFactory.createEdgeCreationDescription();
        createKnowsEdge.setName("CreateKnowsEdge");
        createKnowsEdge.setLabel("knows (Association)");
        createKnowsEdge.getEdgeMappings().add(knowsEdge);

        InitEdgeCreationOperation knowsEdgeOp = viewpointToolFactory.createInitEdgeCreationOperation();
        ChangeContext knowsSourceCtx = viewpointToolFactory.createChangeContext();
        knowsSourceCtx.setBrowseExpression("aql:source");

        CreateInstance createNonContainmentRef = viewpointToolFactory.createCreateInstance();
        createNonContainmentRef.setTypeName("ecore.EReference");
        createNonContainmentRef.setReferenceName("eStructuralFeatures");
        createNonContainmentRef.setVariableName("newKnowsRef");

        SetValue setKnowsName = viewpointToolFactory.createSetValue();
        setKnowsName.setFeatureName("name");
        setKnowsName.setValueExpression("aql:'knows_' + target.name.toLowerFirst()");

        SetValue setKnowsType = viewpointToolFactory.createSetValue();
        setKnowsType.setFeatureName("eType");
        setKnowsType.setValueExpression("aql:target");

        SetValue setNonContainment = viewpointToolFactory.createSetValue();
        setNonContainment.setFeatureName("containment");
        setNonContainment.setValueExpression("aql:false");

        createNonContainmentRef.getSubModelOperations().add(setKnowsName);
        createNonContainmentRef.getSubModelOperations().add(setKnowsType);
        createNonContainmentRef.getSubModelOperations().add(setNonContainment);
        knowsSourceCtx.getSubModelOperations().add(createNonContainmentRef);
        knowsEdgeOp.setFirstModelOperations(knowsSourceCtx);
        createKnowsEdge.setInitialOperation(knowsEdgeOp);
        toolSection.getOwnedTools().add(createKnowsEdge);
    }

    public static void main(String[] args) {
        try {
            File result = generateToResources();
            System.out.println("Generation completed: " + result.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to generate .odesign model: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
