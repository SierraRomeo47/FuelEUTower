/**
 * After `npm run build`, starts `next start` and GETs every App Router page plus /api/health.
 * Fails on non-200 or trivial error markers in HTML.
 */
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const port = Number(process.env.SMOKE_PORT || '3205');

const PAGES = [
  '/',
  '/fleet',
  '/ledger',
  '/flexibility',
  '/doc-tracker',
  '/import',
  '/settings',
];

function wait(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  const proc = spawn('npx', ['next', 'start', '-p', String(port)], {
    cwd: root,
    shell: true,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, PORT: String(port) },
  });

  const shutdown = async () => {
    await new Promise((resolve) => {
      if (process.platform === 'win32' && proc.pid) {
        const killer = spawn('taskkill', ['/PID', String(proc.pid), '/T', '/F'], {
          shell: true,
          stdio: 'ignore',
        });
        killer.on('close', () => resolve());
        killer.on('error', () => resolve());
      } else {
        try {
          proc.kill('SIGTERM');
        } catch {
          /* ignore */
        }
        setTimeout(resolve, 400);
      }
    });
  };

  let ready = false;
  proc.stdout.on('data', (d) => {
    const t = d.toString();
    if (t.includes('Ready') || t.includes('started server')) ready = true;
  });

  for (let i = 0; i < 90; i++) {
    await wait(500);
    if (ready) break;
    try {
      const r = await fetch(`http://127.0.0.1:${port}/`);
      if (r.ok) {
        ready = true;
        break;
      }
    } catch {
      /* still starting */
    }
  }

  if (!ready) {
    await shutdown();
    console.error('smoke-routes: server never became ready');
    process.exit(1);
  }

  const base = `http://127.0.0.1:${port}`;
  const failures = [];

  for (const p of PAGES) {
    let res;
    try {
      res = await fetch(`${base}${p}`);
    } catch (e) {
      failures.push(`${p}: ${e.message}`);
      continue;
    }
    if (!res.ok) {
      failures.push(`${p}: HTTP ${res.status}`);
      continue;
    }
    const text = await res.text();
    if (text.length < 300) {
      failures.push(`${p}: body too small (${text.length} bytes)`);
      continue;
    }
    if (
      text.includes('Application error:') ||
      text.includes('missing required error components')
    ) {
      failures.push(`${p}: Next error content`);
    }
  }

  try {
    const h = await fetch(`${base}/api/health`);
    if (!h.ok) {
      failures.push(`/api/health: HTTP ${h.status}`);
    } else {
      const j = await h.json();
      if (j.ok !== true) failures.push(`/api/health: expected { ok: true }`);
    }
  } catch (e) {
    failures.push(`/api/health: ${e.message}`);
  }

  await shutdown();

  if (failures.length) {
    console.error('smoke-routes FAIL');
    for (const f of failures) console.error(' -', f);
    process.exit(1);
  }

  console.log(
    `smoke-routes: OK ${PAGES.length} pages + /api/health (port ${port})`,
  );
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
