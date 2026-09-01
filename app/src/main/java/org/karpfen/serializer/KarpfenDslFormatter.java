package org.karpfen.serializer;

import kmeta.KmetaBaseVisitor;
import kmeta.KmetaLexer;
import kmeta.KmetaParser;
import kmodel.KmodelBaseVisitor;
import kmodel.KmodelLexer;
import kmodel.KmodelParser;
import kstates.KstatesBaseVisitor;
import kstates.KstatesLexer;
import kstates.KstatesParser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

// Its a Formatter based on ANTRL4 TokenStream rewriting .kmeta/.kmodel/.kstates text by reusing Karpfen ASTs.
// Applied at T2D and D2T stage universally in eclipse ide. To force formatting for roundtrip enginerring
public class KarpfenDslFormatter {

    // 4-spaces by default
    private static final String INDENT = "    ";

    public static String formatAuto(String rawText, String filename) {
        if (rawText == null || rawText.isBlank())
            return "";
        String lowerName = filename != null ? filename.toLowerCase() : "";

        if (lowerName.endsWith(".kmeta")) {
            return formatKMeta(rawText);
        } else if (lowerName.endsWith(".kmodel")) {
            return formatKModel(rawText);
        } else if (lowerName.endsWith(".kstates")) {
            return formatKStates(rawText);
        }
        return rawText;
    }

    // Formats .kmeta source code
    public static String formatKMeta(String rawKMeta) {
        if (rawKMeta == null || rawKMeta.isBlank())
            return "";

        try {
            KmetaLexer lexer = new KmetaLexer(CharStreams.fromString(rawKMeta));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            KmetaParser parser = new KmetaParser(tokens);

            KmetaParser.Kmeta_fileContext tree = parser.kmeta_file();
            if (tree == null || tree.type_definition() == null || tree.type_definition().isEmpty()) {
                return rawKMeta;
            }
            KmetaFormatterVisitor visitor = new KmetaFormatterVisitor();
            return visitor.visitKmeta_file(tree);
        } catch (Exception e) {
            // TODO: malformed text, throw warn error
            return rawKMeta;
        }
    }

    // Formats .kmodel source code
    public static String formatKModel(String rawKModel) {
        if (rawKModel == null || rawKModel.isBlank())
            return "";

        try {
            KmodelLexer lexer = new KmodelLexer(CharStreams.fromString(rawKModel));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            KmodelParser parser = new KmodelParser(tokens);

            KmodelParser.Kmodel_fileContext tree = parser.kmodel_file();
            if (tree == null || tree.make_object_block() == null) {
                return rawKModel;
            }
            KmodelFormatterVisitor visitor = new KmodelFormatterVisitor();
            return visitor.visitKmodel_file(tree);
        } catch (Exception e) {
            // TODO: malformed text, throw warn error
            return rawKModel;
        }
    }

    // Formats .kstates source code
    public static String formatKStates(String rawKStates) {
        if (rawKStates == null || rawKStates.isBlank())
            return "";

        try {
            KstatesLexer lexer = new KstatesLexer(CharStreams.fromString(rawKStates));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            KstatesParser parser = new KstatesParser(tokens);

            KstatesParser.Kstates_fileContext tree = parser.kstates_file();
            if (tree == null || tree.statemachine() == null) {
                return rawKStates;
            }
            KstatesFormatterVisitor visitor = new KstatesFormatterVisitor(tokens);
            return visitor.visitKstates_file(tree);
        } catch (Exception e) {
            return rawKStates;
        }
    }

    // !! KMeta Visitor

    private static class KmetaFormatterVisitor extends KmetaBaseVisitor<String> {

