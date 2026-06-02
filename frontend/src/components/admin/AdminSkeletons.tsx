import {
  Skeleton,
  SkeletonCard,
  SkeletonForm,
  SkeletonTable,
} from "@/components/ui/Skeleton";

export function AdminDashboardSkeleton() {
  return (
    <div
      className="space-y-8 font-mono"
      role="status"
      aria-busy="true"
      aria-live="polite"
      data-testid="skeleton-admin-dashboard"
    >
      <span className="sr-only">Loading admin dashboard</span>
      <div>
        <Skeleton className="h-3 w-28 mb-2" />
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-3 w-72 mt-2" />
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <SkeletonCard key={i} className="p-4" label="Loading stat card">
            <Skeleton className="h-7 w-16" />
            <Skeleton className="h-3 w-20 mt-2" />
          </SkeletonCard>
        ))}
      </div>

      <div>
        <Skeleton className="h-3 w-36 mb-3" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <SkeletonCard key={i} className="p-4" label="Loading quick action">
              <div className="flex items-center gap-3">
                <Skeleton className="h-3 w-3 rounded-full" />
                <div className="space-y-2">
                  <Skeleton className="h-3 w-32" />
                  <Skeleton className="h-3 w-40" />
                </div>
                <Skeleton className="h-3 w-4 ml-auto" />
              </div>
            </SkeletonCard>
          ))}
        </div>
      </div>

      <SkeletonCard className="p-4" label="Loading profile status">
        <Skeleton className="h-3 w-24 mb-2" />
        <div className="flex items-center gap-2">
          <Skeleton className="h-2 w-2 rounded-full" />
          <Skeleton className="h-3 w-60" />
        </div>
        <div className="mt-2 flex items-center gap-2">
          <Skeleton className="h-2 w-2 rounded-full" />
          <Skeleton className="h-3 w-72" />
        </div>
      </SkeletonCard>
    </div>
  );
}

export function AdminProjectsSkeleton() {
  return (
    <div
      className="space-y-6 font-mono"
      role="status"
      aria-busy="true"
      aria-live="polite"
      data-testid="skeleton-admin-projects"
    >
      <span className="sr-only">Loading admin projects</span>
      <div className="flex items-center justify-between">
        <div>
          <Skeleton className="h-3 w-36 mb-2" />
          <Skeleton className="h-6 w-28" />
          <Skeleton className="h-3 w-28 mt-2" />
        </div>
        <Skeleton className="h-9 w-28 rounded-lg" />
      </div>

      <SkeletonTable
        rows={5}
        columns={5}
        columnWidths={["w-28", "w-16", "w-16", "w-12", "w-20"]}
        rowHeightClass="h-3"
        testId="skeleton-table-projects"
        label="Loading projects table"
      />
    </div>
  );
}

export function AdminExperienceSkeleton() {
  return (
    <div
      className="space-y-6 font-mono"
      role="status"
      aria-busy="true"
      aria-live="polite"
      data-testid="skeleton-admin-experience"
    >
      <span className="sr-only">Loading admin experience</span>
      <div className="flex items-center justify-between">
        <div>
          <Skeleton className="h-3 w-40 mb-2" />
          <Skeleton className="h-6 w-28" />
          <Skeleton className="h-3 w-32 mt-2" />
        </div>
        <Skeleton className="h-9 w-28 rounded-lg" />
      </div>

      <SkeletonTable
        rows={6}
        columns={6}
        columnWidths={["w-16", "w-16", "w-32", "w-24", "w-24", "w-20"]}
        rowHeightClass="h-3"
        testId="skeleton-table-experience"
        label="Loading experience table"
      />
    </div>
  );
}

