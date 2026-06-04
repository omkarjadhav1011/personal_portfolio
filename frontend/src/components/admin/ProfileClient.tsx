
import { useRef, useState, type ReactNode } from "react";
import {
  Image as ImageIcon,
  FileText,
  User,
  GitBranch,
  Briefcase,
  Link2,
  Boxes,
  Sparkles,
  Plus,
  Trash2,
  type LucideIcon,
} from "lucide-react";
import { FormInput, FormTextarea, FormCheckbox } from "@/components/admin/FormField";
import { TagInput } from "@/components/admin/TagInput";
import { TechPicksEditor } from "@/components/admin/TechPicksEditor";
import { LoadingButton } from "@/components/ui/LoadingButton";
import { useToast } from "@/components/admin/ToastProvider";
import { useFormValidation } from "@/hooks/useFormValidation";
import { profileSchema } from "@/lib/admin-validations";
import { DEFAULT_ROLE, DEFAULT_ACCENT } from "@/lib/defaults";
import type { CurrentRole, SocialLink, TechPick } from "@/types";
import { useQueryClient } from "@tanstack/react-query";
import { authFetch, assetUrl } from "@/lib/api";
import { profileKeys } from "@/api/profile";
import { motion } from "framer-motion";

interface Profile {
  name: string;
  handle: string;
  headline: string;
  bio: string;
  currentBranch: string;
  currentStatus: string;
  availableForWork: boolean;
  email: string;
  location: string;
  socials: SocialLink[];
  funFacts: string[];
  stash?: string[];
  currentRole?: CurrentRole;
  avatarUrl?: string;
  resumeUrl?: string;
  resumeFilename?: string;
  techPicks?: TechPick[];
}

/**
 * Card with a clear, color-accented header. `accent` is a git color token name
 * (e.g. "git-green") resolved against the CSS variables in globals.css.
 */
function Section({
  icon: Icon,
  title,
  hint,
  accent,
  action,
  children,
}: {
  icon: LucideIcon;
  title: string;
  hint?: ReactNode;
  accent: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-xl border border-terminal-border bg-terminal-surface">
      <header className="flex items-center gap-3 border-b border-terminal-border bg-terminal-bg/40 px-5 py-3">
        <div
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
          style={{
            color: `rgb(var(--color-${accent}))`,
            background: `rgb(var(--color-${accent}) / 0.12)`,
            border: `1px solid rgb(var(--color-${accent}) / 0.3)`,
          }}
        >
          <Icon size={16} />
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="font-sans text-sm font-semibold leading-tight text-text-primary">
            {title}
          </h2>
          {hint && (
            <p className="mt-0.5 truncate font-mono text-[11px] leading-tight text-text-muted">
              {hint}
            </p>
          )}
        </div>
        {action}
      </header>
      <div className="space-y-4 p-5">{children}</div>
    </section>
  );
}

