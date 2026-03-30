## FuelEU Control Tower — Live Demo Script (5–8 minutes)

### 0) Pre-demo checklist (2 minutes before presenting)
- Docker Desktop running
- Backend running on `http://localhost:8082`
- Web app running on `http://localhost:3000`
- Postgres container `fueleu-postgres` is **Up**

### 1) Start services (if not already running)
**Infra** (from repo root):
- `docker compose -f infra/docker/docker-compose.yml up -d`

**Backend** (from repo root; assumes JDK 21 available):
- Set `JAVA_HOME` and start: `apps/api/gradlew.bat -p apps/api bootRun`

**Frontend**:
- `cd apps/web`
- `npm run start:clean`

### 2) Demo narrative (what you say)
“FuelEU pushes fleets to meet GHG intensity limits. The hard part isn’t one calculation—it’s operating compliance across vessel-years with auditability. This control tower ingests structured XML/XLSX inputs and lets us execute flexibility actions (bank/borrow/pool) under guardrails.”

### 3) Step-by-step actions (what you click/do)

#### Step A — Executive dashboard (30–45s)
- Open `http://localhost:3000`
- Explain KPIs and “High Risk Constituents” list.

#### Step B — Import XML (60–90s)
- Go to **Data Import Center** (`/import`)
- Upload: `Study Material/9231614_PortPart1-DNV.xml`
- Expected: success toast and backend response for “XML processed”

CLI fallback if upload UI fails:
- `curl.exe -F "file=@Study Material/9231614_PortPart1-DNV.xml" http://localhost:8082/api/v1/imports/upload`

#### Step C — Register vessel (60–90s)
- Go to **Fleet Registry** (`/fleet`)
- Register a vessel using the IMO from the XML (example: `IMO9715490`)

CLI fallback (PowerShell):
- `$body = @{ imoNumber='IMO9715490'; name='Demo Vessel 9715490'; vesselType='Container'; iceClass='-'; buildYear=2019; flagState='Panama' } | ConvertTo-Json`
- `Invoke-RestMethod -Method Post -Uri http://localhost:8082/api/v1/registry/vessels -ContentType 'application/json' -Body $body`

#### Step D — Execute flexibility (60–90s)
- Go to **Flexibility Workspace** (`/flexibility`)
- Select vessel, pick an action (pool/borrow/bank), submit
- Expected: success toast (“recorded to PostgreSQL”)

CLI fallback (PowerShell):
- `$payload=@{ type='pool'; vesselId='<UUID from registry response>'; amount=1500 } | ConvertTo-Json`
- `Invoke-RestMethod -Method Post -Uri http://localhost:8082/api/v1/flexibility/execute -ContentType 'application/json' -Body $payload`

#### Step E — Close loop (30–45s)
- Return to dashboard and/or fleet registry
- Explain persistence-backed behavior and auditability path (roadmap: correlated audit events).

### 4) Common failure recoveries (keep calm)
- **Port 8082 in use**: `netstat -ano | findstr ":8082"` then stop the listed PID.
- **Backend fails due to DB**: ensure Postgres container is running and the app DB is `fueleu_app`.
- **UI still calling old API port**: set `NEXT_PUBLIC_API_BASE_URL=http://localhost:8082` (optional; code defaults to 8082).

### 5) Backup plan (if live runtime fails)
- Use a screen recording or screenshots of: dashboard, import success, fleet registration, flexibility success.
- Continue the narrative using the same step order and explain what each step proves.

