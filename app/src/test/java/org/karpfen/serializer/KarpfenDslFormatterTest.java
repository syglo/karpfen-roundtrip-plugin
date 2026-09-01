package org.karpfen.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.karpfen.resource.KarpfenResourceInitializer;

import dsl.textual.KstatesDSLConverter;
import states.StateMachine;

public class KarpfenDslFormatterTest {

    private static File kmetaFile;
    private static File kmodelFile;
    private static File kstatesFile;

    @BeforeAll
    static void setUp() {
        KarpfenResourceInitializer.init();

        Path base = Path.of("").toAbsolutePath();
        kmetaFile = base.resolve("../example/statemachine_full_example/cleaning_robot.kmeta").normalize().toFile();
        kmodelFile = base.resolve("../example/statemachine_full_example/cleaning_robot.kmodel").normalize().toFile();
        kstatesFile = base.resolve("../example/statemachine_full_example/cleaning_robot.kstates").normalize().toFile();

        assertTrue(kmetaFile.exists(), "cleaning_robot.kmeta should exist: " + kmetaFile.getAbsolutePath());
        assertTrue(kmodelFile.exists(), "cleaning_robot.kmodel should exist: " + kmodelFile.getAbsolutePath());
        assertTrue(kstatesFile.exists(), "cleaning_robot.kstates should exist: " + kstatesFile.getAbsolutePath());
    }

    @Test
    void testFormatKStatesMessyInput() {
        String messyKStates = "STATEMACHINE ATTACHED TO \"Robot\"{\n"
                + "STATES{\n"
                + "INITIAL STATE \"idle\" { ENTRY { SET(\"speed\", \"0\") } }\n"
                + "STATE \"moving\" {\n"
                + "DO { SET(\"speed\", \"1.5\") }\n"
                + "STATE \"accelerating\" { ENTRY { SET(\"boost\", \"true\") } }\n"
                + "}\n"
                + "}\n"
                + "TRANSITIONS {\n"
                + "TRANSITION \"idle\" -> \"moving\" NOT LOOPING { CONDITION { VALUE(\"start\") } }\n"
                + "TRANSITION \"moving\" -> \"idle\" { }\n"
                + "}\n"
                + "MACROS {\n"
                + "MACRO \"calc_dist\" {\n"
                + "TAKES(\"p1\", \"Vector\")\n"
                + "RETURNS(\"number\")\n"
                + "DEFINITION { EVAL { return 42 } }\n"
                + "}\n"
                + "}\n"
                + "}";

        String formatted = KarpfenDslFormatter.formatKStates(messyKStates);
        assertNotNull(formatted);
        assertFalse(formatted.isBlank());

        // verify structural indentation
        assertTrue(formatted.contains("STATEMACHINE ATTACHED TO \"Robot\" {\n"));
        assertTrue(formatted.contains("    STATES {\n"));
        assertTrue(formatted.contains("        INITIAL STATE \"idle\" {\n"));
        assertTrue(formatted.contains("            ENTRY {\n"));
        assertTrue(formatted.contains("                SET(\"speed\", \"0\")\n"));
        assertTrue(formatted.contains("        STATE \"moving\" {\n"));
        assertTrue(formatted.contains("            STATE \"accelerating\" {\n"));
        assertTrue(formatted.contains("    TRANSITIONS {\n"));
        assertTrue(formatted.contains("        TRANSITION \"idle\" -> \"moving\" NOT LOOPING {\n"));
        assertTrue(formatted.contains("            CONDITION {\n"));
        assertTrue(formatted.contains("                VALUE(\"start\")\n"));
        assertTrue(formatted.contains("        TRANSITION \"moving\" -> \"idle\" { }\n"));
        assertTrue(formatted.contains("    MACROS {\n"));
        assertTrue(formatted.contains("        MACRO \"calc_dist\" {\n"));
        assertTrue(formatted.contains("            TAKES(\"p1\", \"Vector\")\n"));
        assertTrue(formatted.contains("            RETURNS(\"number\")\n"));

        // reparse to assert AST validity
        StateMachine sm = KstatesDSLConverter.INSTANCE.parseKstatesString(formatted);
        assertNotNull(sm);
        assertEquals("Robot", sm.getAttachedToClass());
        assertEquals(2, sm.getStates().size());
        assertEquals(2, sm.getTransitions().size());
        assertEquals(1, sm.getMacros().size());
    }

