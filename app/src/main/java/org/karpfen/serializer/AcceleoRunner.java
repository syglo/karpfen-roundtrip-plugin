package org.karpfen.serializer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.acceleo.query.ast.ASTNode;
import org.eclipse.acceleo.Module;
import org.eclipse.acceleo.ModuleElement;
import org.eclipse.acceleo.Template;
import org.eclipse.acceleo.aql.AcceleoUtil;
import org.eclipse.acceleo.aql.evaluation.AcceleoEvaluator;
import org.eclipse.acceleo.aql.evaluation.strategy.DefaultGenerationStrategy;
import org.eclipse.acceleo.aql.evaluation.strategy.DefaultWriterFactory;
import org.eclipse.acceleo.aql.evaluation.strategy.IAcceleoGenerationStrategy;
import org.eclipse.acceleo.aql.parser.AcceleoParser;
import org.eclipse.acceleo.aql.parser.ModuleLoader;
import org.eclipse.acceleo.aql.validation.AcceleoValidator;
import org.eclipse.acceleo.aql.validation.IAcceleoValidationResult;
import org.eclipse.acceleo.query.AQLUtils;
import org.eclipse.acceleo.query.runtime.IValidationMessage;
import org.eclipse.acceleo.query.runtime.ServiceUtils;
import org.eclipse.acceleo.query.runtime.ValidationMessageLevel;
import org.eclipse.acceleo.query.runtime.impl.namespace.ClassLoaderQualifiedNameResolver;
import org.eclipse.acceleo.query.runtime.impl.namespace.JavaLoader;
import org.eclipse.acceleo.query.runtime.namespace.IQualifiedNameQueryEnvironment;
import org.eclipse.acceleo.query.runtime.namespace.IQualifiedNameResolver;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Standalone Acceleo+EMF class for serializing KMeta/KModel/KStates EMF models
 * into karpfen {@code .kmeta}/{@code .kmodel}/{@code .kstates} DSL text using
 * Acceleo templates in {resources/templates}.
 */
public class AcceleoRunner {

    public String generateToString(String moduleQualifiedName, String templateName, String outputFilename,
            EObject modelRoot) {
        Path tempDir = null;
        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            tempDir = Files.createTempDirectory("acceleo_gen");
            URI targetURI = URI.createFileURI(tempDir.toFile().getAbsolutePath() + "/");

            // Standalone EMF environment
            ResourceSet resourceSetForModels = new ResourceSetImpl();
            resourceSetForModels.getResourceFactoryRegistry().getExtensionToFactoryMap().put(
                    Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());

            EPackage.Registry.INSTANCE.put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
            resourceSetForModels.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);

            if (modelRoot instanceof EPackage pkg) {
                EPackage.Registry.INSTANCE.put(pkg.getNsURI(), pkg);
                resourceSetForModels.getPackageRegistry().put(pkg.getNsURI(), pkg);
            } else if (modelRoot != null && modelRoot.eClass() != null && modelRoot.eClass().getEPackage() != null) {
                EPackage pkg = modelRoot.eClass().getEPackage();
                EPackage.Registry.INSTANCE.put(pkg.getNsURI(), pkg);
                resourceSetForModels.getPackageRegistry().put(pkg.getNsURI(), pkg);
            }

            // Acceleo envrionment setup
            IQualifiedNameResolver resolver = new ClassLoaderQualifiedNameResolver(
                    getClass().getClassLoader(),
                    EPackage.Registry.INSTANCE,
                    AcceleoParser.QUALIFIER_SEPARATOR);

            Map<String, String> options = new LinkedHashMap<>();
            options.put(AcceleoUtil.NEW_LINE_OPTION, "\n");

            IQualifiedNameQueryEnvironment queryEnvironment = AcceleoUtil.newAcceleoQueryEnvironment(
                    options, resolver, resourceSetForModels, false);
            queryEnvironment.registerEPackage(EcorePackage.eINSTANCE);

            if (modelRoot instanceof EPackage pkg) {
                queryEnvironment.registerEPackage(pkg);
            } else if (modelRoot != null && modelRoot.eClass() != null && modelRoot.eClass().getEPackage() != null) {
                queryEnvironment.registerEPackage(modelRoot.eClass().getEPackage());
            }

            // KMeta, KModel AQL services helpers
            ServiceUtils.registerServices(queryEnvironment,
                    ServiceUtils.getServices(queryEnvironment, AcceleoServices.class));

            // Acceleo evaluator
            AcceleoEvaluator evaluator = new AcceleoEvaluator(queryEnvironment.getLookupEngine(), "\n");
            resolver.addLoader(new ModuleLoader(new AcceleoParser(), evaluator));
            resolver.addLoader(new JavaLoader(AcceleoParser.QUALIFIER_SEPARATOR, false));

            IAcceleoGenerationStrategy strategy = new DefaultGenerationStrategy(
                    resourceSetForModels.getURIConverter(), new DefaultWriterFactory());

            // Module ast
            Object resolved = resolver.resolve(moduleQualifiedName);
            if (!(resolved instanceof Module module)) {
                throw new IllegalArgumentException("Could not resolve Acceleo module: " + moduleQualifiedName);
            }

