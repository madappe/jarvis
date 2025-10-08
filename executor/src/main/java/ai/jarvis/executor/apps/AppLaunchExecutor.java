package ai.jarvis.executor.apps;

import java.io.IOException;
import java.util.*;
import ai.jarvis.core.config.AppAllowListConfig;

/**
 * AppLaunchExecutor
 * -----------------
 * Responsibility:
 *  - Normalize spoken app names (e.g. "notepad", "calculator")
 *  - Enforce a simple allow-list (Step 7.1: in-code; Step 7.3: move to config)
 *  - Resolve an OS-appropriate launch command
 *  - Execute the process with ProcessBuilder
 *
 * Public API:
 *  - execute(String appSpokenName): LaunchResult
 *
 * Notes:
 *  - Keep side effects minimal; no router/CLI wiring here (that’s Step 7.2+)
 *  - Favor clarity over cleverness; we’ll iterate safely.
 */
public class AppLaunchExecutor {

    // --- OS detection (simple & explicit) ---
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);

    // Daten werden nun aus der zentralen YAML-Konfiguration geladen
    private final Map<String, String> allowList;
    private final Map<String, String> synonyms;

    public AppLaunchExecutor() {
        AppAllowListConfig config = new AppAllowListConfig();
        this.allowList = config.getAllowedApps();
        this.synonyms = config.getSynonyms();

        // Sicherheits-Log
        System.out.println("[AppLaunchExecutor] Loaded " + allowList.size() + " allowed apps, "
                + synonyms.size() + " synonyms from config.");
    }

    /**
     * Execute an app launch by spoken name.
     *
     * @param appSpokenName the spoken or parsed name (e.g., "notepad", "öffne chrome")
     * @return LaunchResult containing success flag, user-friendly message, and resolved command details
     */
    public LaunchResult execute(String appSpokenName) {
        // Validate input
        if (appSpokenName == null || appSpokenName.trim().isEmpty()) {
            return LaunchResult.failure("No application name provided.");
        }

        // Normalize spoken name to a canonical key
        String normalized = AppNameNormalizer.normalize(appSpokenName);
        String mapped = resolveToNormalizedKey(normalized);

        if (mapped == null) {
            return LaunchResult.failure("App is not in the allow-list: \"" + appSpokenName + "\"");
        }

        String target = allowList.get(mapped);
        if (target == null) {
            return LaunchResult.failure("Internal mapping error for: " + mapped);
        }

        // Build OS-specific command
        List<String> command = buildCommandForOS(mapped, target);

        if (command.isEmpty()) {
            return LaunchResult.failure("No suitable launch command for this OS.");
        }

        // Execute
        try {
            new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            return LaunchResult.success("Launched \"" + mapped + "\"", command);
        } catch (IOException ex) {
            return LaunchResult.failure("Failed to launch \"" + mapped + "\": " + ex.getMessage(), command);
        }
    }

    // --- Helper: map normalized spoken token to an allow-listed key ---
    private String resolveToNormalizedKey(String normalized) {
        if (allowList.containsKey(normalized)) {
            return normalized;
        }
        String viaSyn = synonyms.get(normalized);
        if (viaSyn != null && allowList.containsKey(viaSyn)) {
            return viaSyn;
        }
        return null;
    }

    // --- Helper: OS-specific command resolution ---
    private List<String> buildCommandForOS(String normalizedKey, String target) {
        // Windows
        if (isWindows()) {
            return buildWindowsCommand(normalizedKey, target);
        }
        // macOS
        if (isMac()) {
            return buildMacCommand(normalizedKey, target);
        }
        // Linux / others
        return buildLinuxCommand(normalizedKey, target);
    }

    private List<String> buildWindowsCommand(String normalizedKey, String target) {
        // Prefer known executables
        switch (normalizedKey) {
            case "notepad":
                return Arrays.asList("cmd", "/c", "start", "", "notepad.exe");
            case "calculator":
                // “calc.exe” is available on Windows
                return Arrays.asList("cmd", "/c", "start", "", "calc.exe");
            case "explorer":
                return Arrays.asList("cmd", "/c", "start", "", "explorer.exe");
            case "chrome":
                // If chrome.exe is in PATH, start will resolve it
                return Arrays.asList("cmd", "/c", "start", "", "chrome.exe");
            case "vscode":
                return Arrays.asList("cmd", "/c", "start", "", "code");
            case "files":
                // Open current directory in Explorer
                return Arrays.asList("cmd", "/c", "start", "", ".");
            default:
                // Fallback: try target via start
                return Arrays.asList("cmd", "/c", "start", "", target);
        }
    }

    private List<String> buildMacCommand(String normalizedKey, String target) {
        // macOS uses "open". For apps, -a <AppName>. For folders/URLs: open <path>.
        switch (normalizedKey) {
            case "notepad":
                // No native Notepad; try TextEdit
                return Arrays.asList("open", "-a", "TextEdit");
            case "calculator":
                return Arrays.asList("open", "-a", "Calculator");
            case "chrome":
                return Arrays.asList("open", "-a", "Google Chrome");
            case "vscode":
                return Arrays.asList("open", "-a", "Visual Studio Code");
            case "files":
                // Finder on current dir
                return Arrays.asList("open", ".");
            default:
                // Fallback open (file/app if resolvable)
                return Arrays.asList("open", target);
        }
    }

    private List<String> buildLinuxCommand(String normalizedKey, String target) {
        // Try common binaries first; else xdg-open as a generic fallback
        switch (normalizedKey) {
            case "notepad":
                // Try some common editors; last resort xdg-open on current dir
                if (isOnPath("gedit")) return Collections.singletonList("gedit");
                if (isOnPath("kate")) return Collections.singletonList("kate");
                if (isOnPath("xed")) return Collections.singletonList("xed");
                return Arrays.asList("xdg-open", ".");
            case "calculator":
                if (isOnPath("gnome-calculator")) return Collections.singletonList("gnome-calculator");
                if (isOnPath("kcalc")) return Collections.singletonList("kcalc");
                return Arrays.asList("xdg-open", ".");
            case "chrome":
                if (isOnPath("google-chrome")) return Collections.singletonList("google-chrome");
                if (isOnPath("chromium")) return Collections.singletonList("chromium");
                // As a generic fallback, xdg-open about:blank
                return Arrays.asList("xdg-open", "about:blank");
            case "vscode":
                if (isOnPath("code")) return Collections.singletonList("code");
                return Arrays.asList("xdg-open", ".");
            case "files":
                return Arrays.asList("xdg-open", ".");
            default:
                // Generic best-effort
                if (isOnPath(target)) return Collections.singletonList(target);
                return Arrays.asList("xdg-open", target);
        }
    }

    private boolean isWindows() { return OS.contains("win"); }
    private boolean isMac() { return OS.contains("mac"); }

    /**
     * Check whether a binary is resolvable on PATH (best-effort).
     * Note: This is a lightweight presence check; it doesn’t guarantee launch success.
     */
    private boolean isOnPath(String binary) {
        try {
            Process proc = new ProcessBuilder("which", binary).start();
            int exit = proc.waitFor();
            return exit == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