        @Override
        public String visitKmeta_file(KmetaParser.Kmeta_fileContext ctx) {
            StringBuilder sb = new StringBuilder();
            List<KmetaParser.Type_definitionContext> types = ctx.type_definition();

            for (int i = 0; i < types.size(); i++) {
                sb.append(visitType_definition(types.get(i)));
                if (i < types.size() - 1) {
                    sb.append("\n\n");
                }
            }
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public String visitType_definition(KmetaParser.Type_definitionContext ctx) {
            StringBuilder sb = new StringBuilder();
            String typeName = (ctx.STRING() != null && !ctx.STRING().isEmpty()) ? unquote(ctx.STRING(0).getText()) : "";
            String typeDoc = (ctx.STRING() != null && ctx.STRING().size() > 1) ? unquote(ctx.STRING(1).getText()) : "";

            sb.append(String.format("type \"%s\" \"%s\" {\n", typeName, typeDoc));

            if (ctx.rule_list() != null) {
                List<String> propLines = new ArrayList<>();
                List<String> hasLines = new ArrayList<>();
                List<String> knowsLines = new ArrayList<>();

                for (KmetaParser.RuleContext rule : ctx.rule_list().rule_()) {
                    if (rule.prop_rule() != null) {
                        if (rule.prop_rule().STRING() != null && rule.prop_rule().STRING().getText() != null) {
                            String key = unquote(rule.prop_rule().STRING().getText());
                            String val = formatRuleValue(rule.prop_rule().rule_value());
                            propLines.add(INDENT + String.format("prop(\"%s\", %s)", key, val));
                        }
                    } else if (rule.has_rule() != null) {
                        if (rule.has_rule().STRING() != null && rule.has_rule().STRING().getText() != null) {
                            String key = unquote(rule.has_rule().STRING().getText());
                            String val = formatRuleValue(rule.has_rule().rule_value());
                            hasLines.add(INDENT + String.format("has(\"%s\", %s)", key, val));
                        }
                    } else if (rule.knows_rule() != null) {
                        if (rule.knows_rule().STRING() != null && rule.knows_rule().STRING().getText() != null) {
                            String key = unquote(rule.knows_rule().STRING().getText());
                            String val = formatRuleValue(rule.knows_rule().rule_value());
                            knowsLines.add(INDENT + String.format("knows(\"%s\", %s)", key, val));
                        }
                    }
                }

                boolean needSectionBreak = false;

                if (!propLines.isEmpty()) {
                    sb.append(String.join("\n", propLines)).append("\n");
                    needSectionBreak = true;
                }
                if (!hasLines.isEmpty()) {
                    if (needSectionBreak)
                        sb.append("\n");
                    sb.append(String.join("\n", hasLines)).append("\n");
                    needSectionBreak = true;
                }
                if (!knowsLines.isEmpty()) {
                    if (needSectionBreak)
                        sb.append("\n");
                    sb.append(String.join("\n", knowsLines)).append("\n");
                }
            }

            sb.append("}");
            return sb.toString();
        }

        private String formatRuleValue(KmetaParser.Rule_valueContext ctx) {
            if (ctx == null)
                return "\"\"";
            if (ctx.LIST() != null && ctx.STRING() != null) {
                return String.format("list(\"%s\")", unquote(ctx.STRING().getText()));
            }
            if (ctx.STRING() != null) {
                return String.format("\"%s\"", unquote(ctx.STRING().getText()));
            }
            return "\"\"";
        }
    }

    // !! KModel Visitor

    private static class KmodelFormatterVisitor extends KmodelBaseVisitor<String> {

        @Override
        public String visitKmodel_file(KmodelParser.Kmodel_fileContext ctx) {
            if (ctx.make_object_block() == null)
                return "";
            return formatMakeObject(ctx.make_object_block(), 0).trim() + "\n";
        }

