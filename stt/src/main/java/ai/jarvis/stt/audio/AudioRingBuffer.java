package ai.jarvis.stt.audio;

import java.util.Arrays;

/**
 * Einfacher, thread-sicherer Ringpuffer für PCM-Bytes.
 * Schreiben: Producer-Thread (Audio-Capture)
 * Lesen:     Consumer (z. B. STT, Analyzer)
 */
public final class AudioRingBuffer {
    private final byte[] buf;
    private int head = 0; // nächste Schreibposition
    private int size = 0; // aktuell belegte Bytes

    public AudioRingBuffer(int capacityBytes) {
        if (capacityBytes <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.buf = new byte[capacityBytes];
    }

    public synchronized int capacity() { return buf.length; }
    public synchronized int size()     { return size; }

    /** Fügt bytes ein. Überschreibt älteste Daten, wenn voll. */
    public synchronized void write(byte[] data, int off, int len) {
        int cap = buf.length;
        int remain = len;
        int pos = head;

        while (remain > 0) {
            int spaceAtEnd = cap - pos;
            int chunk = Math.min(spaceAtEnd, remain);
            System.arraycopy(data, off + (len - remain), buf, pos, chunk);

            pos = (pos + chunk) % cap;
            remain -= chunk;
        }

        // Kopf versetzen
        head = pos;
        // Größe anpassen (max cap)
        size = Math.min(cap, size + len);
        // Wenn übergelaufen, bleibt size=cap (älteste Daten überschrieben)
    }

    /** Kopiert die aktuell vorhandenen Bytes (neueste zuerst, d. h. chronologisch korrekt) in ein neues Array. */
    public synchronized byte[] snapshot() {
        byte[] out = new byte[size];
        int cap = buf.length;
        int tail = (head - size + cap) % cap;

        if (tail + size <= cap) {
            System.arraycopy(buf, tail, out, 0, size);
        } else {
            int first = cap - tail;
            System.arraycopy(buf, tail, out, 0, first);
            System.arraycopy(buf, 0, out, first, size - first);
        }
        return out;
    }

    /** Löscht den Inhalt. */
    public synchronized void clear() {
        Arrays.fill(buf, (byte)0);
        head = 0;
        size = 0;
    }
}
