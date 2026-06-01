import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface SkeletonProps {
  className?: string;
}

export function Skeleton({ className }: SkeletonProps) {
  return (
    <div
      className={cn(
        "animate-pulse rounded-md bg-terminal-surface/60",
        className
      )}
    />
  );
}

interface SkeletonContainerProps {
  className?: string;
  label?: string;
  testId?: string;
  children: ReactNode;
}

function SkeletonContainer({ className, label = "Loading content", testId, children }: SkeletonContainerProps) {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-live="polite"
      data-testid={testId}
      className={className}
    >
      <span className="sr-only">{label}</span>
      {children}
    </div>
  );
}

interface SkeletonCardProps {
  className?: string;
  label?: string;
  testId?: string;
  children?: ReactNode;
}

export function SkeletonCard({ className, label, testId, children }: SkeletonCardProps) {
  return (
    <SkeletonContainer
      label={label}
      testId={testId}
      className={cn("rounded-xl border border-terminal-border bg-terminal-surface", className)}
    >
      {children}
    </SkeletonContainer>
  );
}

interface SkeletonTableProps {
  rows?: number;
  columns?: number;
  columnWidths?: string[];
  rowHeightClass?: string;
  header?: boolean;
  className?: string;
  testId?: string;
  label?: string;
}

export function SkeletonTable({
  rows = 5,
  columns = 4,
  columnWidths,
  rowHeightClass = "h-4",
  header = true,
  className,
  testId,
  label = "Loading table",
}: SkeletonTableProps) {
  const widths = columnWidths && columnWidths.length === columns
    ? columnWidths
    : Array.from({ length: columns }, () => "w-24");

  return (
    <SkeletonCard className={className} testId={testId} label={label}>
      <div className="overflow-hidden">
        <div className="w-full text-xs">
          {header && (
            <div className="border-b border-terminal-border bg-terminal-bg">
              <div className="grid" style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
                {widths.map((w, i) => (
                  <div key={`h-${i}`} className="px-4 py-3">
                    <Skeleton className={cn("h-3", w)} />
                  </div>
                ))}
              </div>
            </div>
          )}
          <div>
            {Array.from({ length: rows }).map((_, r) => (
              <div
                key={`r-${r}`}
                className="border-b border-terminal-border/50"
              >
                <div className="grid" style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
                  {widths.map((w, c) => (
                    <div key={`c-${r}-${c}`} className="px-4 py-3">
                      <Skeleton className={cn(rowHeightClass, w)} />
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </SkeletonCard>
  );
}

interface SkeletonFormProps {
  rows?: number;
  columns?: 1 | 2 | 3;
  includeTextarea?: boolean;
  includeActions?: boolean;
  className?: string;
  testId?: string;
  label?: string;
}

export function SkeletonForm({
  rows = 4,
  columns = 2,
  includeTextarea = true,
  includeActions = true,
  className,
  testId,
  label = "Loading form",
}: SkeletonFormProps) {
  const columnClass = columns === 3 ? "grid-cols-3" : columns === 1 ? "grid-cols-1" : "grid-cols-2";

  return (
    <SkeletonCard className={cn("p-5 space-y-4", className)} testId={testId} label={label}>
      <div className={cn("grid gap-3", columnClass)}>
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="space-y-2">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-9 w-full rounded-lg" />
          </div>
        ))}
      </div>
      {includeTextarea && (
        <div className="space-y-2">
          <Skeleton className="h-3 w-28" />
          <Skeleton className="h-24 w-full rounded-lg" />
        </div>
      )}
      {includeActions && (
        <div className="flex gap-2 pt-2">
          <Skeleton className="h-9 w-full rounded-lg" />
          <Skeleton className="h-9 w-24 rounded-lg" />
        </div>
      )}
    </SkeletonCard>
  );
}

export function HeroSkeleton() {
  return (
    <SkeletonContainer
      testId="skeleton-hero"
      label="Loading hero section"
      className="relative min-h-screen flex flex-col items-center justify-center px-4 py-20"
    >
      <div className="w-full max-w-3xl mx-auto space-y-8">
        {/* Terminal window skeleton */}
        <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
          <div className="flex items-center gap-1.5 px-4 py-3 bg-terminal-bg border-b border-terminal-border">
            <span className="w-3 h-3 rounded-full bg-[#ff5f57]/40 animate-pulse" />
            <span className="w-3 h-3 rounded-full bg-[#febc2e]/40 animate-pulse" />
            <span className="w-3 h-3 rounded-full bg-[#28c840]/40 animate-pulse" />
          </div>
          <div className="p-6 space-y-3">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-4 w-4/5" />
            <Skeleton className="h-4 w-1/3" />
          </div>
        </div>
        {/* Name skeleton */}
        <div className="text-center space-y-4">
          <Skeleton className="h-12 w-64 mx-auto" />
          <Skeleton className="h-4 w-80 mx-auto" />
          <Skeleton className="h-8 w-48 mx-auto rounded-full" />
        </div>
      </div>
    </SkeletonContainer>
  );
}

export function ProjectCardSkeleton() {
  return (
    <SkeletonCard className="p-5 space-y-4" label="Loading project card">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2">
          <Skeleton className="w-4 h-4" />
          <Skeleton className="h-4 w-32" />
        </div>
        <div className="flex gap-2">
          <Skeleton className="w-4 h-4" />
          <Skeleton className="w-4 h-4" />
        </div>
      </div>
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-3/4" />
      <div className="flex gap-2">
        <Skeleton className="h-5 w-16 rounded-full" />
        <Skeleton className="h-5 w-20 rounded-full" />
        <Skeleton className="h-5 w-14 rounded-full" />
      </div>
      <div className="flex items-center justify-between pt-1">
        <div className="flex items-center gap-2">
          <Skeleton className="w-3 h-3 rounded-full" />
          <Skeleton className="h-3 w-16" />
        </div>
        <div className="flex gap-3">
          <Skeleton className="h-3 w-8" />
          <Skeleton className="h-3 w-8" />
        </div>
      </div>
    </SkeletonCard>
  );
}

export function ProjectsSkeleton() {
  return (
    <SkeletonContainer
      testId="skeleton-projects"
      label="Loading projects section"
      className="py-24 px-4"
    >
      <div className="max-w-6xl mx-auto">
        <div className="mb-10 space-y-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-8 w-56" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <ProjectCardSkeleton key={i} />
          ))}
        </div>
      </div>
    </SkeletonContainer>
  );
}

