package Util;

import java.util.ArrayList;
import java.util.List;

public final class JsonUtil {

    private JsonUtil() {}

    /* ==========================================================
                            WRITING
       ========================================================== */

    public static String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /* ==========================================================
                            READING
       ========================================================== */

    public static String extractString(String json, String field) {

        String search = "\"" + field + "\":\"";

        int start = json.indexOf(search);

        if (start == -1) {
            return "";
        }

        start += search.length();

        int end = json.indexOf("\"", start);

        return json.substring(start, end)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public static int extractInt(String json, String field) {

        String search = "\"" + field + "\":";

        int start = json.indexOf(search);

        if (start == -1) {
            return 0;
        }

        start += search.length();

        int end = json.indexOf(",", start);

        if (end == -1) {
            end = json.indexOf("}", start);
        }

        return Integer.parseInt(json.substring(start, end).trim());
    }

    public static long extractLong(String json, String field) {

        String search = "\"" + field + "\":";

        int start = json.indexOf(search);

        if (start == -1) {
            return 0L;
        }

        start += search.length();

        int end = json.indexOf(",", start);

        if (end == -1) {
            end = json.indexOf("}", start);
        }

        return Long.parseLong(json.substring(start, end).trim());
    }

    public static boolean extractBoolean(String json, String field) {

        String search = "\"" + field + "\":";

        int start = json.indexOf(search);

        if (start == -1) {
            return false;
        }

        start += search.length();

        int end = json.indexOf(",", start);

        if (end == -1) {
            end = json.indexOf("}", start);
        }

        return Boolean.parseBoolean(json.substring(start, end).trim());
    }

    /* ==========================================================
                         JSON ARRAY HELPERS
       ========================================================== */

    public static List<String> splitJsonArray(String arrayJson) {

        List<String> objects = new ArrayList<>();

        int depth = 0;
        int start = -1;

        for (int i = 0; i < arrayJson.length(); i++) {

            char c = arrayJson.charAt(i);

            if (c == '{') {

                if (depth == 0) {
                    start = i;
                }

                depth++;
            }

            else if (c == '}') {

                depth--;

                if (depth == 0) {
                    objects.add(arrayJson.substring(start, i + 1));
                }
            }
        }

        return objects;
    }

    public static String extractArray(String json, String field) {

        String search = "\"" + field + "\":[";

        int start = json.indexOf(search);

        if (start == -1) {
            return "[]";
        }

        start += search.length() - 1; // include '['

        int depth = 0;
        boolean insideString = false;

        for (int i = start; i < json.length(); i++) {

            char c = json.charAt(i);

            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                insideString = !insideString;
            }

            if (!insideString) {

                if (c == '[') {
                    depth++;
                }

                else if (c == ']') {
                    depth--;

                    if (depth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }

        return "[]";
    }

    public static List<String> extractStringArray(String json, String key) {

        List<String> result = new ArrayList<>();

        String pattern = "\"" + key + "\"";

        int keyIndex = json.indexOf(pattern);

        if (keyIndex == -1) {
            return result;
        }

        int arrayStart = json.indexOf("[", keyIndex);
        int arrayEnd = json.indexOf("]", arrayStart);

        if (arrayStart == -1 || arrayEnd == -1) {
            return result;
        }


        String content =
                json.substring(arrayStart + 1, arrayEnd).trim();


        if (content.isEmpty()) {
            return result;
        }


        String[] parts = content.split(",");


        for (String part : parts) {

            String cleaned = part.trim();

            if (cleaned.startsWith("\"")) {
                cleaned = cleaned.substring(1);
            }

            if (cleaned.endsWith("\"")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }

            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }


        return result;
    }

    public static String removeQuotes(String value) {

        if (value == null) {
            return null;
        }

        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

}