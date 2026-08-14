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
import org.eclipse.sirius.diagram.description.DiagramDescription;
import org.eclipse.sirius.diagram.description.EdgeMapping;
import org.eclipse.sirius.diagram.description.NodeMapping;
import org.eclipse.sirius.diagram.description.style.FlatContainerStyleDescription;
import org.eclipse.sirius.diagram.description.style.NodeStyleDescription;
import org.eclipse.sirius.diagram.description.style.EdgeStyleDescription;
import org.eclipse.sirius.diagram.description.style.StyleFactory;
import org.eclipse.sirius.diagram.description.style.StylePackage;
import org.eclipse.sirius.diagram.description.DescriptionFactory;
import org.eclipse.sirius.viewpoint.LabelAlignment;
import org.eclipse.sirius.viewpoint.ViewpointPackage;
import org.eclipse.sirius.viewpoint.description.Group;
import org.eclipse.sirius.viewpoint.description.Viewpoint;

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
            org.eclipse.sirius.viewpoint.description.DescriptionPackage.eINSTANCE
        );
        EPackage.Registry.INSTANCE.put(ViewpointPackage.eNS_URI, ViewpointPackage.eINSTANCE);

        // Sirius diagram and style
        EPackage.Registry.INSTANCE.put(
            org.eclipse.sirius.diagram.description.DescriptionPackage.eNS_URI,
            org.eclipse.sirius.diagram.description.DescriptionPackage.eINSTANCE
        );
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
        attrStyle.setLabelExpression("aql:self.name + ' : ' + if self.eType <> null then self.eType.name else 'EString' endif");
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
        hasEdge.setSemanticCandidatesExpression("aql:self.eClassifiers->filter(ecore::EClass).eStructuralFeatures->filter(ecore::EReference)->select(r | r.containment)");
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
        knowsEdge.setSemanticCandidatesExpression("aql:self.eClassifiers->filter(ecore::EClass).eStructuralFeatures->filter(ecore::EReference)->select(r | not r.containment)");
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


        ///////
        // !!! KModel object diagram
        ///////
        DiagramDescription kmodelDiagram = DescriptionFactory.eINSTANCE.createDiagramDescription();
        kmodelDiagram.setName("KModelObjectDiagram");
        kmodelDiagram.setLabel("KModel Object Diagram");
        kmodelDiagram.setDomainClass("ecore.EObject");
        kmodelDiagram.getMetamodel().add(EcorePackage.eINSTANCE);
        viewpoint.getOwnedRepresentations().add(kmodelDiagram);

        // Object container node
        ContainerMapping eObjectNode = DescriptionFactory.eINSTANCE.createContainerMapping();
        eObjectNode.setName("EObjectNode");
        eObjectNode.setDomainClass("ecore.EObject");
        eObjectNode.setSemanticCandidatesExpression("aql:self.eAllContents(ecore::EObject)->including(self)");

        FlatContainerStyleDescription objStyle = StyleFactory.eINSTANCE.createFlatContainerStyleDescription();

        //objStyle.setLabelExpression("aql:self.eClass().name + if self.eClass().getEStructuralFeature('name') <> null and selfaql:self.eClass().name + if self.eClass().getEStructuralFeature('name') <> null and self.eGet(self.eClass().getEStructuralFeature('name')) <> null then ' : ' + self.eGet(self.eClass().getEStructuralFeature('name')).toString() else '' endif");
        // AQL Expression: "turtle : Robot" or "APB 2101 : Room"
        objStyle.setLabelExpression("aql:if self.eClass().getEStructuralFeature('__id__') <> null and self.eGet(self.eClass().getEStructuralFeature('__id__')) <> '' then self.eGet(self.eClass().getEStructuralFeature('__id__')).toString() + ' : ' + self.eClass().name else self.eClass().name endif");

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