            // Diagnostics, parsing errorrs
            if (module.getAst() != null && !module.getAst().getErrors().isEmpty()) {
                StringBuilder parseErrors = new StringBuilder(
                        "[Acceleo Parse Errors in " + moduleQualifiedName + "]:\n");
                for (ASTNode err : module.getAst().getErrors()) {
                    int start = module.getAst().getStartPosition(err);
                    int end = module.getAst().getEndPosition(err);
                    parseErrors.append(String.format(" - Syntax Error: %s (offset: %d..%d)\n",
                            err.getClass().getSimpleName(), start, end));
                }
                throw new RuntimeException(parseErrors.toString());
            }

            // Check for correctnes
            AcceleoValidator validator = new AcceleoValidator(queryEnvironment);
            IAcceleoValidationResult validationResult = validator.validate(module.getAst(), moduleQualifiedName);
            if (validationResult != null && !validationResult.getValidationMessages().isEmpty()) {
                boolean hasErrors = false;
                StringBuilder valErrors = new StringBuilder(
                        "[Acceleo Validation Messages for " + moduleQualifiedName + "]:\n");
                for (IValidationMessage msg : validationResult.getValidationMessages()) {
                    if (msg.getLevel() == ValidationMessageLevel.ERROR) {
                        hasErrors = true;
                        valErrors.append(String.format(" - [ERROR] %s (offset: %d..%d)\n", msg.getMessage(),
                                msg.getStartPosition(), msg.getEndPosition()));
                    }
                }
                if (hasErrors) {
                    throw new RuntimeException(valErrors.toString());
                }
            }

            Set<String> nsURIs = AQLUtils.getAllNeededEPackages(resolver, moduleQualifiedName);
            AQLUtils.registerEPackages(queryEnvironment, EPackage.Registry.INSTANCE, nsURIs);

            // Find .mtl templates for text generation
            List<Template> templates = new ArrayList<>();
            for (ModuleElement element : module.getModuleElements()) {
                if (element instanceof Template t) {
                    templates.add(t);
                }
            }

            if (templates.isEmpty()) {
                throw new IllegalStateException("No templates defined in module: " + moduleQualifiedName);
            }

            Template targetTemplate = templates.stream()
                    .filter(t -> t.getName() != null && t.getName().equalsIgnoreCase(templateName))
                    .findFirst()
                    .orElse(templates.get(0));

            // Bind arguments and generate text
            String parameterName = targetTemplate.getParameters().get(0).getName();
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put(parameterName, modelRoot);

            URI logURI = AcceleoUtil.getlogURI(targetURI, options.get(AcceleoUtil.LOG_URI_OPTION));
            AcceleoUtil.generate(
                    targetTemplate, variables, evaluator, queryEnvironment, strategy, targetURI, logURI,
                    new BasicMonitor());

            // Runtime errors
            Diagnostic diagnostic = evaluator.getGenerationResult().getDiagnostic();
            if (diagnostic != null && diagnostic.getSeverity() == Diagnostic.ERROR) {
                StringBuilder errorMsg = new StringBuilder();
                collectDiagnostics(diagnostic, errorMsg);
                if (!errorMsg.isEmpty()) {
                    throw new RuntimeException("[Acceleo Evaluation Errors]" + errorMsg);
                }
            }

            // Temp file
            File generatedFile = new File(tempDir.toFile(), outputFilename);
            if (!generatedFile.exists()) {
                File[] files = tempDir.toFile().listFiles((dir, name) -> !name.endsWith(".log"));
                if (files != null && files.length > 0) {
                    generatedFile = files[0];
                } else {
                    throw new IOException("Acceleo couldn't generate text files in dir: " + tempDir.toAbsolutePath());
                }
            }

            // Read generated acceleo and format it
            String rawGenerated = Files.readString(generatedFile.toPath(), StandardCharsets.UTF_8);
            if (outputFilename.endsWith(".kmeta")) {
                return KarpfenDslFormatter.formatKMeta(rawGenerated);
            } else if (outputFilename.endsWith(".kmodel")) {
                return KarpfenDslFormatter.formatKModel(rawGenerated);
            } else if (outputFilename.endsWith(".kstates")) {
                return KarpfenDslFormatter.formatKStates(rawGenerated);
            }
            // TODO: throw error, unknown file extension.
            return rawGenerated;
        } catch (Exception e) {
            throw new RuntimeException("[Karpfen] Acceleo generation error: " + e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
            if (tempDir != null) {
                deleteDirectory(tempDir.toFile());
            }
        }
    }

    private void collectDiagnostics(Diagnostic diagnostic, StringBuilder sb) {
        if (diagnostic.getSeverity() == Diagnostic.ERROR && diagnostic.getMessage() != null
                && !diagnostic.getMessage().isBlank()) {
            if (!"Acceleo parsing error see validation for more details".equals(diagnostic.getMessage())) {
                sb.append("\n - ").append(diagnostic.getMessage());
            }
        }
        for (Diagnostic child : diagnostic.getChildren()) {
            collectDiagnostics(child, sb);
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}