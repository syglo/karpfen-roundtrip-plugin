package org.karpfen.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

import dsl.textual.KstatesDSLConverter;
import states.StateMachine;

public class KstatesResource extends ResourceImpl {

    private StateMachine parsedStateMachine;

    public KstatesResource(URI uri) {
        super(uri);
    }

    // T2D
    @Override
    protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        try {
            // parse kmeta text dsl to karpfen ast metamodel
            this.parsedStateMachine = KstatesDSLConverter.INSTANCE.parseKstatesString(content);

            EcoreFactory factory = EcoreFactory.eINSTANCE;
            String smName = getURI().trimFileExtension().lastSegment();
            if (smName == null || smName.isBlank()) {
                smName = "kstatesMachine";
            }

            EPackage statePackage = factory.createEPackage();
            statePackage.setName(smName);
            statePackage.setNsURI("http://github/karpfen/states/" + smName);
            statePackage.setNsPrefix(smName);

            // EClss
            EClass smClass = factory.createEClass();
            smClass.setName("StateMachine_" + this.parsedStateMachine.getAttachedToClass());

            statePackage.getEClassifiers().add(smClass);

            // TODO

            getContents().clear();
            getContents().add(statePackage);
        } catch (Exception e) {
            throw new IOException("[Karpfen] Failed to load .kstates resource from: " + getURI(), e);
        }
    }

    // D2T
    @Override
    protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
        throw new UnsupportedOperationException("D2T for .kmeta later");
    }

    public StateMachine getParsedSatteMachine() {
        return parsedStateMachine;
    }
}
