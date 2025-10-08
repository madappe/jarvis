package ai.jarvis.stt.api;

/**
 * Optionales Ergebnis-Objekt für Adapter, die mehr Metadaten liefern möchten.
 * Aktuell minimal gehalten; kann später um Zeitstempel/Confidence/etc. erweitert werden.
 */
public class SttResult {
    public final String text;
    public final boolean isFinal;

    public SttResult(String text, boolean isFinal) {
        this.text = text;
        this.isFinal = isFinal;
    }
}
