package ai.jarvis.executor.tts;

import ai.jarvis.core.tts.TtsAdapter;
import ai.jarvis.core.tts.TtsException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Windows-specific Text-to-Speech implementation using
 * PowerShell and the System.Speech SAPI engine.
 *
 * Primary goal: minimal, robust execution for Windows 11.
 * - No external dependencies
 * - Input validation
 * - OS guard (fails fast on non-Windows)
 * - Simple synchronous speak() call with a sane timeout
 */
public final class WindowsTtsExecutor implements TtsAdapter {

    // Reasonable default; long texts will take time.
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    // PowerShell entry point on Windows.
    private static final String POWERSHELL = "powershell.exe";

    /**
     * Speak the given text synchronously via Windows SAPI.
     * Validates OS and input; throws TtsException on failure.
     *
     * @param text Non-null, non-blank text to speak.
     * @throws TtsException When validation or engine execution fails.
     */
    @Override
    public void speak(String text) throws TtsException {
        // Basic input validation
        if (text == null || text.trim().isEmpty()) {
            throw new TtsException("TTS text must not be null or blank.");
        }

        // OS guard to avoid accidental calls on non-Windows environments
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            throw new TtsException("WindowsTtsExecutor can only run on Windows. Detected: " + osName);
        }

        // Build PowerShell command that:
        // 1) Loads System.Speech
        // 2) Instantiates SpeechSynthesizer
        // 3) Speaks the provided text
        //
        // Note: We pass the text as a quoted PowerShell string; single quotes avoid most escaping issues.
        // Any single quotes inside the text must be doubled for PowerShell.
        String safeText = text.replace("'", "''");

        String psCommand =
                "Add-Type -AssemblyName System.Speech;" +
                        "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;" +
                        "$s.Speak('" + safeText + "');";

        ProcessBuilder pb = new ProcessBuilder(
                POWERSHELL,
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                psCommand
        );

        // Inherit current environment; no special working dir needed
        pb.redirectErrorStream(true); // combine stdout/stderr for easier diagnostics

        Process process = null;
        try {
            process = pb.start();

            // Wait with timeout to avoid hanging if PowerShell locks up
            boolean finished = process.waitFor(DEFAULT_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                // Try to kill to avoid zombie processes
                process.destroyForcibly();
                throw new TtsException("TTS PowerShell timed out after " + DEFAULT_TIMEOUT.getSeconds() + "s.");
            }

            int exit = process.exitValue();
            if (exit != 0) {
                // Read any combined output for diagnostics
                byte[] out = process.getInputStream().readAllBytes();
                String msg = new String(out, StandardCharsets.UTF_8);
                throw new TtsException("TTS PowerShell exited with code " + exit + ". Output: " + msg);
            }

        } catch (IOException e) {
            throw new TtsException("Failed to start PowerShell TTS process.", e);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TtsException("Interrupted while waiting for TTS process.", ie);
        } finally {
            if (process != null) {
                // Ensure resources are closed
                try {
                    process.getInputStream().close();
                } catch (IOException ignored) { }
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) { }
                try {
                    process.getErrorStream().close();
                } catch (IOException ignored) { }
            }
        }
    }

    /**
     * @return Short name of the engine for logs/debugging.
     */
    @Override
    public String engineName() {
        return "Windows SAPI (PowerShell)";
    }
}
