/**
 * Post-build guard: ensures Tailwind/PostCSS output landed in .next/static/css.
 * Catches misconfigured postcss, missing globals.css import, or broken builds.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const cssDir = path.join(root, '.next', 'static', 'css');

function fail(msg) {
  console.error('verify-next-css:', msg);
  process.exit(1);
}

if (!fs.existsSync(cssDir)) {
  fail(`missing directory ${cssDir} (run "npm run build" from apps/web first)`);
}

const files = fs.readdirSync(cssDir).filter((f) => f.endsWith('.css'));
if (files.length === 0) {
  fail(`no .css files under .next/static/css`);
}

let matched = null;
for (const f of files) {
  const p = path.join(cssDir, f);
  const s = fs.readFileSync(p, 'utf8');
  const hasTailwind =
    s.includes('tailwindcss') || s.includes('tailwind');
  const hasUtilities =
    s.includes('.flex{') ||
    s.includes('.flex{display:flex') ||
    s.includes('display:flex');
  if (hasTailwind && hasUtilities) {
    matched = { f, len: s.length };
    break;
  }
}

if (!matched) {
  fail(
    'no bundle looked like Tailwind output (expected tailwind fingerprint + flex utilities)',
  );
}

console.log(`verify-next-css: OK ${matched.f} (${matched.len} bytes)`);
