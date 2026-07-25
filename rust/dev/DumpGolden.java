import com.panayotis.lalein.PluralResolver;
import com.panayotis.lalein.PluralResolvers;
import com.panayotis.lalein.PluralType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates the golden plural-category corpus for the Rust port (and any
 * future port). For every language in the official cldr-plurals.json and
 * every probe value used by CldrPluralComplianceTest, it emits the category
 * produced by the Java reference implementation:
 *     lang;value;CATEGORY
 *
 * Build and run from the repository root:
 *   javac -cp core/target/classes -d /tmp/lalein-golden rust/dev/DumpGolden.java
 *   java  -cp core/target/classes:/tmp/lalein-golden DumpGolden
 *
 * The Java side is itself verified against the official CLDR data with zero
 * deviations, so parity with this corpus implies CLDR conformance.
 */
public class DumpGolden {
    public static void main(String[] args) throws Exception {
        List<String> langs = new ArrayList<>();
        Pattern blockStart = Pattern.compile("^\\s+\"([^\"]+)\":\\s*\\{\\s*$");
        Pattern baseLang = Pattern.compile("[a-z]{2,3}|pt-PT");
        for (String line : Files.readAllLines(Path.of("core/src/test/resources/cldr-plurals.json"))) {
            var m = blockStart.matcher(line);
            if (m.matches() && baseLang.matcher(m.group(1)).matches())
                langs.add(m.group(1));
        }

        StringBuilder out = new StringBuilder();
        for (String lang : langs) {
            PluralResolver r = "pt-PT".equals(lang)
                    ? PluralResolvers.usingLocale(new Locale("pt", "PT"))
                    : PluralResolvers.usingLanguage(lang);
            for (double v : VALUES) {
                PluralType t = r.findType(v);
                out.append(lang).append(';').append(fmt(v)).append(';')
                        .append(t == null ? "OTHER" : t.name()).append('\n');
            }
        }
        Path target = Path.of("corpus/cldr-golden.csv");
        Files.createDirectories(target.getParent());
        Files.writeString(target, out.toString());
        System.out.println("wrote " + target + " (" + langs.size() + " languages x "
                + VALUES.length + " values)");
    }

    private static String fmt(double v) {
        return v == (long) v ? Long.toString((long) v) : Double.toString(v);
    }

    /** Same probe values as CldrPluralComplianceTest.buildValues(). */
    private static final double[] VALUES = build();

    private static double[] build() {
        List<Double> list = new ArrayList<>();
        for (int n = 0; n <= 200; n++) list.add((double) n);
        list.addAll(Arrays.asList(
                1000.0, 10000.0, 100000.0, 1_000_000.0, 1_000_001.0, 2_000_000.0,
                1001.0, 1002.0, 1003.0, 1004.0,
                1011.0, 1012.0, 1013.0, 1014.0, 1019.0,
                1021.0, 1022.0, 1024.0, 1101.0, 1121.0,
                5000.0, 15000.0, 20000.0, 40000.0, 60000.0, 80000.0,
                1_100_000.0, 2_100_000.0,
                3_000_000.0,
                0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9,
                1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9,
                2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7,
                0.01, 0.02, 0.03, 0.04, 0.05, 0.11, 0.12, 0.13, 0.14, 0.15,
                0.21, 0.32, 0.91,
                1.01, 1.11, 1.21,
                10.1, 10.2, 100.1,
                0.001, 0.0001,
                -1.0, -5.0, -1.5, -21.0
        ));
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }
}