export function SkillsSkeleton() {
  return (
    <SkeletonContainer
      testId="skeleton-skills"
      label="Loading skills section"
      className="py-24 px-4"
    >
      <div className="max-w-6xl mx-auto">
        <div className="mb-10 space-y-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-8 w-48" />
        </div>
        <div className="space-y-8">
          {Array.from({ length: 4 }).map((_, branchIdx) => (
            <div key={branchIdx} className="space-y-3">
              <Skeleton className="h-5 w-40" />
              <div className="flex flex-wrap gap-3">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Skeleton key={i} className="h-10 w-28 rounded-lg" />
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </SkeletonContainer>
  );
}

export function ContactSkeleton() {
  return (
    <SkeletonContainer
      testId="skeleton-contact"
      label="Loading contact section"
      className="py-24 px-4"
    >
      <div className="max-w-2xl mx-auto space-y-6">
        <div className="space-y-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-64" />
        </div>
        <SkeletonCard className="overflow-hidden">
          <div className="flex items-center gap-1.5 px-4 py-3 bg-terminal-bg border-b border-terminal-border">
            <Skeleton className="w-3 h-3 rounded-full" />
            <Skeleton className="w-3 h-3 rounded-full" />
            <Skeleton className="w-3 h-3 rounded-full" />
          </div>
          <div className="p-6 space-y-5">
            <Skeleton className="h-9 w-full rounded-lg" />
            <Skeleton className="h-9 w-full rounded-lg" />
            <Skeleton className="h-24 w-full rounded-lg" />
            <Skeleton className="h-10 w-full rounded-lg" />
          </div>
        </SkeletonCard>
      </div>
    </SkeletonContainer>
  );
}

export function ExperienceSkeleton() {
  return (
    <SkeletonContainer
      testId="skeleton-experience"
      label="Loading experience section"
      className="py-24 px-4"
    >
      <div className="max-w-4xl mx-auto">
        <div className="mb-10 space-y-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-8 w-48" />
        </div>
        <div className="space-y-8">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex gap-6">
              <div className="flex flex-col items-center">
                <Skeleton className="w-8 h-8 rounded-full" />
                <Skeleton className="w-0.5 h-24 mt-2" />
              </div>
              <div className="flex-1 space-y-3 pb-8">
                <Skeleton className="h-5 w-48" />
                <Skeleton className="h-4 w-36" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-4/5" />
                <div className="flex gap-2">
                  <Skeleton className="h-5 w-16 rounded-full" />
                  <Skeleton className="h-5 w-20 rounded-full" />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </SkeletonContainer>
  );
}

export function HeatmapSkeleton() {
  return (
    <SkeletonContainer
      testId="skeleton-heatmap"
      label="Loading contribution heatmap"
      className="py-8 px-4"
    >
      <div className="max-w-5xl mx-auto">
        <SkeletonCard className="p-4">
          <div className="mb-3 grid grid-cols-12 gap-2">
            {Array.from({ length: 12 }).map((_, i) => (
              <Skeleton key={i} className="h-3 w-10" />
            ))}
          </div>
          <div className="grid grid-cols-12 gap-2">
            {Array.from({ length: 84 }).map((_, i) => (
              <Skeleton key={i} className="w-3 h-3 rounded-sm" />
            ))}
          </div>
          <div className="mt-3 flex items-center justify-end gap-1.5">
            <Skeleton className="h-3 w-8" />
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="w-3 h-3 rounded-sm" />
            ))}
            <Skeleton className="h-3 w-8" />
          </div>
        </SkeletonCard>
      </div>
    </SkeletonContainer>
  );
}