        private String formatMakeObject(KmodelParser.Make_object_blockContext ctx, int indentLevel) {
            if (ctx == null || ctx.object_signature() == null)
                return "";

            StringBuilder sb = new StringBuilder();
            String indent = INDENT.repeat(indentLevel);
            String bodyIndent = INDENT.repeat(indentLevel + 1);

            String key = unquote(ctx.object_signature().children.get(0).getText());
            String className = unquote(
                    ctx.object_signature().children.get(ctx.object_signature().children.size() - 1).getText());

            sb.append(String.format("make object \"%s\":\"%s\" {\n", key, className));

            if (ctx.statement() != null) {
                List<String> propLines = new ArrayList<>();
                List<String> hasBlocks = new ArrayList<>();
                List<String> knowsLines = new ArrayList<>();

                for (KmodelParser.StatementContext stmt : ctx.statement()) {
                    if (stmt.prop_statement() != null && stmt.prop_statement().STRING().size() >= 2) {
                        String propKey = unquote(stmt.prop_statement().STRING(0).getText());
                        String propVal = unquote(stmt.prop_statement().STRING(1).getText());
                        propLines.add(bodyIndent + String.format("prop(\"%s\") -> \"%s\"", propKey, propVal));
                    } else if (stmt.has_statement() != null && stmt.has_statement().STRING() != null
                            && stmt.has_statement().make_object_block() != null) {
                        String hasKey = unquote(stmt.has_statement().STRING().getText());
                        String childFormatted = formatMakeObject(stmt.has_statement().make_object_block(),
                                indentLevel + 1);
                        hasBlocks.add(bodyIndent + String.format("has(\"%s\") -> %s", hasKey, childFormatted.trim()));
                    } else if (stmt.knows_statement() != null && stmt.knows_statement().STRING().size() >= 2) {
                        String knowsKey = unquote(stmt.knows_statement().STRING(0).getText());
                        String targetId = unquote(stmt.knows_statement().STRING(1).getText());
                        knowsLines.add(bodyIndent + String.format("knows(\"%s\") -> \"%s\"", knowsKey, targetId));
                    }
                }

                // empty line after first brace (root object)
                if (indentLevel == 0 && (!propLines.isEmpty() || !hasBlocks.isEmpty())) {
                    sb.append("\n");
                }

                boolean needSectionBreak = false;

                // props
                if (!propLines.isEmpty()) {
                    sb.append(String.join("\n", propLines)).append("\n");
                    needSectionBreak = true;
                }

                // has
                if (!hasBlocks.isEmpty()) {
                    if (needSectionBreak)
                        sb.append("\n");
                    if (indentLevel == 0 || hasBlocks.size() > 1) {
                        sb.append(String.join("\n\n", hasBlocks)).append("\n");
                    } else {
                        sb.append(String.join("\n", hasBlocks)).append("\n");
                    }
                    needSectionBreak = true;
                }

                // knows
                if (!knowsLines.isEmpty()) {
                    if (needSectionBreak)
                        sb.append("\n");
                    sb.append(String.join("\n", knowsLines)).append("\n");
                }
            }

            sb.append(indent).append("}\n");
            return sb.toString();
        }
    }

    // !! KStates Visitor

    private static class KstatesFormatterVisitor extends KstatesBaseVisitor<String> {

        private final CommonTokenStream tokens;
        private final java.util.Set<Integer> emittedCommentIndices = new java.util.HashSet<>();

        public KstatesFormatterVisitor(CommonTokenStream tokens) {
            this.tokens = tokens;
        }

        private void emitCommentsBefore(StringBuilder sb, ParserRuleContext ctx, String indent) {
            if (ctx == null || ctx.getStart() == null)
                return;
            emitCommentsBeforeToken(sb, ctx.getStart().getTokenIndex(), indent);
        }

        private void emitCommentsBeforeToken(StringBuilder sb, int tokenIndex, String indent) {
            if (tokens == null || tokenIndex < 0)
                return;
            List<org.antlr.v4.runtime.Token> hidden = tokens.getHiddenTokensToLeft(tokenIndex);
            if (hidden != null) {
                for (org.antlr.v4.runtime.Token t : hidden) {
                    if (t.getType() == KstatesLexer.LINE_COMMENT && emittedCommentIndices.add(t.getTokenIndex())) {
                        String text = t.getText().trim();
                        sb.append(indent).append(text).append("\n");
                    }
                }
            }
        }

