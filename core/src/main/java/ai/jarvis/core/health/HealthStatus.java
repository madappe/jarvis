package ai.jarvis.core.health;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Struktur für Health-Infos.
 */
public class HealthStatus {
    public boolean ok;
    public Map<String, Object> details = new LinkedHashMap<>();

    public HealthStatus ok(boolean ok) {
        this.ok = ok;
        return this;
    }

    public HealthStatus detail(String key, Object value) {
        details.put(key, value);
        return this;
    }
}
