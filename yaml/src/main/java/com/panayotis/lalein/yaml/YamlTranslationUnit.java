package com.panayotis.lalein.yaml;

import com.amihaiemil.eoyaml.Node;
import com.amihaiemil.eoyaml.YamlMapping;
import com.amihaiemil.eoyaml.YamlNode;
import com.panayotis.lalein.LaleinException;
import com.panayotis.lalein.Parameter;
import com.panayotis.lalein.PluralType;
import com.panayotis.lalein.TranslationUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.panayotis.lalein.PluralType.*;

class YamlTranslationUnit implements TranslationUnit {
    private final String handler;
    private final String format;
    private final List<Parameter> parameters;


    YamlTranslationUnit(YamlNode handler, YamlNode data) {
        this.handler = asString(handler);
        if (data.type() == Node.SCALAR) {
            this.format = asString(data);
            this.parameters = null;
        } else {
            YamlMapping mData = data.asMapping();
            this.parameters = new ArrayList<>();
            if (allChildrenAreStrings(mData.asMapping())) {
                // Simple version: only one parameter
                this.format = "%{base}";
                addParam(mData, "base", 1);
            } else {
                // More complex version: parameters could be more than one
                List<YamlNode> params = new ArrayList<>(mData.keys());
                this.format = "%{" + asString(params.get(0)) + "}";
                int previousIndex = 0;
                for (YamlNode key : params) {
                    String name = asString(key);

                    int index;
                    String baseName;
                    if (name.startsWith("^")) {
                        baseName = name.substring(1);
                        index = previousIndex;
                    } else {
                        baseName = name;
                        index = ++previousIndex;
                    }
                    addParam(mData.yamlMapping(key), baseName, index);
                }
            }
        }
    }

    @Override
    public String getHandler() {
        return handler;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public Iterable<Parameter> getParameters() {
        return parameters;
    }

    private void addParam(YamlMapping value, String name, int index) {
        String invalid = PluralType.findInvalidKey(value.keys().stream().map(YamlTranslationUnit::asString).collect(Collectors.toList()));
        if (invalid != null)
            throw new IllegalArgumentException("Unknown tag " + invalid + " in parameter named " + name + " for handler '" + handler + "'");
        parameters.add(getParameter(value, name, index));
    }

    private Parameter getParameter(YamlMapping data, String name, int index) {
        return new Parameter(name, index,
                data.string(ZERO.tag),
                data.string(ONE.tag),
                data.string(TWO.tag),
                data.string(FEW.tag),
                data.string(MANY.tag),
                data.string(OTHER.tag));
    }

    private boolean allChildrenAreStrings(YamlMapping data) {
        for (YamlNode value : data.values())
            if (value.type() != Node.SCALAR)
                return false;
        return true;
    }

    private static String asString(YamlNode node) {
        if (node.type() != Node.SCALAR)
            throw new LaleinException("invalid yaml type " + node.type() + ": a scalar is expected pfr node " + node);
        return node.asScalar().value();
    }
}
