package ai.jarvis.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lädt Aliase & STT-Replacements aus jarvis-aliases.properties.
 * Reihenfolge (später gewinnt):
 *   1) Datei im Arbeitsverzeichnis: ./jarvis-aliases.properties (optional)
 *   2) Ressource im Classpath: /jarvis-aliases.properties (default)
 */
public final class AliasConfig {

    private static final String FILE_NAME = "jarvis-aliases.properties";
    private static final String APP_PREFIX = "app.";
    private static final String REP_PREFIX = "stt.replace.";

    private final Map<String, String> appAliases;     // z.B. "calculator" -> "calc"
    private final List<Map.Entry<String,String>> replacements; // z.B. "oh pen" -> "open"

    private static volatile AliasConfig INSTANCE;

    private AliasConfig(Properties props) {
        // Alle app.* in Map (Key normiert klein/trim)
        appAliases = props.entrySet().stream()
                .filter(e -> e.getKey().toString().startsWith(APP_PREFIX))
                .collect(Collectors.toMap(
                        e -> e.getKey().toString().substring(APP_PREFIX.length()).toLowerCase().trim(),
                        e -> e.getValue().toString().trim(),
                        (a,b) -> b,
                        LinkedHashMap::new
                ));

        // Alle stt.replace.* als Liste in Einfügereihenfolge (wichtig!)
        replacements = props.entrySet().stream()
                .filter(e -> e.getKey().toString().startsWith(REP_PREFIX))
                .map(e -> Map.entry(
                        e.getKey().toString().substring(REP_PREFIX.length()).toLowerCase(),
                        e.getValue().toString()
                ))
                .collect(Collectors.toCollection(LinkedList::new));
    }

    /** Lazy-Singleton laden */
    public static AliasConfig get() {
        if (INSTANCE == null) {
            synchronized (AliasConfig.class) {
                if (INSTANCE == null) INSTANCE = load();
            }
        }
        return INSTANCE;
    }

    private static AliasConfig load() {
        Properties p = new Properties();

        // 1) Classpath-Default
        try (InputStream in = AliasConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {}

        // 2) Datei im Arbeitsverzeichnis (optional, überschreibt Defaults)
        Path local = Paths.get(FILE_NAME);
        if (Files.isRegularFile(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                Properties override = new Properties();
                override.load(in);
                p.putAll(override);
            } catch (IOException ignored) {}
        }

        return new AliasConfig(p);
    }

    /** Wendet stt.replace.* sequentiell an und räumt Mehrfachspaces. */
    public String normalizeSttText(String text) {
        if (text == null) return null;
        String s = text.toLowerCase().trim();
        for (var rep : replacements) {
            s = s.replace(rep.getKey(), rep.getValue());
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Mappt gesprochenen App-Namen via app.*; wenn nichts passt, unverändert zurück. */
    public String normalizeApp(String appRaw) {
        if (appRaw == null) return null;
        String key = appRaw.toLowerCase().trim();
        return appAliases.getOrDefault(key, appRaw);
    }
}
