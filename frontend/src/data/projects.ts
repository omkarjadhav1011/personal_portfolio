import type { Project } from "@/types";
import { SITE_URL } from "@/lib/site";

/**
 * Static project data — the fallback set, and the reference for what the admin
 * panel should hold.
 *
 * ⚠ ENGAGEMENT METRICS ARE ZERO ON PURPOSE. The previous version of this file
 * (and the database seeded from it) carried invented star, fork and commit
 * counts — 19 stars on one repo, 84 commits on another — that matched nothing
 * in the real GitHub account, plus a `lastCommit` of "just now" that was never
 * true. Publishing fabricated metrics is a truthfulness problem before it is an
 * SEO one. Fill these in only with numbers that can be verified, or leave them
 * at zero and let the work speak.
 *
 * ⚠ Two descriptions below are marked DRAFT: they were reconstructed by reading
 * the repositories, not supplied by the owner, and must be reviewed before they
 * go live.
 */
export const projects: Project[] = [
  {
    id: "1",
    slug: "portfolio-ai-assistant",
    repoName: "personal_portfolio",
    description:
      "This portfolio: a Spring Boot API and React SPA with an AI assistant, multi-provider LLM failover, and a public MCP server.",
    language: "Java",
    languageColor: "#b07219",
    stars: 0,
    forks: 0,
    commits: 0,
    lastCommit: "",
    lastCommitMsg: "",
    tags: ["Java", "Spring Boot", "React", "TypeScript", "PostgreSQL", "Docker"],
    liveUrl: SITE_URL,
    repoUrl: "https://github.com/omkarjadhav1011/personal_portfolio",
    status: "active",
    pinned: true,
    longDescription:
      "A Git-themed developer portfolio built as a full application rather than a static site. " +
      "The backend is Spring Boot 3.5 on Java 21 with PostgreSQL and Flyway migrations; the " +
      "frontend is React 18 with Vite and TypeScript. Beyond the public pages it carries an admin " +
      "panel, an encrypted document vault using per-file AES-256-GCM data keys, TOTP multi-factor " +
      "auth, and an AI assistant. The assistant runs on an ordered provider chain (Groq, Cerebras, " +
      "Mistral, Gemini, OpenRouter) with per-provider circuit breaking and daily quotas, so a dead " +
      "or rate-limited provider fails over instead of failing. Retrieval is backed by pgvector with " +
      "Gemini embeddings, and a public read-only MCP server exposes the same query layer so a " +
      "recruiter's own AI client can evaluate the work directly.",
  },
  {
    id: "2",
    slug: "ai-interview-preparation-system",
    repoName: "InterviewAI",
    description:
      "Voice-driven interview practice: speech recognition transcribes spoken answers, the Gemini API scores them and returns feedback.",
    language: "Python",
    languageColor: "#3572A5",
    stars: 0,
    forks: 0,
    commits: 0,
    lastCommit: "",
    lastCommitMsg: "",
    tags: ["Python", "Gemini API", "Speech Recognition"],
    repoUrl: "https://github.com/omkarjadhav1011/InterviewAI",
    status: "active",
    pinned: true,
    longDescription:
      "A voice-driven interview practice system that runs question-and-answer sessions with " +
      "automated feedback. Speech Recognition converts the user's spoken answer to text, which " +
      "feeds an evaluation pipeline; the Gemini API scores the response and generates actionable " +
      "feedback using structured prompt templates.",
  },
  {
    id: "3",
    slug: "text-to-image-generator",
    repoName: "Text-to-Image Generator",
    description:
      "A Streamlit web app that turns a text prompt into an image using a Hugging Face hosted model.",
    language: "Python",
    languageColor: "#3572A5",
    stars: 0,
    forks: 0,
    commits: 0,
    lastCommit: "",
    lastCommitMsg: "",
    tags: ["Python", "Streamlit", "Hugging Face API"],
    // No repoUrl: the previously published link (/snapsktch) is a 404, and no
    // repository has been confirmed. A dead code link costs more credibility
    // than a missing one.
    status: "active",
    pinned: true,
    longDescription:
      "A Streamlit web app that takes a text prompt and generates a corresponding image via a " +
      "Hugging Face hosted model. Handles the API integration, the request/response flow, and " +
      "error handling for failed or slow generations.",
  },
  {
    id: "4",
    slug: "expense-tracker",
    repoName: "expense-tracker",
    description:
      "Full-stack expense manager: React frontend, Spring Boot REST API, PostgreSQL schema with category and monthly aggregation.",
    language: "Java",
    languageColor: "#b07219",
    stars: 0,
    forks: 0,
    commits: 0,
    lastCommit: "",
    lastCommitMsg: "",
    tags: ["React", "Spring Boot", "PostgreSQL", "REST APIs"],
    repoUrl: "https://github.com/omkarjadhav1011/expense-tracker",
    status: "active",
    pinned: true,
    longDescription:
      "A full-stack expense management application with a React frontend and Spring Boot REST API " +
      "backend. Includes the PostgreSQL schema design and the queries for adding, filtering and " +
      "aggregating expenses, producing monthly and category-wise spending breakdowns.",
  },
  {
    id: "5",
    slug: "crop-recommendation",
    repoName: "crop-recommendation",
    // DRAFT — reconstructed from the repository source, pending owner review.
    description:
      "A Flask web app that recommends a crop from seven soil and climate readings using a trained classifier.",
    language: "Python",
    languageColor: "#3572A5",
    stars: 0,
    forks: 0,
    commits: 0,
    lastCommit: "",
    lastCommitMsg: "",
    tags: ["Python", "Flask", "Machine Learning"],
    repoUrl: "https://github.com/omkarjadhav1011/crop-recommendation",
    status: "active",
    pinned: false,
    longDescription:
      "DRAFT — pending review. A Flask application that recommends a crop from seven soil and " +
      "climate inputs: nitrogen, phosphorus, potassium, temperature, humidity, pH and rainfall. " +
      "A classifier trained on a 2,200-row dataset covering 22 crops is loaded from a pickle file " +
      "and served behind a small multi-page UI (home, service, input form, result, help, contact).",
  },
  {
    id: "6",
    slug: "dev-mobiles",
    repoName: "Mobile_Shop",
    // DRAFT — reconstructed from the repository source, pending owner review.
    description:
      "A PHP and MySQL e-commerce site for a mobile phone store, with customer accounts, cart, orders and an admin back office.",
    language: "PHP",
    languageColor: "#4F5D95",
    stars: 0,
    forks: 0,
    commits: 0,
    lastCommit: "",
    lastCommitMsg: "",
    tags: ["PHP", "MySQL", "JavaScript", "HTML5", "CSS3"],
    repoUrl: "https://github.com/omkarjadhav1011/Mobile_Shop",
    status: "archived",
    pinned: false,
    longDescription:
      "DRAFT — pending review. A server-rendered e-commerce application for a mobile phone store, " +
      "branded 'Dev Mobiles', built in PHP against a 13-table MySQL schema. The storefront covers " +
      "registration and login, product browsing by brand, search, wishlist, cart, checkout with a " +
      "generated order number, order tracking and invoices. A separate admin area manages products, " +
      "brands, inventory, order status, customer reviews, enquiries and date-range sales reports.",
  },
];
