package ai.jarvis.core.config;

/**
 * Simple POJO for audio-related configuration:
 * - preferred microphone name (exact or partial match)
 */
public class AudioConfig {

    // Example: "Logitech" or full mixer name for precise match
    private String preferredMicName;

    public String getPreferredMicName() {
        return preferredMicName;
    }

    public void setPreferredMicName(String preferredMicName) {
        this.preferredMicName = preferredMicName;
    }
}
