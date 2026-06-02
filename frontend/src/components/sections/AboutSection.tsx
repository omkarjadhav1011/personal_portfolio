
import { BookText, ExternalLink, Github, Linkedin, Mail, Twitter } from "lucide-react";
import { ScrollReveal } from "@/components/ui/ScrollReveal";
import type { Profile, Skill } from "@/types";

interface TechPick {
  name: string;
  glyph: string;
  tint: string;
}

const TECH_PICKS_FALLBACK: TechPick[] = [
  { name: "TypeScript", glyph: "TS", tint: "#3178c6" },
  { name: "React", glyph: "⚛", tint: "#61dafb" },
  { name: "Next.js", glyph: "▲", tint: "#ffffff" },
  { name: "Tailwind", glyph: "≈", tint: "#38bdf8" },
  { name: "Python", glyph: "🐍", tint: "#3776ab" },
  { name: "Node.js", glyph: "⬢", tint: "#3c873a" },
  { name: "PostgreSQL", glyph: "◆", tint: "#336791" },
  { name: "Git", glyph: "⎇", tint: "#f05033" },
];

const FUN_FACT_GLYPHS = ["✦", "↗", "⚡", "☕", "◆", "→"];

const SOCIAL_ICON: Record<string, { Icon: React.ElementType; tint: string }> = {
  github: { Icon: Github, tint: "var(--color-text-primary, 230 237 243)" },
  linkedin: { Icon: Linkedin, tint: "var(--color-git-blue, 88 166 255)" },
  twitter: { Icon: Twitter, tint: "var(--color-git-purple, 210 168 255)" },
};

function Avatar({ name, avatarUrl }: { name: string; avatarUrl?: string }) {
  const initials = name
    .split(" ")
    .map((s) => s[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
  return (
    <div className="relative shrink-0">
      <div
        className="w-20 h-20 sm:w-24 sm:h-24 rounded-2xl flex items-center justify-center font-mono font-bold text-2xl sm:text-3xl overflow-hidden"
        style={{
          background: avatarUrl
            ? undefined
            : "linear-gradient(135deg, rgb(var(--color-git-green) / 0.2), rgb(var(--color-git-blue) / 0.2))",
          border: "1.5px solid rgb(var(--color-git-green) / 0.5)",
          color: "rgb(var(--color-git-green))",
          boxShadow: "0 0 30px rgb(var(--color-git-green) / 0.15)",
        }}
      >
        {avatarUrl ? (
          <img
            src={avatarUrl}
            alt={name}
            className="w-full h-full object-cover"
            onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = "none"; }}
          />
        ) : (
          initials
        )}
      </div>
      <div
        className="absolute -bottom-1 -right-1 w-5 h-5 rounded-full flex items-center justify-center"
        style={{
          background: "rgb(var(--color-terminal-bg))",
          border: "2px solid rgb(var(--color-terminal-bg))",
        }}
      >
        <span
          className="w-3 h-3 rounded-full animate-pulse"
          style={{
            background: "rgb(var(--color-git-green))",
            boxShadow: "0 0 8px rgb(var(--color-git-green))",
          }}
        />
      </div>
    </div>
  );
}

function TechChip({ t }: { t: TechPick }) {
  const isUrl = t.glyph.startsWith("http") || t.glyph.startsWith("/");
  const cp = t.glyph.codePointAt(0) ?? 0;
  const isEmoji = !isUrl && cp > 0x7f;
  return (
    <div
      className="inline-flex items-center gap-1.5 pl-1 pr-2.5 py-1 rounded-full font-mono text-xs transition-transform hover:-translate-y-0.5"
      style={{
        background: "rgb(var(--color-terminal-bg) / 0.7)",
        border: "1px solid rgb(var(--color-terminal-border))",
        color: "rgb(var(--color-text-primary))",
      }}
    >
      <span
        className="flex items-center justify-center rounded-full font-bold overflow-hidden"
        style={{
          width: 18,
          height: 18,
          background: `${t.tint}22`,
          border: `1px solid ${t.tint}55`,
          color: t.tint,
          fontSize: isUrl ? undefined : (isEmoji ? 11 : 10),
          lineHeight: 1,
        }}
      >
        {isUrl ? (
          <img src={t.glyph} alt={t.name} className="w-full h-full object-contain p-0.5" />
        ) : (
          t.glyph
        )}
      </span>
      <span>{t.name}</span>
    </div>
  );
}

