package ai.jarvis.core.alias;

import ai.jarvis.core.config.AliasConfig;

import java.util.*;

/**
 * Centralized service for normalizing application names (aliases → canonical).
 *
 * Sources:
 *  - AliasConfig properties with keys starting "app." (e.g., app.notepad=notepad,editor,txt)
 *  - Built-in minimal fallbacks for Windows (only used if no config entry matches)
 *
 * Contract:
 *  - Input is any raw user token like "Editor" or "calculator".
 *  - Output is a canonical app id like "notepad" or "calc".
 *  - If no mapping found, returns the lower-cased input for safe downstream handling.
 */
public final class AppAliasService {

    private final AliasConfig aliasConfig;
    private final Map<String, String> synonymToCanonical;

    /**
     * Build the alias map from AliasConfig once.
     * Keys:  every synonym (lower-case, trimmed)
     * Value: canonical app id (key name after "app.")
     */
    public AppAliasService(AliasConfig aliasConfig) {
        this.aliasConfig = aliasConfig;
        this.synonymToCanonical = new HashMap<String, String>();
        this.loadFromAliasConfig();
        this.addBuiltInFallbacks();
    }

    /**
     * Normalize a raw application name to its canonical id.
     * If nothing matches, returns the sanitized lower-case input.
     */
    public String normalize(String rawAppName) {
        if (rawAppName == null) return null;
        String key = rawAppName.trim().toLowerCase(Locale.ROOT);
        if (key.length() == 0) return key;
        String canonical = this.synonymToCanonical.get(key);
        return canonical != null ? canonical : key;
    }

    // --- internal helpers ---

    /**
     * Load aliases from AliasConfig. Expected format:
     *   app.<canonical>=comma,separated,synonyms
     * Example:
     *   app. Notepad=notepad,editor,txteditor
     */
    private void loadFromAliasConfig() {
        Properties props = this.aliasConfig.getProperties();
        if (props == null) return;

        for (Map.Entry<Object, Object> e : props.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k == null || v == null) continue;
            String key = String.valueOf(k).trim();
            if (!key.startsWith("app.")) continue;

            String canonical = key.substring("app.".length()).trim().toLowerCase(Locale.ROOT);
            String csv = String.valueOf(v);
            String[] tokens = csv.split(",");
            for (int i = 0; i < tokens.length; i++) {
                String s = tokens[i].trim().toLowerCase(Locale.ROOT);
                if (s.length() == 0) continue;
                // Map synonym → canonical
                this.synonymToCanonical.put(s, canonical);
            }
            // Always ensure the canonical itself resolves to itself.
            this.synonymToCanonical.put(canonical, canonical);
        }
    }

    /**
     * Minimal built-in fallbacks (only used if not configured).
     * This avoids regressions when no alias properties are present yet.
     */
    private void addBuiltInFallbacks() {
        // Only add fallback if not overridden by config.
        this.putIfAbsent("editor", "notepad");
        this.putIfAbsent("paint", "mspaint");
        this.putIfAbsent("calculator", "calc");
        this.putIfAbsent("rechner", "calc"); // common DE synonym
    }

    private void putIfAbsent(String synonym, String canonical) {
        if (!this.synonymToCanonical.containsKey(synonym)) {
            this.synonymToCanonical.put(synonym, canonical);
            // ensure canonical self-map too
            if (!this.synonymToCanonical.containsKey(canonical)) {
                this.synonymToCanonical.put(canonical, canonical);
            }
        }
    }
}