export function AdminSkillsSkeleton() {
  return (
    <div
      className="space-y-8 font-mono"
      role="status"
      aria-busy="true"
      aria-live="polite"
      data-testid="skeleton-admin-skills"
    >
      <span className="sr-only">Loading admin skills</span>
      <div>
        <Skeleton className="h-3 w-40 mb-2" />
        <Skeleton className="h-6 w-24" />
      </div>

      <div className="space-y-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <SkeletonCard key={i} className="overflow-hidden" label="Loading skill branch">
            <div className="flex items-center justify-between px-4 py-3 bg-terminal-bg border-b border-terminal-border">
              <Skeleton className="h-4 w-28" />
              <Skeleton className="h-3 w-20" />
            </div>
            <div className="p-3 flex flex-wrap gap-2">
              {Array.from({ length: 6 }).map((__, j) => (
                <Skeleton key={j} className="h-6 w-24 rounded-full" />
              ))}
            </div>
          </SkeletonCard>
        ))}
      </div>

      <div>
        <div className="flex items-center justify-between mb-3">
          <div>
            <Skeleton className="h-3 w-44 mb-2" />
            <Skeleton className="h-5 w-32" />
          </div>
          <Skeleton className="h-8 w-24 rounded-lg" />
        </div>
        <SkeletonTable
          rows={4}
          columns={4}
          columnWidths={["w-20", "w-28", "w-32", "w-20"]}
          rowHeightClass="h-3"
          testId="skeleton-table-skills-diff"
          label="Loading skill diffs"
        />
      </div>
    </div>
  );
}

export function AdminProfileSkeleton() {
  return (
    <div
      className="space-y-6 font-mono"
      role="status"
      aria-busy="true"
      aria-live="polite"
      data-testid="skeleton-admin-profile"
    >
      <span className="sr-only">Loading admin profile</span>
      <div>
        <Skeleton className="h-3 w-44 mb-2" />
        <Skeleton className="h-6 w-24" />
      </div>

      <div className="space-y-6 max-w-2xl">
        <SkeletonForm
          rows={4}
          columns={2}
          includeTextarea
          includeActions={false}
          testId="skeleton-form-profile-basic"
          label="Loading profile basic info"
        />
        <SkeletonForm
          rows={2}
          columns={2}
          includeTextarea={false}
          includeActions={false}
          testId="skeleton-form-profile-status"
          label="Loading profile git status"
        />
        <SkeletonCard className="p-5 space-y-4" label="Loading socials">
          <div className="flex items-center justify-between border-b border-terminal-border pb-2">
            <Skeleton className="h-3 w-16" />
            <Skeleton className="h-3 w-10" />
          </div>
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="grid grid-cols-3 gap-2 items-end">
              <div className="space-y-2">
                <Skeleton className="h-3 w-12" />
                <Skeleton className="h-8 w-full rounded-lg" />
              </div>
              <div className="space-y-2">
                <Skeleton className="h-3 w-10" />
                <Skeleton className="h-8 w-full rounded-lg" />
              </div>
              <div className="flex gap-2 items-end">
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-3 w-10" />
                  <Skeleton className="h-8 w-full rounded-lg" />
                </div>
                <Skeleton className="h-8 w-8 rounded-lg" />
              </div>
            </div>
          ))}
        </SkeletonCard>
        <SkeletonForm
          rows={1}
          columns={1}
          includeTextarea
          includeActions={false}
          testId="skeleton-form-profile-funfacts"
          label="Loading profile fun facts"
        />
        <Skeleton className="h-9 w-full rounded-lg" />
      </div>
    </div>
  );
}

export function AdminLoginSkeleton() {
  return (
    <div
      className="min-h-screen flex items-center justify-center bg-terminal-bg px-4"
      role="status"
      aria-busy="true"
      aria-live="polite"
      data-testid="skeleton-admin-login"
    >
      <span className="sr-only">Loading admin login</span>
      <div className="w-full max-w-sm">
        <SkeletonCard className="overflow-hidden shadow-terminal" label="Loading login form">
          <div className="flex items-center gap-1.5 px-4 py-3 bg-terminal-bg border-b border-terminal-border">
            <Skeleton className="w-3 h-3 rounded-full" />
            <Skeleton className="w-3 h-3 rounded-full" />
            <Skeleton className="w-3 h-3 rounded-full" />
            <Skeleton className="h-3 w-36 ml-2" />
          </div>
          <div className="p-6 space-y-4">
            <div className="space-y-2">
              <Skeleton className="h-3 w-32" />
              <Skeleton className="h-3 w-36" />
            </div>
            <div className="space-y-3">
              <div className="space-y-2">
                <Skeleton className="h-3 w-16" />
                <Skeleton className="h-8 w-full rounded-lg" />
              </div>
              <div className="space-y-2">
                <Skeleton className="h-3 w-16" />
                <Skeleton className="h-8 w-full rounded-lg" />
              </div>
            </div>
            <Skeleton className="h-9 w-full rounded-lg" />
          </div>
        </SkeletonCard>
        <Skeleton className="h-3 w-40 mx-auto mt-4" />
      </div>
    </div>
  );
}
