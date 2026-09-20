import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { Link } from "react-router-dom";
import { useProfile } from "@/api/profile";
import { useDomainProjects } from "@/api/projects";
import { useExperience } from "@/api/experience";
import { useSkillBranches, useSkillDiff } from "@/api/skills";
import { useNewMessageCount } from "@/api/messages";
import { useTelemetry, type TelemetrySummary } from "@/api/telemetry";

/** Admin dashboard (replaces admin/page.tsx). Stats come from the live queries. */
export default function Dashboard() {
  useDocumentTitle("Admin");
  const projects = useDomainProjects().data ?? [];
  const experience = useExperience().data ?? [];
  const branches = useSkillBranches().data ?? [];
  const diffs = useSkillDiff().data ?? [];
  const profile = useProfile().data;
  const newMessages = useNewMessageCount().data ?? 0;
  const totalSkills = branches.reduce((acc, b) => acc + b.skills.length, 0);

  const cards = [
    { label: "projects", value: projects.length, href: "/admin/projects", color: "text-git-blue", border: "border-git-blue/20", bg: "bg-git-blue/5" },
    { label: "experience entries", value: experience.length, href: "/admin/experience", color: "text-git-green", border: "border-git-green/20", bg: "bg-git-green/5" },
    { label: "skills", value: totalSkills, href: "/admin/skills", color: "text-git-orange", border: "border-git-orange/20", bg: "bg-git-orange/5" },
    { label: "skill diffs", value: diffs.length, href: "/admin/skills", color: "text-git-purple", border: "border-git-purple/20", bg: "bg-git-purple/5" },
    { label: "📬 new messages", value: newMessages, href: "/admin/messages", color: "text-git-red", border: "border-git-red/20", bg: "bg-git-red/5" },
  ];

  const actions = [
    { href: "/admin/projects", label: "Manage Projects", cmd: "git checkout projects" },
    { href: "/admin/experience", label: "Manage Experience", cmd: "git checkout experience" },
    { href: "/admin/skills", label: "Manage Skills", cmd: "git checkout skills" },
    { href: "/admin/profile", label: "Edit Profile", cmd: "git checkout profile" },
  ];

  return (
    <div className="space-y-8 font-mono">
      <div>
        <div className="text-text-faint text-xs mb-1">$ git status</div>
        <h1 className="text-2xl font-bold text-text-primary">Admin Dashboard</h1>
        <p className="text-text-muted text-sm mt-1">
          # On branch admin/panel — manage your portfolio content
        </p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {cards.map((card) => (
          <Link
            key={card.label}
            to={card.href}
            className={`rounded-xl border ${card.border} ${card.bg} p-4 hover:shadow-card-hover hover:border-git-green/30 transition-all duration-200`}
          >
            <div className={`text-3xl font-bold ${card.color}`}>{card.value}</div>
            <div className="text-text-muted text-xs mt-1">{card.label}</div>
          </Link>
        ))}
      </div>

      <EngagementPanel />

      <div>
        <div className="text-text-faint text-xs mb-3">$ git log --quick-actions</div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {actions.map((item) => (
            <Link
              key={item.label}
              to={item.href}
              className="flex items-center gap-3 p-4 rounded-xl border border-terminal-border bg-terminal-surface hover:border-git-green/30 hover:bg-git-green/5 transition-all duration-150"
            >
              <span className="text-git-green text-xs">$</span>
              <div>
                <div className="text-text-secondary text-xs">{item.label}</div>
                <div className="text-text-faint text-[10px]">{item.cmd}</div>
              </div>
              <span className="ml-auto text-text-faint text-xs">→</span>
            </Link>
          ))}
        </div>
      </div>

      <StatusPanel
        profileLoaded={!!profile}
        projects={projects.length}
        experience={experience.length}
        totalSkills={totalSkills}
        branches={branches.length}
      />
    </div>
  );
}

/** Engagement telemetry panel (lead capture D3): the trailing week's passive signals. */
function EngagementPanel() {
  const { data, isPending, isError } = useTelemetry(7);

  const stats: Array<{ label: string; value: (s: TelemetrySummary) => string; color: string }> = [
    { label: "resume downloads", value: (s) => String(s.byType.RESUME_DOWNLOAD ?? 0), color: "text-git-green" },
    {
      label: "JD matches",
      value: (s) => {
        const n = s.byType.RECRUITER_MATCH ?? 0;
        return s.avgFitScore != null ? `${n} · avg fit ${Math.round(s.avgFitScore)}%` : String(n);
      },
      color: "text-git-blue",
    },
    { label: "MCP tool calls", value: (s) => String(s.byType.MCP_TOOL ?? 0), color: "text-git-purple" },
    { label: "chat sessions", value: (s) => String(s.byType.CHAT_SESSION ?? 0), color: "text-git-orange" },
  ];

  return (
    <div className="rounded-xl border border-terminal-border bg-terminal-surface p-4">
      <div className="text-text-faint text-xs mb-3">$ git shortlog --since=&quot;7 days ago&quot; — engagement</div>
      {isPending && <p className="text-text-muted text-xs">Loading engagement…</p>}
      {isError && <p className="text-git-red text-xs">Failed to load engagement summary.</p>}
      {data && (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {stats.map((stat) => (
              <div key={stat.label}>
                <div className={`text-xl font-bold ${stat.color}`}>{stat.value(data)}</div>
                <div className="text-text-muted text-xs mt-0.5">{stat.label}</div>
              </div>
            ))}
          </div>
          {data.topTools.length > 0 && (
            <div className="mt-3 pt-3 border-t border-terminal-border text-xs text-text-muted">
              # top MCP tools:{" "}
              {data.topTools.map((t) => `${t.tool} (${t.count})`).join(" · ")}
            </div>
          )}
          {data.total === 0 && (
            <div className="mt-3 text-xs text-text-faint"># no engagement recorded this week yet</div>
          )}
        </>
      )}
    </div>
  );
}

function StatusPanel({
  profileLoaded,
  projects,
  experience,
  totalSkills,
  branches,
}: {
  profileLoaded: boolean;
  projects: number;
  experience: number;
  totalSkills: number;
  branches: number;
}) {
  return (
    <div className="rounded-xl border border-terminal-border bg-terminal-surface p-4">
      <div className="text-text-faint text-xs mb-2">$ git remote -v</div>
      <div className="flex items-center gap-2">
        <span className={`w-2 h-2 rounded-full ${profileLoaded ? "bg-git-green" : "bg-git-orange"} animate-pulse`} />
        <span className="text-text-secondary text-xs">
          {profileLoaded ? "Profile data loaded from database" : "Profile not loaded"}
        </span>
      </div>
      <div className="mt-2 flex items-center gap-2">
        <span className="w-2 h-2 rounded-full bg-git-blue" />
        <span className="text-text-secondary text-xs">
          {projects} project(s) · {experience} experience entry/entries · {totalSkills} skill(s) in {branches} branch(es)
        </span>
      </div>
    </div>
  );
}
