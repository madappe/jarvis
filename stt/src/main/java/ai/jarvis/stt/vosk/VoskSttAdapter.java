package ai.jarvis.stt.vosk;

import ai.jarvis.stt.api.SttAdapter;
import ai.jarvis.stt.api.SttListener;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


import javax.sound.sampled.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streamt Mikrofon-Audio (16k/16bit/mono) in Vosk und liefert Partial/Final-Events.
 * Hinweis: Erfordert ein entpacktes Vosk-Modell (Ordner) und einen Mixer-Index.
 */
public final class VoskSttAdapter implements SttAdapter {

    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 16_000f, 16, 1, 2, 16_000f, false
    );

    private final String modelDir;
    private final int mixerIndex;

    private SttListener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private TargetDataLine line;
    private Model model;
    private Recognizer recognizer;

    public VoskSttAdapter(String modelDir, int mixerIndex) {
        this.modelDir = modelDir;
        this.mixerIndex = mixerIndex;
    }

    @Override
    public void setListener(SttListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() throws Exception {
        if (running.get()) return;

        // 1) Modell + Recognizer vorbereiten
        model = new Model(modelDir);
        recognizer = new Recognizer(model, FORMAT.getSampleRate());

        // 2) Mikrofon öffnen (gewählter Mixer)
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        if (mixerIndex < 0 || mixerIndex >= infos.length) {
            throw new IllegalArgumentException("Ungültiger Mixer-Index: " + mixerIndex);
        }
        Mixer mixer = AudioSystem.getMixer(infos[mixerIndex]);
        DataLine.Info li = new DataLine.Info(TargetDataLine.class, FORMAT);
        line = (TargetDataLine) mixer.getLine(li);
        line.open(FORMAT);
        line.start();

        // 3) Worker starten
        running.set(true);
        worker = new Thread(() -> {
            // ~200 ms Chunks wie im MicTester
            int chunkBytes = (int)(FORMAT.getFrameRate() * 0.2) * FORMAT.getFrameSize(); // ≈6400
            byte[] buf = new byte[chunkBytes];

            try {
                while (running.get()) {
                    int n = line.read(buf, 0, buf.length);
                    if (n <= 0) continue;

                    boolean isFinal = recognizer.acceptWaveForm(buf, n);
                    if (isFinal) {
                        String json = recognizer.getResult();          // {"text":"..."}
                        String text = extract(json, "text");
                        if (listener != null && text != null && !text.isBlank()) {
                            listener.onFinal(text);
                        }
                    } else {
                        String json = recognizer.getPartialResult();   // {"partial":"..."}
                        String partial = extract(json, "partial");
                        if (listener != null && partial != null && !partial.isBlank()) {
                            listener.onPartial(partial);
                        }
                    }
                }

                // Abschlussresultat (falls noch was gepuffert ist)
                String json = recognizer.getFinalResult(); // {"text":"..."}
                String finalText = extract(json, "text");
                if (listener != null && finalText != null && !finalText.isBlank()) {
                    listener.onFinal(finalText);
                }
            } catch (Exception ignored) {
                // bei Stop oder Line-Close kann read/recognizer fehlschlagen → ok
            } finally {
                cleanup();
            }
        }, "VoskSttAdapter");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        running.set(false);
        if (worker != null) {
            try { worker.join(500); } catch (InterruptedException ignored) {}
            worker = null;
        }
        cleanup();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // --- Helpers ---

    private void cleanup() {
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
            line = null;
        }
        if (recognizer != null) {
            try { recognizer.close(); } catch (Exception ignored) {}
            recognizer = null;
        }
        if (model != null) {
            try { model.close(); } catch (Exception ignored) {}
            model = null;
        }
    }

    /** Winziger JSON-Extractor ohne externe Lib (für {"key":"value"}). */
    private static String extract(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return "";
    }
}