export function ProfileClient({ initialProfile }: { initialProfile: Profile }) {
  const [form, setForm] = useState<Profile>({
    ...initialProfile,
    currentRole: initialProfile.currentRole ?? DEFAULT_ROLE,
  });
  const [loading, setLoading] = useState(false);
  const [avatarPreview, setAvatarPreview] = useState<string | undefined>(initialProfile.avatarUrl);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);
  const [resumeName, setResumeName] = useState<string | undefined>(
    initialProfile.resumeUrl ? initialProfile.resumeFilename ?? "resume.pdf" : undefined,
  );
  const [resumeUploading, setResumeUploading] = useState(false);
  const resumeInputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { errors, validate } = useFormValidation(profileSchema);

  function field(key: keyof Profile, value: unknown) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function roleField<K extends keyof CurrentRole>(key: K, value: CurrentRole[K]) {
    setForm((prev) => ({
      ...prev,
      currentRole: { ...(prev.currentRole ?? DEFAULT_ROLE), [key]: value },
    }));
  }

  function updateSocial(index: number, key: keyof SocialLink, value: string) {
    const updated = form.socials.map((s, i) => (i === index ? { ...s, [key]: value } : s));
    field("socials", updated);
  }

  function addSocial() {
    field("socials", [...form.socials, { label: "", url: "", icon: "" }]);
  }

  function removeSocial(index: number) {
    field("socials", form.socials.filter((_, i) => i !== index));
  }

  async function handleAvatarChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setAvatarPreview(URL.createObjectURL(file));
    setAvatarUploading(true);
    try {
      const body = new FormData();
      body.append("file", file);
      const res = await authFetch("/api/profile/avatar", { method: "POST", body });
      if (res.ok) {
        // Cache-bust so the browser re-fetches the new image from the server
        setAvatarPreview(`/api/profile/avatar?v=${Date.now()}`);
        toast("Avatar updated", "success");
        await queryClient.invalidateQueries({ queryKey: profileKeys.detail });
      } else {
        const err = await res.json();
        toast(err.error?.message ?? "Failed to upload avatar", "error");
        setAvatarPreview(initialProfile.avatarUrl);
      }
    } finally {
      setAvatarUploading(false);
      if (avatarInputRef.current) avatarInputRef.current.value = "";
    }
  }

  async function handleResumeChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setResumeUploading(true);
    try {
      const body = new FormData();
      body.append("file", file);
      const res = await authFetch("/api/profile/resume", { method: "POST", body });
      if (res.ok) {
        const data = await res.json();
        setResumeName(data.resumeFilename ?? file.name);
        toast("Resume updated", "success");
        await queryClient.invalidateQueries({ queryKey: profileKeys.detail });
      } else {
        const err = await res.json();
        toast(err.error?.message ?? "Failed to upload resume", "error");
      }
    } finally {
      setResumeUploading(false);
      if (resumeInputRef.current) resumeInputRef.current.value = "";
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate(form)) return;
    setLoading(true);
    try {
      const res = await authFetch("/api/profile", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      });
      if (res.ok) {
        const saved = await res.json();
        // Push the server response directly into the query cache so the public
        // page picks up the new techPicks immediately without a separate refetch.
        queryClient.setQueryData(profileKeys.detail, saved);
        toast("Profile updated", "success");
      } else {
        const err = await res.json();
        toast(err.error?.message ?? "Failed to update", "error");
      }
    } finally {
      setLoading(false);
    }
  }

  const inputClass =
    "w-full bg-terminal-bg border border-terminal-border rounded-lg px-3 py-2 font-mono text-xs text-text-primary placeholder-text-faint outline-none focus:border-git-blue/50 focus-visible:ring-1 focus-visible:ring-git-blue/20 transition-colors";

  const role = form.currentRole ?? DEFAULT_ROLE;

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="font-mono">
      {/* Page header */}
      <div className="mb-6">
        <div className="mb-1 font-mono text-xs text-text-faint">
          $ git config --global user.profile
        </div>
        <h1 className="font-sans text-2xl font-bold text-text-primary">Profile</h1>
        <p className="mt-1 font-sans text-sm text-text-muted">
          Edit how you appear across the public site. Changes go live on save.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="mx-auto max-w-3xl space-y-5 pb-4">
        {/* Media — avatar + resume side by side */}
        <div className="grid gap-5 md:grid-cols-2">
          <Section icon={ImageIcon} title="Avatar" accent="git-green">
            <div className="flex items-center gap-5">
              <div className="relative shrink-0">
                <div
                  className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-2xl font-mono text-2xl font-bold"
                  style={{
                    background: avatarPreview
                      ? undefined
                      : "linear-gradient(135deg, rgb(var(--color-git-green) / 0.2), rgb(var(--color-git-blue) / 0.2))",
                    border: "1.5px solid rgb(var(--color-git-green) / 0.5)",
                    color: "rgb(var(--color-git-green))",
                  }}
                >
                  {avatarPreview ? (
                    <img src={assetUrl(avatarPreview)} alt="avatar" className="h-full w-full object-cover" />
                  ) : (
                    form.name.split(" ").map((s) => s[0]).join("").slice(0, 2).toUpperCase() || "?"
                  )}
                </div>
                {avatarUploading && (
                  <div className="absolute inset-0 flex items-center justify-center rounded-2xl bg-terminal-bg/70">
                    <span className="animate-pulse font-mono text-[10px] text-git-green">saving…</span>
                  </div>
                )}
              </div>
              <div className="space-y-2">
                <input
                  ref={avatarInputRef}
                  type="file"
                  accept="image/jpeg,image/png,image/gif,image/webp"
                  className="hidden"
                  onChange={handleAvatarChange}
                />
                <button
                  type="button"
                  disabled={avatarUploading}
                  onClick={() => avatarInputRef.current?.click()}
                  className="rounded-lg border border-terminal-border px-3 py-1.5 font-mono text-xs text-text-muted transition-colors hover:border-git-blue/50 hover:text-text-primary disabled:opacity-50"
                >
                  {avatarUploading ? "uploading…" : "$ git add avatar.jpg"}
                </button>
                <div className="font-mono text-[10px] text-text-faint">
                  JPEG · PNG · GIF · WebP — max 5 MB
                </div>
              </div>
            </div>
          </Section>

          <Section
            icon={FileText}
            title="Resume"
            accent="git-purple"
            hint={'powers the "git export --resume" button'}
          >
            <div className="flex items-center gap-5">
              <div className="relative shrink-0">
                <div
                  className="flex h-20 w-20 items-center justify-center rounded-2xl font-mono text-3xl"
                  style={{
                    background:
                      "linear-gradient(135deg, rgb(var(--color-git-purple) / 0.2), rgb(var(--color-git-blue) / 0.15))",
                    border: "1.5px solid rgb(var(--color-git-purple) / 0.5)",
                    color: "rgb(var(--color-git-purple))",
                  }}
                >
                  {resumeName ? "📄" : "∅"}
                </div>
                {resumeUploading && (
                  <div className="absolute inset-0 flex items-center justify-center rounded-2xl bg-terminal-bg/70">
                    <span className="animate-pulse font-mono text-[10px] text-git-purple">saving…</span>
                  </div>
                )}
              </div>
              <div className="space-y-2">
                <div className="font-mono text-xs text-text-muted">
                  {resumeName ? (
                    <span className="text-text-primary">{resumeName}</span>
                  ) : (
                    <span className="text-text-faint">no resume uploaded yet</span>
                  )}
                </div>
                <input
                  ref={resumeInputRef}
                  type="file"
                  accept="application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.pdf,.doc,.docx"
                  className="hidden"
                  onChange={handleResumeChange}
                />
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={resumeUploading}
                    onClick={() => resumeInputRef.current?.click()}
                    className="rounded-lg border border-terminal-border px-3 py-1.5 font-mono text-xs text-text-muted transition-colors hover:border-git-purple/50 hover:text-text-primary disabled:opacity-50"
                  >
                    {resumeUploading ? "uploading…" : resumeName ? "$ git add resume --force" : "$ git add resume"}
                  </button>
                  {resumeName && (
                    <a
                      href={assetUrl(`/api/profile/resume?v=${Date.now()}`)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="rounded-lg border border-terminal-border px-3 py-1.5 font-mono text-xs text-text-muted transition-colors hover:border-git-blue/50 hover:text-text-primary"
                    >
                      preview
                    </a>
                  )}
                </div>
                <div className="font-mono text-[10px] text-text-faint">PDF · DOC · DOCX — max 5 MB</div>
              </div>
            </div>
          </Section>
        </div>

        {/* Basic info */}
        <Section icon={User} title="Basic Info" accent="git-blue">
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="name" value={form.name} onChange={(e) => field("name", e.target.value)} error={errors.name} required />
            <FormInput label="handle" value={form.handle} onChange={(e) => field("handle", e.target.value)} error={errors.handle} required placeholder="omkarjadhav" />
          </div>
          <FormInput label="headline" value={form.headline} onChange={(e) => field("headline", e.target.value)} error={errors.headline} required />
          <FormTextarea label="bio" value={form.bio} onChange={(e) => field("bio", e.target.value)} error={errors.bio} rows={5} required />
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="email" type="email" value={form.email} onChange={(e) => field("email", e.target.value)} error={errors.email} required />
            <FormInput label="location" value={form.location} onChange={(e) => field("location", e.target.value)} error={errors.location} required />
          </div>
        </Section>

        {/* Git status */}
        <Section icon={GitBranch} title="Git Status" accent="git-green">
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="current branch" value={form.currentBranch} onChange={(e) => field("currentBranch", e.target.value)} error={errors.currentBranch} required placeholder="main" />
            <FormInput label="current status" value={form.currentStatus} onChange={(e) => field("currentStatus", e.target.value)} error={errors.currentStatus} required placeholder="Open to internships" />
          </div>
          <FormCheckbox label="available for work" checked={form.availableForWork} onChange={(v) => field("availableForWork", v)} />
        </Section>

        {/* Currently working at */}
        <Section
          icon={Briefcase}
          title="Currently Working At"
          accent="git-orange"
          hint="LinkedIn-style strip on About"
        >
          <FormCheckbox
            label="show on profile"
            checked={role.enabled}
            onChange={(v) => roleField("enabled", v)}
          />
          {role.enabled && (
            <>
              <div className="grid grid-cols-2 gap-3">
                <FormInput
                  label="role title"
                  value={role.title}
                  onChange={(e) => roleField("title", e.target.value)}
                  placeholder="Full-Stack Developer Intern"
                />
                <FormInput
                  label="company"
                  value={role.company}
                  onChange={(e) => roleField("company", e.target.value)}
                  placeholder="NonStop io Technologies"
                />
              </div>
              <div className="grid grid-cols-3 gap-3">
                <FormInput
                  label="monogram (1-2 chars)"
                  value={role.monogram ?? ""}
                  onChange={(e) => roleField("monogram", e.target.value)}
                  placeholder="N"
                  maxLength={3}
                />
                <FormInput
                  label="logo url (optional)"
                  value={role.logoUrl ?? ""}
                  onChange={(e) => roleField("logoUrl", e.target.value)}
                  placeholder="https://..."
                />
                <FormInput
                  label="company url"
                  value={role.url ?? ""}
                  onChange={(e) => roleField("url", e.target.value)}
                  placeholder="https://nonstopio.com"
                />
              </div>
              <div className="grid grid-cols-3 gap-3">
                <FormInput
                  label="started at"
                  value={role.startedAt}
                  onChange={(e) => roleField("startedAt", e.target.value)}
                  placeholder="Mar 2024"
                />
                <FormInput
                  label="tenure"
                  value={role.tenure ?? ""}
                  onChange={(e) => roleField("tenure", e.target.value)}
                  placeholder="8 mos"
                />
                <FormInput
                  label="location"
                  value={role.location ?? ""}
                  onChange={(e) => roleField("location", e.target.value)}
                  placeholder="Pune, India · Hybrid"
                />
              </div>
              <FormInput
                label="accent (hex)"
                value={role.accent ?? ""}
                onChange={(e) => roleField("accent", e.target.value)}
                placeholder={DEFAULT_ACCENT}
              />
              {/* Live preview */}
              <div className="pt-2">
                <div className="mb-2 font-mono text-[10px] text-text-faint">preview:</div>
                <div
                  className="flex items-center gap-3 rounded-lg bg-terminal-bg/60 p-3"
                  style={{ border: `1px solid ${role.accent || "rgb(var(--color-terminal-border))"}` }}
                >
                  <div
                    className="flex h-12 w-12 items-center justify-center overflow-hidden rounded-lg font-mono text-lg font-bold"
                    style={{
                      background: `linear-gradient(135deg, ${role.accent ?? DEFAULT_ACCENT}33, ${role.accent ?? DEFAULT_ACCENT}0d)`,
                      border: `1px solid ${role.accent ?? DEFAULT_ACCENT}66`,
                      color: role.accent ?? DEFAULT_ACCENT,
                    }}
                  >
                    {role.logoUrl ? (
                      <img src={role.logoUrl} alt={role.company} className="h-full w-full object-cover" />
                    ) : (
                      role.monogram || role.company.charAt(0).toUpperCase() || "?"
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-xs font-semibold text-text-primary">{role.title || "(role)"}</div>
                    <div className="text-[11px]" style={{ color: role.accent ?? DEFAULT_ACCENT }}>
                      {role.company || "(company)"}
                    </div>
                    <div className="text-[10px] text-text-muted">
                      {role.startedAt || "(started)"} {role.tenure ? `· ${role.tenure}` : ""}
                    </div>
                  </div>
                </div>
              </div>
            </>
          )}
        </Section>

        {/* Socials */}
        <Section
          icon={Link2}
          title="Socials"
          accent="git-blue"
          action={
            <button
              type="button"
              onClick={addSocial}
              className="inline-flex shrink-0 items-center gap-1 font-mono text-xs text-git-green transition-colors hover:text-git-green/80"
            >
              <Plus size={12} /> add
            </button>
          }
        >
          {form.socials.length === 0 && (
            <p className="font-mono text-xs text-text-faint">
              no socials yet — click “add” to create one.
            </p>
          )}
          {form.socials.map((social, i) => (
            <div key={i} className="grid grid-cols-3 items-end gap-2">
              <div>
                <label className="mb-1 block font-mono text-[10px] text-text-muted">label</label>
                <input className={inputClass} value={social.label} onChange={(e) => updateSocial(i, "label", e.target.value)} placeholder="GitHub" />
              </div>
              <div>
                <label className="mb-1 block font-mono text-[10px] text-text-muted">url</label>
                <input className={inputClass} value={social.url} onChange={(e) => updateSocial(i, "url", e.target.value)} placeholder="https://github.com/..." />
              </div>
              <div className="flex items-end gap-2">
                <div className="flex-1">
                  <label className="mb-1 block font-mono text-[10px] text-text-muted">icon key</label>
                  <input className={inputClass} value={social.icon} onChange={(e) => updateSocial(i, "icon", e.target.value)} placeholder="github" />
                </div>
                <button
                  type="button"
                  onClick={() => removeSocial(i)}
                  aria-label="Remove social link"
                  className="flex h-[34px] w-9 shrink-0 items-center justify-center rounded-lg border border-terminal-border text-text-muted transition-colors hover:border-git-red/50 hover:text-git-red"
                >
                  <Trash2 size={13} />
                </button>
              </div>
            </div>
          ))}
        </Section>

        {/* Tech I Reach For */}
        <Section icon={Boxes} title="Tech I Reach For" accent="git-purple">
          <TechPicksEditor
            values={form.techPicks ?? []}
            onChange={(v) => field("techPicks", v)}
          />
        </Section>

        {/* Fun facts & stash */}
        <Section icon={Sparkles} title="Fun Facts & Stash" accent="git-yellow">
          <TagInput label="fun facts (one per tag)" values={form.funFacts} onChange={(v) => field("funFacts", v)} placeholder="I debug at 2am..." />
          <TagInput label="stash (hobbies/interests)" values={form.stash ?? []} onChange={(v) => field("stash", v)} placeholder="☕ Coffee-driven development" />
        </Section>

        {/* Sticky save dock — Save is always reachable */}
        <div className="sticky bottom-4 z-10 pt-1">
          <div className="flex items-center justify-between gap-3 rounded-xl border border-terminal-border bg-terminal-surface/95 px-4 py-3 shadow-terminal backdrop-blur">
            <span className="hidden min-w-0 items-center gap-2 font-mono text-[11px] text-text-faint sm:flex">
              <span className="text-git-green">$</span>
              <span className="truncate">git commit -m &apos;update: profile&apos;</span>
            </span>
            <LoadingButton
              type="submit"
              loading={loading}
              loadingText="Saving..."
              className="shrink-0 px-6 py-2.5"
            >
              Save changes
            </LoadingButton>
          </div>
        </div>
      </form>
    </motion.div>
  );
}