function FunCard({ glyph, text }: { glyph: string; text: string }) {
  return (
    <div
      className="flex items-start gap-3 p-3 rounded-lg"
      style={{
        background: "rgb(var(--color-terminal-bg) / 0.5)",
        border: "1px solid rgb(var(--color-terminal-border))",
      }}
    >
      <span
        className="flex items-center justify-center w-7 h-7 rounded-full shrink-0 font-mono text-sm"
        style={{
          background: "rgb(var(--color-git-green) / 0.12)",
          color: "rgb(var(--color-git-green))",
        }}
      >
        {glyph}
      </span>
      <span className="text-sm leading-snug pt-1 text-text-secondary">{text}</span>
    </div>
  );
}

function ConnectBtn({
  label,
  href,
  tint,
  Icon,
}: {
  label: string;
  href: string;
  tint: string;
  Icon: React.ElementType;
}) {
  return (
    <a
      href={href}
      target={href.startsWith("mailto:") ? undefined : "_blank"}
      rel="noopener noreferrer"
      className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg font-mono text-sm transition-transform hover:-translate-y-0.5"
      style={{
        background: `rgb(${tint} / 0.08)`,
        border: `1px solid rgb(${tint} / 0.35)`,
        color: `rgb(${tint})`,
      }}
    >
      <Icon size={14} />
      <span>{label}</span>
      <ExternalLink size={11} className="opacity-70" />
    </a>
  );
}

function Rule() {
  return (
    <div
      className="h-px my-6"
      style={{ background: "rgb(var(--color-terminal-border) / 0.7)" }}
    />
  );
}

interface AboutSectionProps {
  profile: Profile;
  topSkills: Skill[];
}

