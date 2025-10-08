package ai.jarvis.executor;

import java.io.IOException;

public class WindowsExecutor implements CommandExecutor {
    @Override
    public boolean launchApp(String displayNameOrExe) {
        try {
            new ProcessBuilder("cmd", "/c", "start", "\"\"", displayNameOrExe).start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
