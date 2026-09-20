package com.portfolio.seed;

import com.portfolio.experience.CommitEntry;
import com.portfolio.experience.ExperienceRepository;
import com.portfolio.profile.CurrentRole;
import com.portfolio.profile.Profile;
import com.portfolio.profile.ProfileRepository;
import com.portfolio.profile.SocialLink;
import com.portfolio.project.Project;
import com.portfolio.project.ProjectRepository;
import com.portfolio.skill.Skill;
import com.portfolio.skill.SkillBranch;
import com.portfolio.skill.SkillBranchRepository;
import com.portfolio.skill.SkillDiff;
import com.portfolio.skill.SkillDiffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bootstraps demo content into an empty database. Follows the conditional-wiring pattern:
 * inert unless {@code SEED_DEMO_DATA=true}, so it can be switched off from the deploy
 * environment without a code change.
 *
 * <p><b>Turn this off once real content is curated.</b> Only {@code seedProfile} guards on
 * {@code count() > 0}; projects/experience/skill-diffs guard per-row on slug/hash/name, so a
 * placeholder you delete in the admin panel is re-inserted on the next restart while this
 * stays enabled — and Render's free tier restarts often.
 */
@Component
@ConditionalOnProperty(name = "SEED_DEMO_DATA", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final ExperienceRepository experienceRepository;
    private final SkillBranchRepository skillBranchRepository;
    private final SkillDiffRepository skillDiffRepository;

    public DataSeeder(ProfileRepository profileRepository,
                      ProjectRepository projectRepository,
                      ExperienceRepository experienceRepository,
                      SkillBranchRepository skillBranchRepository,
                      SkillDiffRepository skillDiffRepository) {
        this.profileRepository = profileRepository;
        this.projectRepository = projectRepository;
        this.experienceRepository = experienceRepository;
        this.skillBranchRepository = skillBranchRepository;
        this.skillDiffRepository = skillDiffRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("🌱 Seeding database (idempotent)...");
        seedProfile();
        seedProjects();
        seedExperience();
        seedSkills();
        seedSkillDiffs();
        log.info("✅ Seed check complete");
    }

    private void seedProfile() {
        if (profileRepository.count() > 0) {
            return;
        }
        Profile p = new Profile();
        p.setName("Omkar Jadhav");
        p.setHandle("omkarjadhav1011");
        p.setHeadline("Software Development Engineer I at Nonstop IO Technologies");
        // Written to be quotable in isolation: an AI assistant retrieves a passage,
        // not a page, so the opening sentence names the subject in full instead of
        // starting with "I". Education is past tense — he graduated in 2026.
        p.setBio("""
                Omkar Jayvant Jadhav is a Software Development Engineer I at Nonstop IO
                Technologies in Kharadi, Pune. He works on backend development for an
                enterprise reporting product, writing C#, NestJS and SQL against live
                production modules.

                He graduated from KIT's College of Engineering (Autonomous), Kolhapur in
                2026 with a B.Tech in Computer Science & Engineering (Data Science), and
                builds LLM-integrated applications with the Gemini and Hugging Face APIs.""");
        p.setCurrentBranch("main");
        p.setCurrentStatus("Building backend services at Nonstop IO Technologies");
        p.setAvailableForWork(false);
        p.setEmail("jadhavomkar101103@gmail.com");
        p.setLocation("Pune, Maharashtra, India");
        // A profile link is an identity claim, so only confirmed ones belong here.
        // Removed: an X/Twitter account he does not own. The LinkedIn slug is
        // omkar-jadhav-st, NOT the shorter in/omkarjadhav that was published for
        // months -- that one belongs to a different Omkar Jadhav (Dropouts
        // Technologies LLP), and a wrong sameAs tells Google to merge him with a
        // stranger. Do not "simplify" this URL.
        p.setSocials(List.of(
                new SocialLink("GitHub", "https://github.com/omkarjadhav1011", "github"),
                new SocialLink("LeetCode", "https://leetcode.com/u/jadhav_omkar1013/", "leetcode"),
                new SocialLink("LinkedIn", "https://www.linkedin.com/in/omkar-jadhav-st/", "linkedin")));
        // Replaced template filler with claims that are actually verifiable.
        p.setFunFacts(List.of(
                "Solved 210+ problems on LeetCode",
                "Took the long route into engineering: diploma at ICRE Gargoti, then B.Tech at KIT Kolhapur",
                "Wrote the audit-logging layer that tracks user actions across a production reporting product"));
        p.setStash(List.of(
                "⑂  Built this site end to end: Spring Boot API, React SPA, PostgreSQL",
                "🤖  Wrote a multi-provider LLM failover router so the assistant survives a dead provider",
                "🔐  Encrypts every vault file with its own AES-256-GCM data key"));
        // One spelling of the employer, everywhere: "NonStop io Technologies",
        // "NonstopIO" and "Nonstop IO Technologies" were all live at once, which
        // defeats the point of naming an employer for entity association.
        p.setCurrentRole(new CurrentRole(true, "Software Development Engineer I", "Nonstop IO Technologies",
                "N", "", "https://nonstopio.com", "Kharadi, Pune, Maharashtra, India · On-site",
                "Feb 2026", "7 mos", "#00ff88"));
        profileRepository.save(p);
        log.info("✓ Profile seeded");
    }

    private void seedProjects() {
        // Intentionally empty. Every project previously seeded here carried
        // fabricated engagement metrics (stars/forks/commit counts matching nothing
        // in the real GitHub account) and repo URLs that 404. None of it was a
        // confirmed fact. Real projects are entered through the admin panel, where
        // the numbers can be true. Publishing invented metrics is a truthfulness
        // problem before it is an SEO one.
        List<Project> projects = List.of();

        int order = 0;
        int seeded = 0;
        for (Project p : projects) {
            p.setSortOrder(order++);
            if (!projectRepository.existsBySlug(p.getSlug())) {
                projectRepository.save(p);
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("✓ {} projects seeded", seeded);
        }
    }

    private void seedExperience() {
        List<CommitEntry> entries = List.of(
                experience("9c4e1a7", "job", "Software Development Engineer I", "Nonstop IO Technologies",
                        "Aug 2026", null,
                        List.of("Backend development on an enterprise reporting product, contributing to live production modules",
                                "Implemented end-to-end user audit functionality tracking and logging user actions across the application for compliance and traceability",
                                "Contributed to the Report Builder module and wrote optimized SQL queries for reporting, audit logs, and data-retrieval flows"),
                        "work/nonstop-io", "#00ff88", "green",
                        List.of("C#", "NestJS", "SQL"), "https://nonstopio.com"),
                experience("3b7d0f2", "job", "Software Developer Intern", "Nonstop IO Technologies",
                        "Feb 2026", "Aug 2026",
                        List.of("Joined the backend team working on the enterprise reporting product in C#, NestJS and SQL",
                                "Converted to Software Development Engineer I in August 2026"),
                        "work/nonstop-io", "#00ff88", "green",
                        List.of("C#", "NestJS", "SQL"), "https://nonstopio.com"),
                experience("0d3f9e1", "education", "B.Tech, Computer Science & Engineering (Data Science)",
                        "KIT's College of Engineering (Autonomous), Kolhapur", "2023", "2026",
                        List.of("Graduated in 2026 with 80%",
                                "Coursework: Data Structures & Algorithms, DBMS, Operating Systems, Computer Networks, Data Science"),
                        "edu/btech-cse", "#58a6ff", "blue",
                        List.of("DSA", "DBMS", "Data Science"), null),
                experience("1e2b4c7", "education", "Diploma, Computer Engineering",
                        "Institute of Civil and Rural Engineering, Gargoti", "2020", "2023",
                        List.of("Graduated in 2023 with 87%"),
                        "edu/diploma-cse", "#58a6ff", "blue",
                        List.of("C++", "DBMS"), null),
                experience("2c3d5e8", "education", "High School (SSC)",
                        "Shankar Chakru Patil Madhyamik Vidhyalaya, Dindewadi", "2019", "2020",
                        List.of("Completed in 2020 with 94%"),
                        "edu/high-school", "#58a6ff", "blue",
                        List.of(), null),
                experience("a2d8e4f", "achievement", "Python Bootcamp: Zero to Hero", "Udemy",
                        "Jan 2023", null,
                        List.of("Completed the Python programming bootcamp"),
                        "cert/python-bootcamp", "#e3b341", "yellow",
                        List.of("Python", "Udemy"), null),
                experience("b5c7f2a", "achievement", "Java Programming: Beginner to Master", "Udemy",
                        "Mar 2023", null,
                        List.of("Completed the Java programming course covering core Java and OOP"),
                        "cert/java-programming", "#e3b341", "yellow",
                        List.of("Java", "OOP", "Udemy"), null),
                experience("c8e1a94", "achievement", "AI, Machine Learning & Data Science Bootcamp", "Udemy",
                        "2023", null,
                        List.of("Completed the AI, machine learning and data science bootcamp"),
                        "cert/ai-ml-ds", "#e3b341", "yellow",
                        List.of("Udemy"), null));

        int order = 0;
        int seeded = 0;
        for (CommitEntry e : entries) {
            e.setSortOrder(order++);
            if (!experienceRepository.existsByHash(e.getHash())) {
                experienceRepository.save(e);
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("✓ {} experience entries seeded", seeded);
        }
    }

    /**
     * Mirrors {@code frontend/src/data/skills.ts}, which is itself the on-page mirror
     * of the schema {@code knowsAbout} array — structured data may only claim what a
     * reader can see, so all three must agree.
     *
     * <p>Next.js was removed: it is a withdrawn skill claim. So were Tailwind CSS,
     * C++, MongoDB, NumPy, Pandas, Scikit-learn and Jupyter, which are absent from
     * the confirmed skills list.
     */
    private void seedSkills() {
        seedBranch("feature/languages", "#58a6ff", 0, List.of(
                skill("C#", 4, "#", null), skill("SQL", 4, "🗄", null),
                skill("JavaScript", 4, "JS", null), skill("TypeScript", 3, "TS", null),
                skill("Python", 4, "🐍", null), skill("Java", 3, "☕", null)));
        seedBranch("feature/backend", "#00ff88", 1, List.of(
                skill("NestJS", 4, "🐱", null), skill("Spring Boot", 3, "🌱", null),
                skill("REST APIs", 4, "🔌", null), skill("PostgreSQL", 3, "🐘", null),
                skill("MySQL", 4, "🗃", null), skill("PHP", 3, "🐘", null)));
        seedBranch("feature/frontend", "#f0883e", 2, List.of(
                skill("React", 3, "⚛", null), skill("HTML5", 5, "🌐", null),
                skill("CSS3", 4, "🎨", null)));
        seedBranch("feature/ai", "#d2a8ff", 3, List.of(
                skill("Gemini API", 4, "✦", null), skill("Hugging Face API", 3, "🤗", null),
                skill("Prompt Engineering", 3, "💬", null)));
        seedBranch("feature/tools", "#e3b341", 4, List.of(
                skill("Git", 5, "⑂", null), skill("GitHub", 4, "🐙", null),
                skill("Docker", 3, "🐳", null), skill("Postman", 4, "📮", null),
                skill("Streamlit", 3, "📊", null), skill("VS Code", 5, "💻", null)));
        seedBranch("feature/core-cs", "#7ee787", 5, List.of(
                skill("Data Structures & Algorithms", 4, "🧮", null), skill("OOP", 4, "🧱", null),
                skill("DBMS", 4, "🗂", null), skill("MVC Architecture", 3, "🏛", null)));
    }

    private void seedBranch(String branchName, String color, int offset, List<Skill> skills) {
        SkillBranch branch = skillBranchRepository.findByBranchName(branchName).orElse(null);
        if (branch == null) {
            branch = new SkillBranch();
            branch.setBranchName(branchName);
            branch.setColor(color);
            branch.setOffset(offset);
            for (Skill s : skills) {
                s.setBranch(branch);
                branch.getSkills().add(s);
            }
            skillBranchRepository.save(branch);
            log.info("✓ skill branch {} seeded ({} skills)", branchName, skills.size());
        }
    }

    private void seedSkillDiffs() {
        List<SkillDiff> diffs = List.of(
                skillDiff("C#", "added", "day-to-day backend work at Nonstop IO"),
                skillDiff("NestJS", "added", "services on the enterprise reporting product"),
                skillDiff("TypeScript", "added", "typed everything, including this site"),
                skillDiff("Docker", "added", "containerized local dependencies"),
                skillDiff("SQL", "modified", "optimizing reporting, audit-log and data-retrieval queries"),
                skillDiff("Spring Boot", "modified", "the API behind this portfolio"));

        int order = 0;
        int seeded = 0;
        for (SkillDiff d : diffs) {
            d.setSortOrder(order++);
            if (!skillDiffRepository.existsByName(d.getName())) {
                skillDiffRepository.save(d);
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("✓ {} skill diffs seeded", seeded);
        }
    }

    // ── builders ────────────────────────────────────────────────────────────

    private static Project project(String slug, String repoName, String description, String language,
                                   String languageColor, int stars, int forks, int commits, String lastCommit,
                                   String lastCommitMsg, List<String> tags, String liveUrl, String repoUrl,
                                   String status, boolean pinned, String longDescription) {
        Project p = new Project();
        p.setSlug(slug);
        p.setRepoName(repoName);
        p.setDescription(description);
        p.setLanguage(language);
        p.setLanguageColor(languageColor);
        p.setStars(stars);
        p.setForks(forks);
        p.setCommits(commits);
        p.setLastCommit(lastCommit);
        p.setLastCommitMsg(lastCommitMsg);
        p.setTags(tags);
        p.setLiveUrl(liveUrl);
        p.setRepoUrl(repoUrl);
        p.setStatus(status);
        p.setPinned(pinned);
        p.setLongDescription(longDescription);
        return p;
    }

    private static CommitEntry experience(String hash, String type, String title, String org, String date,
                                          String dateEnd, List<String> description, String branch,
                                          String branchColor, String colorKey, List<String> tags, String url) {
        CommitEntry e = new CommitEntry();
        e.setHash(hash);
        e.setType(type);
        e.setTitle(title);
        e.setOrg(org);
        e.setDate(date);
        e.setDateEnd(dateEnd);
        e.setDescription(description);
        e.setBranch(branch);
        e.setBranchColor(branchColor);
        e.setColorKey(colorKey);
        e.setTags(tags);
        e.setUrl(url);
        return e;
    }

    private static Skill skill(String name, int level, String icon, String tag) {
        Skill s = new Skill();
        s.setName(name);
        s.setLevel(level);
        s.setIcon(icon);
        s.setTag(tag);
        return s;
    }

    private static SkillDiff skillDiff(String name, String type, String note) {
        SkillDiff d = new SkillDiff();
        d.setName(name);
        d.setType(type);
        d.setNote(note);
        return d;
    }
}
