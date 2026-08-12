package org.karpfen;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.junit.jupiter.api.Test;

import dsl.functional.MetamodelBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SetupTest {

    @Test
    void testEmfInitialization() {
        EcoreFactory factory = EcoreFactory.eINSTANCE;
        EPackage testPackage = factory.createEPackage();
        testPackage.setName("kmeta");
        testPackage.setNsURI("http://org.karpfen/kmeta");

        assertNotNull(testPackage);
        System.out.println("EMF Standalone initialized successfully: " + testPackage.getName());
    }

    @Test
    public void testKarpfenJarIsAccessible() {
        // Instantiate a builder class provided by karpfen-dsl-tools.jar
        MetamodelBuilder builder = new MetamodelBuilder();
        
        // Assert that the builder object was successfully instantiated
        assertNotNull(builder, "It should not be null when initialized.");
        
        System.out.println("Success, karpfen-dsl-tools.jar is linked.");
    }
}