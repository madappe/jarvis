package ai.jarvis.executor.apps;

import java.util.Collections;
import java.util.List;

/**
 * LaunchResult
 * ------------
 * Small value object to report execution outcome.
 */
public class LaunchResult {
    private final boolean success;
    private final String message;
    private final List<String> command;

    private LaunchResult(boolean success, String message, List<String> command) {
        this.success = success;
        this.message = message;
        this.command = command == null ? Collections.emptyList() : command;
    }

    public static LaunchResult success(String message, List<String> command) {
        return new LaunchResult(true, message, command);
    }

    public static LaunchResult failure(String message) {
        return new LaunchResult(false, message, Collections.emptyList());
    }

    public static LaunchResult failure(String message, List<String> command) {
        return new LaunchResult(false, message, command);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<String> getCommand() { return command; }
}
