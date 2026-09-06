package org.karpfen.transformer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;

import states.JoinTransition;
import states.Macro;
import states.State;
import states.StateMachine;
import states.Transition;
import states.TransitionLike;
import states.actions.ActionBlock;
import states.actions.ActionItem;
import states.actions.ActionOperationType;
import states.actions.ActionRightSide;
import states.actions.ActionRule;
import states.actions.EvalActionRightSide;
import states.actions.InScopeBlock;
import states.actions.MacroActionRightSide;
import states.actions.ValueActionRightSide;
import states.actions.WithBlock;
import states.conditions.CompositeCondition;
import states.conditions.Condition;
import states.conditions.EvalCondition;
import states.conditions.EventCondition;
import states.conditions.ValueCondition;
import states.macros.TakesDirective;

/**
 * Transforms a Karpfen behavioral State Machine AST ({@link StateMachine}) into
 * a dynamic EMF
 * behavioral statechart metamodel and instance graph.
 * 
 * Instantiates dynamic {@code StateMachine}, {@code State}, and
 * {@code Transition} {@link EObject}s,
 * maps entry/do actions and guard conditions, maintains composite state
 * nesting, and preserves
 * macro blocks across transformation boundaries.
 */
public class KStatesToEcoreTransformer {

    private final EcoreFactory factory = EcoreFactory.eINSTANCE;

    public static final String ID_FEATURE_NAME = "__id__";

    // Dynamic EPackage and EClass schema references
    private EPackage statePackage;
    private EClass stateMachineClass;
    private EClass stateClass;
    private EClass transitionClass;

    /**
     * Transforms a parsed Karpfen {@link StateMachine} AST into a dynamic EMF state
     * machine {@link EObject}.
     *
     * @param stateMachine the parsed Karpfen state machine AST
     * @param packageName  package and classifier namespace name
     * @return the root {@link EObject} representing the dynamic state machine
     */
    public EObject transform(StateMachine stateMachine, String packageName) {
        if (stateMachine == null) {
            return null;
        }

        // epackage schema
        initSchema(packageName);

        // root statemachine eobject
        EObject smObj = statePackage.getEFactoryInstance().create(stateMachineClass);
        String attachedClass = stateMachine.getAttachedToClass() != null ? stateMachine.getAttachedToClass() : "Object";
        smObj.eSet(stateMachineClass.getEStructuralFeature("attachedToClass"), attachedClass);
        smObj.eSet(stateMachineClass.getEStructuralFeature(ID_FEATURE_NAME), "StateMachine_" + attachedClass);

        Map<String, EObject> stateMap = new HashMap<>();

        // instantiate all states
        @SuppressWarnings("unchecked")
        List<EObject> statesList = (List<EObject>) smObj.eGet(stateMachineClass.getEStructuralFeature("states"));
        if (stateMachine.getStates() != null) {
            for (State s : stateMachine.getStates()) {
                EObject stateObj = transformState(s, stateMap);
                statesList.add(stateObj);
            }
        }

        // instantiate all transitions source -> target
        @SuppressWarnings("unchecked")
        List<EObject> transitionsList = (List<EObject>) smObj
                .eGet(stateMachineClass.getEStructuralFeature("transitions"));
        if (stateMachine.getTransitions() != null) {
            for (TransitionLike t : stateMachine.getTransitions()) {
                EObject transObj = transformTransition(t, stateMap);
                transitionsList.add(transObj);
            }
        }

        // just copy macro blocsk
        @SuppressWarnings("unchecked")
        List<String> macrosList = (List<String>) smObj.eGet(stateMachineClass.getEStructuralFeature("macros"));
        if (stateMachine.getMacros() != null) {
            for (Macro macro : stateMachine.getMacros()) {
                String macroText = formatMacro(macro);
                if (macroText != null && !macroText.isBlank()) {
                    macrosList.add(macroText);
                }
            }
        }

        return smObj;
    }

