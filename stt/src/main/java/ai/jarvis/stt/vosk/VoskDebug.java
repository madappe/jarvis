package ai.jarvis.stt.vosk;

import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;

/**
 * Debug: streamt 16k/16bit/mono vom gewählten Mixer in Vosk und druckt
 * die ROHEN JSON-Ausgaben (partial/result/final) Zeile für Zeile.
 */
public final class VoskDebug {

    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 16_000f, 16, 1, 2, 16_000f, false
    );

    private VoskDebug() {}

    public static void run(String modelDir, int mixerIndex, int seconds) throws Exception {
        // Modell + Recognizer
        try (Model model = new Model(modelDir);
             Recognizer rec = new Recognizer(model, FORMAT.getSampleRate())) {

            // Mikro vorbereiten
            Mixer.Info[] infos = AudioSystem.getMixerInfo();
            if (mixerIndex < 0 || mixerIndex >= infos.length) {
                throw new IllegalArgumentException("Ungültiger Mixer-Index: " + mixerIndex);
            }
            Mixer mixer = AudioSystem.getMixer(infos[mixerIndex]);
            DataLine.Info li = new DataLine.Info(TargetDataLine.class, FORMAT);
            try (TargetDataLine line = (TargetDataLine) mixer.getLine(li)) {
                line.open(FORMAT);
                line.start();

                System.out.println("VoskDebug: capturing " + seconds + "s from mixer [" + mixerIndex + "] " + infos[mixerIndex].getName());

                int chunkBytes = (int)(FORMAT.getFrameRate() * 0.2) * FORMAT.getFrameSize(); // ~200ms
                byte[] buf = new byte[chunkBytes];
                long end = System.currentTimeMillis() + seconds * 1000L;

                while (System.currentTimeMillis() < end) {
                    int n = line.read(buf, 0, buf.length);
                    if (n <= 0) continue;

                    boolean accepted = rec.acceptWaveForm(buf, n);
                    if (accepted) {
                        // vollständiges Ergebnis für eine Äußerung
                        String json = rec.getResult();
                        System.out.println("[ACCEPT] " + json);
                    } else {
                        // Zwischenstand
                        String json = rec.getPartialResult();
                        System.out.println("[PARTIAL] " + json);
                    }
                }

                // Aufräumen/Finale
                String finalJson = rec.getFinalResult();
                System.out.println("[FINAL] " + finalJson);
            }
        }
    }
}
