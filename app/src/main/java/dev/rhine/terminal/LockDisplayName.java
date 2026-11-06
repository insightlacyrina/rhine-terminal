package dev.rhine.terminal;

import android.content.SharedPreferences;

import java.util.regex.Pattern;

/** Local display label shared by the settings and both lock wallpaper renderers. */
final class LockDisplayName {
    static final String KEY = "operator";
    static final String DEFAULT = "ZSY";
    static final int MAX_LENGTH = 24;
    private static final Pattern SPACING = Pattern.compile("[\\p{Z}\\p{Cc}]+");

    private LockDisplayName() {}

    static String read(SharedPreferences preferences) {
        return normalize(preferences.getString(KEY, DEFAULT));
    }

    static String normalize(String value) {
        if (value == null) return DEFAULT;
        String name = SPACING.matcher(value).replaceAll(" ").trim();
        if (name.isEmpty()) return DEFAULT;
        if (name.length() <= MAX_LENGTH) return name;
        int end = Character.isHighSurrogate(name.charAt(MAX_LENGTH - 1)) ? MAX_LENGTH - 1 : MAX_LENGTH;
        return name.substring(0, end).trim();
    }
}
