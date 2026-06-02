// Prerender step: rasterizes the OpenGraph card (ported from the Next.js
// opengraph-image.tsx design) to a static public/opengraph-image.png.
// Regenerate with:  npm run gen:og
import { Resvg } from "@resvg/resvg-js";
import { mkdirSync, writeFileSync } from "node:fs";

const NAME = "Omkar Jadhav";
const HANDLE = "omkarjadhav";
const HEADLINE = "B.Tech CSE (Data Science) Student &amp; Full-Stack Developer";
const STATUS = "Open to internships &amp; collaborations";

const svg = `<svg width="1200" height="630" viewBox="0 0 1200 630" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
      <path d="M40 0 H0 V40" fill="none" stroke="#303639" stroke-opacity="0.3" stroke-width="1"/>
    </pattern>
  </defs>
  <rect width="1200" height="630" fill="#0d1117"/>
  <rect width="1200" height="630" fill="url(#grid)" opacity="0.4"/>
  <g font-family="'JetBrains Mono','DejaVu Sans Mono','Courier New',monospace">
    <text x="80" y="140" font-size="22">
      <tspan fill="#00ff88">git:</tspan><tspan fill="#e6edf3"> ${HANDLE}</tspan><tspan fill="#484f58">/</tspan><tspan fill="#58a6ff">main</tspan>
    </text>
    <text x="80" y="265" font-size="72" font-weight="700" fill="#e6edf3">${NAME}</text>
    <text x="80" y="335" font-size="26" fill="#8b949e">${HEADLINE}</text>
    <g transform="translate(80,395)">
      <rect x="0" y="0" width="560" height="48" rx="24" fill="#00ff88" fill-opacity="0.06" stroke="#00ff88" stroke-opacity="0.35" stroke-width="1"/>
      <circle cx="28" cy="24" r="6" fill="#00ff88"/>
      <text x="48" y="31" font-size="18" fill="#00ff88">${STATUS}</text>
    </g>
    <text x="1120" y="595" font-size="14" fill="#484f58" text-anchor="end">${HANDLE}.vercel.app</text>
  </g>
</svg>`;

const resvg = new Resvg(svg, {
  fitTo: { mode: "width", value: 1200 },
  font: { loadSystemFonts: true },
  background: "#0d1117",
});
const png = resvg.render().asPng();

mkdirSync("public", { recursive: true });
writeFileSync("public/opengraph-image.png", png);
console.log(`✓ public/opengraph-image.png generated (${png.length} bytes)`);
