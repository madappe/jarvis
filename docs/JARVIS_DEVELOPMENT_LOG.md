# JARVIS PROJECT - Development Log

**Projekt:** Voice Assistant "Jarvis"
**Start:** Oktober 2025
**Tech Stack:** Java 17, Maven, Vosk STT, Windows SAPI TTS
**Repository GitHub:** https://github.com/madappe/jarvis.git
**Repository GitLab:** https://gitlab.com/strujic1989/ai-jarvis

---

## 📋 Quick Reference

### Projekt-Struktur
```
jarvis/
├── core/          # Shared logic (Skills, Router, Config)
├── executor/      # Command execution (Apps, TTS)
├── stt/           # Speech-to-Text (Audio, Vosk, VAD)
├── cli/           # Main entry point
├── ui/            # Future GUI (planned)
└── skills/        # YAML skill definitions
```

### CLI-Kommandos (Wichtigste)
```
# Audio Testing
--list-mics                    # List available microphones
--mic-test [idx] [sec]        # Test audio levels
--mic-vad [idx] [sec]         # Voice Activity Detection check
--mic-capture [idx] [sec]     # Record audio to buffer
--mic-dump [idx] [sec] [file] # Save audio as WAV

# Speech-to-Text
--stt-dummy            # Simulate STT
--stt-vosk   [sec] # Live Vosk recognition
--stt-vosk-exec    # Vosk + auto-execute

# Skills
--list-skills                  # List loaded skills
--dry-run           # Test pattern matching
--run               # Route and execute
--say                   # Direct TTS output
--reload                      # Reload skill definitions

# System
--help | -h                   # Show help
--version                     # Show version
--health                      # System health check
```

---

## 📊 Entwicklungsstand

### ✅ Phase A - Projektbasis (ABGESCHLOSSEN)

**A.0 - Multi-Modul Setup**
- Java 17 + Maven Multi-Modul-Architektur
- Module: core, executor, stt, cli, ui, skills
- SLF4J + Logback Logging
- Fat-JAR Build mit Assembly-Plugin
- **Status:** Stabil, kompiliert fehlerfrei

---

### ✅ Phase B - Audio & Speech (IN ARBEIT)

#### **B.1.1 - Mikrofon-Verwaltung** ✅
**Ziel:** Geräteerkennung und Listing  
**Implementiert:**
- `AudioDeviceService.listInputDevices()` - Liste aller Eingabegeräte
- CLI-Flag `--list-mics` - Zeigt Mikrofone mit Format-Support
- Target-Format: 16kHz, mono, 16-bit PCM (little-endian)

**Ergebnis:** Alle verfügbaren Mikrofone sichtbar mit Kompatibilitäts-Check

---

#### **B.1.2 - Persistente Mikrofon-Auswahl** ✅
**Ziel:** Standardmikrofon konfigurierbar machen  
**Implementiert:**
- `AudioDeviceService.selectPreferredDeviceIndex(String)` - Intelligente Auswahl
- System-Property: `-Djarvis.audio.preferredMic="Name"`
- Umgebungsvariable: `JARVIS_AUDIO_PREFERRED`
- Fallback-Logik: Name-Match → Format-OK → Index 0

**Ergebnis:** Alle Audio-Kommandos nutzen automatisch das konfigurierte Mikrofon

---

#### **B.1.3a - VAD Precheck (Energy-Gate)** ✅
**Ziel:** Spracherkennung vor STT-Start validieren  
**Implementiert:**
- `MicTester.vadCheck()` - RMS/Peak-basierte Analyse
- Ambient Noise Baseline (~0.4s Sampling)
- Dynamischer Threshold: `ambient_RMS * 3.5` (clamped 600-2500)
- CLI-Flag `--mic-vad [idx] [sec]`

**Ergebnis:**
- Stille → `VoiceDetected: false`
- Sprache → `VoiceDetected: true`
- Schnelle Diagnose möglich (3-10 Sekunden)

---

#### **B.1.3b - VAD mit Frame-Hysterese** ✅
**Ziel:** Fehltrigger durch kurze Geräusche eliminieren  
**Implementiert:**
- Frame-Analyse: 20ms-Fenster
- ON-Threshold: ≥6 Frames über Schwelle (~120ms)
- OFF-Threshold: ≥10 Frames unter 0.7×Schwelle (~200ms)
- Hysterese-Zähler: `runAbove` / `runBelow`

**Code-Details:**
```java
// MicTester.java - Key constants
private static final int FRAME_MS = 20;
private static final int ON_MIN_FRAMES = 6;    // ~120ms speech
private static final int OFF_MIN_FRAMES = 10;  // ~200ms silence
private static final double OFF_FACTOR = 0.7;  // off-threshold reduction
```

