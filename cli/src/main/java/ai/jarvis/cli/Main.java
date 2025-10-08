package ai.jarvis.cli;

import ai.jarvis.core.events.Event;
import ai.jarvis.core.events.EventBus;
import ai.jarvis.executor.CommandExecutor;
import ai.jarvis.executor.WindowsExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Scanner;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public record UtteranceEvent(String text) implements Event {}

    public static void main(String[] args) {
        EventBus bus = new EventBus();
        CommandExecutor exec = new WindowsExecutor();

        bus.subscribe(e -> {
            if (e instanceof UtteranceEvent u) {
                String s = u.text().trim();
                log.info("[BUS] Empfangen: {}", s);

                // Mini-Regeln: "öffne X", "open X", "start X"
                String low = s.toLowerCase(Locale.ROOT);
                if (low.startsWith("öffne ") || low.startsWith("open ") || low.startsWith("start ")) {
                    String target = s.replaceFirst("(?i)^(öffne|open|start)\\s+", "").trim();

                    // kleine Alias-Hilfen
                    if (target.equalsIgnoreCase("notepad") || target.equalsIgnoreCase("editor")) {
                        target = "notepad.exe";
                    }

                    boolean ok = exec.launchApp(target);
                    if (ok) log.info("Starte: {}", target);
                    else     log.warn("Konnte nicht starten: {}", target);
                }
            }
        });

        log.info("Jarvis gestartet. Befehle wie: 'öffne notepad' / 'open notepad'. 'exit' beendet.");
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if ("exit".equalsIgnoreCase(line)) {
                log.info("Beende Jarvis. Auf Wiedersehen.");
                break;
            }
            bus.publish(new UtteranceEvent(line));
        }
    }
}
