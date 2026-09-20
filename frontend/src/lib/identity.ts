/**
 * The identity constants — one definition of who this site is about.
 *
 * Google builds a person entity by finding claims about an identifier and
 * corroborating them across independent sources. Corroboration works on
 * matching, so *identical* phrasing across the site, `llms.txt`, the JSON-LD
 * description, the meta description, the GitHub bio and the LinkedIn About
 * section is worth considerably more than six paraphrases of the same facts.
 * Paraphrase fragments the signal; repetition concentrates it.
 *
 * This matters more here than it would on most sites. At least fifteen software
 * engineers share the name "Omkar Jadhav" — one at Google, one a Senior SWE at
 * LTIMindtree, two in Kolhapur, one in Pune. The name cannot identify him. The
 * combination of employer, institution and stack can, and only if every surface
 * states it the same way.
 */

/**
 * Full legal name. The single most valuable string on the site: the short form
 * is shared with fifteen engineers, this one with none. It appeared nowhere on
 * the site before the SEO work.
 */
export const LEGAL_NAME = "Omkar Jayvant Jadhav";

/** The form used in headings, navigation and everyday copy. */
export const COMMON_NAME = "Omkar Jadhav";

/**
 * Variants worth resolving to this entity, including one common misspelling.
 *
 * Deliberately NOT included: "Omkar Jadhav Pune", "Omkar Jadhav KIT Kolhapur"
 * and similar. Those are search queries, not names — putting them in
 * `alternateName` would be keyword stuffing inside structured data.
 */
export const NAME_VARIANTS = ["Omkar J. Jadhav", "Omkar Jaywant Jadhav"];

/**
 * THE canonical disambiguating statement. Reuse it verbatim; do not reword it
 * per surface.
 *
 * Written to survive extraction: an answer engine retrieves a passage, not a
 * page, so it opens with the full name rather than a pronoun, names the
 * employer and the institution rather than referring to "his company" or
 * "university", and gives a concrete year instead of "recently".
 */
export const CANONICAL_STATEMENT =
  "Omkar Jayvant Jadhav is a Software Development Engineer I at Nonstop IO " +
  "Technologies in Kharadi, Pune, India. He graduated from KIT's College of " +
  "Engineering (Autonomous), Kolhapur in 2026 with a B.Tech in Computer Science " +
  "& Engineering (Data Science), and works on backend development in C#, NestJS " +
  "and SQL.";

/** Employer, spelled one way everywhere. Three spellings were once live at once. */
export const EMPLOYER = "Nonstop IO Technologies";

/** Current job title. */
export const JOB_TITLE = "Software Development Engineer I";
