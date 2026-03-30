# Developer Guidelines & Domain Context (FuelEU Platform)

## 1. Domain Glossary
If building within this codebase, understand these fundamental constants:
- **ICB (Initial Compliance Balance):** Pre-flexibility calculation of zero-emissions trajectory distance.
- **ACB (Adjusted Compliance Balance):** The ICB adjusted with prior-year surplus carry-forwards, multiplied by compliance bounds.
- **VCB (Verified Compliance Balance):** The locked and regulator-approved final value representing the ship's financial regulatory state.
- **GHG Limit:** The legally defined greenhouse gas target trajectory per year.
- **DoC (Document of Compliance):** A physical/digital permit essential for ongoing maritime operations in EU ports.

## 2. Engineering Constraints
1. **NO UI-SIDE MATH:** The calculation engine evaluates legally binding constants. No math happens in Next.js `onChange` events. Calculations happen on the backend, and results are delivered to the frontend payload. 
2. **Audit Before Comfort:** When adding an action spanning state updates on `Banking`, `Borrowing`, or `Pooling`, it must insert a correlated `AuditEvent`.
3. **Rule Versioning:** Never hardcode 1.1x as the borrowing multiplier globally without tying it to the `RegulatoryParameter` active for that `ReportingPeriod`. The rule might change in 2028.

## 3. Tooling
- Frontend: TypeScript, Next.js (App Router), Tailwind, shadcn/ui.
- Backend: Java 21, Spring Boot 3, Spring Modulith.
- Database: PostgreSQL (Relational consistency).
