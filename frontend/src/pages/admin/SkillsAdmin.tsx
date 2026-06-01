import { useSkillBranchesList, useSkillDiffList } from "@/api/skills";
import { SkillsClient } from "@/components/admin/SkillsClient";

export default function SkillsAdmin() {
  const branchesQ = useSkillBranchesList();
  const diffsQ = useSkillDiffList();

  if (branchesQ.isPending || diffsQ.isPending)
    return <p className="font-mono text-sm text-text-muted">Loading skills…</p>;
  if (branchesQ.isError || diffsQ.isError || !branchesQ.data || !diffsQ.data)
    return <p className="font-mono text-sm text-git-red">Failed to load skills.</p>;

  return <SkillsClient initialBranches={branchesQ.data} initialDiffs={diffsQ.data} />;
}
