/**
 * The JSON-LD entity graph.
 *
 * This is the highest-leverage file in the SEO work, because the central problem
 * on this site is not ranking — it is identity. At least fifteen people named
 * Omkar Jadhav work in software, including a Software Engineer at Google, a
 * Senior SWE at LTIMindtree, two developers in Kolhapur and one in Pune. Nothing
 * about the string "Omkar Jadhav" distinguishes him from any of them.
 *
 * What does distinguish him is a graph of *relationships*: this person works for
 * THAT organisation, graduated from THAT institution, wrote THAT code, and is the
 * same entity as THAT GitHub account. Schools and employers are already known
 * entities with their own presence, so linking to them borrows disambiguating
 * power the name alone cannot supply.
 *
 * Two rules govern everything below.
 *
 * 1. EVERY NODE HAS A STABLE @id, AND REFERENCES USE {"@id": ...} RATHER THAN
 *    REPEATING THE NODE. Without it, a Person object copied onto eight pages can
 *    be read as up to eight entities; with it, all eight pages contribute
 *    evidence to one. The @id is an identifier, not a fetchable address, but it
 *    is built from VITE_SITE_URL like every other absolute URL here so the
 *    domain migration stays a one-value change.
 *
 * 2. STRUCTURED DATA MAY ONLY CLAIM WHAT A READER CAN SEE. Everything below is
 *    derived from the same API payload that renders the visible page — skills
 *    come from the rendered skills section, credentials from the rendered
 *    timeline, job title from the rendered role card. Nothing is invented here,
 *    and nothing is hardcoded that the page does not also display. The two
 *    exceptions are called out where they occur.
 */

/** Minimal JSON-LD shapes. Deliberately loose: schema.org is not a closed set. */
export type JsonLdValue = string | number | boolean | null | JsonLdNode | JsonLdValue[];
export interface JsonLdNode {
  [key: string]: JsonLdValue | undefined;
}

/** A reference to a node defined elsewhere in the graph. */
const ref = (id: string): JsonLdNode => ({ "@id": id });

/** Drops undefined, null, empty strings and empty arrays — schema.org treats an
 *  empty value as a claim of emptiness, so omitting is always better. */
function clean(node: JsonLdNode): JsonLdNode {
  const out: JsonLdNode = {};
  for (const [key, value] of Object.entries(node)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value) && value.length === 0) continue;
    out[key] = value;
  }
  return out;
}

// ─── Inputs ──────────────────────────────────────────────────────────────────

export interface GraphProfile {
  name: string;
  headline?: string;
  bio?: string;
  email?: string;
  location?: string;
  avatarUrl?: string | null;
  socials?: { label: string; url: string; icon: string }[];
  currentRole?: {
    enabled?: boolean;
    title?: string;
    company?: string;
    url?: string;
    location?: string;
    startedAt?: string;
  } | null;
}

export interface GraphProject {
  slug: string;
  repoName: string;
  description?: string;
  longDescription?: string | null;
  language?: string;
  tags?: string[];
  repoUrl?: string | null;
  liveUrl?: string | null;
}

export interface GraphExperience {
  type: string;
  title: string;
  org: string;
  date?: string;
  dateEnd?: string | null;
  description?: string[];
}

export interface GraphSkillBranch {
  branchName: string;
  skills: { name: string }[];
}

export interface GraphInput {
  siteUrl: string;
  /** Origin the backend serves assets from; avatar paths are relative to it. */
  assetOrigin?: string;
  route: string;
  profile: GraphProfile;
  projects: GraphProject[];
  experience: GraphExperience[];
  skillBranches: GraphSkillBranch[];
}

// ─── Constants that are facts, not page content ──────────────────────────────

/**
 * His full legal name. Not read from the profile record because that field holds
 * the common form ("Omkar Jadhav") that the site displays everywhere.
 *
 * This is the single most valuable string in the graph. "Omkar Jadhav" is shared
 * with at least fifteen software engineers; "Omkar Jayvant Jadhav" is effectively
 * unique. It is visible on the page — the biography opens with it — so marking it
 * up is legitimate.
 */
const LEGAL_NAME = "Omkar Jayvant Jadhav";

/**
 * Name variants worth resolving to this entity, including one common
 * misspelling. Deliberately NOT included: strings like "Omkar Jadhav Pune" or
 * "Omkar Jadhav KIT Kolhapur". Those are search queries, not names, and putting
 * them here would be keyword stuffing in structured data.
 */
const NAME_VARIANTS = ["Omkar J. Jadhav", "Omkar Jaywant Jadhav"];

/**
 * Institution names exactly as the institutions write them, with the @id each
 * maps to. "KIT College" or "ICRE Gargoti" will not match the real-world entity,
 * which is the entire point of linking to it.
 */
