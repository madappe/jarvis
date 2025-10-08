package ai.jarvis.core.skills;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/**
 * SkillsLoader
 * -------------
 * Lädt SkillDefinition-Dateien (.yaml) aus:
 *  - internen Ressourcen (resources/skills)
 *  - externem Verzeichnis ./skills (neben dem JAR)
 *
 * Gibt eine kombinierte Liste zurück (doppelte Skillnamen werden ignoriert).
 */
public class SkillsLoader {

    // Standardverzeichnis für externe Skills
    public static final String DEFAULT_SKILLS_DIR = "skills";

    /**
     * Lädt alle Skills aus Ressourcen und externem Verzeichnis zusammen.
     */
    public List<SkillDefinition> loadMergedSkills() {
        List<SkillDefinition> merged = new ArrayList<>();

        // 1) interne Skills vom Classpath laden
        merged.addAll(loadFromClasspath("skills"));
        System.out.println("[SkillsLoader] Internal skills loaded: " + merged.size());

        // 2) externe Skills vom Dateisystem laden
        List<SkillDefinition> external = loadFromExternalFolder(Paths.get("skills"));
        if (!external.isEmpty()) {
            merged.addAll(external);
            System.out.println("[SkillsLoader] External skills loaded: " + external.size());
        } else {
            System.out.println("[SkillsLoader] No external skills found.");
        }

        System.out.println("[SkillsLoader] Total merged skills: " + merged.size());

        // Activate dedupe: external overrides internal if duplicate names found
        List<SkillDefinition> deduped = dedupeByName(merged);
        return deduped;
    }

