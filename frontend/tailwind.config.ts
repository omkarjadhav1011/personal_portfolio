import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Terminal color system — values come from CSS variables (globals.css)
        terminal: {
          bg: "rgb(var(--color-terminal-bg) / <alpha-value>)",
          surface: "rgb(var(--color-terminal-surface) / <alpha-value>)",
          window: "rgb(var(--color-terminal-window) / <alpha-value>)",
          border: "rgb(var(--color-terminal-border) / <alpha-value>)",
        },
        git: {
          green: "rgb(var(--color-git-green) / <alpha-value>)",
          "green-dim": "rgb(var(--color-git-green-dim) / <alpha-value>)",
          "green-muted": "rgb(var(--color-git-green-muted) / <alpha-value>)",
          blue: "rgb(var(--color-git-blue) / <alpha-value>)",
          "blue-dim": "rgb(var(--color-git-blue-dim) / <alpha-value>)",
          orange: "rgb(var(--color-git-orange) / <alpha-value>)",
          red: "rgb(var(--color-git-red) / <alpha-value>)",
          purple: "rgb(var(--color-git-purple) / <alpha-value>)",
          yellow: "rgb(var(--color-git-yellow) / <alpha-value>)",
        },
        text: {
          primary: "rgb(var(--color-text-primary) / <alpha-value>)",
          secondary: "rgb(var(--color-text-secondary) / <alpha-value>)",
          muted: "rgb(var(--color-text-muted) / <alpha-value>)",
          faint: "rgb(var(--color-text-faint) / <alpha-value>)",
        },
      },
      fontFamily: {
        mono: ["var(--font-mono)", "JetBrains Mono", "Fira Code", "monospace"],
        sans: ["var(--font-sans)", "Inter", "system-ui", "sans-serif"],
      },
      fontSize: {
        "2xs": ["0.625rem", { lineHeight: "1rem" }],
      },
      animation: {
        "cursor-blink": "blink 1s step-end infinite",
        "fade-up": "fadeUp 0.6s ease-out forwards",
        "draw-line": "drawLine 1.5s ease-out forwards",
        "terminal-boot": "terminalBoot 0.3s ease-out forwards",
        float: "float 2s ease-in-out infinite",
      },
      keyframes: {
        blink: {
          "0%, 100%": { opacity: "1" },
          "50%": { opacity: "0" },
        },
        fadeUp: {
          "0%": { opacity: "0", transform: "translateY(20px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        drawLine: {
          "0%": { strokeDashoffset: "1000" },
          "100%": { strokeDashoffset: "0" },
        },
        terminalBoot: {
          "0%": { opacity: "0", transform: "scale(0.98)" },
          "100%": { opacity: "1", transform: "scale(1)" },
        },
        float: {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-6px)" },
        },
      },
      boxShadow: {
        terminal:
          "0 0 0 1px rgb(var(--color-terminal-border)), 0 8px 32px rgb(var(--color-shadow-alpha))",
        "terminal-green":
          "0 0 0 1px rgb(var(--color-git-green-dim)), 0 0 20px rgb(var(--color-git-green) / 0.1)",
        "card-hover":
          "0 8px 32px rgb(var(--color-shadow-alpha)), 0 0 0 1px rgb(var(--color-terminal-border))",
      },
      backgroundImage: {
        "grid-pattern":
          "linear-gradient(rgb(var(--color-terminal-border) / 0.3) 1px, transparent 1px), linear-gradient(90deg, rgb(var(--color-terminal-border) / 0.3) 1px, transparent 1px)",
        "gradient-radial": "radial-gradient(var(--tw-gradient-stops))",
      },
      backgroundSize: {
        grid: "32px 32px",
      },
    },
  },
  plugins: [],
};

export default config;
