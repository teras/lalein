package com.panayotis.lalein;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend for Mozilla's Fluent (.ftl) translation format.
 *
 * <p>Supports a pragmatic subset:
 * <ul>
 *   <li>simple messages (<code>name = value</code>)</li>
 *   <li>select expressions over a single variable (<code>{ $v -&gt; [k] v *[d] v }</code>)</li>
 *   <li>arbitrarily nested select expressions for multi-parameter plurals</li>
 *   <li>standard CLDR plural variants: <code>zero</code>, <code>one</code>, <code>two</code>,
 *       <code>few</code>, <code>many</code>, <code>other</code></li>
 *   <li>line comments (<code># ...</code>)</li>
 * </ul>
 *
 * <p>Each unique Fluent variable referenced inside a message becomes a
 * {@link Parameter} in the resulting {@link Lalein}, with its argument index
 * assigned by order of first appearance.
 */
@SuppressWarnings("unused")
public class FluentLalein {

    // === Entry points (read) ===

    public static Lalein fromResource(String resource) {
        try (InputStream is = FluentLalein.class.getResourceAsStream(resource)) {
            if (is == null)
                throw new LaleinException("Fluent resource not found: " + resource);
            return fromString(drain(new InputStreamReader(is, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new LaleinException("Fluent", "resource " + resource, e);
        }
    }

    public static Lalein fromString(String data) {
        return new FluentParser(data).parse();
    }

    public static Lalein fromFile(File data) {
        try (Reader r = new InputStreamReader(Files.newInputStream(data.toPath()), StandardCharsets.UTF_8)) {
            return fromString(drain(r));
        } catch (IOException e) {
            throw new LaleinException("Fluent", "file " + data.getAbsolutePath(), e);
        }
    }

    public static Lalein fromStream(InputStream data) {
        try (Reader r = new InputStreamReader(data, StandardCharsets.UTF_8)) {
            return fromString(drain(r));
        } catch (IOException e) {
            throw new LaleinException("Fluent", data.getClass().getName(), e);
        }
    }

    public static Lalein fromReader(Reader data) {
        try {
            return fromString(drain(data));
        } catch (IOException e) {
            throw new LaleinException("Fluent", data.getClass().getName(), e);
        }
    }

    // === Entry points (write) ===

    public static String toString(Lalein lalein) {
        return new FluentWriter().write(lalein);
    }

    // === Helpers ===

    private static String drain(Reader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        return sb.toString();
    }
}
