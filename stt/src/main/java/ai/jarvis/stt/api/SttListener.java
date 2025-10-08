package ai.jarvis.stt.api;

/**
 * Listener für STT-Ereignisse.
 * - onPartial: Zwischenstände (können sich noch ändern)
 * - onFinal:   finales Ergebnis für ein Segment/Utterance
 */
public interface SttListener {

    /**
     * Wird mit Zwischenständen aufgerufen (nicht verbindlich).
     * @param text erkannter Zwischenstand (kann leer sein)
     */
    void onPartial(String text);

    /**
     * Wird mit finalem Ergebnis aufgerufen.
     * @param text endgültiger Text für ein Segment/Utterance (nicht leer)
     */
    void onFinal(String text);
}