        private String getRawText(ParserRuleContext ctx) {
            if (ctx == null)
                return "";
            if (tokens != null && ctx.getSourceInterval() != null) {
                return tokens.getText(ctx.getSourceInterval());
            }
            return ctx.getText();
        }

        @Override
        public String visitKstates_file(KstatesParser.Kstates_fileContext ctx) {
            if (ctx == null || ctx.statemachine() == null)
                return "";
            String res = visitStatemachine(ctx.statemachine());
            if (ctx.getStop() != null) {
                StringBuilder trailing = new StringBuilder();
                emitCommentsBeforeToken(trailing, ctx.getStop().getTokenIndex(), "");
                if (trailing.length() > 0) {
                    res = res + trailing.toString();
                }
            }
            return res;
        }

        @Override
        public String visitStatemachine(KstatesParser.StatemachineContext ctx) {
            StringBuilder sb = new StringBuilder();
            emitCommentsBefore(sb, ctx, "");
            String attached = (ctx.STRING() != null) ? unquote(ctx.STRING().getText()) : "Object";
            sb.append(String.format("STATEMACHINE ATTACHED TO \"%s\" {\n", attached));

            // STATES block
            if (ctx.states_block() != null) {
                emitCommentsBefore(sb, ctx.states_block(), INDENT);
                sb.append(INDENT).append("STATES {\n");
                List<KstatesParser.State_definitionContext> states = ctx.states_block().state_definition();
                for (int i = 0; i < states.size(); i++) {
                    sb.append(formatState(states.get(i), 2));
                }
                sb.append(INDENT).append("}\n");
            }

            // TRANSITIONS block
            if (ctx.transitions_block() != null) {
                sb.append("\n");
                emitCommentsBefore(sb, ctx.transitions_block(), INDENT);
                sb.append(INDENT).append("TRANSITIONS {\n");
                List<KstatesParser.Transition_definitionContext> transitions = ctx.transitions_block()
                        .transition_definition();
                for (int i = 0; i < transitions.size(); i++) {
                    sb.append(formatTransition(transitions.get(i), 2));
                }
                sb.append(INDENT).append("}\n");
            }

            // MACROS block
            if (ctx.macros_block() != null && ctx.macros_block().macro_definition() != null
                    && !ctx.macros_block().macro_definition().isEmpty()) {
                sb.append("\n");
                emitCommentsBefore(sb, ctx.macros_block(), INDENT);
                sb.append(INDENT).append("MACROS {\n");
                List<KstatesParser.Macro_definitionContext> macros = ctx.macros_block().macro_definition();
                for (int i = 0; i < macros.size(); i++) {
                    sb.append(formatMacro(macros.get(i), 2));
                }
                sb.append(INDENT).append("}\n");
            }

            sb.append("}\n");
            return sb.toString();
        }

        private String formatState(KstatesParser.State_definitionContext ctx, int indentLevel) {
            if (ctx == null)
                return "";
            StringBuilder sb = new StringBuilder();
            String indent = INDENT.repeat(indentLevel);
            emitCommentsBefore(sb, ctx, indent);

            boolean isInit = ctx.INITIAL() != null;
            String name = (ctx.STRING() != null) ? unquote(ctx.STRING().getText()) : "State";
            sb.append(indent).append(isInit ? "INITIAL " : "").append("STATE \"").append(name).append("\" {\n");

            if (ctx.entry_block() != null && ctx.entry_block().action_block() != null) {
                emitCommentsBefore(sb, ctx.entry_block(), indent + INDENT);
                sb.append(indent).append(INDENT).append("ENTRY {\n");
                sb.append(formatActionBlock(ctx.entry_block().action_block(), indentLevel + 2));
                sb.append(indent).append(INDENT).append("}\n");
            }

            if (ctx.do_block() != null && ctx.do_block().action_block() != null) {
                emitCommentsBefore(sb, ctx.do_block(), indent + INDENT);
                sb.append(indent).append(INDENT).append("DO {\n");
                sb.append(formatActionBlock(ctx.do_block().action_block(), indentLevel + 2));
                sb.append(indent).append(INDENT).append("}\n");
            }

            if (ctx.state_definition() != null) {
                for (KstatesParser.State_definitionContext child : ctx.state_definition()) {
                    sb.append(formatState(child, indentLevel + 1));
                }
            }

            sb.append(indent).append("}\n");
            return sb.toString();
        }

