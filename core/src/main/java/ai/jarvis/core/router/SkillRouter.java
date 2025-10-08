package ai.jarvis.core.router;

import ai.jarvis.core.skills.SkillDefinition;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Findet anhand von Patterns den passenden Skill und extrahiert Parameter.
 */
public class SkillRouter {

    // Regex zum Finden benannter Gruppen in Java-Patterns: (?<name>...)
    private static final Pattern NAMED_GROUP_FINDER = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9_]*)>");

    /**
     * Versucht, für den Satz einen Skill zu finden.
     * @param text   Eingabesatz
     * @param skills geladene Skill-Definitionen
     * @return Optionales IntentMatch
     */
    public Optional<IntentMatch> route(String text, List<SkillDefinition> skills) {
        if (text == null) return Optional.empty();
        final String low = text.trim();

        for (SkillDefinition def : skills) {
            if (def.match == null || def.match.patterns == null) continue;

            for (String pat : def.match.patterns) {
                try {
                    Pattern p = Pattern.compile(pat, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    Matcher m = p.matcher(low);
                    if (m.matches()) {
                        Map<String, String> params = extractParams(p, m);
                        return Optional.of(new IntentMatch(def.name, pat, text, params));
                    }
                } catch (Exception ex) {
                    // Ungültiges Pattern ignorieren
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Extrahiert Parameter aus benannten Gruppen; fallback: arg1, arg2...
     */
    private Map<String, String> extractParams(Pattern pattern, Matcher matcher) {
        // Benannte Gruppen sammeln
        Set<String> names = findGroupNames(pattern.pattern());
        Map<String, String> out = new LinkedHashMap<>();

        if (!names.isEmpty()) {
            for (String name : names) {
                try {
                    String val = matcher.group(name);
                    if (val != null) out.put(name, val.trim());
                } catch (IllegalArgumentException ignored) {
                    // falls Name doch nicht vorhanden ist
                }
            }
        } else {
            // Fallback: durchnummerieren
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String val = matcher.group(i);
                if (val != null) out.put("arg" + i, val.trim());
            }
        }
        return out;
    }

    private Set<String> findGroupNames(String regex) {
        var m = NAMED_GROUP_FINDER.matcher(regex);
        Set<String> names = new LinkedHashSet<>();
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
