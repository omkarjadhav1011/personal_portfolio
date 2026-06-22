package com.portfolio.rag;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * Triggers an async RAG re-index after any successful write on a corpus-affecting admin controller
 * (Phase D1). {@code @AfterReturning} fires only on success (not on a thrown exception), so a
 * rejected/invalid request won't re-index. Covers profile (incl. resume upload), projects,
 * experience, and skills — and any future write methods added to those controllers.
 */
@Aspect
@Component
public class CorpusReindexAspect {

    private final ReindexTrigger trigger;

    public CorpusReindexAspect(ReindexTrigger trigger) {
        this.trigger = trigger;
    }

    @Pointcut("within(com.portfolio.profile.ProfileController) "
            + "|| within(com.portfolio.project.ProjectController) "
            + "|| within(com.portfolio.experience.ExperienceController) "
            + "|| within(com.portfolio.skill.SkillBranchController) "
            + "|| within(com.portfolio.skill.SkillDiffController)")
    void corpusController() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.PutMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.PatchMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    void writeMapping() {
    }

    @AfterReturning("corpusController() && writeMapping()")
    public void afterCorpusWrite() {
        trigger.reindexAsync();
    }
}