**Ergebnis:**
- Einzelne Klicks/Tipper → ignoriert
- Kontinuierliche Sprache → erkannt
- Stabile Umschaltung zwischen ON/OFF

**CLI-Output:**
```
[AUDIO] VAD-Check: device=Microphone (idx=7), duration=5s
[AUDIO] RMS=1234.5 | Peak=8765.4 | Threshold=1800.0
[AUDIO] VoiceDetected: true
[AUDIO] FramesAbove=18 | FramesBelow=3 | Decision=ON
```

---

#### **B.1.3c - VAD Integration in STT-Pipeline** 🔄 IN ARBEIT
**Ziel:** Nur STT starten wenn Sprache erkannt wurde  
**Geplant:**
1. VAD-Vorprüfung in `VoskSttAdapter.start()`
2. Optional: `--stt-vosk-vad` Flag für automatische VAD-Filterung
3. Event-basierte Integration: `VoiceDetectedEvent` → STT-Start

**Nächster Schritt:** Warte auf "Go" für Implementierung

---

#### **B.1.4 - STT-Testmodus** ⏳ GEPLANT
- Dummy-Recognizer mit vordefinierten Phrasen
- Transkription von Testdateien

---

#### **B.2 - Push-to-Talk** ⏳ GEPLANT
- Hotkey-Erkennung (z.B. Ctrl+Space)
- Start/Stop Audio-Aufnahme on-demand

---

#### **B.3 - Wake-Word Detection** ⏳ GEPLANT
- Keyword-Spotting ("Hey Jarvis")
- Mini-NN-Modell oder Pattern-Matching

---

#### **B.4 - Audio-Monitoring-Service** ⏳ GEPLANT
- Dauerhafte Hintergrund-Überwachung
- Event-Bus-Integration

---

#### **B.5 - Vollständige STT-Pipeline** ⏳ GEPLANT
- VAD → STT → NLU → Executor
- Error-Handling & Retry-Logik

---

### 🔄 Phase C - NLU / Intent-Parsing (TEILWEISE)

#### **C.1 - NluAdapter Interface** ✅
**Implementiert:**
- Interface `NluAdapter` (Text → Intent + Slots)
- DTO-Struktur für JSON-Schema
- CLI-Vorbereitung `--nlu` (noch nicht implementiert)

**Nächste Schritte:**
- C.2: LLM-Integration (OpenAI/Local)
- C.3: Fallback-Regeln
- C.4: Schema-Validierung
- C.5: CLI-Testkommando

---

### ✅ Phase D - Skill-System (TEILWEISE)

#### **D.1 - Skill-Loader & Router** ✅
**Implementiert:**
- `SkillsLoader` - Lädt YAML aus `resources/skills/` + externe `./skills/`
- `SkillRouter` - Pattern-Matching mit benannten Gruppen
- `IntentMatch` - DTO für Routing-Ergebnisse
- Deduplizierung: Externe Skills überschreiben interne

**Skills aktiv:**
- `app.launch.yaml` - Anwendungen starten
- `say.hello.yaml` - TTS-Ausgabe

**CLI-Kommandos:**
- `--list-skills` - Zeigt geladene Skills
- `--dry-run <text>` - Testet Pattern-Matching
- `--route <text>` - Einmaliges Routing
- `--run <text>` - Routing + Ausführung
- `--reload` - Skills neu laden

**Nächste Schritte:**
- D.2: Skill-Parameter (z.B. `--name Igor`)
- D.3: Hot-Reload über CLI
- D.4: Permissions & Bestätigung
- D.5: Debug-Logging

---

### ✅ Phase E - TTS-System (BASIC IMPLEMENTIERT)

#### **E.1 - Windows SAPI Integration** ✅
**Implementiert:**
- `WindowsTtsExecutor` - PowerShell + System.Speech
- `SayExecutor` - Wrapper für Skill-Integration
- CLI-Kommando `--say <text>`

**Features:**
- Synchrones Blocking (max 20s Timeout)
- Input-Validierung
- Error-Handling mit aussagekräftigen Messages

**Nächste Schritte:**
- E.2: Mehrsprachigkeit (DE/EN Stimmen)
- E.3: TTS-Caching
- E.4: Volume/Pitch/Rate-Steuerung
- E.5: Fallback-TTS für andere OS

---

### ⏳ Phase F-I - Geplante Features

**F - Sicherheitsmodell**
- Bestätigungssystem für kritische Befehle
- PIN/Passwort-Schutz
- Audit-Log

**G - UI & Service-Modus**
- SystemTray-Interface
- Autostart
- Background-Service

**H - Tests & Telemetrie**
- Unit-Tests (Audio, Skills, Router)
- Latenz-Metriken
- Self-Check-Kommando

