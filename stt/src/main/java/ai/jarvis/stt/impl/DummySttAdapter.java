package ai.jarvis.stt.impl;

import ai.jarvis.stt.api.SttAdapter;
import ai.jarvis.stt.api.SttListener;

/**
 * Sehr einfacher STT-Adapter, der eine vorgegebene Phrase als
 * Teil-Ergebnisse (partial) und am Ende als final ausgibt.
 * Dient nur zum Testen der Ereigniskette.
 */
public final class DummySttAdapter implements SttAdapter {
    private final String phrase;
    private volatile boolean running = false;
    private Thread worker;
    private SttListener listener;

    public DummySttAdapter(String phrase) {
        this.phrase = (phrase == null || phrase.isBlank()) ? "öffne notepad" : phrase;
    }

    @Override
    public void setListener(SttListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;

        worker = new Thread(() -> {
            try {
                String[] tokens = phrase.split("\\s+");
                StringBuilder acc = new StringBuilder();
                for (int i = 0; i < tokens.length && running; i++) {
                    if (acc.length() > 0) acc.append(' ');
                    acc.append(tokens[i]);
                    if (listener != null) listener.onPartial(acc.toString());
                    Thread.sleep(300); // kleine Verzögerung zwischen partials
                }
                if (running && listener != null) listener.onFinal(phrase);
            } catch (InterruptedException ignored) {
                // Thread beendet
            } finally {
                running = false;
            }
        }, "DummySttAdapter");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            try { worker.join(500); } catch (InterruptedException ignored) {}
            worker = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
