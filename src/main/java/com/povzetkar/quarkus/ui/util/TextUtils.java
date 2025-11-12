package com.povzetkar.quarkus.ui.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtils {

    private TextUtils() {
    }

    public static String extractSection(String text, Pattern pattern) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}