        private String formatActionBlock(KstatesParser.Action_blockContext ctx, int indentLevel) {
            if (ctx == null || ctx.action_item() == null)
                return "";
            StringBuilder sb = new StringBuilder();
            for (KstatesParser.Action_itemContext item : ctx.action_item()) {
                sb.append(formatActionItem(item, indentLevel));
            }
            return sb.toString();
        }

        private String formatActionItem(KstatesParser.Action_itemContext item, int indentLevel) {
            if (item == null)
                return "";
            String indent = INDENT.repeat(indentLevel);
            StringBuilder sb = new StringBuilder();
            emitCommentsBefore(sb, item, indent);

            if (item.action_rule() != null) {
                KstatesParser.Action_ruleContext rule = item.action_rule();
                String op = (rule.action_operation() != null) ? rule.action_operation().getText() : "SET";
                List<TerminalNode> strTokens = rule.STRING();
                String right = (rule.action_right_side() != null) ? formatRightSide(rule.action_right_side()) : "";

                if (strTokens.size() >= 2) {
                    String left = unquote(strTokens.get(0).getText());
                    String second = unquote(strTokens.get(1).getText());
                    sb.append(indent).append(String.format("%s(\"%s\", \"%s\", %s)\n", op, left, second, right));
                } else if (!strTokens.isEmpty()) {
                    String left = unquote(strTokens.get(0).getText());
                    if (!right.isBlank()) {
                        sb.append(indent).append(String.format("%s(\"%s\", %s)\n", op, left, right));
                    } else {
                        sb.append(indent).append(String.format("%s(\"%s\")\n", op, left));
                    }
                }
            } else if (item.in_scope_block() != null) {
                KstatesParser.In_scope_blockContext inScope = item.in_scope_block();
                String paths = inScope.STRING().stream().map(s -> "\"" + unquote(s.getText()) + "\"")
                        .collect(Collectors.joining(", "));
                sb.append(indent).append("IN SCOPE (").append(paths).append(") {\n");
                if (inScope.action_block() != null) {
                    sb.append(formatActionBlock(inScope.action_block(), indentLevel + 1));
                }
                sb.append(indent).append("}\n");
            } else if (item.with_block() != null) {
                KstatesParser.With_blockContext with = item.with_block();
                String macro = (with.macro_call() != null) ? formatMacroCall(with.macro_call()) : "MACRO()";
                String alias = (with.STRING() != null) ? unquote(with.STRING().getText()) : "alias";
                sb.append(indent).append(String.format("WITH %s AS \"%s\" {\n", macro, alias));
                if (with.action_block() != null) {
                    sb.append(formatActionBlock(with.action_block(), indentLevel + 1));
                }
                sb.append(indent).append("}\n");
            }
            return sb.toString();
        }

        private String formatRightSide(KstatesParser.Action_right_sideContext ctx) {
            if (ctx == null)
                return "\"\"";
            if (ctx.macro_call() != null) {
                return formatMacroCall(ctx.macro_call());
            }
            if (ctx.eval_statement() != null) {
                return formatEvalStatement(ctx.eval_statement());
            }
            if (ctx.STRING() != null) {
                return String.format("\"%s\"", unquote(ctx.STRING().getText()));
            }
            return ctx.getText();
        }

        private String formatEvalStatement(KstatesParser.Eval_statementContext ctx) {
            if (ctx == null)
                return "";
            String code = "";
            if (ctx.eval_code_block() != null) {
                code = getRawText(ctx.eval_code_block()).trim();
            }
            return "EVAL { " + code + " }";
        }

