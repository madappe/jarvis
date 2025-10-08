package ai.jarvis.executor.skills;

import ai.jarvis.core.tts.TtsAdapter;
import ai.jarvis.core.tts.TtsException;
import ai.jarvis.executor.tts.WindowsTtsExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Executor for the "say.hello" style skills that should produce audible TTS output.
 * This class does NOT perform any routing/registration. It is a plain executor
 * that can be called by the existing skill pipeline. Wiring happens in a later step.
 *
 * Responsibilities:
 * - Validate incoming text
 * - Call a TtsAdapter (Windows SAPI implementation for now)
 * - Provide minimal logging for observability
 *
 * Design notes:
 * - Keep constructor flexible: allow injecting a TtsAdapter for future tests or other OS backends.
 * - Provide simple overloads (String text / List<String> args) to match typical skill call shapes.
 */
public final class SayExecutor {

    private static final Logger log = LoggerFactory.getLogger(SayExecutor.class);

    // Default adapter for Windows 11 (SAPI via PowerShell)
    private final TtsAdapter tts;

    /**
     * Create a SayExecutor with a provided TtsAdapter.
     * Use this when you want to inject a mock or a different backend.
     *
     * @param tts Non-null TTS backend.
     */
    public SayExecutor(TtsAdapter tts) {
        this.tts = Objects.requireNonNull(tts, "tts must not be null");
        log.debug("SayExecutor initialized with engine: {}", this.tts.engineName());
    }

    /**
     * Create a SayExecutor with the default Windows implementation.
     * Primary target: Windows 11 using SAPI (PowerShell).
     */
    public SayExecutor() {
        this(new WindowsTtsExecutor());
    }

    /**
     * Execute TTS for a single text.
     * Validates input and throws IllegalArgumentException for blank text.
     *
     * @param text Non-null, non-blank text to speak.
     * @throws TtsException If the underlying TTS engine fails.
     */
    public void execute(String text) throws TtsException {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("SayExecutor requires non-blank text.");
        }
        log.info("SayExecutor speaking ({}): {}", tts.engineName(), preview(text));
        long start = System.nanoTime();
        tts.speak(text);
        long end = System.nanoTime();
        log.info("SayExecutor done in {} ms", (end - start) / 1_000_000);
    }

    /**
     * Execute TTS when the caller provides arguments, e.g. parsed tokens from a skill.
     * Joins args with a single space (common behavior for simple skill wrappers).
     *
     * @param args List of tokens forming the text to speak.
     * @throws TtsException If the underlying TTS engine fails.
     */
    public void execute(List<String> args) throws TtsException {
        if (args == null || args.isEmpty()) {
            throw new IllegalArgumentException("SayExecutor requires at least one argument to speak.");
        }
        String text = String.join(" ", args).trim();
        execute(text);
    }

    /**
     * @return The name of the active TTS engine (for diagnostics or UI).
     */
    public String engineName() {
        return tts.engineName();
    }

    // Helpers

    // Produce a short preview for logs without dumping the full text if it's long.
    private static String preview(String s) {
        final int max = 80;
        String trimmed = s.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max) + "...";
    }
}
