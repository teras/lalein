package com.panayotis.lalein;

public class LaleinException extends RuntimeException {
    public LaleinException(String message) {
        this(message, null);
    }

    LaleinException(String inputType, String type, Throwable cause) {
        this("Unable to read " + inputType + " using " + type, cause);
    }

    public LaleinException(String message, Throwable cause) {
        super(message, cause);
    }
}