    private void initSchema(String packageName) {
        statePackage = factory.createEPackage();
        String name = (packageName != null && !packageName.isBlank()) ? packageName : "kstatesPackage";
        statePackage.setName(name);
        statePackage.setNsURI("http://github/karpfen/states/" + name);
        statePackage.setNsPrefix(name);

        // Statemachine eclass
        stateMachineClass = factory.createEClass();
        stateMachineClass.setName("StateMachine");
        stateMachineClass.getESuperTypes().add(EcorePackage.Literals.EOBJECT);

        EAttribute smId = factory.createEAttribute();
        smId.setName(ID_FEATURE_NAME);
        smId.setEType(EcorePackage.Literals.ESTRING);
        smId.setID(true);
        stateMachineClass.getEStructuralFeatures().add(smId);

        EAttribute attachedFeat = factory.createEAttribute();
        attachedFeat.setName("attachedToClass");
        attachedFeat.setEType(EcorePackage.Literals.ESTRING);
        stateMachineClass.getEStructuralFeatures().add(attachedFeat);

        EAttribute macrosFeat = factory.createEAttribute();
        macrosFeat.setName("macros");
        macrosFeat.setEType(EcorePackage.Literals.ESTRING);
        macrosFeat.setUpperBound(-1);
        stateMachineClass.getEStructuralFeatures().add(macrosFeat);

        // Each state eclass
        stateClass = factory.createEClass();
        stateClass.setName("State");
        stateClass.getESuperTypes().add(EcorePackage.Literals.EOBJECT);

        EAttribute stateName = factory.createEAttribute();
        stateName.setName("name");
        stateName.setEType(EcorePackage.Literals.ESTRING);
        stateName.setID(true);
        stateClass.getEStructuralFeatures().add(stateName);

        EAttribute stateInit = factory.createEAttribute();
        stateInit.setName("isInitial");
        stateInit.setEType(EcorePackage.Literals.EBOOLEAN);
        stateClass.getEStructuralFeatures().add(stateInit);

        EAttribute entryAction = factory.createEAttribute();
        entryAction.setName("entryAction");
        entryAction.setEType(EcorePackage.Literals.ESTRING);
        stateClass.getEStructuralFeatures().add(entryAction);

        EAttribute doAction = factory.createEAttribute();
        doAction.setName("doAction");
        doAction.setEType(EcorePackage.Literals.ESTRING);
        stateClass.getEStructuralFeatures().add(doAction);

        EReference innerStates = factory.createEReference();
        innerStates.setName("innerStates");
        innerStates.setEType(stateClass);
        innerStates.setContainment(true);
        innerStates.setUpperBound(-1);
        stateClass.getEStructuralFeatures().add(innerStates);

        // Transition eclass
        transitionClass = factory.createEClass();
        transitionClass.setName("Transition");
        transitionClass.getESuperTypes().add(EcorePackage.Literals.EOBJECT);

        EAttribute transName = factory.createEAttribute();
        transName.setName("name");
        transName.setEType(EcorePackage.Literals.ESTRING);
        transitionClass.getEStructuralFeatures().add(transName);

        EAttribute condFeat = factory.createEAttribute();
        condFeat.setName("condition");
        condFeat.setEType(EcorePackage.Literals.ESTRING);
        transitionClass.getEStructuralFeatures().add(condFeat);

        EAttribute notLoopingFeat = factory.createEAttribute();
        notLoopingFeat.setName("notLooping");
        notLoopingFeat.setEType(EcorePackage.Literals.EBOOLEAN);
        transitionClass.getEStructuralFeatures().add(notLoopingFeat);

        EReference srcRef = factory.createEReference();
        srcRef.setName("sourceState");
        srcRef.setEType(stateClass);
        srcRef.setContainment(false);
        srcRef.setUpperBound(1);
        transitionClass.getEStructuralFeatures().add(srcRef);

        EReference tgtRef = factory.createEReference();
        tgtRef.setName("targetState");
        tgtRef.setEType(stateClass);
        tgtRef.setContainment(false);
        tgtRef.setUpperBound(1);
        transitionClass.getEStructuralFeatures().add(tgtRef);

        // References in statemachine
        EReference statesRef = factory.createEReference();
        statesRef.setName("states");
        statesRef.setEType(stateClass);
        statesRef.setContainment(true);
        statesRef.setUpperBound(-1);
        stateMachineClass.getEStructuralFeatures().add(statesRef);

        EReference transRef = factory.createEReference();
        transRef.setName("transitions");
        transRef.setEType(transitionClass);
        transRef.setContainment(true);
        transRef.setUpperBound(-1);
        stateMachineClass.getEStructuralFeatures().add(transRef);

        statePackage.getEClassifiers().add(stateMachineClass);
        statePackage.getEClassifiers().add(stateClass);
        statePackage.getEClassifiers().add(transitionClass);
    }

