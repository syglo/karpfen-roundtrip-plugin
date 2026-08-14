package org.karpfen.design;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.sirius.diagram.description.ContainerMapping;
import org.eclipse.sirius.diagram.description.DiagramDescription;
import org.eclipse.sirius.diagram.description.style.FlatContainerStyleDescription;
import org.eclipse.sirius.diagram.description.style.StyleFactory;
import org.eclipse.sirius.diagram.description.DescriptionFactory;
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
        
        EPackage.Registry.INSTANCE.put(
            org.eclipse.sirius.viewpoint.description.DescriptionPackage.eNS_URI,
            org.eclipse.sirius.viewpoint.description.DescriptionPackage.eINSTANCE
        );
        EPackage.Registry.INSTANCE.put(ViewpointPackage.eNS_URI, ViewpointPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(
            org.eclipse.sirius.diagram.description.DescriptionPackage.eNS_URI,
            org.eclipse.sirius.diagram.description.DescriptionPackage.eINSTANCE
        );
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

        // KMeta class diagram
        DiagramDescription kmetaDiagram = DescriptionFactory.eINSTANCE.createDiagramDescription();
        kmetaDiagram.setName("KMetaClassDiagram");
        kmetaDiagram.setDomainClass("ecore.EPackage");
        viewpoint.getOwnedRepresentations().add(kmetaDiagram);

        // Node mapping for EClass
        ContainerMapping eClassNode = DescriptionFactory.eINSTANCE.createContainerMapping();
        eClassNode.setName("EClassNode");
        eClassNode.setDomainClass("ecore.EClass");
        eClassNode.setSemanticCandidatesExpression("aql:self.eClassifiers->filter(ecore::EClass)");

        FlatContainerStyleDescription style = StyleFactory.eINSTANCE.createFlatContainerStyleDescription();
        eClassNode.setStyle(style);

        kmetaDiagram.getContainerMappings().add(eClassNode);

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
