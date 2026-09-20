import { describe, expect, it } from "vitest";
import { buildGraph, type JsonLdNode } from "@/lib/schema/graph";
import { profile } from "@/data/profile";
import { projects } from "@/data/projects";
import { timeline } from "@/data/experience";
import { skillBranches } from "@/data/skills";

/**
 * Structural validation of the JSON-LD entity graph.
 *
 * This runs against the static content files rather than the live API on
 * purpose: the backend is on a free tier that sleeps, and a validation suite
 * that only works when a third-party service is awake is not a validation
 * suite. The static files are the reference for what the database should hold.
 *
 * What this cannot do is call Google's Rich Results Test, which requires a live
 * public URL. Run that against the deployed site after Wave 3 ships.
 */

const SITE = "https://example.com";

function graphFor(route: string) {
  return buildGraph({
    siteUrl: SITE,
    assetOrigin: "https://api.example.com",
    route,
    profile,
    projects,
    experience: timeline,
    skillBranches,
  });
}

function nodes(graph: JsonLdNode): JsonLdNode[] {
  return graph["@graph"] as unknown as JsonLdNode[];
}

/** Every `{"@id": "..."}` reference anywhere in the tree, at any depth. */
function collectRefs(value: unknown, found: string[] = []): string[] {
  if (Array.isArray(value)) {
    value.forEach((v) => collectRefs(v, found));
  } else if (value && typeof value === "object") {
    const obj = value as Record<string, unknown>;
    const keys = Object.keys(obj);
    if (keys.length === 1 && keys[0] === "@id" && typeof obj["@id"] === "string") {
      found.push(obj["@id"]);
    } else {
      Object.values(obj).forEach((v) => collectRefs(v, found));
    }
  }
  return found;
}

function walk(value: unknown, visit: (node: Record<string, unknown>) => void): void {
  if (Array.isArray(value)) {
    value.forEach((v) => walk(v, visit));
  } else if (value && typeof value === "object") {
    visit(value as Record<string, unknown>);
    Object.values(value as Record<string, unknown>).forEach((v) => walk(v, visit));
  }
}

