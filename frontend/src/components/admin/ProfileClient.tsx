
import { useState } from "react";
import { FormInput, FormTextarea, FormCheckbox } from "@/components/admin/FormField";
import { TagInput } from "@/components/admin/TagInput";
import { LoadingButton } from "@/components/ui/LoadingButton";
import { useToast } from "@/components/admin/ToastProvider";
import { useFormValidation } from "@/hooks/useFormValidation";
import { profileSchema } from "@/lib/admin-validations";
import { DEFAULT_ROLE, DEFAULT_ACCENT } from "@/lib/defaults";
import type { CurrentRole, SocialLink } from "@/types";
import { useQueryClient } from "@tanstack/react-query";
import { authFetch } from "@/lib/api";
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
}

export function ProfileClient({ initialProfile }: { initialProfile: Profile }) {
  const [form, setForm] = useState<Profile>({
    ...initialProfile,
    currentRole: initialProfile.currentRole ?? DEFAULT_ROLE,
  });
  const [loading, setLoading] = useState(false);
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
        toast("Profile updated", "success");
        await queryClient.invalidateQueries({ queryKey: profileKeys.detail });
      } else {
        const err = await res.json();
        toast(err.error?.message ?? "Failed to update", "error");
      }
    } finally {
      setLoading(false);
    }
  }

  const inputClass =
    "w-full bg-terminal-bg border border-terminal-border rounded-lg px-3 py-2 font-mono text-xs text-text-primary placeholder-text-faint focus:outline-none focus:border-git-blue/50 focus:ring-1 focus:ring-git-blue/20 transition-colors";

  const role = form.currentRole ?? DEFAULT_ROLE;

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-6 font-mono"
    >
      <div>
        <div className="text-text-faint text-xs mb-1">$ git config --global user.profile</div>
        <h1 className="text-xl font-bold text-text-primary">Profile</h1>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 max-w-2xl">
        {/* Basic info */}
        <div className="rounded-xl border border-terminal-border bg-terminal-surface p-5 space-y-4">
          <div className="text-text-faint text-xs border-b border-terminal-border pb-2">## Basic Info</div>
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
        </div>

        {/* Git status */}
        <div className="rounded-xl border border-terminal-border bg-terminal-surface p-5 space-y-4">
          <div className="text-text-faint text-xs border-b border-terminal-border pb-2">## Git Status</div>
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="current branch" value={form.currentBranch} onChange={(e) => field("currentBranch", e.target.value)} error={errors.currentBranch} required placeholder="main" />
            <FormInput label="current status" value={form.currentStatus} onChange={(e) => field("currentStatus", e.target.value)} error={errors.currentStatus} required placeholder="Open to internships" />
          </div>
          <FormCheckbox label="available for work" checked={form.availableForWork} onChange={(v) => field("availableForWork", v)} />
        </div>

        {/* Currently working at */}
        <div className="rounded-xl border border-terminal-border bg-terminal-surface p-5 space-y-4">
          <div className="text-text-faint text-xs border-b border-terminal-border pb-2">
            ## Currently Working At <span className="text-text-faint">— LinkedIn-style strip on About</span>
          </div>
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
                <div className="text-[10px] text-text-faint mb-2">preview:</div>
                <div
                  className="flex items-center gap-3 p-3 rounded-lg bg-terminal-bg/60"
                  style={{ border: `1px solid ${role.accent || "rgb(var(--color-terminal-border))"}` }}
                >
                  <div
                    className="w-12 h-12 rounded-lg flex items-center justify-center font-mono font-bold text-lg overflow-hidden"
                    style={{
                      background: `linear-gradient(135deg, ${role.accent ?? DEFAULT_ACCENT}33, ${role.accent ?? DEFAULT_ACCENT}0d)`,
                      border: `1px solid ${role.accent ?? DEFAULT_ACCENT}66`,
                      color: role.accent ?? DEFAULT_ACCENT,
                    }}
                  >
                    {role.logoUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={role.logoUrl} alt={role.company} className="w-full h-full object-cover" />
                    ) : (
                      role.monogram || role.company.charAt(0).toUpperCase() || "?"
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-xs text-text-primary font-semibold truncate">{role.title || "(role)"}</div>
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
        </div>

        {/* Socials */}
        <div className="rounded-xl border border-terminal-border bg-terminal-surface p-5 space-y-4">
          <div className="flex items-center justify-between border-b border-terminal-border pb-2">
            <div className="text-text-faint text-xs">## Socials</div>
            <button type="button" onClick={addSocial} className="text-xs text-git-green hover:underline">+ add</button>
          </div>
          {form.socials.map((social, i) => (
            <div key={i} className="grid grid-cols-3 gap-2 items-end">
              <div>
                <label className="block text-[10px] text-text-muted mb-1">label</label>
                <input className={inputClass} value={social.label} onChange={(e) => updateSocial(i, "label", e.target.value)} placeholder="GitHub" />
              </div>
              <div>
                <label className="block text-[10px] text-text-muted mb-1">url</label>
                <input className={inputClass} value={social.url} onChange={(e) => updateSocial(i, "url", e.target.value)} placeholder="https://github.com/..." />
              </div>
              <div className="flex gap-2 items-end">
                <div className="flex-1">
                  <label className="block text-[10px] text-text-muted mb-1">icon key</label>
                  <input className={inputClass} value={social.icon} onChange={(e) => updateSocial(i, "icon", e.target.value)} placeholder="github" />
                </div>
                <button type="button" onClick={() => removeSocial(i)} className="py-2 px-2 text-git-red text-xs hover:underline">×</button>
              </div>
            </div>
          ))}
        </div>

        {/* Fun facts & stash */}
        <div className="rounded-xl border border-terminal-border bg-terminal-surface p-5 space-y-4">
          <div className="text-text-faint text-xs border-b border-terminal-border pb-2">## Fun Facts & Stash</div>
          <TagInput label="fun facts (one per tag)" values={form.funFacts} onChange={(v) => field("funFacts", v)} placeholder="I debug at 2am..." />
          <TagInput label="stash (hobbies/interests)" values={form.stash ?? []} onChange={(v) => field("stash", v)} placeholder="☕ Coffee-driven development" />
        </div>

        <LoadingButton
          type="submit"
          loading={loading}
          loadingText="Saving..."
          className="w-full py-2.5"
        >
          $ git commit -m &apos;update: profile&apos;
        </LoadingButton>
      </form>
    </motion.div>
  );
}
