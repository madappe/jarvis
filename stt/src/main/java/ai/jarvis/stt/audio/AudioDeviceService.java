package ai.jarvis.stt.audio;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists available audio input devices (mixers) that can provide a TargetDataLine.
 * Also checks if they support the requested PCM format (16kHz, mono, 16-bit, signed, little-endian).
 */
public class AudioDeviceService {

    // Requested format for Jarvis STT
    // 16_000 Hz, mono, 16-bit, signed, little-endian
    private static final AudioFormat REQUESTED_FORMAT =
            new AudioFormat(
                    16_000.0f,  // sampleRate
                    16,         // sampleSizeInBits
                    1,          // channels
                    true,       // signed
                    false       // bigEndian (false = little-endian)
            );

    /**
     * Returns a list of input-capable mixers with a compatibility flag for our format.
     */
    public List<AudioDeviceInfo> listInputDevices() {
        List<AudioDeviceInfo> result = new ArrayList<>();

        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : infos) {
            Mixer mixer = AudioSystem.getMixer(info);

            // We only care about mixers that can provide a TargetDataLine (input)
            Line.Info targetInfo = new DataLine.Info(TargetDataLine.class, REQUESTED_FORMAT);

            boolean supportsFormat = mixer.isLineSupported(targetInfo);

            // Fallback check: some mixers only report generic support; try without fixed format
            if (!supportsFormat) {
                Line.Info genericInfo = new DataLine.Info(TargetDataLine.class, null);
                if (mixer.isLineSupported(genericInfo)) {
                    // We'll mark as potentially supported; detailed open-test happens later
                    supportsFormat = true;
                }
            }

            result.add(new AudioDeviceInfo(
                    info.getName(),
                    info.getDescription(),
                    supportsFormat
            ));
        }
        return result;
    }

    /**
     * Returns the requested AudioFormat used by our STT pipeline.
     */
    public AudioFormat getRequestedFormat() {
        return REQUESTED_FORMAT;
    }

    /**
     * Selects input device by preferred name fragment.
     * Priority:
     *  1) Name match that supports our format ([OK])
     *  2) First device that supports our format ([OK])
     *  3) Any name match (even if not [OK])
     *  4) Index 0 as last resort
     */
    public int selectPreferredDeviceIndex(String preferredName) {
        var devices = listInputDevices();
        if (devices.isEmpty()) return -1;

        String pref = preferredName == null ? "" : preferredName.trim().toLowerCase(java.util.Locale.ROOT);

        Integer nameOk = null;     // name match + [OK]
        Integer nameAny = null;    // name match (any)
        int fallbackOk = -1;       // first [OK]

        for (int i = 0; i < devices.size(); i++) {
            var d = devices.get(i);

            // remember first [OK]
            if (d.isSupportsFormat() && fallbackOk < 0) {
                fallbackOk = i;
            }

            if (!pref.isEmpty() && d.getName().toLowerCase(java.util.Locale.ROOT).contains(pref)) {
                if (nameAny == null) nameAny = i;
                if (d.isSupportsFormat() && nameOk == null) nameOk = i;
            }
        }

        if (nameOk != null) return nameOk;
        if (fallbackOk >= 0) return fallbackOk;
        if (nameAny != null) return nameAny;
        return 0;
    }
}
