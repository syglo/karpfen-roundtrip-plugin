package org.karpfen;

import dsl.functional.MetamodelBuilder;
import dsl.functional.ModelBuilder;
import meta.Metamodel;
import instance.Model;
import kmeta.KmetaParser;
import dsl.textual.KmetaDSLConverter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {

    @Test
    void testKMetaParsing() {
        String kmetaCode = """
                type "Robot" "A person entity" {
                    prop("name", "string")
                    prop("age", "number")
                }
                """;

        // Metamodel metamodel = KmetaDSLConverter.parseKmetaString(kmetaCode, null);
        // assertNotNull(metamodel, "Parsed metamodel should not be null");
        // System.out.println(metamodel.toString());

        // MetamodelBuilder builder = new MetamodelBuilder();
        // Metamodel metamodel = builder.derive()

        // MetamodelBuilder builder = new MetamodelBuilder();
        // Test parsing text into Metamodel AST
        // Metamodel metamodel = builder.string
    }
}
