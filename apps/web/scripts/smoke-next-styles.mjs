/**
 * Spins up `next start` on a free port, fetches `/`, then loads the linked CSS.
 * Mimics the browser: broken or 400 CSS responses => unstyled UI (regression guard).
 *
 * Prerequisite: `npm run build` must have been run.
 */
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const port = Number(process.env.SMOKE_PORT || '3199');

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

  let stderr = '';
  proc.stderr.on('data', (d) => {
    stderr += d.toString();
  });

  let ready = false;
  proc.stdout.on('data', (d) => {
    const t = d.toString();
    if (t.includes('Ready') || t.includes('started server')) ready = true;
  });

  proc.on('error', (err) => {
    console.error('smoke-next-styles: failed to spawn next start', err);
    process.exit(1);
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

  if (!ready) {
    await shutdown();
    console.error(stderr.slice(-2000));
    console.error('smoke-next-styles: server never became ready');
    process.exit(1);
  }

  let htmlRes;
  try {
    htmlRes = await fetch(`http://127.0.0.1:${port}/`);
  } catch (e) {
    await shutdown();
    throw e;
  }

  if (!htmlRes.ok) {
    await shutdown();
    console.error('smoke-next-styles: HTML status', htmlRes.status);
    process.exit(1);
  }

  const html = await htmlRes.text();
  const m = html.match(/href="(\/_next\/static\/css\/[^"]+\.css[^"]*)"/);
  if (!m) {
    await shutdown();
    console.error('smoke-next-styles: no stylesheet link in HTML head');
    process.exit(1);
  }

  const cssPath = m[1].split('"')[0];
  const cssUrl = `http://127.0.0.1:${port}${cssPath}`;

  let cssRes;
  try {
    cssRes = await fetch(cssUrl);
  } catch (e) {
    await shutdown();
    throw e;
  }

  if (!cssRes.ok) {
    await shutdown();
    console.error(
      `smoke-next-styles: CSS ${cssRes.status} for ${cssPath} — page will look unstyled`,
    );
    process.exit(1);
  }

  const css = await cssRes.text();
  if (css.length < 8000) {
    await shutdown();
    console.error(
      `smoke-next-styles: CSS bundle too small (${css.length} bytes); expected Tailwind output`,
    );
    process.exit(1);
  }
  if (!css.includes('.flex') && !css.includes('flex')) {
    await shutdown();
    console.error('smoke-next-styles: CSS missing flex utilities');
    process.exit(1);
  }

  console.log(
    `smoke-next-styles: OK HTML ${htmlRes.status} CSS ${cssRes.status} ${cssPath} (${css.length} bytes)`,
  );
  await shutdown();
  process.exit(0);
}

main().catch(async (e) => {
  console.error(e);
  process.exit(1);
});
