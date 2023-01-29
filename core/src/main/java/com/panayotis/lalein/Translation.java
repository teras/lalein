package com.panayotis.lalein;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

class Translation {
    final String format;
    final Map<String, Parameter> parameters;

    Translation(String format, Map<String, Parameter> parameters) {
        this.format = format;
        this.parameters = parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Translation)) return false;
        Translation that = (Translation) o;
        if (!(format.equals(that.format) && Objects.equals(parameters, that.parameters)))
            return false;
        return parameters == null
                || new ArrayList<>(parameters.keySet()).equals(new ArrayList<>(that.parameters.keySet()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, parameters);
    }
}
