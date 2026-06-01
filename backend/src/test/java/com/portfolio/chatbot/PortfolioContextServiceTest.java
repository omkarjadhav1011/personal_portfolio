package com.portfolio.chatbot;

import com.portfolio.experience.CommitEntry;
import com.portfolio.experience.ExperienceRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test against the configured Postgres (docker-compose must be up). Transactional
 * via {@code @DataJpaTest}, so seeded rows roll back after the test.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioContextServiceTest {

    @Autowired ProfileRepository profileRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired ExperienceRepository experienceRepository;
    @Autowired SkillBranchRepository skillBranchRepository;
    @Autowired SkillDiffRepository skillDiffRepository;

    @Test
    void buildsContextFromSampleData() {
        clearAll();
        seedProfile();
        seedProjects();
        seedExperience();
        seedSkills();
        seedDiff();

        PortfolioContextService service = new PortfolioContextService(
                profileRepository, projectRepository, experienceRepository,
                skillBranchRepository, skillDiffRepository);

        PortfolioContext ctx = service.getContext();

        System.out.println("===== ASSEMBLED PORTFOLIO CONTEXT =====");
        System.out.println(ctx);
        System.out.println("=======================================");

        // profile
        assertEquals("Omkar Jadhav", ctx.profile().name());
        assertEquals(List.of("Loves git"), ctx.profile().funFacts());
        assertEquals(1, ctx.profile().socials().size());

        // projects: pinned first
        assertEquals(2, ctx.projects().size());
        assertTrue(ctx.projects().get(0).pinned(), "pinned project should come first");
        assertEquals("pinned-proj", ctx.projects().get(0).slug());
        assertEquals(List.of("java", "spring"), ctx.projects().get(0).tags());

        // experience
        assertEquals(1, ctx.experience().size());
        assertEquals(List.of("Built things"), ctx.experience().get(0).description());

        // skills: branch present, skills sorted by name asc
        assertEquals(1, ctx.skillBranches().size());
        List<String> skillNames = ctx.skillBranches().get(0).skills().stream()
                .map(PortfolioContext.SkillSummary::name).toList();
        assertEquals(List.of("Axios", "Zod"), skillNames, "skills sorted by name asc");

        // diff
        assertEquals(1, ctx.skillDiff().size());
        assertEquals("TypeScript", ctx.skillDiff().get(0).name());
    }

    /** Wipe all tables so assertions are deterministic regardless of accumulated dev data
     *  (rolled back at test end by @DataJpaTest). Branches cascade to skills. */
    private void clearAll() {
        skillBranchRepository.deleteAll();
        skillDiffRepository.deleteAll();
        projectRepository.deleteAll();
        experienceRepository.deleteAll();
        profileRepository.deleteAll();
        profileRepository.flush();
    }

    private void seedProfile() {
        Profile p = new Profile();
        p.setName("Omkar Jadhav");
        p.setHandle("omkarjadhav");
        p.setHeadline("Full-Stack Dev");
        p.setBio("Builder.");
        p.setCurrentBranch("main");
        p.setCurrentStatus("Available");
        p.setAvailableForWork(true);
        p.setEmail("omkar@example.com");
        p.setLocation("Pune, India");
        p.setSocials(List.of(new SocialLink("GitHub", "https://github.com/x", "github")));
        p.setFunFacts(List.of("Loves git"));
        profileRepository.saveAndFlush(p);
    }

    private void seedProjects() {
        Project unpinned = newProject("plain-proj", false, 1);
        unpinned.setTags(List.of("react"));
        Project pinned = newProject("pinned-proj", true, 5);
        pinned.setTags(List.of("java", "spring"));
        projectRepository.saveAll(List.of(unpinned, pinned));
        projectRepository.flush();
    }

    private Project newProject(String slug, boolean pinned, int order) {
        Project p = new Project();
        p.setSlug(slug);
        p.setRepoName("repo-" + slug);
        p.setDescription("desc");
        p.setLanguage("Java");
        p.setLanguageColor("#b07219");
        p.setLastCommit("2026");
        p.setLastCommitMsg("msg");
        p.setPinned(pinned);
        p.setSortOrder(order);
        return p;
    }

    private void seedExperience() {
        CommitEntry e = new CommitEntry();
        e.setHash("h1");
        e.setType("job");
        e.setTitle("Intern");
        e.setOrg("NonStop io");
        e.setDate("2024");
        e.setDescription(List.of("Built things"));
        e.setBranch("main");
        e.setBranchColor("#00ff88");
        e.setSortOrder(1);
        experienceRepository.saveAndFlush(e);
    }

    private void seedSkills() {
        SkillBranch branch = new SkillBranch();
        branch.setBranchName("frontend");
        branch.setColor("#00ff88");
        branch.setOffset(0);

        Skill zod = new Skill();
        zod.setName("Zod");
        zod.setLevel(4);
        zod.setBranch(branch);

        Skill axios = new Skill();
        axios.setName("Axios");
        axios.setLevel(3);
        axios.setBranch(branch);

        branch.getSkills().add(zod);
        branch.getSkills().add(axios);
        skillBranchRepository.saveAndFlush(branch);
    }

    private void seedDiff() {
        SkillDiff d = new SkillDiff();
        d.setName("TypeScript");
        d.setType("added");
        d.setSortOrder(0);
        skillDiffRepository.saveAndFlush(d);
    }
}
