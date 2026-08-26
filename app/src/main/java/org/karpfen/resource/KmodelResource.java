package org.karpfen.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.karpfen.design.KarpfenLog;
import org.karpfen.serializer.AcceleoKModelSerializer;
import org.karpfen.serializer.EcoreToKModelManualSerializer;
import org.karpfen.serializer.KModelSerializer;
import org.karpfen.serializer.KarpfenDslFormatter;
import org.karpfen.serializer.SerializerMode;
import org.karpfen.transformer.KMetaToEcoreTransformer;
import org.karpfen.transformer.KModelToEcoreInstanceTransformer;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KmodelDSLConverter;
import instance.Model;
import meta.Metamodel;

public class KmodelResource extends ResourceImpl {

    public static SerializerMode ACTIVE_MODE = SerializerMode.ACCELEO_TEMPLATE;

    private Model parsedModel;
    private EPackage companionPackage;

    public KmodelResource(URI uri) {
        super(uri);
        KarpfenResourceInitializer.init();
    }

    @Override
    public EObject getEObject(String uriFragment) {
        if (uriFragment != null && !getContents().isEmpty()) {
            String targetId = uriFragment.startsWith("//") ? uriFragment.substring(2)
                    : (uriFragment.startsWith("/") ? uriFragment.substring(1) : uriFragment);

            TreeIterator<EObject> all = getAllContents();
            while (all.hasNext()) {
                EObject obj = all.next();
                if (obj.eClass() != null) {
                    EStructuralFeature idFeat = obj.eClass()
                            .getEStructuralFeature(KMetaToEcoreTransformer.ID_FEATURE_NAME);
                    if (idFeat != null && obj.eIsSet(idFeat)) {
                        Object val = obj.eGet(idFeat);
                        if (val != null && targetId.equals(val.toString().trim())) {
                            return obj;
                        }
                    }
                }
            }
        }
        return super.getEObject(uriFragment);
    }

