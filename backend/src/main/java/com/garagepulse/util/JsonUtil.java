package com.garagepulse.util;

/**
 * Minimal JSON string builder. Avoids pulling in Gson/Jackson for a
 * project this size — every servlet just calls these small helpers.
 */
public class JsonUtil {

    private JsonUtil() { }

    public static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "");
    }

    /** Wraps a raw value (already-valid JSON key:value pairs) in { }. */
    public static String object(String rawFields) {
        return "{" + rawFields + "}";
    }

    /** Joins already-built JSON objects into a [ ] array. */
    public static String array(java.util.List<String> jsonObjects) {
        return "[" + String.join(",", jsonObjects) + "]";
    }

    public static String field(String key, String value) {
        return "\"" + key + "\":\"" + escape(value) + "\"";
    }

    public static String field(String key, Number value) {
        return "\"" + key + "\":" + (value == null ? "null" : value.toString());
    }

    public static String field(String key, boolean value) {
        return "\"" + key + "\":" + value;
    }

    public static String successMessage(String message) {
        return object(field("success", true) + "," + field("message", message));
    }

    public static String errorMessage(String message) {
        return object(field("success", false) + "," + field("message", message));
    }
}