describe("JSON-LD entity graph", () => {
  it("is serialisable and correctly shaped", () => {
    const graph = graphFor("/");
    expect(graph["@context"]).toBe("https://schema.org");
    expect(Array.isArray(graph["@graph"])).toBe(true);
    expect(() => JSON.parse(JSON.stringify(graph))).not.toThrow();
  });

  it("defines exactly one Person, and every page resolves to that same @id", () => {
    const routes = ["/", "/recruiter", "/mcp", `/projects/${projects[0].slug}`];
    const personIds = new Set<string>();

    for (const route of routes) {
      const people = nodes(graphFor(route)).filter((n) => n["@type"] === "Person");
      expect(people, `${route} must define exactly one Person`).toHaveLength(1);
      personIds.add(people[0]["@id"] as string);
    }

    // The whole point of a stable @id: four pages, one entity — not four.
    expect(personIds.size).toBe(1);
    expect([...personIds][0]).toBe(`${SITE}/#person`);
  });

  it("gives every node a unique @id", () => {
    const ids = nodes(graphFor(`/projects/${projects[0].slug}`))
      .map((n) => n["@id"])
      .filter(Boolean) as string[];
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("resolves every internal @id reference to a node that exists", () => {
    for (const route of ["/", `/projects/${projects[0].slug}`]) {
      const graph = graphFor(route);
      const defined = new Set(nodes(graph).map((n) => n["@id"]).filter(Boolean) as string[]);
      const referenced = collectRefs(nodes(graph));
      const dangling = referenced.filter((r) => !defined.has(r));
      expect(dangling, `${route} has dangling @id references`).toEqual([]);
    }
  });

  it("populates the Person properties that carry the disambiguation", () => {
    const person = nodes(graphFor("/")).find((n) => n["@type"] === "Person")!;

    // The legal name is the single most valuable string here: "Omkar Jadhav" is
    // shared with at least fifteen software engineers, the full form with none.
    expect(person.name).toBe("Omkar Jayvant Jadhav");
    expect(person.alternateName).toContain("Omkar Jadhav");
    expect(person.alternateName).toContain("Omkar Jaywant Jadhav");

    for (const key of ["jobTitle", "description", "url", "worksFor", "address", "homeLocation"]) {
      expect(person[key], `Person.${key} must be present`).toBeTruthy();
    }

    expect(Array.isArray(person.knowsAbout)).toBe(true);
    expect((person.alumniOf as unknown[]).length).toBeGreaterThanOrEqual(3);
    expect((person.hasCredential as unknown[]).length).toBeGreaterThanOrEqual(6);
  });

  it("never claims a withdrawn technology in knowsAbout", () => {
    const person = nodes(graphFor("/")).find((n) => n["@type"] === "Person")!;
    const claimed = (person.knowsAbout as string[]).map((s) => s.toLowerCase());
    for (const withdrawn of ["next.js", "nextjs", "fastapi", "chromadb"]) {
      expect(claimed, `knowsAbout must not contain ${withdrawn}`).not.toContain(withdrawn);
    }
  });

  it("lists only confirmed, owned profiles in sameAs", () => {
    const person = nodes(graphFor("/")).find((n) => n["@type"] === "Person")!;
    const sameAs = (person.sameAs ?? []) as string[];

    expect(sameAs).toContain("https://github.com/omkarjadhav1011");
    expect(sameAs).toContain("https://leetcode.com/u/jadhav_omkar1013/");

    // linkedin.com/in/omkarjadhav belongs to a different Omkar Jadhav. A wrong
    // sameAs does not merely fail to help — it tells Google to merge this
    // entity with a stranger's, and reciprocity will never verify.
    expect(sameAs.some((u) => u.includes("linkedin.com/in/omkarjadhav"))).toBe(false);
    // He does not own an X/Twitter account.
    expect(sameAs.some((u) => /twitter\.com|x\.com/.test(u))).toBe(false);
  });

  it("links each school as an EducationalOrganization under its full name", () => {
    const schools = nodes(graphFor("/")).filter((n) => n["@type"] === "EducationalOrganization");
    const names = schools.map((s) => s.name);
    expect(names).toContain("KIT's College of Engineering (Autonomous), Kolhapur");
    expect(names).toContain("Institute of Civil and Rural Engineering, Gargoti");
    expect(names).toContain("Shankar Chakru Patil Madhyamik Vidhyalaya, Dindewadi");
  });

  it("describes a project as SoftwareSourceCode authored by the Person", () => {
    const project = projects[0];
    const graph = graphFor(`/projects/${project.slug}`);
    const code = nodes(graph).find((n) => n["@type"] === "SoftwareSourceCode")!;

    expect(code.name).toBe(project.repoName);
    expect(code.author).toEqual({ "@id": `${SITE}/#person` });
    expect(code.url).toBe(`${SITE}/projects/${project.slug}`);

    const breadcrumb = nodes(graph).find((n) => n["@type"] === "BreadcrumbList")!;
    expect((breadcrumb.itemListElement as unknown[]).length).toBe(3);
  });

  it("omits codeRepository rather than linking a repository that does not exist", () => {
    // Three repo links previously published here were 404s. A dead code link
    // costs more credibility than a missing one.
    const noRepo = projects.find((p) => !p.repoUrl);
    expect(noRepo, "expected at least one project with no repository").toBeTruthy();

    const code = nodes(graphFor(`/projects/${noRepo!.slug}`)).find(
      (n) => n["@type"] === "SoftwareSourceCode",
    )!;
    expect(code).not.toHaveProperty("codeRepository");
  });

  it("emits no empty values and no relative URLs", () => {
    const graph = graphFor(`/projects/${projects[0].slug}`);

    walk(nodes(graph), (node) => {
      for (const [key, value] of Object.entries(node)) {
        // schema.org reads an empty value as a claim of emptiness; omit instead.
        expect(value, `${key} is empty`).not.toBe("");
        if (Array.isArray(value)) expect(value.length, `${key} is an empty array`).toBeGreaterThan(0);

        if (
          typeof value === "string" &&
          ["url", "@id", "item", "contentUrl", "codeRepository"].includes(key)
        ) {
          expect(value, `${key} must be an absolute URL`).toMatch(/^(https?:|mailto:)/);
        }
      }
    });
  });
});
