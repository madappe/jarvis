package ai.jarvis.cli;

import ai.jarvis.executor.WindowsExecutor;
import ai.jarvis.core.health.HealthService;
import ai.jarvis.core.health.HealthStatus;
import ai.jarvis.stt.audio.MicTester;
import ai.jarvis.stt.audio.AudioCapture;
import ai.jarvis.stt.audio.WavIO;
import ai.jarvis.stt.api.SttListener;
import ai.jarvis.stt.impl.DummySttAdapter;
import ai.jarvis.stt.vosk.VoskBootstrap;
import ai.jarvis.stt.vosk.VoskSttAdapter;
import ai.jarvis.stt.vosk.VoskDebug;
import ai.jarvis.core.skills.SkillsLoader;
import ai.jarvis.core.skills.SkillDefinition;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ai.jarvis.cli.SkillDispatcher;


/**
 * Einstiegspunkt für das UI (Main-Klasse).
 * Unterstützt:
 *  - öffne|open|start <App>
 *  - --help
 *  - --version
 */
public class JarvisCli {

    private final SkillDispatcher dispatcher = new SkillDispatcher();

    public static void main(String[] args) {

        // Keine Args -> Hilfe anzeigen
        if (args.length == 0) {
            printUsage();
            return;
        }

        // Unified CLI parsing (compact & consistent)
        final String cmd = (args == null || args.length == 0) ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
        final String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        String joined = String.join(" ", args).trim();

        // --- neue Schalter ---
        if ("--help".equals(cmd) || "-h".equals(cmd)) {
            printHelp();
            return;
        }
        if ("--help-en".equals(cmd)) {
            printHelpEn();
            return;
        }
        if ("--version".equals(cmd) || "-v".equals(cmd)) {
            printVersion();
            return;
        }
        if ("--health".equals(cmd)) {
            runHealth();
            return;
        }
        if ("--list-mics".equals(cmd)) {
            handleListMics();
            return;
        }
        if ("--mic-vad".equals(cmd)) {
            handleMicVad(rest);
            return;
        }
        if ("--mic-test".equals(cmd)) {
            ai.jarvis.stt.audio.AudioDeviceService svc = new ai.jarvis.stt.audio.AudioDeviceService();

            // 1️⃣  Preferred Mic aus System-Property oder ENV
            String preferred = System.getProperty("jarvis.audio.preferredMic", "").trim();
            if (preferred.isEmpty()) {
                String env = System.getenv("JARVIS_AUDIO_PREFERRED");
                if (env != null) preferred = env.trim();
            }

            // 2️⃣  Device-Index ermitteln
            int selectedIndex = -1;
            try {
                // Wenn erster Parameter eine Zahl ist → nutze diese
                if (rest.length >= 1) {
                    selectedIndex = Integer.parseInt(rest[0]) - 1; // CLI zeigt 1-basiert, intern 0-basiert
                } else {
                    selectedIndex = svc.selectPreferredDeviceIndex(preferred);
                }
            } catch (NumberFormatException ex) {
                System.out.println("[WARN] Ungültiger Index, benutze bevorzugtes Gerät stattdessen.");
                selectedIndex = svc.selectPreferredDeviceIndex(preferred);
            }

            // 3️⃣  Dauer (Sekunden)
            int seconds = 5; // Standarddauer
            if (rest.length >= 2) {
                try {
                    seconds = Integer.parseInt(rest[1]);
                } catch (NumberFormatException ignore) {
                    System.out.println("[WARN] Ungültige Sekundenangabe – verwende 5 Sekunden.");
                }
            }

            // 4️⃣  Sicherheit: wenn kein Gerät verfügbar
            if (selectedIndex < 0) {
                System.out.println("[ERROR] Kein gültiges Eingabegerät gefunden!");
                return;
            }

            System.out.println("[AUDIO] Starte Mikrofontest – Index " + (selectedIndex + 1) + " (" + seconds + "s)");
            ai.jarvis.stt.audio.MicTester.test(selectedIndex, seconds);
            return;
        }
        if ("--mic-capture".equals(cmd)) {
            ai.jarvis.stt.audio.AudioDeviceService svc = new ai.jarvis.stt.audio.AudioDeviceService();

            // 1️⃣ Preferred Mic aus Property oder ENV lesen
            String preferred = System.getProperty("jarvis.audio.preferredMic", "").trim();
            if (preferred.isEmpty()) {
                String env = System.getenv("JARVIS_AUDIO_PREFERRED");
                if (env != null) preferred = env.trim();
            }

            // 2️⃣ Parameter interpretieren
            int idx = -1;
            int secs = 5; // Standarddauer
            try {
                if (rest.length == 0) {
                    // Kein Parameter angegeben → automatisches Gerät
                    idx = svc.selectPreferredDeviceIndex(preferred);
                } else if (rest.length == 1) {
                    // 🟢 NEU: Ein Argument = bevorzugt Index
                    try {
                        idx = Integer.parseInt(rest[0]) - 1;
                        // Sekunden bleiben Standard (5)
                    } catch (NumberFormatException ex) {
                        // Kein Index → dann Sekunden
                        try {
                            secs = Integer.parseInt(rest[0]);
                            idx = svc.selectPreferredDeviceIndex(preferred);
                        } catch (NumberFormatException ex2) {
                            System.out.println("[ERROR] Ungültiges Argument. Nutzung: --mic-capture [idx] [sekunden]");
                            return;
                        }
                    }
                } else {
                    // Mehrere Parameter: Index + Sekunden
                    idx = Integer.parseInt(rest[0]) - 1;
                    secs = Integer.parseInt(rest[1]);
                }
            } catch (NumberFormatException ex) {
                System.out.println("[WARN] Ungültiger Index oder Sekundeneingabe – benutze bevorzugtes Gerät und 5s.");
                idx = svc.selectPreferredDeviceIndex(preferred);
                secs = 5;
            }

            // 3️⃣ Sicherheit: Index prüfen
            if (idx < 0) {
                System.out.println("[ERROR] Kein gültiges Eingabegerät gefunden!");
                return;
            }

            // 4️⃣ Aufnahme starten
            try {
                ai.jarvis.stt.audio.AudioCapture cap = new ai.jarvis.stt.audio.AudioCapture(2000);
                cap.start(idx);
                System.out.println("[AUDIO] Aufnahme gestartet (Index " + (idx + 1) + ") für " + secs + "s ...");
                Thread.sleep(secs * 1000L);
                System.out.println("[AUDIO] Buffered: " + cap.bufferedBytes() + " Bytes (~" + cap.getBufferedMillis() + " ms)");
                cap.stop();
                System.out.println("[AUDIO] Aufnahme gestoppt.");
            } catch (Exception e) {
                System.err.println("[ERROR] Mic-Capture fehlgeschlagen: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }
        if ("--mic-dump".equals(cmd)) {
            ai.jarvis.stt.audio.AudioDeviceService svc = new ai.jarvis.stt.audio.AudioDeviceService();

            // 1) Preferred Mic aus Property oder ENV lesen
            String preferred = System.getProperty("jarvis.audio.preferredMic", "").trim();
            if (preferred.isEmpty()) {
                String env = System.getenv("JARVIS_AUDIO_PREFERRED");
                if (env != null) preferred = env.trim();
            }

            // Defaults
            int idx = -1;           // 0-basiert intern
            int secs = 3;           // Default-Aufnahmedauer
            String out = "mic_dump.wav";

            // 2) Argumente robust interpretieren
            try {
                if (rest.length == 0) {
                    // keine Args → auto index, default secs+out
                    idx = svc.selectPreferredDeviceIndex(preferred);

                } else if (rest.length == 1) {
                    // 1 Arg → bevorzugt als Index interpretieren
                    try {
                        idx = Integer.parseInt(rest[0]) - 1;  // einzelner Wert = Index
                        // secs default 3, out default "mic_dump.wav"
                    } catch (NumberFormatException nfe) {
                        // sonst Dateiname
                        out = rest[0];
                        idx = svc.selectPreferredDeviceIndex(preferred);
                    }

                } else if (rest.length == 2) {
                    // 2 Args → (idx secs) ODER (idx out) ODER (out secs)
                    boolean a0Int, a1Int;
                    int a0 = 0, a1 = 0;
                    try { a0 = Integer.parseInt(rest[0]); a0Int = true; } catch (NumberFormatException e) { a0Int = false; }
                    try { a1 = Integer.parseInt(rest[1]); a1Int = true; } catch (NumberFormatException e) { a1Int = false; }

                    if (a0Int && a1Int) {
                        // idx secs
                        idx = a0 - 1;
                        secs = a1;
                    } else if (a0Int) {
                        // idx out
                        idx = a0 - 1;
                        out = rest[1];
                    } else if (a1Int) {
                        // out secs (auto idx)
                        out = rest[0];
                        secs = a1;
                        idx = svc.selectPreferredDeviceIndex(preferred);
                    } else {
                        System.out.println("[ERROR] Ungültige Argumente. Nutzung: --mic-dump [idx] [sekunden] [datei]");
                        System.out.println("Beispiele: --mic-dump 8 3 sample.wav  |  --mic-dump 3  |  --mic-dump sample.wav 5");
                        return;
                    }

                } else {
                    // ≥3 Args → klassisch: idx secs out
                    idx = Integer.parseInt(rest[0]) - 1;
                    secs = Integer.parseInt(rest[1]);
                    out = rest[2];
                }
            } catch (NumberFormatException nfe) {
                System.out.println("[WARN] Ungültiger Index/Sekunden – verwende bevorzugtes Gerät und 3s.");
                idx = svc.selectPreferredDeviceIndex(preferred);
                secs = 3;
            }

            // 3) Sicherheit: Index prüfen
            if (idx < 0) {
                System.out.println("[ERROR] Kein gültiges Eingabegerät gefunden!");
                return;
            }

            // 4) Aufnahme + Snapshot speichern
            try {
                ai.jarvis.stt.audio.AudioCapture cap = new ai.jarvis.stt.audio.AudioCapture(5000); // 5s Ringpuffer
                cap.start(idx);
                System.out.println("[AUDIO] Aufnahme gestartet (Index " + (idx + 1) + "). Warte " + secs + "s ...");
                Thread.sleep(secs * 1000L);

                byte[] pcm = cap.snapshot();
                cap.stop();

                if (pcm == null || pcm.length == 0) {
                    System.out.println("[ERROR] Snapshot leer.");
                    return;
                }

                ai.jarvis.stt.audio.WavIO.savePcm16Mono16kAsWav(pcm, java.nio.file.Path.of(out));
                System.out.println("[AUDIO] Snapshot: " + pcm.length + " Bytes gespeichert als " + out);

            } catch (Exception e) {
                System.err.println("[ERROR] mic-dump fehlgeschlagen: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }
        if ("--stt-dummy".equals(cmd)) {
            // Phrase aus restlichen Args zusammensetzen; Default, wenn nichts angegeben
            String phrase = (args.length >= 2) ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    : "öffne notepad";
            DummySttAdapter ad = new DummySttAdapter(phrase);
            ad.setListener(new SttListener() {
                @Override public void onPartial(String text) { System.out.println("[partial] " + text); }
                @Override public void onFinal(String text)   { System.out.println("[final]   " + text); }
            });
            System.out.println("Dummy STT gestartet mit Phrase: \"" + phrase + "\"");
            try {
                ad.start();
                while (ad.isRunning()) {
                    Thread.sleep(50); // warten bis final kam
                }
            } catch (Exception e) {
                System.err.println("Dummy STT Fehler: " + e.getMessage());
                e.printStackTrace();
            } finally {
                ad.stop();
            }
            return;
        }
        if ("--stt-dummy-exec".equals(cmd)) {
            String phrase = (args.length >= 2)
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    : "öffne notepad";
            DummySttAdapter ad = new DummySttAdapter(phrase);
            ad.setListener(new SttListener() {
                @Override public void onPartial(String text) {
                    System.out.println("[partial] " + text);
                }
                @Override public void onFinal(String text) {
                    System.out.println("[final]   " + text);

                    // Mehrwort-Appnamen + Aliase
                    String lower = text.toLowerCase().trim();
                    String[] parts = lower.split("\\s+");
                    if (parts.length >= 2 &&
                            ("öffne".equals(parts[0]) || "open".equals(parts[0]) || "start".equals(parts[0]))) {

                        // alles nach dem Verb zusammenfassen (Mehrwort-Unterstützung)
                        String appRaw = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
                        String app = normalizeApp(appRaw); // Aliase anwenden (calc, mspaint, notepad, ...)

                        try {
                            WindowsExecutor exec = new WindowsExecutor();
                            exec.launchApp(app);
                            System.out.println("Ausgeführt: öffne " + app);
                        } catch (Exception e) {
                            System.err.println("Ausführung fehlgeschlagen: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("Kein ausführbarer Befehl erkannt.");
                    }
                }
            });
            System.out.println("Dummy STT (exec) gestartet mit Phrase: \"" + phrase + "\"");
            try {
                ad.start();
                while (ad.isRunning()) { Thread.sleep(50); }
            } catch (Exception e) {
                System.err.println("Dummy STT Fehler: " + e.getMessage());
                e.printStackTrace();
            } finally {
                ad.stop();
            }
            return;
        }
        if ("--vosk-check".equals(cmd)) {
            if (args.length < 2) {
                System.out.println("Nutzung: --vosk-check <ModelOrdner>");
                System.out.println("Beispiel: --vosk-check C:\\models\\vosk\\vosk-model-small-de-0.15");
                return;
            }
            String modelDir = args[1];
            try {
                VoskBootstrap.checkModel(modelDir);
                System.out.println("OK: Modell ist ladbar.");
            } catch (Exception e) {
                System.err.println("Fehler beim Laden des Modells: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }
        if ("--stt-vosk".equals(cmd)) {
            if (args.length < 3) {
                System.out.println("Nutzung: --stt-vosk <ModelOrdner> <MixerIndex> [Sekunden]");
                System.out.println("Beispiel: --stt-vosk \"C:\\models\\vosk\\vosk-model-small-en-us-0.15\" 7 10");
                return;
            }
            String modelDir = args[1];
            int mixerIdx = Integer.parseInt(args[2]);
            int secs = (args.length >= 4) ? Integer.parseInt(args[3]) : 10;

            VoskSttAdapter ad = new VoskSttAdapter(modelDir, mixerIdx);
            ad.setListener(new SttListener() {
                @Override public void onPartial(String text) { System.out.println("[partial] " + text); }
                @Override public void onFinal(String text)   { System.out.println("[final]   " + text); }
            });

            try {
                System.out.println("Starte Vosk STT für " + secs + "s ...");
                ad.start();
                Thread.sleep(secs * 1000L);
            } catch (Exception e) {
                System.err.println("Vosk STT Fehler: " + e.getMessage());
                e.printStackTrace();
            } finally {
                ad.stop();
                System.out.println("Vosk STT gestoppt.");
            }
            return;
        }
        if ("--stt-vosk-exec".equals(cmd)) {
            if (args.length < 3) {
                System.out.println("Nutzung: --stt-vosk-exec <ModelOrdner> <MixerIndex> [Sekunden]");
                System.out.println("Beispiel (EN): --stt-vosk-exec \"C:\\models\\vosk\\vosk-model-small-en-us-0.15\" 7 10");
                System.out.println("Beispiel (DE): --stt-vosk-exec \"C:\\models\\vosk\\vosk-model-small-de-0.15\" 7 10");
                return;
            }
            String modelDir = args[1];
            int mixerIdx = Integer.parseInt(args[2]);
            int secs = (args.length >= 4) ? Integer.parseInt(args[3]) : 12;

            VoskSttAdapter ad = new VoskSttAdapter(modelDir, mixerIdx);
            ad.setListener(new SttListener() {
                @Override public void onPartial(String text) {
                    if (text != null && !text.isBlank()) {
                        System.out.println("[partial] " + text);
                    }
                }
                @Override
                public void onFinal(String text) {
                    if (text == null) return;

                    // Raw final
                    System.out.println("[final]   " + text);

                    // 1) STT-Text normalisieren (aus Config)
                    String norm = ai.jarvis.core.config.AliasConfig.get().normalizeSttText(text);
                    System.out.println("[debug] norm='" + norm + "'");

                    // 2) Sonderzeichen zu Spaces machen (macht z.B. "up=open calculator" -> "up open calculator")
                    String cleaned = norm.replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();  // nur Buchstaben/Ziffern behalten
                    String[] toks = cleaned.isEmpty() ? new String[0] : cleaned.split("\\s+");

                    if (toks.length == 0) {
                        System.out.println("[debug] tokens=0 → kein Befehl");
                        return;
                    }

                    // 3) Verb im Token-Stream suchen
                    int verbIdx = -1;
                    for (int i = 0; i < toks.length; i++) {
                        String t = toks[i];
                        if ("open".equals(t) || "start".equals(t) || "öffne".equals(t)) {
                            verbIdx = i; break;
                        }
                    }
                    if (verbIdx < 0) {
                        System.out.println("[debug] kein Verb (open/start/öffne) gefunden in tokens=" + java.util.Arrays.toString(toks));
                        return;
                    }

                    // 4) Alles NACH dem Verb ist der App-Name (Mehrwort erlaubt)
                    if (verbIdx >= toks.length - 1) {
                        System.out.println("[debug] kein App-Name nach Verb vorhanden");
                        return;
                    }
                    String appRaw = String.join(" ", java.util.Arrays.copyOfRange(toks, verbIdx + 1, toks.length));

                    // 5) App-Alias anwenden (aus Config)
                    String app = ai.jarvis.core.config.AliasConfig.get().normalizeApp(appRaw);

                    System.out.println("[debug] verb='" + toks[verbIdx] + "', appRaw='" + appRaw + "' → app='" + app + "'");

                    try {
                        ai.jarvis.executor.WindowsExecutor exec = new ai.jarvis.executor.WindowsExecutor();
                        exec.launchApp(app);
                        System.out.println("Ausgeführt: " + toks[verbIdx] + " " + app);
                    } catch (Exception e) {
                        System.err.println("Ausführung fehlgeschlagen: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });

            try {
                System.out.println("Starte Vosk STT (exec) für " + secs + "s ...");
                ad.start();
                Thread.sleep(secs * 1000L);
            } catch (Exception e) {
                System.err.println("Vosk STT Fehler: " + e.getMessage());
                e.printStackTrace();
            } finally {
                ad.stop();
                System.out.println("Vosk STT gestoppt.");
            }
            return;
        }
        if ("--stt-vosk-debug".equals(cmd)) {
            if (args.length < 3) {
                System.out.println("Nutzung: --stt-vosk-debug <ModelOrdner> <MixerIndex> [Sekunden]");
                System.out.println("Beispiel: --stt-vosk-debug \"C:\\models\\vosk\\vosk-model-small-en-us-0.15\" 7 10");
                return;
            }
            String modelDir = args[1];
            int mixerIdx = Integer.parseInt(args[2]);
            int secs = (args.length >= 4) ? Integer.parseInt(args[3]) : 10;
            try {
                VoskDebug.run(modelDir, mixerIdx, secs);
            } catch (Exception e) {
                System.err.println("VoskDebug Fehler: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }
        if ("--list-skills".equals(cmd)) {
            // Keine zusätzlichen Argumente notwendig
            listSkills();
            return;
        }
        if ("--dry-run".equals(cmd)) {
            // Baue den Satz aus den restlichen Argumenten zusammen
            String utterance = String.join(" ", rest).trim();

            // Prüfung: wurde überhaupt ein Text angegeben?
            if (utterance.isEmpty()) {
                System.out.println("[ERROR] Fehlender Text für --dry-run.");
                System.out.println("Nutzung: --dry-run <satz>");
                return;
            }

            // Bestehende Logik aufrufen
            dryRun(utterance);
            return;
        }
        if ("--route".equals(cmd)) {
            // Baue den gesamten Satz aus den restlichen Argumenten zusammen
            String utterance = String.join(" ", rest).trim();

            // Prüfung: wurde überhaupt ein Text angegeben?
            if (utterance.isEmpty()) {
                System.out.println("[ERROR] Fehlender Text für --route.");
                System.out.println("Nutzung: --route <satz>");
                return;
            }

            // Bestehende Logik aufrufen
            routeOnce(utterance);
            return;
        }
        if ("--say".equals(cmd)) {
            // Join all remaining arguments into one text string
            String text = String.join(" ", rest).trim();

            // Guard: missing or empty input
            if (text.isEmpty()) {
                System.out.println("[ERROR] Fehlender Text für --say.");
                System.out.println("Nutzung: --say <text>");
                System.exit(2);
            }

            ai.jarvis.executor.skills.SayExecutor say = new ai.jarvis.executor.skills.SayExecutor();
            long start = System.nanoTime();
            try {
                say.execute(text);
                long tookMs = (System.nanoTime() - start) / 1_000_000L;

                // Format elapsed time
                String duration = (tookMs < 1000)
                        ? tookMs + " ms"
                        : String.format(java.util.Locale.ROOT, "%.1f s", tookMs / 1000.0);

                System.out.println("[OK] Spoken (" + say.engineName() + ") in " + duration +
                        ": \"" + text + "\"");
                System.exit(0);

            } catch (ai.jarvis.core.tts.TtsException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(java.util.Locale.ROOT);
                if (msg.contains("timed out")) {
                    System.out.println("[ERROR] TTS timed out. Try shorter text.");
                } else if (msg.contains("powershell")) {
                    System.out.println("[ERROR] PowerShell TTS failed. Ensure 'powershell.exe' is available and audio output is set.");
                } else if (msg.contains("exited with code")) {
                    System.out.println("[ERROR] TTS engine error. Check audio device and try again.");
                } else {
                    System.out.println("[ERROR] TTS error: " + ex.getMessage());
                }
                System.exit(3);
            }

            return;
        }
        if ("--run".equals(cmd)) {
            // -------------------------------------------------------------
            // 1) Build the full user sentence from CLI args (after --run)
            // -------------------------------------------------------------
            String sentence = String.join(" ", rest).trim();

            // Pflichtargument prüfen
            if (sentence.isEmpty()) {
                System.out.println("[ERROR] Fehlender Text für --run.");
                System.out.println("Nutzung: --run <satz>");
                return;
            }

            // -------------------------------------------------------------
            // 2) Load skills and router (same as before)
            // -------------------------------------------------------------
            ai.jarvis.core.skills.SkillsLoader loader = new ai.jarvis.core.skills.SkillsLoader();
            java.util.List<ai.jarvis.core.skills.SkillDefinition> skills = loader.loadMergedSkills();

            ai.jarvis.core.router.SkillRouter router = new ai.jarvis.core.router.SkillRouter();

            // -------------------------------------------------------------
            // 3) Early dispatch hook for "say.hello"
            // -------------------------------------------------------------
            try {
                java.util.Optional<?> optMatch = router.route(sentence, skills); // your API

                if (optMatch != null && optMatch.isPresent()) {
                    Object match = optMatch.get();

                    // --- Resolve skill name ---
                    String skillName = null;
                    final String[] candidates = { "getName", "name", "getSkillName" };
                    for (String m : candidates) {
                        try {
                            java.lang.reflect.Method gm = match.getClass().getMethod(m);
                            Object v = gm.invoke(match);
                            if (v instanceof String s && !s.isBlank()) {
                                skillName = s;
                                break;
                            }
                        } catch (NoSuchMethodException ignore) {
                        } catch (Exception ignore) {
                        }
                    }

                    // --- Resolve params map ---
                    java.util.Map<String, String> params = java.util.Collections.emptyMap();
                    final String[] paramCandidates = { "getParams", "params" };
                    for (String m : paramCandidates) {
                        try {
                            java.lang.reflect.Method gm = match.getClass().getMethod(m);
                            Object v = gm.invoke(match);
                            if (v instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, String> cast = (java.util.Map<String, String>) v;
                                params = cast;
                                break;
                            }
                        } catch (NoSuchMethodException ignore) {
                        } catch (Exception ignore) {
                        }
                    }

                    // --- Find matching SkillDefinition ---
                    ai.jarvis.core.skills.SkillDefinition matchedDef = null;
                    if (skillName != null) {
                        for (ai.jarvis.core.skills.SkillDefinition def : skills) {
                            String defName = null;
                            try {
                                try {
                                    java.lang.reflect.Field f = def.getClass().getField("name");
                                    Object v = f.get(def);
                                    if (v instanceof String s) defName = s;
                                } catch (NoSuchFieldException ignore) {
                                    final String[] nameGetters = { "getName", "name", "getId", "id", "getKey", "key", "getType", "type" };
                                    for (String nm : nameGetters) {
                                        try {
                                            java.lang.reflect.Method gm = def.getClass().getMethod(nm);
                                            Object v = gm.invoke(def);
                                            if (v instanceof String s && !s.isBlank()) {
                                                defName = s;
                                                break;
                                            }
                                        } catch (NoSuchMethodException ignore2) { }
                                    }
                                }
                            } catch (Exception ignore) { }

                            if (defName != null && defName.equalsIgnoreCase(skillName)) {
                                matchedDef = def;
                                break;
                            }
                        }
                    }

                    // --- Determine spoken text ---
                    String spoken = null;
                    String[] triggers = { "sage", "say", "sprich", "sag" };

                    if (params != null && !params.isEmpty()) {
                        java.util.List<String> values = new java.util.ArrayList<>(params.values());
                        for (int i = values.size() - 1; i >= 0; i--) {
                            String v = values.get(i);
                            if (v != null && !v.isBlank()) {
                                boolean isTrigger = false;
                                for (String t : triggers) {
                                    if (v.equalsIgnoreCase(t)) {
                                        isTrigger = true;
                                        break;
                                    }
                                }
                                if (!isTrigger) {
                                    spoken = v.trim();
                                    break;
                                }
                            }
                        }
                    }

                    if (spoken == null || spoken.isBlank()) {
                        String temp = sentence.trim();
                        for (String t : triggers) {
                            if (temp.toLowerCase(Locale.ROOT).startsWith(t.toLowerCase(Locale.ROOT) + " ")) {
                                spoken = temp.substring(t.length()).trim();
                                break;
                            }
                        }
                        if (spoken == null || spoken.isBlank()) {
                            spoken = sentence;
                        }
                    }

                    java.util.List<String> dispatcherArgs = java.util.Collections.singletonList(spoken);

                    if (matchedDef != null && "say.hello".equalsIgnoreCase(skillName)) {
                        ai.jarvis.cli.SkillDispatcher dispatcher = new ai.jarvis.cli.SkillDispatcher();
                        boolean handled = dispatcher.dispatch(matchedDef, dispatcherArgs);
                        if (handled) {
                            System.out.println("[CLI] Executed: " + skillName);
                            System.exit(0);
                        }
                    }
                }
            } catch (Throwable t) {
                System.out.println("[CLI] Debug: TTS hook skipped (" + t.getClass().getSimpleName() + ").");
            }

            // -------------------------------------------------------------
            // 4) Fallback: continue with RunCommandHandler
            // -------------------------------------------------------------
            ai.jarvis.executor.apps.AppLaunchExecutor launcher = new ai.jarvis.executor.apps.AppLaunchExecutor();
            RunCommandHandler handler = new RunCommandHandler(router, skills, launcher);

            int code = handler.run(sentence);
            System.exit(code);
        }
        if ("--reload".equals(cmd)) {
            ai.jarvis.core.skills.SkillsLoader loader = new ai.jarvis.core.skills.SkillsLoader();
            java.util.List<ai.jarvis.core.skills.SkillDefinition> defs = loader.loadMergedSkills();

            if (defs.isEmpty()) {
                System.out.println("[CLI] Reloaded: 0 skills found.");
                System.exit(0);
            }

            System.out.println("[CLI] Reloaded: " + defs.size() + " skills.");
            for (ai.jarvis.core.skills.SkillDefinition d : defs) {
                System.out.println("  - " + d.name + (d.description != null ? " :: " + d.description : ""));
            }
            System.exit(0);
        }
        // ----------------------

        if (cmd.equals("öffne") || cmd.equals("open") || cmd.equals("start")) {
            if (args.length < 2) {
                System.out.println("Bitte App-Namen angeben. Beispiel: öffne notepad");
                return;
            }
            String appName = args[1];
            try {
                WindowsExecutor exec = new WindowsExecutor();
                exec.launchApp(appName);
                System.out.println("Starte: " + appName);
            } catch (Exception e) {
                System.err.println("Fehler beim Starten: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        // Unbekannter Befehl -> Hilfe
        printHelp();
    }

    private static String normalizeApp(String app) {
        String a = app.toLowerCase().trim();
        // einfache Aliase für Windows-Standardprogramme
        switch (a) {
            case "calculator":
            case "rechner":
                return "calc";
            case "paint":
                return "mspaint";
            case "editor":
                return "notepad";
            default:
                return app;
        }
    }

    // --list-skills: lädt YAMLs und listet sie
    private static void listSkills() {
        SkillsLoader loader = new SkillsLoader();
        List<SkillDefinition> defs = loader.loadMergedSkills();

        if (defs.isEmpty()) {
            System.out.println("[CLI] Keine Skills gefunden im Ordner: " + Paths.get(SkillsLoader.DEFAULT_SKILLS_DIR).toAbsolutePath());
            return;
        }

        System.out.println("[CLI] Geladene Skills (" + defs.size() + "):");
        for (SkillDefinition d : defs) {
            System.out.println("  - " + d.name + "  :: " + d.description);
        }
    }

    // --dry-run "<satz>": prüft einfache Regex-Matches aus dem YAML
// Ziel: Zeigen, welcher Skill *passen würde* und welche Gruppen wir extrahieren.
    private static void dryRun(String utterance) {
        SkillsLoader loader = new SkillsLoader();
        List<SkillDefinition> defs = loader.loadMergedSkills();

        if (defs.isEmpty()) {
            System.out.println("[CLI] Keine Skills geladen. Leerer 'skills/'-Ordner?");
            return;
        }

        String low = utterance.toLowerCase(Locale.ROOT).trim();
        System.out.println("[CLI] Dry-Run für: \"" + utterance + "\"");

        boolean any = false;
        for (SkillDefinition def : defs) {
            if (def.match == null || def.match.patterns == null) continue;

            for (String pat : def.match.patterns) {
                try {
                    Pattern p = Pattern.compile(pat, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    Matcher m = p.matcher(low);
                    if (m.matches()) {
                        any = true;
                        System.out.println("  -> Match: " + def.name + "  (Pattern: " + pat + ")");
                        // benannte Gruppen (z. B. (?P<app>...)) ausgeben:
                        // Java kennt (?<app>...), nicht (?P<app>...). In Regex oben nutzen wir daher Gruppenname mit ?<app>
                        // Falls du noch (?P<app>) in YAML hast, bitte auf (?<app>) ändern.
                        // Fallback: alle Gruppen ausgeben
                        for (int i = 1; i <= m.groupCount(); i++) {
                            System.out.println("     group[" + i + "]: " + m.group(i));
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("  [WARN] Ungültiges Pattern in " + def.name + ": " + pat + " -> " + ex.getMessage());
                }
            }
        }

        if (!any) {
            System.out.println("  Kein Skill-Match gefunden.");
        }
    }

    private static void printVersion() {
        // Später gern aus MANIFEST/Git ableiten; jetzt statisch
        System.out.println("Jarvis UI version: 0.1.0-SNAPSHOT");
    }

    private static void printHelp() {
        System.out.println("""
    Jarvis CLI – Hilfe

    Nutzung (Flags und Befehle):

    ─── Core ─────────────────────────────────────────────
      --help | -h                     Zeigt diese Hilfe
      --version | -v                  Zeigt die aktuelle Version
      --health                        Führt einen System-Health-Check aus

    ─── Audio ────────────────────────────────────────────
      --list-mics                     Listet verfügbare Mikrofone (Eingabegeräte)
      --mic-test <idx> [s]            Testet Aufnahmepegel für <s> Sekunden (Index aus --list-mics)
      --mic-capture <idx> <out.wav>   Nimmt <s> Sekunden auf und speichert WAV-Datei
      --mic-dump <idx> <out.raw> [s]  Dump roher PCM-Daten für Analyse
      --mic-vad [idx] [seconds]       Energy-based Voice Activity precheck (RMS/Peak/Threshold)

    ─── Speech-to-Text (STT) ─────────────────────────────
      --stt-dummy <satz>              Simuliert STT und zeigt Partial/Final-Ergebnisse
      --stt-dummy-exec <satz>         Simuliert STT und führt finalen Befehl aus
      --stt-vosk                      Startet Vosk-Modell (Live-Erkennung)
      --stt-vosk-exec                 Live-Erkennung → Ausführung über Skills
      --stt-vosk-debug                Zeigt Vosk-Debug-JSONs (Partial/Final)

    ─── Skills / Routing ─────────────────────────────────
      --list-skills                   Listet alle verfügbaren Skills
      --dry-run <satz>                Testet das Parsing ohne Ausführung
      --route <satz>                  Führt einen Satz über Router einmalig aus
      --run <satz>                    Erkennung + Ausführung über Skills
      --say <text>                    Spricht den Text direkt über TTS (ohne Routing)
      --reload                        Lädt alle Skills neu

    ─── Kurzbefehle ──────────────────────────────────────
      öffne|open|start <App-Name>     Startet eine Anwendung nach Name oder Alias

    Beispiele:
      öffne notepad
      --say "Hallo Jarvis"
      --run "sage hallo"
      --dry-run "öffne explorer"
      --reload
      --version
    """);
    }

    private static void printHelpEn() {
        System.out.println("""
    Jarvis CLI – Help

    Usage (flags and commands):

    ─── Core ─────────────────────────────────────────────
      --help | -h                     Show this help message
      --version | -v                  Display current version
      --health                        Run system health check

    ─── Audio ────────────────────────────────────────────
      --list-mics                     List available input devices (microphones)
      --mic-test <idx> [s]            Test audio levels for <s> seconds (index from --list-mics)
      --mic-capture <idx> <out.wav>   Record <s> seconds and save to WAV file
      --mic-dump <idx> <out.raw> [s]  Dump raw PCM data for analysis
      --mic-vad [idx] [seconds] : Energy-VAD Precheck (RMS/Peak/Threshold)

    ─── Speech-to-Text (STT) ─────────────────────────────
      --stt-dummy <sentence>          Simulate STT and print partial/final results
      --stt-dummy-exec <sentence>     Simulate STT and execute the recognized command
      --stt-vosk                      Start Vosk model (live recognition)
      --stt-vosk-exec                 Live recognition → execute skill automatically
      --stt-vosk-debug                Show raw Vosk debug JSONs (partial/final)

    ─── Skills / Routing ─────────────────────────────────
      --list-skills                   List all available skills
      --dry-run <sentence>            Test parsing without execution
      --route <sentence>              Route a single sentence through skill matcher
      --run <sentence>                Full recognition and skill execution
      --say <text>                    Speak text directly via TTS (no routing)
      --reload                        Reload all skill definitions

    ─── Shortcuts ────────────────────────────────────────
      open|start <App-Name>           Launch an application by name or alias

    Examples:
      open notepad
      --say "Hello Jarvis"
      --run "say hello"
      --dry-run "open explorer"
      --reload
      --version
    """);
    }

    private static void runHealth() {
        HealthService svc = new HealthService();
        HealthStatus hs = svc.check();

        System.out.println(hs.ok ? "HEALTH: OK" : "HEALTH: FAIL");
        hs.details.forEach((k, v) -> System.out.println("- " + k + ": " + v));
    }

    private static void printUsage() {
        System.out.println("Jarvis CLI – kein Befehl angegeben.");
        System.out.println("Verfügbare Hauptbefehle:");
        System.out.println("  --help           Zeigt alle verfügbaren Optionen");
        System.out.println("  --say \"Text\"     Spricht den angegebenen Text direkt (TTS)");
        System.out.println("  --run \"Satz\"     Führt Skill-Routing aus, z. B. \"sage hallo\"");
        System.out.println();
        System.out.println("Beispiel:");
        System.out.println("  java -jar cli/target/cli-jar-with-dependencies.jar --say \"hello there\"");
    }

    private static void routeOnce(String utterance) {
        var loader = new ai.jarvis.core.skills.SkillsLoader();
        var defs = loader.loadMergedSkills();
        var router = new ai.jarvis.core.router.SkillRouter();

        var result = router.route(utterance, defs);
        if (result.isEmpty()) {
            System.out.println("[CLI] Kein Match.");
            return;
        }

        var match = result.get();
        System.out.println("[CLI] ROUTE:");
        System.out.println("  skill:   " + match.getSkillName());
        System.out.println("  pattern: " + match.getPattern());
        System.out.println("  text:    " + match.getOriginalText());
        System.out.println("  params:  " + match.getParams());
    }

    // Purpose: Add --list-mics command to print available input devices
    private static void handleListMics() {
        try {
            ai.jarvis.stt.audio.AudioDeviceService svc = new ai.jarvis.stt.audio.AudioDeviceService();
            var devices = svc.listInputDevices();

            System.out.println("[CLI] Available input devices (TargetDataLine):");
            System.out.println("Requested format: " + svc.getRequestedFormat());

            // Optional bevorzugtes Mikro (per JVM-Prop oder ENV)
            String preferred = System.getProperty("jarvis.audio.preferredMic", "").trim();
            if (preferred.isEmpty()) {
                String env = System.getenv("JARVIS_AUDIO_PREFERRED");
                if (env != null) preferred = env.trim();
            }

            for (int i = 0; i < devices.size(); i++) {
                var d = devices.get(i);
                String compat = d.isSupportsFormat() ? "[OK]" : "[?]";
                System.out.printf("  %2d) %s %s%n", i + 1, compat, d.getName());
                System.out.printf("      desc: %s%n", d.getDescription());
            }

            // Auswahl anzeigen (Index ist 0-basiert, Ausgabe +1 zur Liste)
            int sel = svc.selectPreferredDeviceIndex(preferred);
            if (sel >= 0) {
                System.out.println();
                System.out.println("[AUDIO] Selected mixer index: " + (sel + 1));
                System.out.println("        Reason: preferred=\"" + (preferred == null ? "" : preferred) + "\" (fallback if needed)");
            }

            System.out.println();
            System.out.println("[Hint] Override temporarily via JVM arg:");
            System.out.println("       -Djarvis.audio.preferredMic=\"Logitech\"");
            System.out.println("[Hint] Or ENV on Windows PowerShell:");
            System.out.println("       $env:JARVIS_AUDIO_PREFERRED=\"Logitech\"");
        } catch (Exception ex) {
            System.err.println("[ERROR] Could not list microphones: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Run energy-based VAD precheck.
     * Usage: --mic-vad [idx] [seconds]
     * idx optional (like --list-mics index), seconds default 3.
     */
    private static void handleMicVad(String[] rest) {
        int idx = -1;     // we keep -1 invalid here; we require explicit index for now
        int seconds = 3;

        // Arg parsing (simple, consistent with other handlers)
        try {
            if (rest.length == 0) {
                System.out.println("[ERROR] Usage: --mic-vad [idx] [seconds]");
                printHelp();
                return;
            } else if (rest.length == 1) {
                idx = Integer.parseInt(rest[0]);
            } else {
                idx = Integer.parseInt(rest[0]);
                seconds = Integer.parseInt(rest[1]);
            }
        } catch (Exception ex) {
            System.out.println("[CLI] Missing or invalid arguments for --mic-vad. Example: --mic-vad 0 3");
            printHelp();
            return;
        }

        // Execute VAD check using MicTester
        try {
            MicTester.VadResult res = MicTester.vadCheck(idx, seconds);
            System.out.println("[AUDIO] VAD-Check: device=" + res.deviceName
                    + " (idx=" + idx + "), duration=" + res.durationSec + "s");
            System.out.printf("[AUDIO] RMS=%.1f | Peak=%.1f | Threshold=%.1f%n",
                    res.averageRms, res.peak, res.threshold);
            System.out.println("[AUDIO] VoiceDetected: " + res.voiceDetected);
            System.out.println("[AUDIO] FramesAbove=" + res.framesAbove + " | FramesBelow=" + res.framesBelow + " | Decision=" + (res.voiceDetected ? "ON" : "OFF"));
        } catch (IllegalArgumentException ex) {
            System.out.println("[ERROR] Ungültiger Geräteindex: " + ex.getMessage());
        } catch (RuntimeException ex) {
            System.out.println("[ERROR] MicTester.vadCheck: " + ex.getMessage());
        } catch (Exception ex) {
            System.out.println("[ERROR] Unerwarteter Fehler: " + ex.getMessage());
        }
    }

}
