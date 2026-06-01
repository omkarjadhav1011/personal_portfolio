
import { useState, useCallback } from "react";
import type { CommandResult } from "@/types";
import { profile } from "@/data/profile";
import { projects } from "@/data/projects";
import { skillBranches } from "@/data/skills";
import { timeline } from "@/data/experience";
import { fuzzyFilter } from "@/lib/fuzzy";

const KNOWN_COMMANDS = [
  "help", "clear", "whoami", "ls", "skills", "projects",
  "theme dark", "theme light", "cat readme.md",
  "git checkout", "git log", "git status", "git branch",
  "git --version", "git remote", "git stash pop", "git show",
];

const SECTION_IDS = ["hero", "about", "skills", "projects", "experience", "contact"];

function scrollToSection(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth" });
}

interface ParsedCommand {
  base: string;
  sub: string;
  args: string[];
  flags: string[];
  raw: string;
}

function parseCommand(raw: string): ParsedCommand {
  const tokens = raw.trim().split(/\s+/).filter(Boolean);
  const base = tokens[0] ?? "";
  const sub = tokens[1] ?? "";
  const rest = tokens.slice(2);
  const flags = rest.filter((t) => t.startsWith("-"));
  const args = rest.filter((t) => !t.startsWith("-"));
  return { base, sub, args, flags, raw };
}

