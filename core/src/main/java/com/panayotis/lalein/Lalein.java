package com.panayotis.lalein;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class Lalein {
    private static final Pattern tag = Pattern.compile("%\\{(\\w+)}");

    private final Map<String, Translation> registry;

    private Function<String, String> postProcessor = null;
    private PluralResolver pluralResolver = PluralResolvers.usingCurrentLocale();

    Lalein(Map<String, Translation> data) {
        this.registry = data;
    }

    public void setPostProcessor(Function<String, String> postProcessor) {
        this.postProcessor = postProcessor;
    }

    public void setPluralResolver(PluralResolver pluralResolver) {
        this.pluralResolver = pluralResolver == null ? PluralResolvers.usingCurrentLocale() : pluralResolver;
    }

    public String format(String handler, Object... args) {
        if (handler == null)
            return null;
        Translation translation = registry.get(handler);
        String format = translation == null ? handler
                : resolve(handler, translation, args);
        if (postProcessor != null)
            format = postProcessor.apply(format);
        return format == null ? null : String.format(format, args);
    }

    private String resolve(String handler, Translation translation, Object... args) {
        String format = translation.format;
        if (translation.parameters == null)
            return format;
        Matcher matcher = tag.matcher(format);
        while (matcher.find()) {
            String name = matcher.group(1);
            Parameter parameter = translation.parameters.get(name);
            if (parameter == null)
                throw new LaleinException("Unable to locate localization parameter '" + name + "' in '" + handler + "'");
            format = format.substring(0, matcher.start())
                    + parameter.resolve(pluralResolver, handler, name, args)
                    + format.substring(matcher.end());
            matcher = tag.matcher(format);
        }
        return format;
    }

    Iterable<Map.Entry<String, Translation>> entries() {
        return registry.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lalein)) return false;
        Lalein lalein = (Lalein) o;
        return registry.equals(lalein.registry);
    }

    @Override
    public int hashCode() {
        return registry.hashCode();
    }
}