**I - Erweiterungen**
- Multi-Device-Support
- Smart-Home (MQTT, Home Assistant)
- Mediensteuerung (Spotify, VLC)
- Lokales LLM/Whisper

---

## 🔧 Technische Details

### Audio-Pipeline
```
Mikrofon (16kHz/16bit/mono)
  → TargetDataLine (javax.sound.sampled)
  → AudioRingBuffer (thread-safe)
  → VAD Check (Energy + Frame-Hysterese)
  → Vosk Recognizer (acceptWaveForm)
  → Partial/Final Results
```

### Skill-Routing
```
User Input ("öffne notepad")
  → AliasConfig.normalizeSttText() (STT-Korrekturen)
  → SkillRouter.route() (Pattern-Matching)
  → IntentMatch { skillName, pattern, params }
  → SkillDispatcher.dispatch() oder AppLaunchExecutor.execute()
  → Process launch oder TTS-Output
```

### Konfiguration
**Hierarchie (später gewinnt):**
1. Classpath-Ressourcen (`resources/config/`)
2. Arbeitsverzeichnis (`./jarvis-aliases.properties`)
3. System-Properties (`-Djarvis.audio.preferredMic`)
4. Umgebungsvariablen (`JARVIS_AUDIO_PREFERRED`)

---

## 📅 Session History

### Session 2024-10-20
**Thema:** VAD Frame-Hysterese & Dokumentations-System
**Thema:** Dokumentations-System finalisieren

**Achievements:**
- ✅ Frame-basierte VAD-Logik implementiert
- ✅ Hysterese-Zähler für ON/OFF-Entscheidung
- ✅ CLI-Diagnose erweitert (FramesAbove/Below)
- ✅ Session-Starter-Template erstellt
- ✅ Development-Log-System aufgesetzt
- ✅ `JARVIS_DEVELOPMENT_LOG.md` vollständig erstellt
- ✅ `session_update_template.md` erstellt
- ✅ Repository-Setup abgeschlossen (GitHub + GitLab)
- ✅ Build-Fix: AppAliasService.java entfernt
- ✅ Maven kompiliert fehlerfrei
- ✅ Projekt-Anweisungen optimiert

**Code-Änderungen:**
- `stt/audio/MicTester.java` - Frame-Analyse + Hysterese
- `cli/JarvisCli.java` - VAD-Output erweitert
- `docs/JARVIS_DEVELOPMENT_LOG.md` - Neu erstellt
- `docs/session_update_template.md` - Neu erstellt
- `core/src/main/java/ai/jarvis/core/alias/AppAliasService.java` - Gelöscht (verwaiste Datei)

**Nächster Schritt:** B.1.3c - VAD in STT-Pipeline integrieren

---

## 🎯 Nächste Sessions (Roadmap)

### Kurzfristig (1-2 Sessions)
1. 🔄 B.1.3c - VAD in STT-Pipeline
2. ⏳ B.1.4 - STT-Testmodus mit Dummy-Daten
3. ⏳ C.2 - LLM-Adapter für Intent-Parsing

### Mittelfristig (3-5 Sessions)
4. ⏳ B.2 - Push-to-Talk Modus
5. ⏳ B.3 - Wake-Word Detection
6. ⏳ D.2-D.3 - Skill-Parameter & Hot-Reload

### Langfristig (6+ Sessions)
7. ⏳ G.1 - SystemTray-UI
8. ⏳ I.2 - Smart-Home-Integration
9. ⏳ I.4 - Lokales LLM (Ollama/LMStudio)

---

## 🔍 Quick Fixes & Known Issues

### Audio-Probleme
**Problem:** Mikrofon nicht gefunden  
**Lösung:** `--list-mics` → Index prüfen → `-Djarvis.audio.preferredMic` setzen

**Problem:** Line unavailable  
**Lösung:** Andere Anwendung (Discord, Zoom) beendet? Mixer-Index korrekt?

### STT-Probleme
**Problem:** Vosk erkennt nichts  
**Lösung:** VAD-Check mit `--mic-vad` → Threshold zu hoch? Mikrofon zu leise?

**Problem:** Falsche Transkription  
**Lösung:** `jarvis-aliases.properties` erweitern mit STT-Replacements

### Build-Probleme
**Problem:** Maven-Build schlägt fehl  
**Lösung:** `mvn clean install` → Alle Module einzeln prüfen

---

## 📚 Wichtige Links

- **Vosk Models:** https://alphacephei.com/vosk/models
- **Maven:** https://maven.apache.org/guides/

---

**Letztes Update:** 2024-10-20  
**Version:** 0.1.0-SNAPSHOT  
**Status:** Active Development 🚀