export function AboutSection({ profile, topSkills }: AboutSectionProps) {
  // Priority: admin-managed techPicks > DB-derived topSkills > hardcoded fallback
  const techPicks: TechPick[] = (profile.techPicks && profile.techPicks.length > 0)
    ? profile.techPicks
    : topSkills.length
    ? topSkills.slice(0, 8).map((s, i) => {
        const fallback = TECH_PICKS_FALLBACK[i] ?? TECH_PICKS_FALLBACK[0];
        return { name: s.name, glyph: s.icon || fallback.glyph, tint: fallback.tint };
      })
    : TECH_PICKS_FALLBACK;

  const role = profile.currentRole;

  // Resolve accent color: explicit hex from role, else fall back to git-green token
  const accent = role?.accent || "#00ff88";

  return (
    <section id="about" className="py-16 sm:py-24 px-4 scroll-mt-14">
      <div className="max-w-3xl mx-auto">
        <ScrollReveal>
          <div className="flex items-center gap-3 mb-8">
            <span className="text-git-green font-mono text-sm">$</span>
            <span className="font-mono text-text-muted text-sm">cat README.md</span>
          </div>
        </ScrollReveal>

        <ScrollReveal delay={0.05}>
          <div className="rounded-xl overflow-hidden bg-terminal-surface border border-terminal-border shadow-terminal">
            {/* File header */}
            <div className="flex items-center justify-between px-5 py-3 border-b border-terminal-border bg-terminal-bg/60 font-mono text-xs">
              <div className="flex items-center gap-2 text-text-muted">
                <BookText size={12} className="text-git-green" />
                <span>README.md</span>
                <span className="text-text-faint">· main</span>
              </div>
              <span className="text-text-faint">Preview · Raw</span>
            </div>

            <div className="p-6 sm:p-8">
              {/* Profile header */}
              <div className="flex flex-col sm:flex-row sm:items-center gap-5 mb-5">
                <Avatar name={profile.name} avatarUrl={profile.avatarUrl} />
                <div className="min-w-0 flex-1">
                  <h1 className="font-mono font-bold text-2xl sm:text-3xl tracking-tight leading-tight text-text-primary">
                    {profile.name}
                  </h1>
                  <div className="font-mono text-xs mt-1 text-text-muted">
                    @{profile.handle} · {profile.location}
                  </div>
                  <p className="text-sm mt-2 leading-relaxed text-git-green font-sans">
                    {profile.headline}
                  </p>
                </div>
              </div>

              {/* Status pill */}
              {profile.availableForWork && (
                <div
                  className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full font-mono text-xs mb-5"
                  style={{
                    background: "rgb(var(--color-git-green) / 0.1)",
                    border: "1px solid rgb(var(--color-git-green) / 0.4)",
                    color: "rgb(var(--color-git-green))",
                  }}
                >
                  <span
                    className="w-1.5 h-1.5 rounded-full animate-pulse"
                    style={{ background: "rgb(var(--color-git-green))" }}
                  />
                  {profile.currentStatus || "Available for work"}
                </div>
              )}

              {/* Currently working at — LinkedIn-style strip */}
              {role?.enabled && role.company && (
                <a
                  href={role.url || "#"}
                  target={role.url ? "_blank" : undefined}
                  rel="noopener noreferrer"
                  className="group flex items-center gap-3 p-3 rounded-lg mb-2 transition-all bg-terminal-bg/50 border border-terminal-border hover:bg-[var(--role-hover-bg)] hover:border-[var(--role-hover-border)]"
                  style={
                    {
                      "--role-hover-bg": `${accent}0d`,
                      "--role-hover-border": `${accent}80`,
                    } as React.CSSProperties
                  }
                >
                  <div
                    className="relative flex-shrink-0 w-12 h-12 rounded-lg overflow-hidden flex items-center justify-center font-mono font-bold text-lg"
                    style={{
                      background: `linear-gradient(135deg, ${accent}33, ${accent}0d)`,
                      border: `1px solid ${accent}66`,
                      color: accent,
                    }}
                  >
                    {role.logoUrl ? (
                      <img
                        src={role.logoUrl}
                        alt={role.company}
                        className="w-full h-full object-cover"
                        onError={(e) => {
                          (e.currentTarget as HTMLImageElement).style.display = "none";
                        }}
                      />
                    ) : (
                      role.monogram || role.company.charAt(0).toUpperCase()
                    )}
                    <span
                      className="absolute -top-1 -right-1 w-3 h-3 rounded-full animate-pulse"
                      style={{
                        background: accent,
                        boxShadow: `0 0 0 2px rgb(var(--color-terminal-surface)), 0 0 8px ${accent}99`,
                      }}
                    />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="font-mono text-[10px] uppercase tracking-widest mb-1 text-text-faint">
                      $ git remote · currently @
                    </div>
                    <div className="flex items-baseline gap-2 flex-wrap">
                      <span className="font-semibold text-sm text-text-primary">
                        {role.title}
                      </span>
                      <span className="font-mono text-xs text-text-faint">at</span>
                      <span
                        className="font-mono font-semibold text-sm group-hover:underline"
                        style={{ color: accent }}
                      >
                        {role.company}
                      </span>
                    </div>
                    <div className="font-mono text-[11px] mt-1 text-text-muted">
                      {role.startedAt} — Present
                      {role.tenure && (
                        <>
                          <span className="text-text-faint"> · </span>
                          {role.tenure}
                        </>
                      )}
                      {role.location && (
                        <>
                          <span className="text-text-faint"> · </span>
                          {role.location}
                        </>
                      )}
                    </div>
                  </div>

                  {role.url && (
                    <ExternalLink
                      size={14}
                      className="flex-shrink-0 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
                      style={{ color: accent }}
                    />
                  )}
                </a>
              )}

              <Rule />

              {/* About prose */}
              <h2 className="font-mono text-sm mb-3 text-git-blue">## About Me</h2>
              <div className="space-y-3 text-sm leading-relaxed text-text-secondary font-sans">
                {profile.bio.split(/\n\s*\n/).map((para, i) => (
                  <p key={i}>{para}</p>
                ))}
              </div>

              <Rule />

              {/* Tech chips */}
              <h2 className="font-mono text-sm mb-3 text-git-blue">## Tech I Reach For</h2>
              <div className="flex flex-wrap gap-1.5">
                {techPicks.map((t) => (
                  <TechChip key={t.name} t={t} />
                ))}
              </div>

              <Rule />

              {/* Fun facts */}
              <h2 className="font-mono text-sm mb-3 text-git-blue">## Fun Facts</h2>
              <div className="grid sm:grid-cols-2 gap-2">
                {profile.funFacts.map((text, i) => (
                  <FunCard
                    key={i}
                    glyph={FUN_FACT_GLYPHS[i % FUN_FACT_GLYPHS.length]}
                    text={text}
                  />
                ))}
              </div>

              <Rule />

              {/* Connect */}
              <h2 className="font-mono text-sm mb-3 text-git-blue">## Connect</h2>
              <div className="flex flex-wrap gap-2">
                {profile.socials.map((s) => {
                  const meta = SOCIAL_ICON[s.icon] || {
                    Icon: ExternalLink,
                    tint: "var(--color-text-primary)",
                  };
                  return (
                    <ConnectBtn
                      key={s.label}
                      label={s.label}
                      href={s.url}
                      tint={meta.tint}
                      Icon={meta.Icon}
                    />
                  );
                })}
                <ConnectBtn
                  label={profile.email}
                  href={`mailto:${profile.email}`}
                  tint="var(--color-git-green)"
                  Icon={Mail}
                />
              </div>
            </div>
          </div>
        </ScrollReveal>
      </div>
    </section>
  );
}
