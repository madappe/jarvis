package ai.jarvis.stt.audio;

import javax.sound.sampled.Mixer;

/**
 * Holds basic info about an audio input device (mixer) that can provide a TargetDataLine.
 */
public class AudioDeviceInfo {
    private final String name;         // Human-readable mixer name
    private final String description;  // Mixer description
    private final boolean supportsFormat; // Whether device supports the requested audio format

    public AudioDeviceInfo(String name, String description, boolean supportsFormat) {
        this.name = name;
        this.description = description;
        this.supportsFormat = supportsFormat;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isSupportsFormat() { return supportsFormat; }

    @Override
    public String toString() {
        return "AudioDeviceInfo{name='%s', supportsFormat=%s, description='%s'}"
                .formatted(name, supportsFormat, description);
    }
}