export function AboutSkeleton() {
  return (
    <SkeletonContainer
      testId="skeleton-about"
      label="Loading about section"
      className="py-24 px-4"
    >
      <div className="max-w-3xl mx-auto">
        <div className="flex items-center gap-3 mb-8">
          <Skeleton className="h-4 w-4 rounded-full" />
          <Skeleton className="h-4 w-40" />
        </div>
        <SkeletonCard className="overflow-hidden">
          <div className="flex items-center justify-between px-5 py-3 bg-terminal-bg border-b border-terminal-border">
            <div className="flex items-center gap-2">
              <Skeleton className="h-3 w-3 rounded" />
              <Skeleton className="h-3 w-20" />
            </div>
          </div>
          <div className="p-6 md:p-8 space-y-6">
            <div className="space-y-2">
              <Skeleton className="h-8 w-48" />
              <Skeleton className="h-4 w-64" />
            </div>
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-6 w-24 rounded-full" />
              ))}
            </div>
            <Skeleton className="h-px w-full rounded-none" />
            <div className="space-y-3">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-5/6" />
              <Skeleton className="h-4 w-2/3" />
            </div>
            <Skeleton className="h-px w-full rounded-none" />
            <div className="space-y-3">
              <Skeleton className="h-5 w-40" />
              <div className="flex flex-wrap gap-2">
                {Array.from({ length: 8 }).map((_, i) => (
                  <Skeleton key={i} className="h-6 w-24 rounded-full" />
                ))}
              </div>
            </div>
            <Skeleton className="h-px w-full rounded-none" />
            <div className="space-y-3">
              <Skeleton className="h-5 w-32" />
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-4 w-4/5" />
              ))}
            </div>
            <Skeleton className="h-px w-full rounded-none" />
            <div className="space-y-3">
              <Skeleton className="h-5 w-24" />
              <div className="flex flex-wrap gap-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-4 w-20" />
                ))}
              </div>
            </div>
          </div>
        </SkeletonCard>
      </div>
    </SkeletonContainer>
  );
}
