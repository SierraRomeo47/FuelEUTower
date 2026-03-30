## FuelEU Control Tower — Capstone Presentation (15–20 slides)

**Target duration**: 10–12 minutes total + 5–8 minutes live demo  
**Audience**: MBA capstone evaluators (business + tech overview)  

### Slide 1 — Title
- FuelEU Control Tower: Pooling, Banking & Borrowing Platform (MVP)
- Your name, program, date

### Slide 2 — Agenda
- Problem & context
- Solution overview
- Architecture
- Key features (import + flexibility)
- Value/ROI
- Demo
- Roadmap & close

### Slide 3 — Why now (FuelEU pressure)
- Regulation-driven compliance target trajectory
- Penalty + DoC readiness risk
- Spreadsheet workflows break at fleet scale

### Slide 4 — Business problem (pain points)
- Manual reconciliation across vessel-years
- Low auditability (hard to defend calculations)
- Slow decision cycles for flexibility mechanisms

### Slide 5 — Opportunity sizing (impact)
- Fleet-level deficit aggregation → € exposure
- Operational cost of manual compliance processes
- Competitive advantage of “control tower” approach

### Slide 6 — Solution in one sentence
- “A deterministic, auditable compliance control tower that ingests FuelEU data and executes banking/borrowing/pooling actions within regulatory guardrails.”

### Slide 7 — Product scope (MVP modules)
- Fleet registry
- Import center (Excel/XML)
- Executive dashboard (KPIs + high-risk vessels)
- Flexibility workspace (bank/borrow/pool)
- Ledger + DoC tracker pages (foundation)

### Slide 8 — Stakeholder map
- Compliance manager, commercial pooling manager, verifier liaison, executives
- What each sees/does in the system

### Slide 9 — Architecture (high-level)
- Next.js UI → Spring Boot API → PostgreSQL
- Dockerized infra (Postgres/Redis/MinIO/Keycloak)

### Slide 10 — Data model (high-level)
- Vessel → ReportingPeriod → VesselYear
- Banking/Borrowing/Pooling records
- Import batch tracking

### Slide 11 — Methodology guardrails (what makes it “regulatory-safe”)
- No UI-side binding math
- Parameterization (caps/multipliers) and versioning by reporting period
- Audit-first principle (production hardening item)

### Slide 12 — Import pipeline (XML/XLSX)
- What gets extracted (IMO, energy/emissions)
- Unit conversion and validation
- Persistence behavior (skip if registry missing; deterministic integrity)

### Slide 13 — Flexibility mechanisms (bank/borrow/pool)
- What each does
- Constraints represented (cap, consecutive borrow, pool positivity)
- How operations are recorded in DB

### Slide 14 — Dashboard & decision support
- KPI snapshot (ICB/ACB/borrowing cap/penalty exposure)
- High-risk constituents list

### Slide 15 — ROI model (MBA lens)
- Penalty avoided (baseline vs optimized)
- Time saved (manual → automated)
- Audit/verification cost reduction

### Slide 16 — Risks & limitations (honest assessment)
- Security/RBAC is dev-permissive for MVP demo
- Needs full audit event correlation for production
- Data quality dependency of inputs

### Slide 17 — Roadmap (post-MVP)
- Keycloak RBAC + tenant scoping
- Audit event correlation on flexibility actions
- Typed client from OpenAPI, broader endpoints
- Scenario engine + BI exports

### Slide 18 — Demo (what you will show)
- Import XML
- Fleet registry add vessel
- Execute pooling (or banking/borrowing)
- Show dashboard refresh / persisted state

### Slide 19 — Closing (key takeaways)
- Compliance accuracy + auditability + faster decisions
- Scalable foundation for enterprise rollout

### Slide 20 — Q&A

