import { NextResponse } from 'next/server';

/**
 * Lightweight liveness probe for local Docker / platform checks.
 * Does not validate CSS bundles; use `npm run smoke:styles` after `npm run build` for that.
 */
export async function GET() {
  return NextResponse.json({
    ok: true,
    service: 'fueleu-web',
    time: new Date().toISOString(),
  });
}
