import type { Profile } from "@/types";

export const profile: Profile = {
  name: "Omkar Jadhav",
  handle: "omkarjadhav",
  headline: "B.Tech CSE (Data Science) Student & Full-Stack Developer",
  bio: `I build things for the web and explore the intersection of software and data.
Currently pursuing B.Tech in Computer Science (Data Science) at KIT Kolhapur,
obsessed with clean code, great UX, and systems that scale.

When I'm not writing code, I'm experimenting with machine learning models,
building full-stack apps, or debugging something I broke at 2am.`,
  currentBranch: "main",
  currentStatus: "Open to internships & collaborations",
  availableForWork: true,
  email: "jadhavomkar101103@gmail.com",
  location: "Kolhapur, Maharashtra, India",
  socials: [
    {
      label: "GitHub",
      url: "https://github.com/omkarjadhav",
      icon: "github",
    },
    {
      label: "LinkedIn",
      url: "https://linkedin.com/in/omkarjadhav",
      icon: "linkedin",
    },
    {
      label: "Twitter",
      url: "https://twitter.com/omkarjadhav",
      icon: "twitter",
    },
  ],
  funFacts: [
    "I've written more git commit messages than diary entries",
    "Went from 94% in SSC to building ML models — the plot thickens",
    "I debug in production (just kidding... mostly)",
    "My Hugging Face API calls cost more than my monthly coffee budget",
  ],
  stash: [
    "☕  Coffee-driven development — 3 cups before 10am",
    "♟  Plays chess to debug decision-making",
    "📚  Reading: Designing Data-Intensive Applications (DDIA)",
    "🎵  Codes to lo-fi beats and post-rock",
    "🌱  Contributing to open source, one PR at a time",
  ],
  currentRole: {
    enabled: true,
    title: "Full-Stack Developer Intern",
    company: "NonStop io Technologies",
    monogram: "N",
    logoUrl: "",
    url: "https://nonstopio.com",
    location: "Pune, India · Hybrid",
    startedAt: "Mar 2024",
    tenure: "8 mos",
    accent: "#00ff88",
  },
};
