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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
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
        p.setHandle("omkarjadhav");
        p.setHeadline("B.Tech CSE (Data Science) Student & Full-Stack Developer");
        p.setBio("""
                I build things for the web and explore the intersection of software and data.
                Currently pursuing B.Tech in Computer Science (Data Science) at KIT Kolhapur,
                obsessed with clean code, great UX, and systems that scale.

                When I'm not writing code, I'm experimenting with machine learning models,
                building full-stack apps, or debugging something I broke at 2am.""");
        p.setCurrentBranch("main");
        p.setCurrentStatus("Open to internships & collaborations");
        p.setAvailableForWork(true);
        p.setEmail("jadhavomkar101103@gmail.com");
        p.setLocation("Kolhapur, Maharashtra, India");
        p.setSocials(List.of(
                new SocialLink("GitHub", "https://github.com/omkarjadhav", "github"),
                new SocialLink("LinkedIn", "https://linkedin.com/in/omkarjadhav", "linkedin"),
                new SocialLink("Twitter", "https://twitter.com/omkarjadhav", "twitter")));
        p.setFunFacts(List.of(
                "I've written more git commit messages than diary entries",
                "Went from 94% in SSC to building ML models — the plot thickens",
                "I debug in production (just kidding... mostly)",
                "My Hugging Face API calls cost more than my monthly coffee budget"));
        p.setStash(List.of(
                "☕  Coffee-driven development — 3 cups before 10am",
                "♟  Plays chess to debug decision-making",
                "📚  Reading: Designing Data-Intensive Applications (DDIA)",
                "🎵  Codes to lo-fi beats and post-rock",
                "🌱  Contributing to open source, one PR at a time"));
        p.setCurrentRole(new CurrentRole(true, "Full-Stack Developer Intern", "NonStop io Technologies",
                "N", "", "https://nonstopio.com", "Pune, India · Hybrid", "Mar 2024", "8 mos", "#00ff88"));
        profileRepository.save(p);
        log.info("✓ Profile seeded");
    }

    private void seedProjects() {
        List<Project> projects = List.of(
                project("git-portfolio", "git-portfolio",
                        "A developer portfolio with Git-inspired UI — terminals, branches, and commit logs.",
                        "TypeScript", "#3178c6", 12, 3, 47, "just now",
                        "feat: add command palette with Ctrl+K shortcut",
                        List.of("Next.js", "Tailwind", "Framer Motion"),
                        "https://omkarjadhav.vercel.app", "https://github.com/omkarjadhav/git-portfolio",
                        "active", true,
                        "Built with Next.js 14, this portfolio reimagines personal websites through the lens of Git — commit timelines, branch visualizations, and a fully interactive terminal command palette."),
                project("dev-mobiles", "dev-mobiles",
                        "Mobile shopping e-commerce platform with full auth, cart, search, and purchase flow.",
                        "PHP", "#4F5D95", 8, 2, 84, "2 months ago",
                        "feat: add purchase flow with payment integration",
                        List.of("PHP", "HTML", "CSS", "JavaScript", "MySQL"),
                        null, "https://github.com/omkarjadhav/dev-mobiles", "active", true,
                        "A full-featured mobile shopping platform built during my internship at Dnyanda Solutions. Supports user login, product search, cart management, and a complete purchase flow with payment integration."),
                project("crop-recommendation", "crop-recommendation",
                        "ML-based crop suggestion system using soil and weather data with a real-time farmer UI.",
                        "Python", "#3572A5", 19, 5, 62, "3 months ago",
                        "feat: integrate real-time weather API for dynamic predictions",
                        List.of("Python", "Scikit-learn", "Pandas", "NumPy"),
                        null, "https://github.com/omkarjadhav/crop-recommendation", "active", true,
                        "A machine learning system that recommends optimal crops based on soil composition and real-time weather conditions. Built with Scikit-learn classification models and a clean farmer-friendly UI."),
                project("snapsktch", "snapsktch",
                        "AI-powered text-to-image generator using Hugging Face API and Streamlit.",
                        "Python", "#3572A5", 14, 3, 38, "4 months ago",
                        "chore: update model endpoint to stable-diffusion-xl",
                        List.of("Python", "Streamlit", "Hugging Face", "AI"),
                        null, "https://github.com/omkarjadhav/snapsktch", "active", true,
                        "A text-to-image generation app powered by Hugging Face's diffusion models. Users describe an image in text, and SnapSktch renders it in seconds via Streamlit's interactive UI."));

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
                experience("f3a9b1c", "job", "Web Developer Intern", "Dnyanda Solutions Pvt. Ltd.",
                        "Jul 2022", "Sep 2022",
                        List.of("Developed backend systems using PHP for client e-commerce applications",
                                "Built a complete E-commerce platform with user authentication, cart, and payment flow",
                                "Collaborated on HTML/CSS frontend for responsive shopping interfaces",
                                "Worked with MySQL for product catalogue and order management"),
                        "work/dnyanda-solutions", "#00ff88", "green",
                        List.of("PHP", "HTML", "CSS", "JavaScript", "MySQL"), null),
                experience("a2d8e4f", "achievement", "Python Bootcamp Certification", "Udemy",
                        "Jan 2023", null,
                        List.of("Completed comprehensive Python programming bootcamp",
                                "Covered OOP, file handling, data structures, and web scraping"),
                        "cert/python-bootcamp", "#e3b341", "yellow",
                        List.of("Python", "Udemy", "Certification"), null),
                experience("b5c7f2a", "achievement", "Java Programming Certification", "Udemy",
                        "Mar 2023", null,
                        List.of("Completed Java programming course covering core Java and OOP principles",
                                "Built multiple project applications including a library management system"),
                        "cert/java-programming", "#e3b341", "yellow",
                        List.of("Java", "OOP", "Udemy", "Certification"), null),
                experience("0d3f9e1", "education", "B.Tech Computer Science & Engineering (Data Science)",
                        "KIT College of Engineering, Kolhapur", "Aug 2023", "May 2026",
                        List.of("SGPA: 8.0/10 — Pursuing specialization in Data Science",
                                "Relevant coursework: DSA, OS, DBMS, Computer Networks, Machine Learning",
                                "Building projects in ML, full-stack web development, and systems programming"),
                        "edu/btech-cse", "#58a6ff", "blue",
                        List.of("DSA", "OS", "DBMS", "Machine Learning", "Data Science"), null),
                experience("1e2b4c7", "education", "Diploma in Computer Engineering", "ICRE Gargoti",
                        "Jun 2020", "May 2023",
                        List.of("Graduated with 87% — Top performer in department",
                                "Core subjects: C, C++, Java, DBMS, Digital Electronics, Networking"),
                        "edu/diploma-cse", "#58a6ff", "blue",
                        List.of("C++", "Java", "Networking", "DBMS"), null),
                experience("2c3d5e8", "education", "SSC (Class X)", "Dindewadi High School",
                        "Mar 2020", null,
                        List.of("Scored 94% — School topper in Mathematics and Science"),
                        "edu/high-school", "#58a6ff", "blue",
                        List.of("Mathematics", "Science"), null));

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

    private void seedSkills() {
        seedBranch("feature/web", "#58a6ff", 0, List.of(
                skill("HTML/CSS", 5, "🌐", null), skill("JavaScript", 4, "JS", null),
                skill("PHP", 4, "🐘", "v8"), skill("React", 3, "⚛", null),
                skill("Next.js", 3, "▲", "v14"), skill("Tailwind CSS", 3, "🎨", null)));
        seedBranch("feature/backend", "#00ff88", 1, List.of(
                skill("Python", 4, "🐍", null), skill("Java", 3, "☕", null),
                skill("C++", 3, "⚙", null), skill("MySQL", 4, "🗄", null),
                skill("PostgreSQL", 3, "🐘", null), skill("MongoDB", 3, "🍃", null)));
        seedBranch("feature/ml", "#f0883e", 2, List.of(
                skill("NumPy", 4, "🔢", null), skill("Pandas", 4, "🐼", null),
                skill("Scikit-learn", 3, "🧠", null), skill("Streamlit", 3, "📊", null),
                skill("Jupyter", 4, "📓", null)));
        seedBranch("feature/tools", "#d2a8ff", 3, List.of(
                skill("Git", 5, "⑂", "v2.45"), skill("GitHub", 4, "🐙", null),
                skill("VS Code", 5, "💻", null), skill("Postman", 4, "📮", null)));
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
                skillDiff("TypeScript", "added", "migrating all JS projects"),
                skillDiff("Next.js 14 App Router", "added", "used in this portfolio"),
                skillDiff("Docker", "added", "learning containerization"),
                skillDiff("PostgreSQL", "added", "replacing MySQL in new projects"),
                skillDiff("Python", "modified", "leveling up: async + FastAPI"),
                skillDiff("React", "modified", "deepening patterns & performance"),
                skillDiff("jQuery", "deprecated", "replaced by React"),
                skillDiff("Bootstrap", "deprecated", "replaced by Tailwind CSS"));

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
