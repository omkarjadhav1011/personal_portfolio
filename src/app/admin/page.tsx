import { prisma } from "@/lib/prisma";
import Link from "next/link";

async function getStats() {
  try {
    const [projects, experience, branches, diffs, profileExists] = await Promise.all([
      prisma.project.count(),
      prisma.commitEntry.count(),
      prisma.skillBranch.findMany({ include: { _count: { select: { skills: true } } } }),
      prisma.skillDiff.count(),
      prisma.profile.findUnique({ where: { id: "main" }, select: { id: true } }),
    ]);

    const totalSkills = branches.reduce((acc, b) => acc + b._count.skills, 0);
    return {
      projects,
      experience,
      skills: totalSkills,
      branches: branches.length,
      diffs,
      profileExists: !!profileExists,
      dbAvailable: true,
    };
  } catch {
    return {
      projects: 0,
      experience: 0,
      skills: 0,
      branches: 0,
      diffs: 0,
      profileExists: false,
      dbAvailable: false,
    };
  }
}

export default async function AdminDashboard() {
  const stats = await getStats();

  const cards = [
    { label: "projects", value: stats.projects, href: "/admin/projects", color: "text-git-blue", border: "border-git-blue/20", bg: "bg-git-blue/5" },
    { label: "experience entries", value: stats.experience, href: "/admin/experience", color: "text-git-green", border: "border-git-green/20", bg: "bg-git-green/5" },
    { label: "skills", value: stats.skills, href: "/admin/skills", color: "text-git-orange", border: "border-git-orange/20", bg: "bg-git-orange/5" },
    { label: "skill diffs", value: stats.diffs, href: "/admin/skills", color: "text-git-purple", border: "border-git-purple/20", bg: "bg-git-purple/5" },
  ];

  return (
    <div className="space-y-8 font-mono">
      {/* Header */}
      <div>
        <div className="text-text-faint text-xs mb-1">$ git status</div>
        <h1 className="text-2xl font-bold text-text-primary">Admin Dashboard</h1>
        <p className="text-text-muted text-sm mt-1">
          # On branch admin/panel — manage your portfolio content
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {cards.map((card) => (
          <Link
            key={card.label}
            href={card.href}
            className={`rounded-xl border ${card.border} ${card.bg} p-4 hover:shadow-card-hover hover:border-git-green/30 transition-all duration-200`}
          >
            <div className={`text-3xl font-bold ${card.color}`}>{card.value}</div>
            <div className="text-text-muted text-xs mt-1">{card.label}</div>
          </Link>
        ))}
      </div>

      {/* Quick actions */}
      <div>
        <div className="text-text-faint text-xs mb-3">$ git log --quick-actions</div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {[
            { href: "/admin/projects", label: "Manage Projects", cmd: "git checkout projects" },
            { href: "/admin/experience", label: "Manage Experience", cmd: "git checkout experience" },
            { href: "/admin/skills", label: "Manage Skills", cmd: "git checkout skills" },
            { href: "/admin/profile", label: "Edit Profile", cmd: "git checkout profile" },
          ].map((item) => (
            <Link
              key={item.href}
              href={item.href}
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

      {/* Profile status */}
      <div className="rounded-xl border border-terminal-border bg-terminal-surface p-4">
        <div className="text-text-faint text-xs mb-2">$ git remote -v</div>
        <div className="flex items-center gap-2">
          <span className={`w-2 h-2 rounded-full ${stats.profileExists ? "bg-git-green" : "bg-git-orange"} animate-pulse`} />
          <span className="text-text-secondary text-xs">
            {stats.dbAvailable
              ? stats.profileExists
                ? "Profile data loaded from database"
                : "Profile not seeded — run prisma db seed"
              : "Database unavailable — using safe empty admin state"}
          </span>
        </div>
        <div className="mt-2 flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-git-blue" />
          <span className="text-text-secondary text-xs">
            {stats.projects} project(s) · {stats.experience} experience entry/entries · {stats.skills} skill(s) in {stats.branches} branch(es)
          </span>
        </div>
      </div>
    </div>
  );
}
