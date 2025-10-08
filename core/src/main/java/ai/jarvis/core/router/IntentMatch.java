package ai.jarvis.core.router;

import java.util.Collections;
import java.util.Map;

/**
 * Ergebnis einer erfolgreichen Zuordnung: welcher Skill soll laufen,
 * mit welchen extrahierten Parametern.
 */
public class IntentMatch {
    private final String skillName;
    private final String pattern;
    private final String originalText;
    private final Map<String, String> params;

    public IntentMatch(String skillName, String pattern, String originalText, Map<String, String> params) {
        this.skillName = skillName;
        this.pattern = pattern;
        this.originalText = originalText;
        this.params = (params == null) ? Collections.emptyMap() : Map.copyOf(params);
    }

    public String getSkillName() { return skillName; }
    public String getPattern() { return pattern; }
    public String getOriginalText() { return originalText; }
    public Map<String, String> getParams() { return params; }

    @Override
    public String toString() {
        return "IntentMatch{" +
                "skillName='" + skillName + '\'' +
                ", pattern='" + pattern + '\'' +
                ", originalText='" + originalText + '\'' +
                ", params=" + params +
                '}';
    }
}