        private String formatMacroCall(KstatesParser.Macro_callContext ctx) {
            if (ctx == null || ctx.STRING() == null || ctx.STRING().isEmpty())
                return "MACRO()";
            String macroName = unquote(ctx.STRING(0).getText());
            StringBuilder sb = new StringBuilder();
            sb.append("MACRO(\"").append(macroName).append("\"");
            if (ctx.STRING().size() > 1) {
                for (int i = 1; i < ctx.STRING().size(); i++) {
                    sb.append(", \"").append(unquote(ctx.STRING(i).getText())).append("\"");
                }
            }
            sb.append(")");
            return sb.toString();
        }

        private String formatTransition(KstatesParser.Transition_definitionContext ctx, int indentLevel) {
            if (ctx == null)
                return "";
            StringBuilder sb = new StringBuilder();
            String indent = INDENT.repeat(indentLevel);
            emitCommentsBefore(sb, ctx, indent);

            String transHead = "";
            boolean notLooping = false;
            KstatesParser.Condition_blockContext condBlock = null;

            if (ctx.normal_transition() != null) {
                KstatesParser.Normal_transitionContext nt = ctx.normal_transition();
                String src = (nt.STRING().size() > 0) ? unquote(nt.STRING(0).getText()) : "_";
                String tgt = (nt.STRING().size() > 1) ? unquote(nt.STRING(1).getText()) : "_";
                transHead = String.format("TRANSITION \"%s\" -> \"%s\"", src, tgt);
                notLooping = nt.not_looping() != null;
                condBlock = nt.condition_block();
            } else if (ctx.join_transition() != null) {
                KstatesParser.Join_transitionContext jt = ctx.join_transition();
                List<TerminalNode> strTokens = jt.STRING();
                if (strTokens.size() > 1) {
                    String sources = strTokens.subList(0, strTokens.size() - 1).stream()
                            .map(s -> "\"" + unquote(s.getText()) + "\"").collect(Collectors.joining(", "));
                    String tgt = unquote(strTokens.get(strTokens.size() - 1).getText());
                    transHead = String.format("JOIN TRANSITION %s -> \"%s\"", sources, tgt);
                } else {
                    transHead = "JOIN TRANSITION " + jt.getText();
                }
                notLooping = jt.not_looping() != null;
                condBlock = jt.condition_block();
            } else if (ctx.split_transition() != null) {
                KstatesParser.Split_transitionContext st = ctx.split_transition();
                List<TerminalNode> strTokens = st.STRING();
                if (strTokens.size() > 1) {
                    String src = unquote(strTokens.get(0).getText());
                    String targets = strTokens.subList(1, strTokens.size()).stream()
                            .map(s -> "\"" + unquote(s.getText()) + "\"").collect(Collectors.joining(", "));
                    transHead = String.format("SPLIT TRANSITION \"%s\" -> %s", src, targets);
                } else {
                    transHead = "SPLIT TRANSITION " + st.getText();
                }
                notLooping = st.not_looping() != null;
                condBlock = st.condition_block();
            }

            String loopStr = notLooping ? " NOT LOOPING" : "";

            if (condBlock == null || condBlock.condition_clause() == null || condBlock.condition_clause().isEmpty()) {
                sb.append(indent).append(transHead).append(loopStr).append(" { }\n");
            } else {
                sb.append(indent).append(transHead).append(loopStr).append(" {\n");
                emitCommentsBefore(sb, condBlock, indent + INDENT);
                sb.append(indent).append(INDENT).append("CONDITION {\n");
                for (KstatesParser.Condition_clauseContext clause : condBlock.condition_clause()) {
                    sb.append(formatConditionClause(clause, indentLevel + 2));
                }
                sb.append(indent).append(INDENT).append("}\n");
                sb.append(indent).append("}\n");
            }
            return sb.toString();
        }

