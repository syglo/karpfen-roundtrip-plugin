package org.karpfen.serializer;

import kmeta.KmetaBaseVisitor;
import kmeta.KmetaLexer;
import kmeta.KmetaParser;
import kmodel.KmodelBaseVisitor;
import kmodel.KmodelLexer;
import kmodel.KmodelParser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

// Its a Formatter based on ANTRL4 TokenStream rewriting .kmeta/.kmodel text by reusing Karpfen ASTs.
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
        return rawKStates;
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

    // helpers

    private static String unquote(String text) {
        if (text == null)
            return "";
        return text.trim().replaceAll("^[\"']|[\"']$", "");
    }
}
