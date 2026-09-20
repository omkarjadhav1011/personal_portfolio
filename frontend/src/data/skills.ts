import type { SkillBranch, SkillDiff } from "@/types";

/**
 * Skills, grouped into "branches".
 *
 * This list is the on-page mirror of the schema `knowsAbout` array (rulebook
 * E3/E10): structured data may only claim what a human can see here, so the two
 * must stay identical.
 *
 * REMOVED — withdrawn technologies, previously published as live skill claims:
 *   Next.js (v14)   — was in feature/web and in skillsDiff as "used in this portfolio"
 *   FastAPI         — was in skillsDiff as "leveling up: async + FastAPI"
 * These must never come back. The portfolio project page may describe its own
 * implementation factually, but that is a statement about software, not a
 * personal capability claim.
 *
 * ⚠ ALSO REMOVED — present on the site before, but NOT on the confirmed skills
 * list, so re-add only on the owner's say-so:
 *   Tailwind CSS, C++, MongoDB, NumPy, Pandas, Scikit-learn, Jupyter,
 *   jQuery and Bootstrap (the latter two were "deprecated" diff entries).
 */
export const skillBranches: SkillBranch[] = [
  {
    branchName: "feature/languages",
    color: "#58a6ff",
    offset: 0,
    skills: [
      { name: "C#", level: 4, icon: "#" },
      { name: "SQL", level: 4, icon: "🗄" },
      { name: "JavaScript", level: 4, icon: "JS" },
      { name: "TypeScript", level: 3, icon: "TS" },
      { name: "Python", level: 4, icon: "🐍" },
      { name: "Java", level: 3, icon: "☕" },
    ],
  },
  {
    branchName: "feature/backend",
    color: "#00ff88",
    offset: 1,
    skills: [
      { name: "NestJS", level: 4, icon: "🐱" },
      { name: "Spring Boot", level: 3, icon: "🌱" },
      { name: "REST APIs", level: 4, icon: "🔌" },
      { name: "PostgreSQL", level: 3, icon: "🐘" },
      { name: "MySQL", level: 4, icon: "🗃" },
      { name: "PHP", level: 3, icon: "🐘" },
    ],
  },
  {
    branchName: "feature/frontend",
    color: "#f0883e",
    offset: 2,
    skills: [
      { name: "React", level: 3, icon: "⚛" },
      { name: "HTML5", level: 5, icon: "🌐" },
      { name: "CSS3", level: 4, icon: "🎨" },
    ],
  },
  {
    branchName: "feature/ai",
    color: "#d2a8ff",
    offset: 3,
    skills: [
      { name: "Gemini API", level: 4, icon: "✦" },
      { name: "Hugging Face API", level: 3, icon: "🤗" },
      { name: "Prompt Engineering", level: 3, icon: "💬" },
    ],
  },
  {
    branchName: "feature/tools",
    color: "#e3b341",
    offset: 4,
    skills: [
      { name: "Git", level: 5, icon: "⑂" },
      { name: "GitHub", level: 4, icon: "🐙" },
      { name: "Docker", level: 3, icon: "🐳" },
      { name: "Postman", level: 4, icon: "📮" },
      { name: "Streamlit", level: 3, icon: "📊" },
      { name: "VS Code", level: 5, icon: "💻" },
    ],
  },
  {
    branchName: "feature/core-cs",
    color: "#7ee787",
    offset: 5,
    skills: [
      { name: "Data Structures & Algorithms", level: 4, icon: "🧮" },
      { name: "OOP", level: 4, icon: "🧱" },
      { name: "DBMS", level: 4, icon: "🗂" },
      { name: "MVC Architecture", level: 3, icon: "🏛" },
    ],
  },
];

export const allSkills = skillBranches.flatMap((b) => b.skills);

/**
 * The "git diff" of what's changing in his stack. Grounded in the confirmed
 * timeline — he joined Nonstop IO in Feb 2026, which is when C# and NestJS
 * became day-to-day work.
 */
export const skillsDiff: SkillDiff[] = [
  { type: "added", name: "C#", note: "day-to-day backend work at Nonstop IO" },
  { type: "added", name: "NestJS", note: "services on the enterprise reporting product" },
  { type: "added", name: "TypeScript", note: "typed everything, including this site" },
  { type: "added", name: "Docker", note: "containerized local dependencies" },
  {
    type: "modified",
    name: "SQL",
    note: "optimizing reporting, audit-log and data-retrieval queries",
  },
  { type: "modified", name: "Spring Boot", note: "the API behind this portfolio" },
];