        private String formatConditionClause(KstatesParser.Condition_clauseContext clause, int indentLevel) {
            if (clause == null)
                return "";
            String indent = INDENT.repeat(indentLevel);
            StringBuilder sb = new StringBuilder();
            emitCommentsBefore(sb, clause, indent);

            if (clause.eval_statement() != null) {
                String code = "";
                if (clause.eval_statement().eval_code_block() != null) {
                    code = getRawText(clause.eval_statement().eval_code_block()).trim();
                }
                sb.append(indent).append("EVAL { ").append(code).append(" }\n");
            } else if (clause.event_condition() != null) {
                KstatesParser.Event_conditionContext ec = clause.event_condition();
                String domain = (ec.STRING().size() > 0) ? unquote(ec.STRING(0).getText()) : "public";
                String val = (ec.STRING().size() > 1) ? unquote(ec.STRING(1).getText()) : "";
                sb.append(indent).append(String.format("EVENT(\"%s\", \"%s\")\n", domain, val));
            } else if (clause.value_condition() != null) {
                String boolVar = (clause.value_condition().STRING() != null)
                        ? unquote(clause.value_condition().STRING().getText())
                        : "true";
                sb.append(indent).append(String.format("VALUE(\"%s\")\n", boolVar));
            } else {
                sb.append(indent).append(clause.getText()).append("\n");
            }
            return sb.toString();
        }

        private String formatMacro(KstatesParser.Macro_definitionContext ctx, int indentLevel) {
            if (ctx == null)
                return "";
            StringBuilder sb = new StringBuilder();
            String indent = INDENT.repeat(indentLevel);
            emitCommentsBefore(sb, ctx, indent);
            String name = (ctx.STRING() != null) ? unquote(ctx.STRING().getText()) : "macro";

            sb.append(indent).append("MACRO \"").append(name).append("\" {\n");

            if (ctx.takes_directive() != null) {
                for (KstatesParser.Takes_directiveContext td : ctx.takes_directive()) {
                    emitCommentsBefore(sb, td, indent + INDENT);
                    String param = (td.STRING() != null) ? unquote(td.STRING().getText()) : "param";
                    String type = (td.type_expression() != null) ? td.type_expression().getText().trim() : "\"\"";
                    sb.append(indent).append(INDENT)
                            .append(String.format("TAKES(\"%s\", %s)\n", param, formatTypeExpression(type)));
                }
            }

            if (ctx.returns_directive() != null && ctx.returns_directive().return_expression() != null) {
                emitCommentsBefore(sb, ctx.returns_directive(), indent + INDENT);
                String ret = ctx.returns_directive().return_expression().getText().trim();
                sb.append(indent).append(INDENT).append(String.format("RETURNS(%s)\n", formatTypeExpression(ret)));
            }

            if (ctx.definition_block() != null && ctx.definition_block().eval_statement() != null
                    && ctx.definition_block().eval_statement().eval_code_block() != null) {
                emitCommentsBefore(sb, ctx.definition_block(), indent + INDENT);
                sb.append(indent).append(INDENT).append("DEFINITION {\n");
                sb.append(indent).append(INDENT).append(INDENT).append("EVAL {\n");

                String code = getRawText(ctx.definition_block().eval_statement().eval_code_block());
                String codeIndent = indent + INDENT.repeat(3);
                for (String line : code.split("\\R")) {
                    if (!line.isBlank()) {
                        sb.append(codeIndent).append(line.trim()).append("\n");
                    }
                }

                sb.append(indent).append(INDENT).append(INDENT).append("}\n");
                sb.append(indent).append(INDENT).append("}\n");
            }

            sb.append(indent).append("}\n");
            return sb.toString();
        }

        private String formatTypeExpression(String type) {
            if (type == null || type.isBlank())
                return "\"\"";
            String trimmed = type.trim();
            if (trimmed.startsWith("reference(") || trimmed.startsWith("list(")
                    || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
                return trimmed;
            }
            return String.format("\"%s\"", unquote(trimmed));
        }
    }

    // helpers

    private static String unquote(String text) {
        if (text == null)
            return "";
        return text.trim().replaceAll("^[\"']|[\"']$", "");
    }
}
