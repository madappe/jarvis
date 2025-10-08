package ai.jarvis.core.skills;

import java.util.List;
import java.util.Map;

/**
 * Minimal DTO for a Skill YAML.
 * Keep fields public or add getters/setters; SnakeYAML maps by name.
 */
public class SkillDefinition {

    // Required
    public String name;
    public String description;
    public List<String> examples;
    public String intent;

    // params: list of { key, type, required }
    public List<Param> params;

    // confirmation: { required: boolean }
    public Confirmation confirmation;

    // match: { patterns: [...], synonyms: { alias: [..] } }
    public Match match;

    // execute: { windows: { type: "...", by_alias: { ... } }, ... }
    public Map<String, Object> execute; // keep generic for now

    public static class Param {
        public String key;
        public String type;
        public boolean required;
    }

    public static class Confirmation {
        public boolean required;
    }

    public static class Match {
        public List<String> patterns;
        public Map<String, List<String>> synonyms;
    }
}