export function useTerminal() {
  const [history, setHistory] = useState<Array<{ command: string; result: CommandResult }>>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  const executeCommand = useCallback((raw: string): CommandResult => {
    if (!raw.trim()) return { output: [], type: "success" };

    const cmd = parseCommand(raw.toLowerCase());

    // ── clear ──────────────────────────────────────────────────────────────
    if (cmd.base === "clear") {
      setHistory([]);
      return { output: [], type: "success" };
    }

    // ── help ───────────────────────────────────────────────────────────────
    if (cmd.base === "help" || (cmd.base === "git" && cmd.sub === "--help")) {
      return {
        output: [
          "Available commands:",
          "",
          "  Navigation:",
          "    git checkout <section>  — jump to a section",
          "    ls                      — list all sections",
          "",
          "  Info:",
          "    whoami                  — about me",
          "    git log                 — experience timeline",
          "    git status              — current status",
          "    git branch              — skill branches",
          "    skills                  — list all skills",
          "    projects                — list all projects",
          "",
          "  Other:",
          "    theme dark|light        — switch theme",
          "    git --version           — portfolio version",
          "    cat README.md           — about section",
          "    git stash pop           — unstash my interests",
          "    clear                   — clear terminal",
          "",
          "  Tip: use / to ask the AI assistant anything",
          "  Tip: use Arrow Up/Down to navigate history",
        ],
        type: "success",
      };
    }

    // ── whoami / git config user.name ──────────────────────────────────────
    if (
      cmd.base === "whoami" ||
      (cmd.base === "git" && cmd.sub === "config" && cmd.args[0] === "user.name")
    ) {
      setTimeout(() => scrollToSection("about"), 100);
      return {
        output: [
          profile.name,
          `handle: ${profile.handle}`,
          `location: ${profile.location}`,
          `email: ${profile.email}`,
          `status: ${profile.currentStatus}`,
        ],
        type: "success",
      };
    }

    // ── ls ─────────────────────────────────────────────────────────────────
    if (cmd.base === "ls") {
      return {
        output: [
          "drwxr-xr-x  hero/",
          "drwxr-xr-x  about/",
          "drwxr-xr-x  skills/",
          "drwxr-xr-x  projects/",
          "drwxr-xr-x  experience/",
          "drwxr-xr-x  contact/",
          "-rw-r--r--  README.md",
        ],
        type: "success",
      };
    }

    // ── skills ─────────────────────────────────────────────────────────────
    if (cmd.base === "skills") {
      setTimeout(() => scrollToSection("skills"), 100);
      const lines: string[] = ["Skills by branch:", ""];
      for (const branch of skillBranches) {
        lines.push(`  branch: ${branch.branchName}`);
        for (const skill of branch.skills) {
          const bar = "█".repeat(skill.level) + "░".repeat(5 - skill.level);
          lines.push(`    ${bar}  ${skill.name}${skill.tag ? ` (${skill.tag})` : ""}`);
        }
        lines.push("");
      }
      return { output: lines, type: "success" };
    }

    // ── projects ───────────────────────────────────────────────────────────
    if (cmd.base === "projects") {
      setTimeout(() => scrollToSection("projects"), 100);
      const lines: string[] = ["Pinned repositories:", ""];
      for (const p of projects) {
        lines.push(`  repo: ${p.repoName}  [${p.language}]`);
        lines.push(`     ${p.description}`);
        lines.push(`     * ${p.stars}  forks: ${p.forks}  status: ${p.status}`);
        lines.push("");
      }
      return { output: lines, type: "success" };
    }

    // ── theme dark|light ───────────────────────────────────────────────────
    if (cmd.base === "theme") {
      const mode = cmd.sub;
      if (mode === "dark") {
        document.documentElement.classList.add("dark");
        return { output: ["Switched to dark mode."], type: "success" };
      }
      if (mode === "light") {
        document.documentElement.classList.remove("dark");
        return { output: ["Switched to light mode."], type: "success" };
      }
      return {
        output: ["Usage: theme dark | theme light"],
        type: "error",
      };
    }

    // ── cat README.md ──────────────────────────────────────────────────────
    if (cmd.base === "cat" && cmd.sub === "readme.md") {
      setTimeout(() => scrollToSection("about"), 100);
      return { output: ["Opening README.md..."], type: "success" };
    }

    // ── git commands ───────────────────────────────────────────────────────
    if (cmd.base === "git") {
      // git checkout <section>
      if (cmd.sub === "checkout") {
        const target = cmd.args[0]?.replace(/^\//, "");
        if (!target) {
          return {
            output: ["Usage: git checkout <section>", "Sections: " + SECTION_IDS.join(", ")],
            type: "error",
          };
        }
        if (SECTION_IDS.includes(target)) {
          setTimeout(() => scrollToSection(target), 100);
          return {
            output: [`Switched to branch '${target}'`],
            type: "success",
          };
        }
        return {
          output: [
            `error: pathspec '${target}' did not match any known sections`,
            "",
            "Available sections: " + SECTION_IDS.join(", "),
          ],
          type: "error",
        };
      }

      // git log
      if (cmd.sub === "log") {
        setTimeout(() => scrollToSection("experience"), 100);
        const lines = timeline.map(
          (e) => `${e.hash} (${e.branch}) ${e.title} @ ${e.org}`
        );
        lines.push("", "Scrolling to experience...");
        return { output: lines, type: "success" };
      }

      // git status
      if (cmd.sub === "status") {
        return {
          output: [
            `On branch ${profile.currentBranch}`,
            "Your branch is up to date with 'origin/main'.",
            "",
            `nothing to commit, working tree clean`,
            `(${profile.currentStatus})`,
          ],
          type: "success",
        };
      }

      // git branch
      if (cmd.sub === "branch") {
        setTimeout(() => scrollToSection("skills"), 100);
        const lines = [`* ${profile.currentBranch}`, ...skillBranches.map((b) => `  ${b.branchName}`)];
        return { output: lines, type: "success" };
      }

      // git --version
      if (cmd.sub === "--version") {
        return {
          output: ["git version 2.45.0 (portfolio edition)"],
          type: "success",
        };
      }

      // git remote
      if (cmd.sub === "remote") {
        return {
          output: profile.socials.map((s) => `  ${s.label.toLowerCase()}\t${s.url}`),
          type: "success",
        };
      }

      // git stash pop — easter egg
      if (cmd.sub === "stash" && cmd.args[0] === "pop") {
        return {
          type: "success",
          output: [
            "Applying stash@{0}: WIP on main",
            "",
            "Unstashed interests:",
            ...(profile.stash ?? []).map((item) => `  ${item}`),
            "",
            "Dropped refs/stash@{0}",
          ],
        };
      }

      // git show --contact
      if (cmd.sub === "show") {
        setTimeout(() => scrollToSection("contact"), 100);
        return {
          output: [
            `commit ${Date.now().toString(16).slice(-7)} (HEAD -> contact)`,
            `Author: ${profile.name} <${profile.email}>`,
            "",
            "    Open to: " + profile.currentStatus,
          ],
          type: "success",
        };
      }

      return {
        output: [
          `git: '${cmd.sub}' is not a recognized subcommand. See 'help'.`,
        ],
        type: "error",
      };
    }

    // ── unknown — with fuzzy suggestions ─────────────────────────────────
    const suggestions = fuzzyFilter(raw.trim(), KNOWN_COMMANDS, (c) => c).slice(0, 3);
    if (suggestions.length > 0) {
      return {
        output: [
          `command not found: ${cmd.base}`,
          "",
          "Did you mean:",
          ...suggestions.map((s) => `  ${s}`),
          "",
          "Type 'help' for all commands.",
        ],
        type: "error",
      };
    }
    return {
      output: [
        `command not found: ${cmd.base}`,
        "",
        "Type 'help' to see available commands.",
        "Tip: Switch to Ask AI tab for natural language queries.",
      ],
      type: "error",
    };
  }, []);

  const submit = useCallback(
    (command: string) => {
      const result = executeCommand(command);
      if (command.trim().toLowerCase() !== "clear") {
        setHistory((prev) => [...prev, { command, result }]);
      }
      setHistoryIndex(-1);
    },
    [executeCommand]
  );

  // Returns the command string for the given direction — caller sets input
  const navigateHistory = useCallback(
    (direction: "up" | "down"): string => {
      const commands = history.map((h) => h.command).reverse();
      if (direction === "up") {
        const nextIndex = Math.min(historyIndex + 1, commands.length - 1);
        setHistoryIndex(nextIndex);
        return commands[nextIndex] ?? "";
      } else {
        const nextIndex = Math.max(historyIndex - 1, -1);
        setHistoryIndex(nextIndex);
        return nextIndex === -1 ? "" : (commands[nextIndex] ?? "");
      }
    },
    [history, historyIndex]
  );

  return { history, submit, navigateHistory };
}