    private EObject transformState(State s, Map<String, EObject> stateMap) {
        EObject stateObj = statePackage.getEFactoryInstance().create(stateClass);
        stateObj.eSet(stateClass.getEStructuralFeature("name"), s.getName());
        stateObj.eSet(stateClass.getEStructuralFeature("isInitial"), s.isInitial());

        String entryBody = formatActionBlock(s.getOnEntry());
        if (entryBody != null && !entryBody.isBlank()) {
            stateObj.eSet(stateClass.getEStructuralFeature("entryAction"), entryBody);
        }

        String doBody = formatActionBlock(s.getOnDo());
        if (doBody != null && !doBody.isBlank()) {
            stateObj.eSet(stateClass.getEStructuralFeature("doAction"), doBody);
        }

        stateMap.put(s.getName(), stateObj);

        // Substates recursive
        @SuppressWarnings("unchecked")
        List<EObject> innerList = (List<EObject>) stateObj.eGet(stateClass.getEStructuralFeature("innerStates"));
        if (s.getInnerStates() != null) {
            for (State child : s.getInnerStates()) {
                EObject childObj = transformState(child, stateMap);
                innerList.add(childObj);
            }
        }
        return stateObj;
    }

    private EObject transformTransition(TransitionLike t, Map<String, EObject> stateMap) {
        EObject transObj = statePackage.getEFactoryInstance().create(transitionClass);

        String fromName = "_";
        String toName = "_";

        if (t instanceof Transition trans) {
            fromName = trans.getFromState() != null ? trans.getFromState() : "_";
            toName = trans.getToState() != null ? trans.getToState() : "_";
        } else if (t instanceof JoinTransition jt) {
            fromName = jt.getFromStates() != null ? String.join(", ", jt.getFromStates()) : "_";
            toName = jt.getToState() != null ? jt.getToState() : "_";
        }

        transObj.eSet(transitionClass.getEStructuralFeature("name"), fromName + " -> " + toName);
        transObj.eSet(transitionClass.getEStructuralFeature("sourceState"), stateMap.get(fromName));
        transObj.eSet(transitionClass.getEStructuralFeature("targetState"), stateMap.get(toName));

        boolean notLooping = !t.getAllowLoops();
        transObj.eSet(transitionClass.getEStructuralFeature("notLooping"), notLooping);

        String conditionText = formatCondition(t.getCondition());
        if (conditionText != null && !conditionText.isBlank()) {
            transObj.eSet(transitionClass.getEStructuralFeature("condition"), conditionText);
        }

        return transObj;
    }

