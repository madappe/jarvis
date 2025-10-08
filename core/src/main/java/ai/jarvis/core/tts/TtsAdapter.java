package ai.jarvis.core.tts;

/**
 * Text-to-Speech (TTS) adapter contract.
 * Keep this interface minimal so different OS backends (Windows/macOS/Linux)
 * can implement it without leaking OS-specific details.
 *
 * Primary target now: Windows 11 (SAPI via PowerShell).
 * Other OS backends can be added later behind the same interface.
 */
public interface TtsAdapter {

    /**
     * Speak the given text synchronously (blocking).
     * Implementations must validate the input and throw TtsException
     * if the text is null/blank or if the underlying engine fails.
     *
     * @param text Non-null, non-blank text to speak.
     * @throws TtsException When validation fails or engine cannot speak.
     */
    void speak(String text) throws TtsException;

    /**
     * @return A short, human-readable engine name (e.g. "Windows SAPI").
     *         Useful for logs and debugging.
     */
    String engineName();
}
