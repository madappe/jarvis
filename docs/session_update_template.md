# Session Update Template

**Kopiere diesen Block nach jedem abgeschlossenen Schritt in dein Development-Log!**

---

## ✅ Abgeschlossener Schritt: [ID] - [Titel]

**Datum:** YYYY-MM-DD  
**Dauer:** ~XX Minuten

### 🎯 Ziele
- [Ziel 1]
- [Ziel 2]

### 🔧 Umsetzung
**Geänderte Dateien:**
- `path/to/File.java` - [Kurzbeschreibung der Änderung]
- `path/to/Config.yaml` - [Kurzbeschreibung]

**Neue Klassen/Methoden:**
- `ClassName.methodName()` - [Zweck]

**CLI-Änderungen:**
- Neues Flag: `--example-flag [args]`
- Geändertes Verhalten: `--existing-flag` jetzt mit Parameter X

### ✅ Ergebnis
**Funktionalität:**
- [Was funktioniert jetzt]
- [Welches Verhalten ist neu]

**Testing:**
```bash
# Beispiel-Kommando
java -jar cli/target/cli-jar-with-dependencies.jar --example-flag test

# Erwartete Ausgabe
[CLI] Example output here
```

**Logs:**
```
[INFO] Example log line
[AUDIO] Example audio line
```

### 🐛 Bekannte Probleme
- [Problem 1 + geplanter Fix]
- [Edge-Case X noch nicht behandelt]

### ➡️ Nächster Schritt
**ID:** [B.X.Y - Titel]  
**Ziel:** [Kurzbeschreibung]  
**Abhängigkeiten:** [Falls relevant]

---

## 📊 Changelog (kompakt für Git-Commit)
```
[ID] Kurztitel

- Feature X hinzugefügt
- Bug Y behoben
- Datei Z refactored

Siehe: docs/JARVIS_DEVELOPMENT_LOG.md (Session YYYY-MM-DD)
```

---

**Hinweis:** Nach dem Ausfüllen dieses Template ins Development-Log kopieren unter "Session History"!