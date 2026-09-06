package org.karpfen.serializer;

import java.util.List;
import org.eclipse.emf.ecore.EObject;

/**
 * Manual programmatic Java serializer converting dynamic EMF behavioral state
 * machine graphs into {@code .kstates} DSL text.
 * Without indentation or any formatting, because it is post-processed through
 * ANTLR-based Karpfen DSL formatter.
 * 
 * Emits the {@code STATEMACHINE ATTACHED TO ...} container block,
 * {@code STATES} with nested entry/do actions
 * and composite inner states, {@code TRANSITIONS} with guard conditions and
 * loop directives, and {@code MACROS} declarations.
 */
public class EcoreToKStatesManualSerializer implements KStatesSerializer {

    /**
     * Serializes a dynamic EMF state machine {@link EObject} into Karpfen
     * {@code .kstates} DSL text.
     *
     * @param smObj the root dynamic state machine {@link EObject}
     * @return generated {@code .kstates} source text
     */
    @Override
    public String serialize(EObject smObj) {
        if (smObj == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String attachedClass = (String) smObj.eGet(smObj.eClass().getEStructuralFeature("attachedToClass"));
        if (attachedClass == null || attachedClass.isBlank()) {
            attachedClass = "Object";
        }

        sb.append(String.format("STATEMACHINE ATTACHED TO \"%s\" {\n", attachedClass));

        // STATES block
        sb.append("STATES {\n");
        @SuppressWarnings("unchecked")
        List<EObject> states = (List<EObject>) smObj.eGet(smObj.eClass().getEStructuralFeature("states"));
        if (states != null) {
            for (EObject state : states) {
                serializeState(state, sb);
            }
        }
        sb.append("}\n");

        // TRANSITIONS block
        sb.append("TRANSITIONS {\n");
        @SuppressWarnings("unchecked")
        List<EObject> transitions = (List<EObject>) smObj.eGet(smObj.eClass().getEStructuralFeature("transitions"));
        if (transitions != null) {
            for (EObject t : transitions) {
                serializeTransition(t, sb);
            }
        }
        sb.append("}\n");

        // MACROS block
        @SuppressWarnings("unchecked")
        List<String> macros = (List<String>) smObj.eGet(smObj.eClass().getEStructuralFeature("macros"));
        if (macros != null && !macros.isEmpty()) {
            sb.append("MACROS {\n");
            for (String macroBody : macros) {
                if (macroBody != null && !macroBody.isBlank()) {
                    sb.append(macroBody.trim()).append("\n");
                }
            }
            sb.append("}\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void serializeState(EObject state, StringBuilder sb) {
        Boolean isInit = (Boolean) state.eGet(state.eClass().getEStructuralFeature("isInitial"));
        String name = (String) state.eGet(state.eClass().getEStructuralFeature("name"));
        String initStr = (isInit != null && isInit) ? "INITIAL " : "";

        String entry = (String) state.eGet(state.eClass().getEStructuralFeature("entryAction"));
        String doAct = (String) state.eGet(state.eClass().getEStructuralFeature("doAction"));
        @SuppressWarnings("unchecked")
        List<EObject> inners = (List<EObject>) state.eGet(state.eClass().getEStructuralFeature("innerStates"));

        sb.append(String.format("%sSTATE \"%s\" {\n", initStr, name));

        if (entry != null && !entry.isBlank()) {
            sb.append("ENTRY {\n");
            sb.append(entry.trim()).append("\n");
            sb.append("}\n");
        }

        if (doAct != null && !doAct.isBlank()) {
            sb.append("DO {\n");
            sb.append(doAct.trim()).append("\n");
            sb.append("}\n");
        }

        if (inners != null && !inners.isEmpty()) {
            for (EObject child : inners) {
                serializeState(child, sb);
            }
        }

        sb.append("}\n");
    }

    private void serializeTransition(EObject t, StringBuilder sb) {
        EObject src = (EObject) t.eGet(t.eClass().getEStructuralFeature("sourceState"));
        EObject tgt = (EObject) t.eGet(t.eClass().getEStructuralFeature("targetState"));
        String srcName = (src != null) ? (String) src.eGet(src.eClass().getEStructuralFeature("name")) : "_";
        String tgtName = (tgt != null) ? (String) tgt.eGet(tgt.eClass().getEStructuralFeature("name")) : "_";
        Boolean notLooping = (Boolean) t.eGet(t.eClass().getEStructuralFeature("notLooping"));
        String cond = (String) t.eGet(t.eClass().getEStructuralFeature("condition"));

        String loopStr = (notLooping != null && notLooping) ? " NOT LOOPING" : "";

        if (cond == null || cond.isBlank()) {
            sb.append(String.format("TRANSITION \"%s\" -> \"%s\"%s { }\n", srcName, tgtName, loopStr));
        } else {
            sb.append(String.format("TRANSITION \"%s\" -> \"%s\"%s {\n", srcName, tgtName, loopStr));
            sb.append("CONDITION {\n");
            sb.append(cond.trim()).append("\n");
            sb.append("}\n");
            sb.append("}\n");
        }
    }
}
