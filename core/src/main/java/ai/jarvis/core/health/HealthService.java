package ai.jarvis.core.health;

/**
 * Führt einfache Laufzeit-Checks aus.
 */
public class HealthService {

    public HealthStatus check() {
        HealthStatus hs = new HealthStatus();

        String javaVersion = System.getProperty("java.version", "unknown");
        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");

        boolean javaOk = isJava17Plus(javaVersion);
        boolean eventBusPresent = classPresent("ai.jarvis.core.events.EventBus");
        boolean windowsExecPresent = !osName.toLowerCase().contains("win")
                || classPresent("ai.jarvis.executor.WindowsExecutor");

        boolean allOk = javaOk && eventBusPresent && windowsExecPresent;

        hs.ok(allOk)
                .detail("java.version", javaVersion)
                .detail("os.name", osName)
                .detail("os.arch", osArch)
                .detail("core.EventBus.present", eventBusPresent)
                .detail("executor.WindowsExecutor.present", windowsExecPresent);

        return hs;
    }

    private boolean classPresent(String fqcn) {
        try {
            Class.forName(fqcn, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isJava17Plus(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = parts[0].equals("1") ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
            return major >= 17;
        } catch (Exception e) {
            return false;
        }
    }
}
