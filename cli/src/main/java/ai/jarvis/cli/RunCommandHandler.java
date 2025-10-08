package ai.jarvis.cli;

import ai.jarvis.core.router.IntentMatch;       // liegt bei dir im router-Package
import ai.jarvis.core.router.SkillRouter;       // liegt bei dir im router-Package
import ai.jarvis.core.skills.SkillDefinition;   // liegt bei dir im skills-Package
import ai.jarvis.executor.apps.AppLaunchExecutor;
import ai.jarvis.executor.apps.LaunchResult;

import java.util.List;
import java.util.Optional;

/**
 * RunCommandHandler
 * -----------------
 * Führt --run "<satz>" aus:
 *  - nutzt vorhandenen SkillRouter + bereits geladene SkillDefinitionen
 *  - ruft route(text, skills) auf (deine Signatur)
 *  - führt nur den Skill "app.launch" wirklich aus
 */
public class RunCommandHandler {

    private final SkillRouter router;               // bereitgestellt vom Bootstrap (JarvisCli)
    private final List<SkillDefinition> skills;     // bereits geladene Skills (YAML -> Definitionen)
    private final AppLaunchExecutor appLauncher;    // führt Apps aus

    public RunCommandHandler(SkillRouter router,
                             List<SkillDefinition> skills,
                             AppLaunchExecutor appLauncher) {
        this.router = router;
        this.skills = skills;
        this.appLauncher = appLauncher;
    }

    /**
     * @param sentence z. B. "öffne notepad"
     * @return Exit-Code: 0=OK, 2=kein Skill, 3=nicht unterstützter Skill/Param, 4=Ausführung fehlgeschlagen
     */
    public int run(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            System.out.println("[run] Empty sentence.");
            return 2;
        }

        // Router-Signatur: Optional<IntentMatch> route(String text, List<SkillDefinition> skills)
        Optional<IntentMatch> opt = router.route(sentence, skills);
        if (opt.isEmpty()) {
            System.out.println("[run] No skill matched for: " + sentence);
            return 2;
        }

        IntentMatch match = opt.get();

        // Nur app.launch wird hier ausgeführt
        String skillName = match.getSkillName();
        if (!"app.launch".equals(skillName)) {
            System.out.println("[run] Skill matched but not executable via --run: " + skillName);
            return 3;
        }

        // Parameter {app}
        String app = match.getParams().get("app");
        if (app == null || app.isBlank()) {
            System.out.println("[run] app.launch matched but parameter {app} is missing.");
            return 3;
        }

        // Ausführen
        LaunchResult result = appLauncher.execute(app);

        // Ergebnis
        System.out.println(
                (result.isSuccess() ? "[OK] " : "[FAIL] ")
                        + result.getMessage()
                        + (result.getCommand().isEmpty() ? "" : " :: " + result.getCommand())
        );
        return result.isSuccess() ? 0 : 4;
    }
}
