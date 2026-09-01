package org.karpfen.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

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
                "odesign must contain kmeta, kmodel, and kstates viewpoints.");

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
        assertEquals("aql:self.isKModelRoot()", kmodelDiagram.getPreconditionExpression());
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

        // KStates
        DiagramDescription kstatesDiagram = (DiagramDescription) viewpoint.getOwnedRepresentations().stream()
                .filter(r -> r.getName().equals("KStatesDiagram")).findFirst().orElseThrow();
        assertEquals("ecore.EObject", kstatesDiagram.getDomainClass());
        assertEquals("aql:self.isKStatesRoot()", kstatesDiagram.getPreconditionExpression());
        assertNotNull(kstatesDiagram.getDefaultLayer());
        assertEquals(1, kstatesDiagram.getDefaultLayer().getContainerMappings().size());
        assertEquals(2, kstatesDiagram.getDefaultLayer().getToolSections().size());

        ContainerMapping stateNode = kstatesDiagram.getDefaultLayer().getContainerMappings().get(0);
        assertEquals("StateNode", stateNode.getName());
        assertEquals(
                "aql:self.eAllContents()->including(self)->filter(ecore::EObject)->select(o | o.eClass().name == 'State')",
                stateNode.getSemanticCandidatesExpression());
        assertEquals(ContainerLayout.LIST, stateNode.getChildrenPresentation());
        assertTrue(stateNode.getStyle() instanceof FlatContainerStyleDescription);
        FlatContainerStyleDescription stateStyle = (FlatContainerStyleDescription) stateNode.getStyle();
        assertEquals("18", stateStyle.getWidthComputationExpression());
        assertEquals("5", stateStyle.getHeightComputationExpression());

        assertEquals(2, stateNode.getSubNodeMappings().size(), "EntryActionNode and DoActionNode");
        NodeMapping entryNode = stateNode.getSubNodeMappings().get(0);
        assertEquals("EntryActionNode", entryNode.getName());
        assertEquals(
                "aql:if self.entryAction != null and self.entryAction != '' then Sequence{self} else Sequence{} endif",
                entryNode.getSemanticCandidatesExpression());
        assertEquals("ecore.EObject", entryNode.getDomainClass());

        NodeMapping doNode = stateNode.getSubNodeMappings().get(1);
        assertEquals("DoActionNode", doNode.getName());
        assertEquals("aql:if self.doAction != null and self.doAction != '' then Sequence{self} else Sequence{} endif",
                doNode.getSemanticCandidatesExpression());
        assertEquals("ecore.EObject", doNode.getDomainClass());

        // Additional Layers for KStates Straight / Manhattan
        assertEquals(2, kstatesDiagram.getAdditionalLayers().size());

        AdditionalLayer kstatesStraightLayer = kstatesDiagram.getAdditionalLayers().stream()
                .filter(l -> l.getName().equals("StraightRoutingLayer")).findFirst().orElseThrow();
        assertTrue(kstatesStraightLayer.isActiveByDefault());
        assertTrue(kstatesStraightLayer.isOptional());
        assertEquals(1, kstatesStraightLayer.getEdgeMappings().size());
        EdgeMapping transEdge = kstatesStraightLayer.getEdgeMappings().get(0);
        assertEquals(EdgeRouting.STRAIGHT_LITERAL, transEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, transEdge.getStyle().getEndsCentering());
        assertEquals(1, transEdge.getSourceMapping().size(), "Edge links StateNode");
        assertEquals(1, transEdge.getTargetMapping().size(), "Edge links StateNode");

        AdditionalLayer kstatesOrthoLayer = kstatesDiagram.getAdditionalLayers().stream()
                .filter(l -> l.getName().equals("OrthogonalRoutingLayer")).findFirst().orElseThrow();
        assertFalse(kstatesOrthoLayer.isActiveByDefault());
        assertTrue(kstatesOrthoLayer.isOptional());
        assertEquals(1, kstatesOrthoLayer.getEdgeMappings().size());
        EdgeMapping orthoTransEdge = kstatesOrthoLayer.getEdgeMappings().get(0);
        assertEquals(EdgeRouting.MANHATTAN_LITERAL, orthoTransEdge.getStyle().getRoutingStyle());
        assertEquals(CenteringStyle.NONE, orthoTransEdge.getStyle().getEndsCentering());
        assertEquals(1, orthoTransEdge.getSourceMapping().size());
        assertEquals(1, orthoTransEdge.getTargetMapping().size());
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
        assertEquals("[d_closest_obstacle < 0.2]", services.getTransitionLabel(t3));

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
        statesPkg.getEClassifiers().add(stateClass);

        EObject s1 = statesPkg.getEFactoryInstance().create(stateClass);
        s1.eSet(entryAttr, "EVENT(\"public\", \"start\")\nSET(\"speed\", 10.0)");
        s1.eSet(doAttr, "SET(\"d_closest_obstacle\", 0.0)");

        String entryLabel = services.getEntryLabel(s1);
        assertEquals("entry / EVENT(\"public\", \"start\")\nentry / SET(\"speed\", 10.0)", entryLabel);

        String doLabel = services.getDoLabel(s1);
        assertEquals("do / SET(\"d_closest_obstacle\", 0.0)", doLabel);
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
