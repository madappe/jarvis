package ai.jarvis.core.skills;

public class SkillsLoaderSmokeTest {
    public static void main(String[] args) {
        SkillsLoader loader = new SkillsLoader();
        loader.loadMergedSkills(); // scans ./skills and prints result
    }
}
