package ai.jarvis.stt.vosk;

import org.vosk.Model;

/**
 * Minimaler Bootstrap: Prüft, ob ein Vosk-Modellverzeichnis geladen werden kann.
 * Erwartet den Pfad zu einem entpackten Vosk-Modell (z. B. ...\vosk-model-small-de-0.15).
 */
public final class VoskBootstrap {
    private VoskBootstrap() {}

    /**
     * Lädt das Modell, gibt Erfolgsmeldung aus und schließt wieder.
     * @param modelDir Pfad zum entpackten Modell-Ordner
     * @throws Exception bei nicht gefundenem/defektem Modell
     */
    public static void checkModel(String modelDir) throws Exception {
        long t0 = System.currentTimeMillis();
        try (Model model = new Model(modelDir)) {
            long dt = System.currentTimeMillis() - t0;
            System.out.println("Vosk model loaded: " + modelDir + " (" + dt + " ms)");
        }
    }
}
