import { useProfile } from "@/api/profile";
import { ProfileClient } from "@/components/admin/ProfileClient";

export default function ProfileAdmin() {
  const { data, isPending, isError } = useProfile();
  if (isPending) return <p className="font-mono text-sm text-text-muted">Loading profile…</p>;
  if (isError || !data)
    return <p className="font-mono text-sm text-git-red">Failed to load profile.</p>;
  return <ProfileClient initialProfile={data} />;
}
