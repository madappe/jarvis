package ai.jarvis.core.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AppAllowListConfig
 * ------------------
 * Liest app-allowlist.yaml aus /resources/config/
 * und stellt sie als zwei Maps bereit: allowedApps + synonyms.
 */
public class AppAllowListConfig {

    private final Map<String, String> allowedApps;
    private final Map<String, String> synonyms;

    public AppAllowListConfig() {
        Map<String, Object> data = loadYaml();
        this.allowedApps = toStringMap(data.get("allowedApps"));
        this.synonyms = toStringMap(data.get("synonyms"));
    }

    public Map<String, String> getAllowedApps() {
        return allowedApps;
    }

    public Map<String, String> getSynonyms() {
        return synonyms;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/app-allowlist.yaml")) {
            if (in == null) {
                System.err.println("[AppAllowListConfig] config/app-allowlist.yaml not found!");
                return Collections.emptyMap();
            }
            Yaml yaml = new Yaml();
            return yaml.load(in);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
            return result;
        }
        return Collections.emptyMap();
    }
}