const INSTITUTIONS: Record<string, { id: string; name: string; locality: string }> = {
  kit: {
    id: "#edu-kit-kolhapur",
    name: "KIT's College of Engineering (Autonomous), Kolhapur",
    locality: "Kolhapur",
  },
  icre: {
    id: "#edu-icre-gargoti",
    name: "Institute of Civil and Rural Engineering, Gargoti",
    locality: "Gargoti",
  },
  ssc: {
    id: "#edu-scpmv-dindewadi",
    name: "Shankar Chakru Patil Madhyamik Vidhyalaya, Dindewadi",
    locality: "Dindewadi",
  },
};

/** Maps a free-text organisation name from the timeline onto an institution. */
function institutionFor(org: string) {
  const lower = org.toLowerCase();
  if (lower.includes("kit")) return INSTITUTIONS.kit;
  if (lower.includes("civil and rural") || lower.includes("icre")) return INSTITUTIONS.icre;
  if (lower.includes("shankar chakru") || lower.includes("dindewadi")) return INSTITUTIONS.ssc;
  return null;
}

// ─── Builder ─────────────────────────────────────────────────────────────────

export function buildGraph(input: GraphInput): JsonLdNode {
  const { siteUrl, assetOrigin = "", route, profile, projects, experience, skillBranches } = input;

  const abs = (path: string) => `${siteUrl}/${path.replace(/^\/+/, "")}`;
  const id = (fragment: string) => `${siteUrl}/${fragment.replace(/^\/+/, "")}`;

  const PERSON = id("#person");
  const WEBSITE = id("#website");
  const EMPLOYER = id("#organization");
  const PHOTO = id("#profile-photo");

  const nodes: JsonLdNode[] = [];

  // ── Person: the root. Everything else points back at this @id. ─────────────
  const role = profile.currentRole?.enabled ? profile.currentRole : undefined;

  // sameAs is the mechanism that actually separates him from his namesakes, and
  // it is verified for reciprocity — so only confirmed, owned profiles belong
  // here. A wrong entry does not merely fail to help; it instructs Google to
  // merge him with someone else. LinkedIn is absent until its URL is confirmed:
  // the one previously published resolves to a different Omkar Jadhav.
  const sameAs = (profile.socials ?? [])
    .map((s) => s.url)
    .filter((url) => /^https?:\/\//i.test(url));

  // knowsAbout mirrors the rendered skills section exactly (E3/E10). It is not a
  // curated list here on purpose: if a withdrawn technology ever reappears in
  // the skills data, it shows up on the page and in the graph together, and the
  // prerender content check stops the build before either ships.
  const knowsAbout = skillBranches.flatMap((b) => b.skills.map((s) => s.name));

  const education = experience.filter((e) => e.type === "education");
  const certifications = experience.filter((e) => e.type === "achievement");

  const alumniOf = education
    .map((e) => institutionFor(e.org))
    .filter((x): x is NonNullable<typeof x> => Boolean(x))
    .map((inst) => ref(id(inst.id)));

  // Degrees and certificates. `recognizedBy` points at the institution node for
  // degrees, which is what ties the credential to a real-world organisation.
  const hasCredential = [
    ...education.map((e) => {
      const inst = institutionFor(e.org);
      return clean({
        "@type": "EducationalOccupationalCredential",
        name: e.title,
        credentialCategory: "degree",
        educationalLevel: /b\.?tech|bachelor/i.test(e.title)
          ? "Bachelor's Degree"
          : /diploma/i.test(e.title)
            ? "Diploma"
            : "Secondary Education",
        recognizedBy: inst ? ref(id(inst.id)) : { "@type": "Organization", name: e.org },
        dateCreated: e.dateEnd ?? e.date,
      });
    }),
    ...certifications.map((e) =>
      clean({
        "@type": "EducationalOccupationalCredential",
        name: e.title,
        credentialCategory: "certificate",
        recognizedBy: { "@type": "Organization", name: e.org },
        dateCreated: e.date,
      }),
    ),
  ];

  // The description must be a single quotable sentence — an AI assistant
  // retrieves a passage, not a page. Falls back to the headline.
  const description =
    (profile.bio ?? "").split(/\n\s*\n/)[0]?.replace(/\s+/g, " ").trim() || profile.headline;

  nodes.push(
    clean({
      "@type": "Person",
      "@id": PERSON,
      name: LEGAL_NAME,
      alternateName: [profile.name, ...NAME_VARIANTS].filter(
        (n, i, all) => n && all.indexOf(n) === i,
      ),
      jobTitle: role?.title ?? profile.headline,
      description,
      url: abs("/"),
      mainEntityOfPage: ref(WEBSITE),
      image: profile.avatarUrl ? ref(PHOTO) : undefined,
      email: profile.email ? `mailto:${profile.email}` : undefined,
      sameAs: sameAs.length > 0 ? sameAs : undefined,
      knowsAbout: knowsAbout.length > 0 ? knowsAbout : undefined,
      alumniOf: alumniOf.length > 0 ? alumniOf : undefined,
      hasCredential: hasCredential.length > 0 ? hasCredential : undefined,
      worksFor: role?.company ? ref(EMPLOYER) : undefined,
      // He works on-site in Pune; Kolhapur is his hometown and where he studied.
      // Both are stated so the entity resolves for either city, without either
      // being a false claim.
      address: {
        "@type": "PostalAddress",
        addressLocality: "Pune",
        addressRegion: "Maharashtra",
        addressCountry: "IN",
      },
      homeLocation: {
        "@type": "Place",
        name: "Kolhapur, Maharashtra, India",
      },
      nationality: { "@type": "Country", name: "India" },
    }),
  );

  // ── Profile photo ──────────────────────────────────────────────────────────
  if (profile.avatarUrl) {
    const contentUrl = /^https?:\/\//i.test(profile.avatarUrl)
      ? profile.avatarUrl
      : `${assetOrigin}${profile.avatarUrl}`;
    nodes.push(
      clean({
        "@type": "ImageObject",
        "@id": PHOTO,
        contentUrl,
        url: contentUrl,
        caption: `${LEGAL_NAME}, ${role?.title ?? profile.headline ?? "software engineer"}`,
      }),
    );
  }

  // ── Employer ───────────────────────────────────────────────────────────────
  if (role?.company) {
    nodes.push(
      clean({
        "@type": "Organization",
        "@id": EMPLOYER,
        name: role.company,
        url: role.url,
        address: {
          "@type": "PostalAddress",
          streetAddress: "Kharadi",
          addressLocality: "Pune",
          addressRegion: "Maharashtra",
          addressCountry: "IN",
        },
      }),
    );
  }

  // ── Institutions ───────────────────────────────────────────────────────────
  const seenInstitutions = new Set<string>();
  for (const entry of education) {
    const inst = institutionFor(entry.org);
    if (!inst || seenInstitutions.has(inst.id)) continue;
    seenInstitutions.add(inst.id);
    nodes.push(
      clean({
        "@type": "EducationalOrganization",
        "@id": id(inst.id),
        name: inst.name,
        address: {
          "@type": "PostalAddress",
          addressLocality: inst.locality,
          addressRegion: "Maharashtra",
          addressCountry: "IN",
        },
      }),
    );
  }

  // ── WebSite ────────────────────────────────────────────────────────────────
  // No SearchAction: there is no on-site search endpoint, and marking up a
  // search box that does not exist claims something a reader cannot verify.
  nodes.push(
    clean({
      "@type": "WebSite",
      "@id": WEBSITE,
      url: abs("/"),
      name: `${profile.name} — Portfolio`,
      inLanguage: "en",
      publisher: ref(PERSON),
      about: ref(PERSON),
    }),
  );

  // ── Per-route nodes ────────────────────────────────────────────────────────
  const projectSlug = route.startsWith("/projects/") ? route.split("/").pop() : undefined;

  if (projectSlug) {
    const project = projects.find((p) => p.slug === projectSlug);
    if (project) {
      const projectUrl = abs(route);
      nodes.push(
        clean({
          "@type": "SoftwareSourceCode",
          "@id": `${projectUrl}#software`,
          name: project.repoName,
          description: project.longDescription || project.description,
          url: projectUrl,
          // Omitted when the project has no repository. A codeRepository
          // pointing at a 404 is worse than none: three of the links previously
          // published here were dead.
          codeRepository: project.repoUrl ?? undefined,
          programmingLanguage: project.language,
          keywords: project.tags,
          author: ref(PERSON),
          creator: ref(PERSON),
        }),
      );

      // Mirrors the breadcrumb trail rendered on the page. Positions and labels
      // must match it exactly, or this is markup for content nobody can see.
      nodes.push({
        "@type": "BreadcrumbList",
        "@id": `${projectUrl}#breadcrumb`,
        itemListElement: [
          { "@type": "ListItem", position: 1, name: "Home", item: abs("/") },
          { "@type": "ListItem", position: 2, name: "Projects", item: abs("/#projects") },
          { "@type": "ListItem", position: 3, name: project.repoName, item: projectUrl },
        ],
      });
    }
  }

  return { "@context": "https://schema.org", "@graph": nodes };
}
