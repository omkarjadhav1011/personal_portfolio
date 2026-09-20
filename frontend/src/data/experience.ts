import type { CommitEntry } from "@/types";

/**
 * Career and education timeline.
 *
 * Every entry is from the confirmed subject profile. Institution names are the
 * full official forms, not abbreviations — schema `alumniOf` has to match a real
 * organization for the entity link to resolve, and "ICRE Gargoti" or "KIT
 * College" will not.
 *
 * Removed in the SEO overhaul: a "Web Developer Intern, Dnyanda Solutions
 * Pvt. Ltd., Jul-Sep 2022" entry that came from demo seed data and was never a
 * confirmed fact.
 */
export const timeline: CommitEntry[] = [
  {
    hash: "9c4e1a7",
    type: "job",
    title: "Software Development Engineer I",
    org: "Nonstop IO Technologies",
    date: "Aug 2026",
    description: [
      "Backend development on an enterprise reporting product, contributing to live production modules",
      "Implemented end-to-end user audit functionality tracking and logging user actions across the application for compliance and traceability",
      "Contributed to the Report Builder module and wrote optimized SQL queries for reporting, audit logs, and data-retrieval flows",
    ],
    branch: "work/nonstop-io",
    branchColor: "#00ff88",
    colorKey: "green",
    tags: ["C#", "NestJS", "SQL"],
    url: "https://nonstopio.com",
  },
  {
    hash: "3b7d0f2",
    type: "job",
    title: "Software Developer Intern",
    org: "Nonstop IO Technologies",
    date: "Feb 2026",
    dateEnd: "Aug 2026",
    description: [
      "Joined the backend team working on the enterprise reporting product in C#, NestJS and SQL",
      "Converted to Software Development Engineer I in August 2026",
    ],
    branch: "work/nonstop-io",
    branchColor: "#00ff88",
    colorKey: "green",
    tags: ["C#", "NestJS", "SQL"],
    url: "https://nonstopio.com",
  },
  {
    hash: "0d3f9e1",
    type: "education",
    title: "B.Tech, Computer Science & Engineering (Data Science)",
    org: "KIT's College of Engineering (Autonomous), Kolhapur",
    date: "2023",
    dateEnd: "2026",
    description: [
      "Graduated in 2026 with 80%",
      "Coursework: Data Structures & Algorithms, DBMS, Operating Systems, Computer Networks, Data Science",
    ],
    branch: "edu/btech-cse",
    branchColor: "#58a6ff",
    colorKey: "blue",
    tags: ["DSA", "DBMS", "Data Science"],
  },
  {
    hash: "1e2b4c7",
    type: "education",
    title: "Diploma, Computer Engineering",
    org: "Institute of Civil and Rural Engineering, Gargoti",
    date: "2020",
    dateEnd: "2023",
    description: ["Graduated in 2023 with 87%"],
    branch: "edu/diploma-cse",
    branchColor: "#58a6ff",
    colorKey: "blue",
    tags: ["C++", "DBMS"],
  },
  {
    hash: "2c3d5e8",
    type: "education",
    title: "High School (SSC)",
    org: "Shankar Chakru Patil Madhyamik Vidhyalaya, Dindewadi",
    date: "2019",
    dateEnd: "2020",
    description: ["Completed in 2020 with 94%"],
    branch: "edu/high-school",
    branchColor: "#58a6ff",
    colorKey: "blue",
    tags: [],
  },
  // ⚠ CERTIFICATION DATES NEED CONFIRMATION.
  // The three Udemy certifications are confirmed; their completion dates are
  // not. "Jan 2023" and "Mar 2023" below were carried over from the pre-existing
  // static data (plausibly correct, never verified), and the AI/ML/Data Science
  // bootcamp has no date at all, so it is listed by year. Correct these, or say
  // the word and they can be reduced to year-only for all three.
  {
    hash: "a2d8e4f",
    type: "achievement",
    title: "Python Bootcamp: Zero to Hero",
    org: "Udemy",
    date: "Jan 2023",
    description: ["Completed the Python programming bootcamp"],
    branch: "cert/python-bootcamp",
    branchColor: "#e3b341",
    colorKey: "yellow",
    tags: ["Python", "Udemy"],
  },
  {
    hash: "b5c7f2a",
    type: "achievement",
    title: "Java Programming: Beginner to Master",
    org: "Udemy",
    date: "Mar 2023",
    description: ["Completed the Java programming course covering core Java and OOP"],
    branch: "cert/java-programming",
    branchColor: "#e3b341",
    colorKey: "yellow",
    tags: ["Java", "OOP", "Udemy"],
  },
  {
    hash: "c8e1a94",
    type: "achievement",
    title: "AI, Machine Learning & Data Science Bootcamp",
    org: "Udemy",
    date: "2023",
    description: ["Completed the AI, machine learning and data science bootcamp"],
    branch: "cert/ai-ml-ds",
    branchColor: "#e3b341",
    colorKey: "yellow",
    tags: ["Udemy"],
  },
];
