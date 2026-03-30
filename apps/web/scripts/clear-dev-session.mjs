/**
 * Operational guard: stale Next/Node on :3000 + out-of-sync `.next` produces HTML that
 * references CSS the current process cannot serve (unstyled UI, 400s on /_next/static/css/...).
 *
 * Usage:
 *   node ./scripts/clear-dev-session.mjs           # kill :3000 (best-effort) + rm .next
 *   node ./scripts/clear-dev-session.mjs --no-kill # only rm .next
 *
 * Port defaults to PORT env or 3000.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const skipKill = process.argv.includes('--no-kill');
const port = Number(process.env.PORT || process.env.APP_PORT || '3000');

const nextDir = path.join(root, '.next');

async function main() {
  if (!skipKill) {
    const { default: killPort } = await import('kill-port');
    try {
      await killPort(port);
      console.info(`clear-dev-session: freed listeners on :${port}`);
    } catch {
      console.info(
        `clear-dev-session: nothing to kill on :${port} (or kill-port could not bind — continuing)`,
      );
    }
  }

  if (fs.existsSync(nextDir)) {
    fs.rmSync(nextDir, { recursive: true, force: true });
    console.info('clear-dev-session: removed .next');
  } else {
    console.info('clear-dev-session: no .next directory');
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
