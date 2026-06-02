import { useExperienceList } from "@/api/experience";
import { ExperienceClient } from "@/components/admin/ExperienceClient";

export default function ExperienceAdmin() {
  const { data, isPending, isError } = useExperienceList();
  if (isPending) return <p className="font-mono text-sm text-text-muted">Loading experience…</p>;
  if (isError || !data)
    return <p className="font-mono text-sm text-git-red">Failed to load experience.</p>;
  return <ExperienceClient initialEntries={data} />;
}
