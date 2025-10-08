package ai.jarvis.executor.apps;

import java.text.Normalizer;
import java.util.Locale;

/**
 * AppNameNormalizer
 * -----------------
 * Normalizes spoken app names:
 *  - lowercases
 *  - trims
 *  - removes diacritics
 *  - strips common leading verbs (e.g., "öffne ", "starte")
 */
public final class AppNameNormalizer {
    private AppNameNormalizer() {}

    public static String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);

        // Remove leading German verbs we expect from patterns (defensive)
        s = stripPrefix(s, "öffne ");
        s = stripPrefix(s, "starte ");
        s = stripPrefix(s, "open ");
        s = stripPrefix(s, "start ");

        // Remove accents/diacritics to avoid edge mismatches
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        // Collapse multiple spaces
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String stripPrefix(String s, String prefix) {
        if (s.startsWith(prefix)) return s.substring(prefix.length());
        return s;
    }
}