    /**
     * Formats an {@link ActionBlock} into a serialized Karpfen action statement
     * representation.
     *
     * @param block the action block AST
     * @return string representation of the action block or null if empty
     */
    public static String formatActionBlock(ActionBlock block) {
        if (block == null || block.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < block.getItems().size(); i++) {
            sb.append(formatActionItem(block.getItems().get(i)));
            if (i < block.getItems().size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String formatActionItem(ActionItem item) {
        if (item instanceof ActionRule rule) {
            if (rule.getOperationType() == ActionOperationType.EVENT) {
                String domain = unquote(rule.getLeftSide() != null ? rule.getLeftSide() : "public");
                String val = rule.getSecondSide() != null ? unquote(rule.getSecondSide())
                        : (rule.getRightSide() instanceof ValueActionRightSide v ? unquote(v.getValue()) : "");
                return String.format("EVENT(\"%s\", \"%s\")", domain, val);
            }
            String op = rule.getOperationType().name();
            String left = unquote(rule.getLeftSide() != null ? rule.getLeftSide() : "");
            if (rule.getSecondSide() != null) {
                return String.format("%s(\"%s\", \"%s\", %s)", op, left, unquote(rule.getSecondSide()),
                        formatRightSide(rule.getRightSide()));
            }
            return String.format("%s(\"%s\", %s)", op, left, formatRightSide(rule.getRightSide()));
        } else if (item instanceof InScopeBlock inScope) {
            StringBuilder sb = new StringBuilder();
            String paths = inScope.getPaths().stream().map(p -> "\"" + unquote(p) + "\"")
                    .collect(Collectors.joining(", "));
            sb.append("IN SCOPE (").append(paths).append(") {\n");
            String inner = formatActionBlock(inScope.getBody());
            if (inner != null && !inner.isBlank()) {
                sb.append(inner.trim()).append("\n");
            }
            sb.append("}");
            return sb.toString();
        } else if (item instanceof WithBlock with) {
            StringBuilder sb = new StringBuilder();
            sb.append("WITH ").append(formatRightSide(with.getMacro())).append(" AS \"").append(unquote(with.getName()))
                    .append("\" {\n");
            String inner = formatActionBlock(with.getBody());
            if (inner != null && !inner.isBlank()) {
                sb.append(inner.trim()).append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
        return item != null ? item.toString() : "";
    }

    private static String formatRightSide(ActionRightSide right) {
        if (right instanceof ValueActionRightSide val) {
            return String.format("\"%s\"", unquote(val.getValue()));
        } else if (right instanceof MacroActionRightSide mac) {
            StringBuilder sb = new StringBuilder();
            sb.append("MACRO(\"").append(unquote(mac.getMacroName())).append("\"");
            if (!mac.getArgs().isEmpty()) {
                sb.append(", ");
                sb.append(mac.getArgs().stream().map(a -> "\"" + unquote(a) + "\"").collect(Collectors.joining(", ")));
            }
            sb.append(")");
            return sb.toString();
        } else if (right instanceof EvalActionRightSide ev) {
            return "EVAL { " + ev.getCode().trim() + " }";
        }
        return right != null ? right.toString() : "\"\"";
    }

    /**
     * Formats a {@link Condition} AST into a serialized Karpfen guard expression.
     *
     * @param cond the condition AST
     * @return string representation of the condition expression, or null if empty
     */
    public static String formatCondition(Condition cond) {
        if (cond == null) {
            return null;
        }
        if (cond instanceof EvalCondition ev) {
            return "EVAL { " + ev.getCode().trim() + " }";
        } else if (cond instanceof EventCondition ev) {
            return String.format("EVENT(\"%s\", \"%s\")", unquote(ev.getEventDomain()), unquote(ev.getEventValue()));
        } else if (cond instanceof ValueCondition val) {
            return String.format("VALUE(\"%s\")", unquote(val.getBoolVariable()));
        } else if (cond instanceof CompositeCondition comp) {
            return comp.getClauses().stream().map(KStatesToEcoreTransformer::formatCondition)
                    .collect(Collectors.joining("\n"));
        }
        return cond.toString().trim();
    }

    /**
     * Formats a {@link Macro} AST into a canonical Karpfen macro block declaration.
     *
     * @param macro the macro definition AST
     * @return string representation of the macro block, or null if empty
     */
    public static String formatMacro(Macro macro) {
        if (macro == null)
            return null;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("MACRO \"%s\" {\n", unquote(macro.getName())));

        if (macro.getTakes() != null) {
            for (TakesDirective td : macro.getTakes()) {
                sb.append(String.format("TAKES(\"%s\", %s)\n", unquote(td.getParamName()),
                        formatTypeOrReference(td.getParamType())));
            }
        }

        if (macro.getReturns() != null) {
            sb.append(String.format("RETURNS(%s)\n", formatTypeOrReference(macro.getReturns().getReturnType())));
        }

        if (macro.getDefinition() != null && macro.getDefinition().getCodeBlock() != null) {
            sb.append("DEFINITION {\nEVAL {\n")
                    .append(macro.getDefinition().getCodeBlock().getCode().trim()).append("\n}\n}\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String formatTypeOrReference(String typeStr) {
        if (typeStr == null || typeStr.isBlank())
            return "\"\"";
        String trimmed = typeStr.trim();
        if (trimmed.startsWith("reference(") || trimmed.startsWith("list(")) {
            return trimmed;
        }
        return String.format("\"%s\"", unquote(trimmed));
    }

    private static String unquote(String text) {
        if (text == null)
            return "";
        return text.trim().replaceAll("^[\"']|[\"']$", "");
    }

    /**
     * Returns the dynamic {@link EPackage} schema generated for the state machine.
     *
     * @return the state machine's dynamic EMF package schema
     */
    public EPackage getStatePackage() {
        return statePackage;
    }
}
