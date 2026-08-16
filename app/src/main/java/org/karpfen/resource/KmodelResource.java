package org.karpfen.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.karpfen.transformer.KMetaToEcoreTransformer;
import org.karpfen.transformer.KModelToEcoreInstanceTransformer;

import dsl.textual.KmetaDSLConverter;
import dsl.textual.KmodelDSLConverter;
import instance.Model;
import meta.Metamodel;

public class KmodelResource extends ResourceImpl {

    private Model parsedModel;

    public KmodelResource(URI uri) {
        super(uri);
    }

    // T2D
    @Override
    protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        try {
            // find metamodel .kmeta for .kmodel
            MetamodelResolution resolution = resolveMetamodel(options);
            if (resolution.metamodel == null || resolution.ePackage == null) {
                throw new IOException("Could not resolve Metamodel for: " + getURI());
            }

            // parse kmeta text dsl to karpfen ast metamodel
            this.parsedModel = KmodelDSLConverter.INSTANCE.parseKmodelString(content, resolution.metamodel);

            // transform karpfen ast to EMF ecore
            KModelToEcoreInstanceTransformer transformer = new KModelToEcoreInstanceTransformer();
            List<EObject> rootObjects = transformer.transform(this.parsedModel, resolution.ePackage);

            // from super class
            getContents().clear();
            getContents().addAll(rootObjects);
        } catch (Exception e) {
            throw new IOException("[Karpfen] Failed to load .kmodel resource from: " + getURI(), e);
        }
    }

    private record MetamodelResolution(Metamodel metamodel, EPackage ePackage) {}

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

        // Try search inside resourceset
        ResourceSet rs = getResourceSet();
        if (rs != null) {
            // matching names cleaning_robot.kmeta - cleaning_robot.kmodel
            for (Resource res : rs.getResources()) {
                if (res instanceof KmetaResource kmRes && kmRes.getParsedMetamodel() != null) {
                    String kmBaseName = kmRes.getURI().trimFileExtension().lastSegment();
                    if (modelBaseName != null && modelBaseName.equalsIgnoreCase(kmBaseName)) {
                        if (!kmRes.getContents().isEmpty() && kmRes.getContents().get(0) instanceof EPackage pkg) {
                            return new MetamodelResolution(kmRes.getParsedMetamodel(), pkg);
                        }
                    }
                }
            }
            // any resource set
            for (Resource res : rs.getResources()) {
                if (res instanceof KmetaResource kmRes && kmRes.getParsedMetamodel() != null) {
                    if (!kmRes.getContents().isEmpty() && kmRes.getContents().get(0) instanceof EPackage pkg) {
                        return new MetamodelResolution(kmRes.getParsedMetamodel(), pkg);
                    }
                }
            }
        }

        // Try search in workspace of eclipse platform: uri
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
                // last strategy just find ifle
            }
        }

        return new MetamodelResolution(null, null);
    }

    private MetamodelResolution loadKmetaFile(IFile file) throws Exception {
        try (InputStream kmIn = file.getContents()) {
            String kmContent = new String(kmIn.readAllBytes(), StandardCharsets.UTF_8);
            Metamodel meta = KmetaDSLConverter.INSTANCE.parseKmetaString(kmContent, Collections.emptyList());
            String pkgName = file.getName().replace(".kmeta", "");
            EPackage pkg = new KMetaToEcoreTransformer().transform(
                meta, pkgName, "http://github/karpfen/" + pkgName, pkgName
            );
            return new MetamodelResolution(meta, pkg);
        }
    }

    // D2T
    @Override
    protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
        throw new UnsupportedOperationException("D2T for .kmodel later");
    }

    public Model getParsedModel() {
        return parsedModel;
    }
}
