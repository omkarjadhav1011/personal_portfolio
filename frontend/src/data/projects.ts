import type { Project } from "@/types";

export const projects: Project[] = [
  {
    id: "1",
    slug: "git-portfolio",
    repoName: "git-portfolio",
    description:
      "A developer portfolio with Git-inspired UI — terminals, branches, and commit logs.",
    language: "TypeScript",
    languageColor: "#3178c6",
    stars: 12,
    forks: 3,
    commits: 47,
    lastCommit: "just now",
    lastCommitMsg: "feat: add command palette with Ctrl+K shortcut",
    tags: ["Next.js", "Tailwind", "Framer Motion"],
    liveUrl: "https://omkarjadhav.vercel.app",
    repoUrl: "https://github.com/omkarjadhav/git-portfolio",
    status: "active",
    pinned: true,
    longDescription:
      "Built with Next.js 14, this portfolio reimagines personal websites through the lens of Git — commit timelines, branch visualizations, and a fully interactive terminal command palette.",
  },
  {
    id: "2",
    slug: "dev-mobiles",
    repoName: "dev-mobiles",
    description:
      "Mobile shopping e-commerce platform with full auth, cart, search, and purchase flow.",
    language: "PHP",
    languageColor: "#4F5D95",
    stars: 8,
    forks: 2,
    commits: 84,
    lastCommit: "2 months ago",
    lastCommitMsg: "feat: add purchase flow with payment integration",
    tags: ["PHP", "HTML", "CSS", "JavaScript", "MySQL"],
    repoUrl: "https://github.com/omkarjadhav/dev-mobiles",
    status: "active",
    pinned: true,
    longDescription:
      "A full-featured mobile shopping platform built during my internship at Dnyanda Solutions. Supports user login, product search, cart management, and a complete purchase flow with payment integration.",
  },
  {
    id: "3",
    slug: "crop-recommendation",
    repoName: "crop-recommendation",
    description:
      "ML-based crop suggestion system using soil and weather data with a real-time farmer UI.",
    language: "Python",
    languageColor: "#3572A5",
    stars: 19,
    forks: 5,
    commits: 62,
    lastCommit: "3 months ago",
    lastCommitMsg: "feat: integrate real-time weather API for dynamic predictions",
    tags: ["Python", "Scikit-learn", "Pandas", "NumPy"],
    repoUrl: "https://github.com/omkarjadhav/crop-recommendation",
    status: "active",
    pinned: true,
    longDescription:
      "A machine learning system that recommends optimal crops based on soil composition and real-time weather conditions. Built with Scikit-learn classification models and a clean farmer-friendly UI.",
  },
  {
    id: "4",
    slug: "snapsktch",
    repoName: "snapsktch",
    description:
      "AI-powered text-to-image generator using Hugging Face API and Streamlit.",
    language: "Python",
    languageColor: "#3572A5",
    stars: 14,
    forks: 3,
    commits: 38,
    lastCommit: "4 months ago",
    lastCommitMsg: "chore: update model endpoint to stable-diffusion-xl",
    tags: ["Python", "Streamlit", "Hugging Face", "AI"],
    repoUrl: "https://github.com/omkarjadhav/snapsktch",
    status: "active",
    pinned: true,
    longDescription:
      "A text-to-image generation app powered by Hugging Face's diffusion models. Users describe an image in text, and SnapSktch renders it in seconds via Streamlit's interactive UI.",
  },
];

export const pinnedProjects = projects.filter((p) => p.pinned);
