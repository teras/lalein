package com.panayotis.lalein;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test helper: dumps the FluentWriter output for our round-trip scenarios into
 * a directory readable by an external Fluent parser (Python / JS / Rust).
 * Not a JUnit test — invoke with the {@code main} method from a build script.
 *
 * Each sample writes two artefacts:
 *   <name>.ftl       — the FluentWriter output
 *   <name>.cases     — tab-separated "args\texpected" lines, one per assertion
 */
public final class EmitFluentSamples {

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args.length > 0 ? args[0] : "/tmp/lalein-fluent-verify");
        Files.createDirectories(outDir);

        emitCanonical(outDir);
        emitGender(outDir);
        emitFormalityEcho(outDir);
        emitNestedSelectInPlural(outDir);
        emitNonOtherDefault(outDir);
    }

    // --- Sample 1: canonical Localizable.ftl ----------------------------------

    private static void emitCanonical(Path dir) throws IOException {
        Lalein lalein = FluentLalein.fromResource("/Localizable.ftl");
        String ftl = FluentLalein.toString(lalein);
        writeFtl(dir, "canonical", ftl);
        try (PrintWriter cases = openCases(dir, "canonical")) {
            cases.println("peaches\t\tI have peaches.");
            cases.println("apples\tcount=0\tI don't have apples.");
            cases.println("apples\tcount=1\tI have an apple.");
            cases.println("apples\tcount=2\tI have two apples.");
            cases.println("apples\tcount=42\tI have 42 apples.");
            cases.println("baskets_with_oranges\tbaskets=0,oranges_zero_basket=0\tI don't have a basket or an orange.");
            cases.println("baskets_with_oranges\tbaskets=0,oranges_zero_basket=10\tI don't have a basket but I have 10 oranges.");
            cases.println("baskets_with_oranges\tbaskets=7,oranges=9\tI have 7 baskets with 9 oranges.");
        }
    }

    // --- Sample 2: gender select-mode -----------------------------------------

    private static void emitGender(Path dir) throws IOException {
        Map<String, String> custom = new LinkedHashMap<>();
        custom.put("female", "She liked your post");
        custom.put("male",   "He liked your post");
        Parameter gender = new Parameter(1, null, null, null, null, null,
                "They liked your post", custom);
        Map<String, Parameter> params = new LinkedHashMap<>();
        params.put("gender", gender);
        Map<String, Translation> ts = new LinkedHashMap<>();
        ts.put("liked_post", new Translation("%{gender}", params));
        Lalein lalein = new Lalein(ts);

        writeFtl(dir, "gender", FluentLalein.toString(lalein));
        try (PrintWriter cases = openCases(dir, "gender")) {
            cases.println("liked_post\tgender=female\tShe liked your post");
            cases.println("liked_post\tgender=male\tHe liked your post");
            cases.println("liked_post\tgender=other\tThey liked your post");
            cases.println("liked_post\tgender=unknown\tThey liked your post");
        }
    }

    // --- Sample 3: select-mode with selector echo inside variants ------------

    private static void emitFormalityEcho(Path dir) throws IOException {
        Map<String, String> custom = new LinkedHashMap<>();
        custom.put("formal",   "Good day, Mr. %1$s");
        custom.put("informal", "Hey %1$s");
        Parameter register = new Parameter(1, null, null, null, null, null,
                "Hello %1$s", custom);
        Map<String, Parameter> params = new LinkedHashMap<>();
        params.put("register", register);
        Map<String, Translation> ts = new LinkedHashMap<>();
        ts.put("greeting", new Translation("%{register}", params));
        Lalein lalein = new Lalein(ts);

        writeFtl(dir, "formality", FluentLalein.toString(lalein));
        try (PrintWriter cases = openCases(dir, "formality")) {
            cases.println("greeting\tregister=formal\tGood day, Mr. formal");
            cases.println("greeting\tregister=informal\tHey informal");
            cases.println("greeting\tregister=casual\tHello casual");
        }
    }

    // --- Sample 4: CLDR plural with gender nested in *[other] branch ----------

    private static void emitNestedSelectInPlural(Path dir) throws IOException {
        // Gender is nested inside count's *[other] branch. Args: count, gender.
        Map<String, String> genderCustom = new LinkedHashMap<>();
        genderCustom.put("female", "She sent you %1$d messages.");
        genderCustom.put("male",   "He sent you %1$d messages.");
        Parameter gender = new Parameter(2, null, null, null, null, null,
                "They sent you %1$d messages.", genderCustom);
        Parameter count = new Parameter(1,
                "No new messages.",   // zero
                "1 new message.",     // one
                null, null, null,
                "%{gender}", null);   // other → renders the gender select

        Map<String, Parameter> params = new LinkedHashMap<>();
        params.put("count", count);
        params.put("gender", gender);
        Map<String, Translation> ts = new LinkedHashMap<>();
        ts.put("messages", new Translation("%{count}", params));
        Lalein lalein = new Lalein(ts);

        writeFtl(dir, "nested", FluentLalein.toString(lalein));
        try (PrintWriter cases = openCases(dir, "nested")) {
            cases.println("messages\tcount=0,gender=female\tNo new messages.");
            cases.println("messages\tcount=1,gender=male\t1 new message.");
            cases.println("messages\tcount=5,gender=male\tHe sent you 5 messages.");
            cases.println("messages\tcount=12,gender=female\tShe sent you 12 messages.");
            cases.println("messages\tcount=7,gender=unknown\tThey sent you 7 messages.");
        }
    }

    // --- Sample 5: non-other default (writer normalises to *[other]) ----------

    private static void emitNonOtherDefault(Path dir) throws IOException {
        // Parse a Fluent file with *[civilian] default, then dump via writer.
        // The writer should re-emit using *[other] convention.
        String src = "salute = { $rank ->\n" +
                "   *[civilian] Hello\n" +
                "    [officer]  Salute\n" +
                "}\n";
        Lalein lalein = FluentLalein.fromString(src);
        writeFtl(dir, "salute", FluentLalein.toString(lalein));
        try (PrintWriter cases = openCases(dir, "salute")) {
            cases.println("salute\trank=officer\tSalute");
            cases.println("salute\trank=civilian\tHello");
            cases.println("salute\trank=unknown\tHello");
        }
    }

    // --- helpers --------------------------------------------------------------

    private static void writeFtl(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name + ".ftl"), content, StandardCharsets.UTF_8);
    }

    private static PrintWriter openCases(Path dir, String name) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(dir.resolve(name + ".cases"), StandardCharsets.UTF_8));
    }
}
