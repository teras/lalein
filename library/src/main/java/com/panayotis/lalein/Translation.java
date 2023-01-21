package com.panayotis.lalein;

import java.util.Map;

class Translation {
    final String format;
    final Map<String, Parameter> parameters;

    Translation(String format, Map<String, Parameter> parameters) {
        this.format = format;
        this.parameters = parameters;
    }
}
