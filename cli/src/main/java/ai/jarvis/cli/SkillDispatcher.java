package ai.jarvis.cli;

import ai.jarvis.core.skills.SkillDefinition;  // <-- Core model (Match/Name/Args)
import ai.jarvis.executor.skills.SayExecutor;  // <-- Our executor
import ai.jarvis.core.tts.TtsException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Dispatches matched skills to the appropriate executors.
 *
 * Keep this class tiny and focused:
 * - No framework code here (parsing/matching stays in core)
 * - No OS-specific code here (executors live in executor/*)
 * - No lambdas (team preference); use simple if/else dispatch.
 *
 * Return value:
 * - true  -> this dispatcher handled the skill
 * - false -> not handled; caller may try other handlers
 */
public final class SkillDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SkillDispatcher.class);

    private String resolveSkillName(Object skill) {
        if (skill == null) return null;

        // 1) Direct field access – often 'name' is a public field in SkillDefinition
        try {
            java.lang.reflect.Field f = skill.getClass().getDeclaredField("name");
            f.setAccessible(true);
            Object val = f.get(skill);
            if (val instanceof String s && !s.isBlank()) return s;
        } catch (NoSuchFieldException ignore) {
            // field not present
        } catch (Exception ex) {
            // unexpected reflection issue, ignore
        }

        // 2) Try common getters or methods that return the name/id/key/type
        final String[] candidates = {
                "getName", "name", "getId", "id", "getKey", "key", "getType", "type"
        };
        for (String m : candidates) {
            try {
                java.lang.reflect.Method method = skill.getClass().getMethod(m);
                Object val = method.invoke(skill);
                if (val instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (NoSuchMethodException ignore) {
                // no such method, try next
            } catch (Exception ignore) {
                // invocation failed, try next
            }
        }

        // 3) Fallback: attempt toString() if it contains useful info (e.g., "SkillDefinition{name='say.hello'}")
        try {
            String str = skill.toString();
            if (str != null && str.contains("say.hello")) return "say.hello";
        } catch (Exception ignore) { }

        return null; // nothing found
    }

    /**
     * Try to handle a matched skill by name and arguments.
     *
     * @param skill Matched skill definition (from core router).
     * @param args  Parsed arguments/tokens for the skill (maybe empty).
     * @return true if handled, false otherwise.
     */
    public boolean dispatch(SkillDefinition skill, List<String> args) {
        if (skill == null) {
            log.warn("SkillDispatcher received null skill.");
            return false;
        }

        final String name = resolveSkillName(skill); // e.g., "say.hello"
        if (name == null) {
            log.warn("SkillDefinition has no name.");
            return false;
        }

        // --- Minimal, explicit routing (no lambdas) ---
        if ("say.hello".equalsIgnoreCase(name)) {
            return handleSayHello(args);
        }

        // Not handled here; let caller fall back to other handlers/routes
        return false;
    }

    // --- Handlers ---

    /**
     * Handle "say.hello" skill: speak out the joined args via TTS.
     */
    private boolean handleSayHello(java.util.List<String> args) {
        ai.jarvis.executor.skills.SayExecutor executor = new ai.jarvis.executor.skills.SayExecutor();

        // Build text to speak (JarvisCli liefert bereits 1 String; fallback für Sicherheit)
        final String text;
        if (args == null || args.isEmpty()) {
            text = "Hello!";
        } else if (args.size() == 1) {
            text = args.get(0);
        } else {
            text = String.join(" ", args);
        }

        // Measure duration on the caller side for a friendly CLI line
        final long start = System.nanoTime();
        try {
            executor.execute(text);
            final long tookMs = (System.nanoTime() - start) / 1_000_000L;

            // Compact, user-friendly confirmation line
            System.out.println(
                    "[OK] Spoken (" + executor.engineName() + ") in " + formatDurationMs(tookMs) +
                            ": \"" + preview(text) + "\""
            );
            return true;

        } catch (ai.jarvis.core.tts.TtsException ex) {
            // Map common failure patterns to compact hints
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(java.util.Locale.ROOT);

            if (msg.contains("timed out")) {
                System.out.println("[WARN] TTS timed out. Please try again with a shorter sentence.");
            } else if (msg.contains("powershell")) {
                System.out.println("[ERROR] TTS failed to start PowerShell. Check that 'powershell.exe' is available and audio output is configured.");
            } else if (msg.contains("exited with code")) {
                System.out.println("[ERROR] TTS engine returned an error. Check your audio device and try again.");
            } else {
                System.out.println("[ERROR] TTS error: " + ex.getMessage());
            }
            // Keep logs detailed for developers
            log.error("TTS failed while executing say.hello: {}", ex.getMessage(), ex);
            return false;

        } catch (IllegalArgumentException iae) {
            System.out.println("[WARN] No text to speak.");
            log.warn("Invalid say.hello input: {}", iae.getMessage());
            return false;
        }
    }

// --- Helpers (keep them small and local) ---

    // Short preview for CLI line; avoids flooding terminal
    private static String preview(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() <= 100 ? t : t.substring(0, 100) + "...";
    }

    // Format duration nicely (e.g., "1.6 s" or "240 ms")
    private static String formatDurationMs(long ms) {
        if (ms < 1000) return ms + " ms";
        double sec = ms / 1000.0;
        return String.format(java.util.Locale.ROOT, "%.1f s", sec);
    }
}
