import type { Profile } from "@/types";
import { CANONICAL_STATEMENT } from "@/lib/identity";

/**
 * Static profile data.
 *
 * NOTE: this file is NOT dead code. `Home.tsx` reads the profile from the API,
 * but the Navbar, Footer, StatusBar, ContactSection, ContributionHeatmap,
 * RecruiterPage and the Ctrl+K terminal all import THIS object directly. Two
 * sources of truth for the same facts is how the site ended up claiming three
 * different job titles at once — so any fact corrected here must be corrected
 * in the admin panel (which writes the database) as well.
 *
 * Every fact below is from the confirmed subject profile. Do not add claims
 * that are not verifiable.
 */
export const profile: Profile = {
  name: "Omkar Jadhav",
  // The real GitHub username. This is not decoration: PRCard falls back to
  // `https://github.com/${handle}/${slug}` when a project has no explicit
  // repoUrl, so a wrong handle silently generates 404 links.
  handle: "omkarjadhav1011",
  headline: "Software Development Engineer I at Nonstop IO Technologies",
  // The first paragraph is the canonical statement, verbatim. It is also what
  // the JSON-LD `description` is built from, so the site, the schema and
  // llms.txt all publish the identical sentence — corroboration across sources
  // works on matching, and a paraphrase fragments the signal.
  //
  // ⚠ This is the STATIC copy. The live site reads the bio from the database,
  // so the same text has to be pasted into the admin panel to take effect.
  bio: `${CANONICAL_STATEMENT}

He implemented end-to-end user audit functionality on an enterprise reporting
product, contributed to its Report Builder module, and builds LLM-integrated
applications with the Gemini and Hugging Face APIs.`,
  currentBranch: "main",
  currentStatus: "Building backend services at Nonstop IO Technologies",
  // He is employed. This flag drove the "Open to internships & collaborations"
  // badge that told every recruiter he was still a student looking for work.
  availableForWork: false,
  email: "jadhavomkar101103@gmail.com",
  location: "Pune, Maharashtra, India",
  socials: [
    {
      label: "GitHub",
      url: "https://github.com/omkarjadhav1011",
      icon: "github",
    },
    {
      label: "LeetCode",
      url: "https://leetcode.com/u/jadhav_omkar1013/",
      icon: "leetcode",
    },
    // Confirmed by the owner. Note this is NOT linkedin.com/in/omkarjadhav,
    // which was published here for months and belongs to a different Omkar
    // Jadhav (Dropouts Technologies LLP, University of Pune 2005-2009). A
    // profile link is an identity claim, and a wrong one tells Google to merge
    // this entity with a stranger's. Do not "simplify" this slug.
    {
      label: "LinkedIn",
      url: "https://www.linkedin.com/in/omkar-jadhav-st/",
      icon: "linkedin",
    },
  ],
  funFacts: [
    "Solved 210+ problems on LeetCode",
    "Took the long route into engineering: diploma at ICRE Gargoti, then B.Tech at KIT Kolhapur",
    "Wrote the audit-logging layer that tracks user actions across a production reporting product",
  ],
  // Previously template filler ("Reading DDIA", "Codes to lo-fi beats", "My
  // Hugging Face API calls cost more than my monthly coffee budget"). None of it
  // was verifiable, so it was replaced with claims that are. Personalize freely
  // — just keep every line true.
  stash: [
    "⑂  Built this site end to end: Spring Boot API, React SPA, PostgreSQL",
    "🤖  Wrote a multi-provider LLM failover router so the assistant survives a dead provider",
    "🔐  Encrypts every vault file with its own AES-256-GCM data key",
  ],
  currentRole: {
    enabled: true,
    title: "Software Development Engineer I",
    company: "Nonstop IO Technologies",
    monogram: "N",
    logoUrl: "",
    url: "https://nonstopio.com",
    location: "Kharadi, Pune, Maharashtra, India · On-site",
    startedAt: "Feb 2026",
    // ⚠ Manual value — it does not recompute. Measured from Feb 2026 to
    // Sep 2026. Update it, or derive it from startedAt.
    tenure: "7 mos",
    accent: "#00ff88",
  },
};
