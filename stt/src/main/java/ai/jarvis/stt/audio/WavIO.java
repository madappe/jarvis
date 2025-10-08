package ai.jarvis.stt.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Speichert Roh-PCM (16 kHz / 16-bit / Mono, little-endian) als WAV-Datei.
 */
public final class WavIO {
    private WavIO() {}

    // Muss exakt zum Capture-Format passen:
    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 16_000f, 16, 1, 2, 16_000f, false
    );

    /**
     * Speichert die gegebenen PCM-Bytes als WAV-Datei.
     * @param pcm        Rohdaten (16-bit little-endian, mono, 16 kHz)
     * @param outputPath Zielpfad, z. B. "sample.wav"
     */
    public static void savePcm16Mono16kAsWav(byte[] pcm, Path outputPath) throws IOException {
        // Sicherheits-Check
        if (pcm == null || pcm.length == 0) throw new IllegalArgumentException("Leerer PCM-Puffer");
        if (outputPath == null) throw new IllegalArgumentException("outputPath ist null");

        // Verzeichnis anlegen, falls nötig
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        // PCM -> AudioInputStream wrappen und als WAV schreiben
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcm);
             AudioInputStream ais = new AudioInputStream(bais, FORMAT, pcm.length / FORMAT.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputPath.toFile());
        }
    }
}
