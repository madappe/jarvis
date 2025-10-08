package ai.jarvis.stt.audio;

import javax.sound.sampled.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Liest PCM 16kHz/16bit/mono vom gewählten Mixer in einen Ringpuffer.
 * - start(mixerIndex) / stop()
 * - getBufferedMillis(): grobe Latenzschätzung auf Basis der Puffergröße
 */
public final class AudioCapture {

    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 16_000f, 16, 1, 2, 16_000f, false
    );
    private static final int CHUNK_MS = 20; // ~20ms pro read
    private static final int BYTES_PER_SECOND = 16_000 * 2; // 32_000

    private final AudioRingBuffer ring;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private TargetDataLine line;
    private Thread worker;

    public AudioCapture(int ringCapacityMillis) {
        int capBytes = Math.max(BYTES_PER_SECOND * ringCapacityMillis / 1000, BYTES_PER_SECOND / 2);
        this.ring = new AudioRingBuffer(capBytes);
    }

    public void start(int mixerIndex) throws Exception {
        if (running.get()) return;

        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        if (mixerIndex < 0 || mixerIndex >= infos.length) {
            throw new IllegalArgumentException("Ungültiger Mixer-Index: " + mixerIndex);
        }
        Mixer mixer = AudioSystem.getMixer(infos[mixerIndex]);

        DataLine.Info li = new DataLine.Info(TargetDataLine.class, FORMAT);
        TargetDataLine l = (TargetDataLine) mixer.getLine(li);
        l.open(FORMAT);
        l.start();

        line = l;
        running.set(true);

        worker = new Thread(() -> {
            int chunkBytes = BYTES_PER_SECOND * CHUNK_MS / 1000; // ~640 Bytes
            byte[] buf = new byte[chunkBytes];

            while (running.get()) {
                int n = line.read(buf, 0, buf.length);
                if (n > 0) ring.write(buf, 0, n);
            }
        }, "AudioCaptureWorker");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running.set(false);
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
            line = null;
        }
        if (worker != null) {
            try { worker.join(500); } catch (InterruptedException ignored) {}
            worker = null;
        }
    }

    /** Bytes aktuell im Ringpuffer. */
    public int bufferedBytes() { return ring.size(); }

    /** Grobe Latenzschätzung in Millisekunden. */
    public int getBufferedMillis() {
        return (int) Math.round(bufferedBytes() * 1000.0 / BYTES_PER_SECOND);
    }

    /** Snapshot der aktuell gepufferten Audiodaten. */
    public byte[] snapshot() { return ring.snapshot(); }

    /** Puffer leeren. */
    public void clear() { ring.clear(); }

    public static AudioFormat format() { return FORMAT; }
}