    /**
     * Lädt alle Skill-YAMLs aus dem Ressourcen-Ordner (z. B. resources/skills)
     * – funktioniert auch im JAR.
     */
    public List<SkillDefinition> loadFromClasspath(String resourceFolder) {
        List<SkillDefinition> list = new ArrayList<>();
        final Yaml yaml = new Yaml();

        try {
            // ClassLoader für Ressourcen benutzen
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            // Ressourcen-Listing über getResources funktioniert nur für echte Ordner beim Entwickeln,
            // deshalb greifen wir zusätzlich über getResourceAsStream auf bekannte Dateien zu.
            // -> Wir laden alle .yaml-Dateien, die im Ordner liegen.
            java.net.URL dirURL = cl.getResource(resourceFolder);
            if (dirURL == null) {
                System.out.println("[SkillsLoader] No classpath folder found: " + resourceFolder);
                return list;
            }

            // Wenn es sich um ein echtes Dateisystem handelt (z. B. im IDE-Modus)
            if ("file".equals(dirURL.getProtocol())) {
                Path folder = Paths.get(dirURL.toURI());
                if (Files.isDirectory(folder)) {
                    Files.list(folder)
                            .filter(p -> p.toString().endsWith(".yaml"))
                            .forEach(p -> {
                                SkillDefinition def = readYaml(p);
                                if (def != null) list.add(def);
                            });
                }
            }
            // Wenn es sich um ein JAR handelt
            else if ("jar".equals(dirURL.getProtocol())) {
                // ⭐ Neuer Code || hier neu einbringen ||
                java.net.JarURLConnection conn = (java.net.JarURLConnection) dirURL.openConnection();
                try (java.util.jar.JarFile jar = conn.getJarFile()) {
                    java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        java.util.jar.JarEntry e = entries.nextElement();
                        String name = e.getName(); // z.B. "skills/app.launch.yaml"
                        if (!e.isDirectory()
                                && name.startsWith(resourceFolder + "/")
                                && name.endsWith(".yaml")) {
                            try (InputStream in = Thread.currentThread()
                                    .getContextClassLoader()
                                    .getResourceAsStream(name)) {
                                if (in != null) {
                                    SkillDefinition def = yaml.loadAs(in, SkillDefinition.class);
                                    if (def != null) {
                                        list.add(def);
                                    }
                                }
                            } catch (Exception ex) {
                                System.err.println("[SkillsLoader] Error reading " + name + " from JAR: " + ex.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SkillsLoader] Error loading from classpath: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lädt alle Skills aus einem externen Verzeichnis (z. B. ./skills).
     */
    private List<SkillDefinition> loadFromExternalFolder(Path dir) {
        List<SkillDefinition> list = new ArrayList<>();
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                Files.list(dir)
                        .filter(p -> p.toString().endsWith(".yaml"))
                        .forEach(p -> {
                            SkillDefinition def = readYaml(p);
                            if (def != null) {
                                list.add(def);
                                System.out.println("[SkillsLoader] External skill loaded: " + p.getFileName());
                            }
                        });
            } catch (IOException e) {
                System.err.println("[SkillsLoader] Error scanning external folder: " + e.getMessage());
            }
        }
        return list;
    }

    /**
     * Liest eine einzelne YAML-Datei in eine SkillDefinition.
     */
    private SkillDefinition readYaml(Path yamlPath) {
        try (InputStream in = Files.newInputStream(yamlPath)) {
            Yaml yaml = new Yaml();
            return yaml.loadAs(in, SkillDefinition.class);
        } catch (Exception e) {
            System.err.println("[SkillsLoader] Error reading " + yamlPath + ": " + e.getMessage());
            return null;
        }
    }

    // Merge duplicate skill names; external definitions override internal ones.
    // Logs every replacement for visibility.
    private java.util.List<SkillDefinition> dedupeByName(java.util.List<SkillDefinition> all) {
        if (all == null || all.isEmpty()) return java.util.Collections.emptyList();

        java.util.Map<String, SkillDefinition> byName = new java.util.LinkedHashMap<>();
        java.util.List<SkillDefinition> result = new java.util.ArrayList<>();
        java.util.Set<String> duplicates = new java.util.LinkedHashSet<>();

        for (SkillDefinition def : all) {
            String name = resolveName(def);
            if (name == null || name.isBlank()) {
                // Skip nameless skill definitions
                System.out.println("[SkillsLoader] [WARN] Skill without name ignored.");
                continue;
            }

            if (!byName.containsKey(name)) {
                // first occurrence
                byName.put(name, def);
            } else {
                // duplicate found
                SkillDefinition existing = byName.get(name);
                boolean newIsExternal = isExternal(def);
                boolean oldIsExternal = isExternal(existing);

                if (newIsExternal && !oldIsExternal) {
                    // external overrides internal
                    byName.put(name, def);
                    System.out.println("[SkillsLoader] [WARN] Duplicate skill '" + name +
                            "' detected – external version used.");
                } else if (!newIsExternal && oldIsExternal) {
                    // keep external, ignore internal
                    System.out.println("[SkillsLoader] [INFO] Duplicate skill '" + name +
                            "' ignored (internal skipped, external already present).");
                } else {
                    // same type (both internal/external)
                    System.out.println("[SkillsLoader] [WARN] Duplicate skill '" + name +
                            "' detected – keeping first version.");
                }
                duplicates.add(name);
            }
        }

        result.addAll(byName.values());

        if (!duplicates.isEmpty()) {
            System.out.println("[SkillsLoader] Dedupe summary: " + duplicates.size()
                    + " duplicate skill(s) resolved.");
        }

        return result;
    }


    // Resolve a stable skill name from a SkillDefinition (field 'name' or common getters).
    private String resolveName(Object def) {
        if (def == null) return null;

        // 1) direct field 'name'
        try {
            java.lang.reflect.Field f = def.getClass().getDeclaredField("name");
            f.setAccessible(true);
            Object v = f.get(def);
            if (v instanceof String s && !s.isBlank()) return s;
        } catch (NoSuchFieldException ignored) {
        } catch (Exception ignored) {
        }

        // 2) common getters
        String[] methods = {"getName", "name", "getId", "id", "getKey", "key", "getType", "type"};
        for (String m : methods) {
            try {
                java.lang.reflect.Method gm = def.getClass().getMethod(m);
                Object v = gm.invoke(def);
                if (v instanceof String s && !s.isBlank()) return s;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
            }
        }

        // 3) last resort: toString sniff
        try {
            String s = def.toString();
            if (s != null && !s.isBlank()) return s;
        } catch (Exception ignored) { }

        return null;
    }

    // Detects whether a SkillDefinition originated from an external file.
    // This version infers by class name or origin metadata if available.
    private boolean isExternal(SkillDefinition def) {
        if (def == null) return false;

        try {
            // Many loaders store filename or origin inside SkillDefinition (optional)
            java.lang.reflect.Field source = null;
            try {
                source = def.getClass().getDeclaredField("source");
                source.setAccessible(true);
                Object val = source.get(def);
                if (val != null && val.toString().toLowerCase(java.util.Locale.ROOT).contains("skills")) {
                    // crude heuristic: path contains 'skills' (external YAML folder)
                    return true;
                }
            } catch (NoSuchFieldException ignored) {
            }

            // Fallback: class name or package
            String cls = def.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (cls.contains("external") || cls.contains("yaml")) return true;
        } catch (Exception ignored) {
        }

        return false; // default: internal
    }

}