    // T2D
    @Override
    protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        try {
            // Resolve metamodel only from companion file
            MetamodelResolution resolution = resolveMetamodel(options);
            if (resolution.metamodel == null || resolution.ePackage == null) {
                throw new IllegalArgumentException(
                        "Could not resolve corresponding .kmeta metamodel for: " + getURI().lastSegment());
            }

            this.companionPackage = resolution.ePackage;
            registerPackage(this.companionPackage);

            // Parse kmodel text DSL to Karpfen AST model
            this.parsedModel = KmodelDSLConverter.INSTANCE.parseKmodelString(content, resolution.metamodel);

            // transform Karpfen AST to EMF Ecore instance graph
            KModelToEcoreInstanceTransformer transformer = new KModelToEcoreInstanceTransformer();
            List<EObject> rootObjects = transformer.transform(this.parsedModel, this.companionPackage);

            // from super class
            getErrors().clear();
            getContents().clear();
            getContents().addAll(rootObjects);

            // clear problem markers
            KarpfenProblemMarkerManager.clearMarkers(getURI());

        } catch (Throwable t) {
            int line = KarpfenProblemMarkerManager.findOffendingLine(t, content);
            String message = KarpfenProblemMarkerManager.formatUserMessage(t);

            getErrors().clear();
            getErrors().add(new KarpfenDiagnostic(message, getURI().toString(), line, 0));
            KarpfenProblemMarkerManager.reportError(getURI(), content, t);

            KarpfenLog.warn("Validation error in .kmodel: " + message);
        }
    }

    private record MetamodelResolution(Metamodel metamodel, EPackage ePackage) {
    }

    private MetamodelResolution resolveMetamodel(Map<?, ?> options) throws IOException {
        // Try load from options map
        if (options != null) {
            Metamodel meta = (Metamodel) options.get("METAMODEL_AST");
            EPackage pkg = (EPackage) options.get("EPACKAGE");
            if (meta != null && pkg != null) {
                return new MetamodelResolution(meta, pkg);
            }
        }

        String modelBaseName = getURI().trimFileExtension().lastSegment();

        // Search Eclipse Workspace (platform:/resource/...) - Direct File
        if (getURI() != null && getURI().isPlatformResource()) {
            try {
                String platformPath = getURI().toPlatformString(true);
                IFile modelFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(platformPath));
                IContainer parent = modelFile.getParent();

                if (parent != null && parent.exists()) {
                    // Look for similar file name <name>.kmeta
                    IResource exactMatch = parent.findMember(modelBaseName + ".kmeta");
                    if (exactMatch instanceof IFile exactFile && exactFile.exists()) {
                        return loadKmetaFile(exactFile);
                    }

                    // search for any .kmeta in folder
                    for (IResource member : parent.members()) {
                        if (member instanceof IFile file && "kmeta".equalsIgnoreCase(file.getFileExtension())) {
                            return loadKmetaFile(file);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Search Local File System (file:/...) - Direct File
        if (getURI() != null && getURI().isFile()) {
            try {
                File modelFile = new File(getURI().toFileString());
                File parent = modelFile.getParentFile();
                if (parent != null && parent.exists()) {
                    File exactKmFile = new File(parent, modelBaseName + ".kmeta");
                    if (exactKmFile.exists()) {
                        return loadKmetaDiskFile(exactKmFile);
                    }
                    File[] files = parent.listFiles((dir, name) -> name.toLowerCase().endsWith(".kmeta"));
                    if (files != null && files.length > 0) {
                        return loadKmetaDiskFile(files[0]);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return new MetamodelResolution(null, null);
    }

    private MetamodelResolution loadKmetaFile(IFile file) throws Exception {
        try (InputStream kmIn = file.getContents(true)) {
            String kmContent = new String(kmIn.readAllBytes(), StandardCharsets.UTF_8);
            Metamodel meta = KmetaDSLConverter.INSTANCE.parseKmetaString(kmContent, Collections.emptyList());
            String pkgName = file.getName().replace(".kmeta", "");
            EPackage pkg = new KMetaToEcoreTransformer().transform(
                    meta, pkgName, "http://github/karpfen/" + pkgName, pkgName);

            // Valid HTTP URL scheme prevents MalformedURLException in EMF/Sirius resource
            // locators
            Resource syntheticRes = new ResourceImpl(
                    URI.createURI("http://github.com/karpfen/synthetic/" + pkgName + ".ecore"));
            syntheticRes.getContents().add(pkg);

            return new MetamodelResolution(meta, pkg);
        }
    }

    private MetamodelResolution loadKmetaDiskFile(File file) throws Exception {
        String kmContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Metamodel meta = KmetaDSLConverter.INSTANCE.parseKmetaString(kmContent, Collections.emptyList());
        String pkgName = file.getName().replace(".kmeta", "");
        EPackage pkg = new KMetaToEcoreTransformer().transform(
                meta, pkgName, "http://github/karpfen/" + pkgName, pkgName);

        // Valid HTTP URL scheme prevents MalformedURLException in EMF/Sirius resource
        // locators
        Resource syntheticRes = new ResourceImpl(
                URI.createURI("http://github.com/karpfen/synthetic/" + pkgName + ".ecore"));
        syntheticRes.getContents().add(pkg);

        return new MetamodelResolution(meta, pkg);
    }

    private void registerPackage(EPackage pkg) {
        if (pkg != null) {
            EPackage.Registry.INSTANCE.put(pkg.getNsURI(), pkg);
            if (getResourceSet() != null) {
                getResourceSet().getPackageRegistry().put(pkg.getNsURI(), pkg);
            }
        }
    }

    // D2T
    @Override
    protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
        if (!getContents().isEmpty() && getContents().get(0) instanceof EObject rootObj) {
            KModelSerializer serializer = (ACTIVE_MODE == SerializerMode.ACCELEO_TEMPLATE)
                    ? new AcceleoKModelSerializer()
                    : new EcoreToKModelManualSerializer();
            String generated = serializer.serialize(rootObj);
            String formatted = KarpfenDslFormatter.formatKModel(generated);
            outputStream.write(formatted.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            refreshWorkspaceAfterSave();
        }
    }

    private void refreshWorkspaceAfterSave() {
        if (getURI() != null && getURI().isPlatformResource()) {
            try {
                String platformPath = getURI().toPlatformString(true);
                IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(platformPath));
                if (file.exists() && file.getParent() != null) {
                    file.getParent().refreshLocal(IResource.DEPTH_ONE, null);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public Model getParsedModel() {
        return parsedModel;
    }

    public EPackage getCompanionPackage() {
        return companionPackage;
    }
}
