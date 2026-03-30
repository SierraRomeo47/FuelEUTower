# FuelEUTower

FuelEU Control Tower — maritime compliance MVP covering pooling, banking, borrowing, and fleet/compliance workflows. The repo combines a **Next.js** control-tower UI (`apps/web`), a **Spring Boot** API (`apps/api`), and **PostgreSQL** for persistence.

**Canonical project narrative (progress, capstone, demos, QA):** [FuelEUTower.md](FuelEUTower.md)

---

## Versioning

Versions are declared **per component** so the API and UI can evolve on different schedules.

| Component | Version / identifier | Location |
|-----------|----------------------|----------|
| **Platform (repo)** | Documented below; no single semver at root | This README + [FuelEUTower.md](FuelEUTower.md) |
| **Web app** | `0.1.0` | [apps/web/package.json](apps/web/package.json) (`version`) |
| **API** | `0.0.1-SNAPSHOT` | [apps/api/build.gradle](apps/api/build.gradle) (`version`) |

**Stack baseline (as pinned in manifests — bump when you upgrade tooling):**

| Layer | Version |
|-------|---------|
| Node.js (for `apps/web`) | **18+** recommended (uses `fetch` in smoke scripts) |
| Next.js | **14.2.3** |
| React | **18.x** |
| TypeScript | **5.x** |
| Tailwind CSS | **3.4.x** |
| Java | **21** |
| Spring Boot | **4.0.5** (see `apps/api/build.gradle`) |
| PostgreSQL | Runtime dependency via JDBC/Flyway (see API config) |

When you cut a release or milestone, update the relevant `version` / changelog section in **FuelEUTower.md** and bump **`apps/web/package.json`** and/or **`apps/api/build.gradle`** as appropriate.

---

## Repository layout (short)

| Path | Role |
|------|------|
| `apps/web/` | Next.js App Router UI (FuelEU Tower) |
| `apps/api/` | Spring Boot API, Flyway migrations |
| `fixtures/xml/` | Sample XML for demos and tests |
| `tools/regulatory/` | Regulatory parameter helpers (`params.json`, workbook scripts) |
| `AGENTS.md`, `.cursor/rules/` | Agent/engineering conventions |

---

## Web app — quick start

Install dependencies once in the web package, then run scripts from the repo root or from `apps/web`:

```bash
cd apps/web
npm install
cd ../..
npm run dev
```

Development URL: http://localhost:3000 (keep the terminal open while developing).

Useful scripts (from root, via `package.json` delegates):

| Script | Purpose |
|--------|---------|
| `npm run dev` | Next.js dev server |
| `npm run dev:fresh` | Free port 3000, clear `.next`, then dev (fixes stale CSS/process issues) |
| `npm run build` | Production build |
| `npm run ci` | Lint, build, CSS verification, route + stylesheet smoke tests |

Health check (after build + start): `GET /api/health` on the web app.

---

## Engineering rules

- [AGENTS.md](AGENTS.md) — API contracts, Flyway, testing expectations  
- [.cursor/rules/](.cursor/rules/) — includes `fueleu-domain.mdc` (ICB/ACB/VCB glossary and constraints)

---

## License / attribution

See [FuelEUTower.md](FuelEUTower.md) and your capstone or institutional requirements for citation and submission bundles.
