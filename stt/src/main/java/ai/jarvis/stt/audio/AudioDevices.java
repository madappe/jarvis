package ai.jarvis.stt.audio;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility zum Auflisten von Audio-Eingabegeräten (Mikrofone).
 * Noch keine Aufnahme – nur Erkennung & Beschreibung der Mixer.
 */
public final class AudioDevices {
    private AudioDevices() {}

    // Ziel-Format für spätere STT: 16 kHz, Mono, 16-bit PCM (little-endian)
    private static final AudioFormat TARGET_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16_000f, 16, 1, 2, 16_000f, false
    );

    /** Liefert lesbare Zeilen für jedes Eingabegerät. */
    public static List<String> describeInputMixers() {
        List<String> out = new ArrayList<>();
        Mixer.Info[] all = AudioSystem.getMixerInfo();
        int idx = 0;
        for (Mixer.Info info : all) {
            Mixer m = AudioSystem.getMixer(info);
            boolean supportsAny = supportsAnyTargetDataLine(m);
            if (supportsAny) {
                boolean supportsExact = m.isLineSupported(new DataLine.Info(TargetDataLine.class, TARGET_FORMAT));
                out.add(String.format(
                        "[%d] %s | 16kHz-mono-16bit=%s",
                        idx, info.getName(), supportsExact ? "YES" : "NO"
                ));
            }
            idx++;
        }
        return out;
    }

    /** Prüft, ob der Mixer grundsätzlich eine TargetDataLine (Aufnahme) anbietet. */
    private static boolean supportsAnyTargetDataLine(Mixer mixer) {
        for (Line.Info li : mixer.getTargetLineInfo()) {
            if (li instanceof DataLine.Info dli) {
                if (TargetDataLine.class.isAssignableFrom(dli.getLineClass())) return true;
            }
        }
        return false;
    }

    /** Für spätere Schritte (Aufnahme) nutzbar. */
    public static AudioFormat targetFormat() {
        return TARGET_FORMAT;
    }
}
