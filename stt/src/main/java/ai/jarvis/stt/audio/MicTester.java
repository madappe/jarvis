package ai.jarvis.stt.audio;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads short audio snippets from a chosen mixer (index from AudioSystem.getMixerInfo())
 * using 16 kHz / 16-bit / mono and prints/returns basic levels (RMS & Peak).
 */
public final class MicTester {

    private MicTester() {}

    // Target format identical to AudioDevices.targetFormat()
    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16_000f, 16, 1, 2, 16_000f, false // little endian
    );
    // --- VAD tuning constants ---
    private static final double SENSITIVITY_FACTOR = 3.5;  // multiplier for ambient RMS
    private static final double THR_MIN = 600.0;           // lower clamp (quiet rooms)
    private static final double THR_MAX = 2500.0;          // upper clamp (noisy rooms)
    // --- VAD frame/hysteresis tuning ---
    private static final int FRAME_MS = 20;        // per-frame analysis window (~20 ms)
    private static final int ON_MIN_FRAMES = 6;    // >= ~120 ms above threshold => speech ON
    private static final int OFF_MIN_FRAMES = 10;  // >= ~200 ms below off-threshold => speech OFF
    private static final double OFF_FACTOR = 0.7;  // off-threshold = threshold * OFF_FACTOR

    /**
     * Reads 'seconds' of audio from Mixer[ mixerIndex ] and prints levels every ~200 ms.
     */
    public static void runLevelProbe(int mixerIndex, int seconds) throws Exception {
        if (seconds <= 0) seconds = 5;

        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        if (mixerIndex < 0 || mixerIndex >= infos.length) {
            throw new IllegalArgumentException("Mixer-Index außerhalb des gültigen Bereichs: " + mixerIndex);
        }
        Mixer.Info info = infos[mixerIndex];
        Mixer mixer = AudioSystem.getMixer(info);

        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!mixer.isLineSupported(lineInfo)) {
            // Some drivers don't advertise exactly; try default mixer as fallback:
            if (!AudioSystem.isLineSupported(lineInfo)) {
                throw new LineUnavailableException("Ziel-Format (16kHz/16bit/mono) wird von diesem Mixer nicht gemeldet.");
            }
        }

        TargetDataLine line = null;
        try {
            line = (TargetDataLine) mixer.getLine(lineInfo);
            line.open(FORMAT);
            line.start();

            System.out.println("Mikrofon: " + info.getName() + "  | Format: " + FORMAT.toString());
            System.out.println("Aufnahme läuft für " + seconds + "s ... (Strg+C zum Abbrechen)");

            // 200ms buffer: 16000 samples/s * 2 bytes * 0.2s = 6400 bytes
            final int bytesPerFrame = FORMAT.getFrameSize();
            final int framesPerChunk = (int) (FORMAT.getFrameRate() * 0.2); // 200ms
            final int chunkSize = framesPerChunk * bytesPerFrame;

            byte[] buf = new byte[chunkSize];
            long end = System.currentTimeMillis() + seconds * 1000L;

            while (System.currentTimeMillis() < end) {
                int read = line.read(buf, 0, buf.length);
                if (read <= 0) continue;

                // 16-bit little endian -> signed short samples
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, read).order(ByteOrder.LITTLE_ENDIAN);
                int samples = read / 2;
                long sumSquares = 0;
                int peakAbs = 0;

                for (int i = 0; i < samples; i++) {
                    short s = bb.getShort();
                    int v = s;
                    int abs = Math.abs(v);
                    if (abs > peakAbs) peakAbs = abs;
                    sumSquares += (long) v * v;
                }

                double rms = Math.sqrt(sumSquares / (double) samples);
                // Approximate dBFS (Max 16-bit = 32768)
                double dbfsRms = 20.0 * Math.log10(rms / 32768.0 + 1e-12);
                double dbfsPeak = 20.0 * Math.log10(peakAbs / 32768.0 + 1e-12);

                System.out.printf("RMS: %6.1f dBFS | Peak: %6.1f dBFS%n", dbfsRms, dbfsPeak);
            }

            System.out.println("Fertig.");
        } finally {
            if (line != null) {
                try { line.stop(); } catch (Exception ignored) {}
                try { line.close(); } catch (Exception ignored) {}
            }
        }
    }

    // Simplified wrapper for CLI calls. Same logic as runLevelProbe() with safe exception handling.
    public static void test(int mixerIndex, int seconds) {
        try {
            runLevelProbe(mixerIndex, seconds);
        } catch (IllegalArgumentException ex) {
            System.out.println("[ERROR] Ungültiger Geräteindex: " + ex.getMessage());
        } catch (LineUnavailableException ex) {
            System.out.println("[ERROR] Mikrofon-Zugriff fehlgeschlagen: " + ex.getMessage());
        } catch (Exception ex) {
            System.out.println("[ERROR] MicTester.test: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Result DTO for the energy-based VAD precheck.
     * Minimal fields for CLI printing and future extensions.
     */
    public static final class VadResult {
        public boolean voiceDetected;   // final decision
        public double averageRms;       // average linear RMS (0..32768)
        public double peak;             // peak absolute sample (0..32768)
        public double threshold;        // linear RMS threshold used
        public int durationSec;         // measured duration (seconds)
        public String deviceName;       // mixer/device label
        // Frame/Hysteresis reporting (filled in B.1.3b)
        public int framesAbove;        // number of 20ms frames above ON threshold
        public int framesBelow;        // number of 20ms frames below OFF threshold
        public String decision;        // "ON" (speech) or "OFF" (silence)
    }

    /**
     * Lightweight Voice Activity precheck.
     * Captures 'seconds' of audio, estimates ambient noise in the first ~0.4s,
     * computes RMS/Peak, derives a dynamic threshold and decides if speech is present.
     *
     * @param mixerIndex index from AudioSystem.getMixerInfo() (same as runLevelProbe)
     * @param seconds    capture duration; if <=0 defaults to 3 (max 15)
     * @return VadResult with computed values and decision
     */
    public static VadResult vadCheck(int mixerIndex, int seconds) {
        VadResult out = new VadResult();
        if (seconds <= 0) seconds = 3;
        if (seconds > 15) seconds = 15;
        out.durationSec = seconds;

        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        if (mixerIndex < 0 || mixerIndex >= infos.length) {
            throw new IllegalArgumentException("Mixer-Index außerhalb des gültigen Bereichs: " + mixerIndex);
        }
        Mixer.Info info = infos[mixerIndex];
        out.deviceName = info.getName();

        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
        TargetDataLine line = null;
        try {
            // Prefer chosen mixer; if unsupported, fall back to system default line.
            Mixer mixer = AudioSystem.getMixer(info);
            boolean mixerOk = mixer.isLineSupported(lineInfo);
            boolean sysOk   = AudioSystem.isLineSupported(lineInfo);
            if (!mixerOk && !sysOk) {
                throw new LineUnavailableException("Ziel-Format (16kHz/16bit/mono) wird nicht gemeldet.");
            }
            if (mixerOk) {
                line = (TargetDataLine) mixer.getLine(lineInfo);
            } else {
                line = (TargetDataLine) AudioSystem.getLine(lineInfo);
            }

            line.open(FORMAT);
            line.start();

            // --- Sampling setup ---
            final int bytesPerSample = 2;                           // 16-bit PCM
            final int samplesPerSecond = (int) FORMAT.getSampleRate();
            final int totalSamplesTarget = samplesPerSecond * seconds;
            final int readBlockSamples = 1024;                      // ~64 ms at 16 kHz
            final int readBlockBytes = readBlockSamples * bytesPerSample;
            byte[] buf = new byte[readBlockBytes];

            // --- Frame windowing (20 ms) ---
            final int samplesPerFrame = (int) (FORMAT.getSampleRate() * FRAME_MS / 1000.0);
            long frameSumSquares = 0L;    // energy accumulator for the current frame
            int  frameSamples    = 0;     // how many samples accumulated into current frame

// Thresholds become known after ambient window is filled
            boolean thresholdsReady = false;
            double onThr = 0.0;
            double offThr = 0.0;

// Frame counters (for reporting)
            int framesAbove = 0;
            int framesBelow = 0;

            // --- Accumulators ---
            long sumSquares = 0L;
            int totalSamples = 0;
            int peakAbs = 0;

            // Ambient window ~0.4 s for baseline (noise floor)
            final int ambientSamplesTarget = (int) (FORMAT.getSampleRate() * 0.4); // ~6400
            long ambientSumSquares = 0L;
            int ambientCount = 0;

            // Hysteresis run-length tracking
            int runAbove = 0;       // current consecutive frames >= onThr
            int runBelow = 0;       // current consecutive frames <= offThr
            int maxRunAbove = 0;    // longest ON run observed

            // --- Read loop ---
            while (totalSamples < totalSamplesTarget) {
                int read = line.read(buf, 0, buf.length);
                if (read <= 0) break;

                int samples = read / bytesPerSample;

                // Parse little-endian 16-bit samples
                for (int i = 0; i < samples; i++) {
                    int lo = buf[i * 2] & 0xFF;
                    int hi = buf[i * 2 + 1];         // signed
                    short sample = (short) ((hi << 8) | lo);
                    int abs = Math.abs(sample);
                    if (abs > peakAbs) peakAbs = abs;

                    // Accumulate totals for average RMS reporting
                    sumSquares += (long) sample * sample;

                    // --- Ambient collection in the very beginning (~0.4s) ---
                    if (ambientCount < ambientSamplesTarget) {
                        ambientSumSquares += (long) sample * sample;
                        ambientCount++;

                        // When ambient window is complete, compute thresholds once
                        if (!thresholdsReady && ambientCount >= ambientSamplesTarget) {
                            double ambientRmsNow = Math.sqrt(
                                    ambientSumSquares / (double) Math.max(1, ambientCount)
                            );
                            double thrNow = ambientRmsNow * SENSITIVITY_FACTOR;
                            if (thrNow < THR_MIN) thrNow = THR_MIN;
                            if (thrNow > THR_MAX) thrNow = THR_MAX;
                            onThr = thrNow;
                            offThr = thrNow * OFF_FACTOR;
                            thresholdsReady = true;
                        }
                    }

                    // --- Per-frame energy (20 ms) AFTER thresholds are known ---
                    if (thresholdsReady) {
                        frameSumSquares += (long) sample * sample;
                        frameSamples++;
                        if (frameSamples >= samplesPerFrame) {
                            double frameRms = Math.sqrt(frameSumSquares / (double) frameSamples);
                            if (frameRms >= onThr) {
                                framesAbove++;
                                runAbove++;
                                if (runAbove > maxRunAbove) maxRunAbove = runAbove;
                                runBelow = 0; // reset OFF run
                            } else if (frameRms <= offThr) {
                                framesBelow++;
                                runBelow++;
                                runAbove = 0; // reset ON run
                            } else {
                                // between onThr and offThr => keine Änderung der Runs
                            }
                            // reset frame accumulators
                            frameSumSquares = 0L;
                            frameSamples = 0;
                        }
                    }
                }

                totalSamples += samples;
            }

            // --- Compute RMS and Peak ---
            double avgRms = Math.sqrt(sumSquares / (double) Math.max(1, totalSamples));
            out.averageRms = avgRms;
            out.peak = peakAbs;

            // --- Ambient-based dynamic threshold (16-bit linear scale) ---
            double ambientRms = (ambientCount > 0)
                    ? Math.sqrt(ambientSumSquares / (double) ambientCount)
                    : Math.max(1.0, out.averageRms * 0.6); // fallback if too short

            double thr = ambientRms * SENSITIVITY_FACTOR;       // sensitivity factor
            if (thr < THR_MIN) thr = THR_MIN;      // lower clamp for quiet rooms
            if (thr > THR_MAX) thr = THR_MAX;      // upper clamp for noisy rooms
            out.threshold = thr;

            // Fill frame counters into result (for reporting only in this step)
            out.framesAbove = framesAbove;
            out.framesBelow = framesBelow;
            // Keep decision label consistent with current boolean
            out.decision = out.voiceDetected ? "ON" : "OFF";

            // --- Decision (frame-based with hysteresis) ---
            // Sprach-"ON", wenn wir mindestens ON_MIN_FRAMES am Stück über onThr hatten
            boolean frameDecision = maxRunAbove >= ON_MIN_FRAMES;

            // Peak-Fallback: starke Transienten dürfen eine knappe Avg-Situation bestätigen
            boolean peakFallback = out.peak >= (out.threshold * 1.5);

            // Endgültige Entscheidung: Frames bevorzugt, Peak als Zusatz-Signal
            out.voiceDetected = frameDecision || peakFallback;


            return out;
        } catch (LineUnavailableException e) {
            throw new RuntimeException("Mikrofon-Zugriff fehlgeschlagen: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("MicTester.vadCheck: " + e.getMessage(), e);
        } finally {
            if (line != null) {
                try { line.stop(); } catch (Exception ignored) {}
                try { line.close(); } catch (Exception ignored) {}
            }
        }
    }
}