    @Test
    void testFormatKStatesASTParity() throws IOException {
        String originalText = Files.readString(kstatesFile.toPath(), StandardCharsets.UTF_8);
        StateMachine originalSm = KstatesDSLConverter.INSTANCE.parseKstatesString(originalText);

        String formatted = KarpfenDslFormatter.formatKStates(originalText);
        assertNotNull(formatted);
        assertFalse(formatted.isBlank());

        // write formatted output for inspection
        Path testOutputDir = Path.of("build/test-outputs");
        Files.createDirectories(testOutputDir);
        Files.writeString(testOutputDir.resolve("formatted_cleaning_robot.kstates"), formatted, StandardCharsets.UTF_8);

        // re-parse and verify identical AST metrics
        StateMachine reparsedSm = KstatesDSLConverter.INSTANCE.parseKstatesString(formatted);

        assertEquals(originalSm.getAttachedToClass(), reparsedSm.getAttachedToClass());
        assertEquals(originalSm.getStates().size(), reparsedSm.getStates().size());
        assertEquals(originalSm.getTransitions().size(), reparsedSm.getTransitions().size());
        assertEquals(originalSm.getMacros().size(), reparsedSm.getMacros().size());
    }

    @Test
    void testFormatKStatesCommentPreservation() {
        String inputWithComments = "// Top-level statemachine comment\n"
                + "STATEMACHINE ATTACHED TO \"Robot\" {\n"
                + "    // States section header\n"
                + "    STATES {\n"
                + "        // The initial idle state\n"
                + "        INITIAL STATE \"idle\" {\n"
                + "            // Entry action for idle\n"
                + "            ENTRY {\n"
                + "                // Reset speed\n"
                + "                SET(\"speed\", \"0\")\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "    // Transitions section header\n"
                + "    TRANSITIONS {\n"
                + "        // Transition to moving\n"
                + "        TRANSITION \"idle\" -> \"moving\" { }\n"
                + "    }\n"
                + "}\n";

        String formatted = KarpfenDslFormatter.formatKStates(inputWithComments);
        assertNotNull(formatted);

        // Verify comments exist with correct indentation
        assertTrue(formatted.contains("// Top-level statemachine comment\nSTATEMACHINE"));
        assertTrue(formatted.contains("    // States section header\n    STATES"));
        assertTrue(formatted.contains("        // The initial idle state\n        INITIAL STATE"));
        assertTrue(formatted.contains("            // Entry action for idle\n            ENTRY"));
        assertTrue(formatted.contains("                // Reset speed\n                SET(\"speed\", \"0\")"));
        assertTrue(formatted.contains("    // Transitions section header\n    TRANSITIONS"));
        assertTrue(formatted.contains("        // Transition to moving\n        TRANSITION"));
    }

    @Test
    void testFormatAuto() throws IOException {
        String kstatesText = Files.readString(kstatesFile.toPath(), StandardCharsets.UTF_8);
        String formatted = KarpfenDslFormatter.formatAuto(kstatesText, "test.kstates");
        assertTrue(formatted.startsWith("STATEMACHINE ATTACHED TO \"Robot\" {"));

        String kmetaText = Files.readString(kmetaFile.toPath(), StandardCharsets.UTF_8);
        String formattedMeta = KarpfenDslFormatter.formatAuto(kmetaText, "test.kmeta");
        assertTrue(formattedMeta.contains("type \"Robot\""));

        String kmodelText = Files.readString(kmodelFile.toPath(), StandardCharsets.UTF_8);
        String formattedModel = KarpfenDslFormatter.formatAuto(kmodelText, "test.kmodel");
        assertTrue(formattedModel.startsWith("make object \"APB 2101\":\"Room\" {"));
    }
}
