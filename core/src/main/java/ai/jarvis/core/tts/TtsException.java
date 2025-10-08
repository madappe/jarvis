package ai.jarvis.core.tts;

/**
 * Specific exception type for Text-to-Speech failures.
 * Using a dedicated exception helps callers distinguish TTS errors
 * from generic I/O or process errors and react accordingly.
 */
public class TtsException extends Exception {

    /**
     * Create a new TtsException with a message.
     *
     * @param message Human-readable error description.
     */
    public TtsException(String message) {
        super(message);
    }

    /**
     * Create a new TtsException with a message and a root cause.
     *
     * @param message Human-readable error description.
     * @param cause   Underlying cause (e.g., Process/IO failure).
     */
    public TtsException(String message, Throwable cause) {
        super(message, cause);
    }
}
