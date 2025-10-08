package ai.jarvis.stt.api;

/**
 * Minimale STT-Adapter-Schnittstelle.
 * Implementierungen (z. B. Vosk/Whisper) kapseln Transport & Engine.
 */
public interface SttAdapter {

    /**
     * Setzt den Listener für Partial/Final-Events.
     * Sollte vor start() gesetzt werden.
     */
    void setListener(SttListener listener);

    /**
     * Startet die Erkennung.
     * Implementierungsspezifisch: kann eigenes Audio-Capture starten oder
     * auf einen externen Audio-Stream hören.
     */
    void start() throws Exception;

    /**
     * Stoppt die Erkennung und räumt Ressourcen auf.
     */
    void stop();

    /**
     * @return true, wenn der Adapter aktiv ist (nach erfolgreichem start()).
     */
    boolean isRunning();
}
