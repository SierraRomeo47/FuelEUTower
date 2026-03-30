# Product Requirements Document (PRD): FuelEU Control Tower

## 1. Executive Summary
The **FuelEU Control Tower** is an enterprise-grade web application built to transition maritime companies from Excel-based calculation sheets to a structured, auditable, and multi-user platform. It manages the full lifecycle of FuelEU compliance, including initial balance calculations, banking, borrowing, pooling, workflow milestones, scenario planning, and business intelligence (BI) exports.

## 2. Primary Goals
1. Process structured and unstructured imports (Excel, CSV, XML) for vessel, voyage, port, fuel, and compliance data.
2. Confidently and securely calculate FuelEU compliance balances correctly.
3. Manage banking, borrowing, and pooling functions enforcing strict business rules.
4. Track workflow progression, exceptions, verifier actions, and Document of Compliance (DoC) readiness.
5. Provide a robust framework for Commercial pooling decisions and what-if scenario analyses.
6. Export structured, BI-ready data suitable for Tableau and Power BI.
7. Maintain an unalterable, comprehensive audit trail.

## 3. Core Business Rules
The application strictly enforces regulatory limits and behaviors explicitly in the Domain, Validation, and UI layers:
- **Banking:** Allowed only when the resulting final verified surplus is positive. Blocked after DoC issuance.
- **Borrowing:** Allowed only if a residual deficit exists. Capped at `2% * applicable GHG limit * energy in scope`. The borrowed amount incurs a *110% aggravated repayment penalty* applied to the following reporting period's deficit. Cannot be used consecutively across two reporting periods.
- **Pooling:** A ship can only belong to one pool per reporting period. Allowed only when the combined net compliance balance remains positive. The pool must not push a surplus ship into deficit, nor worsen the deficit of a non-compliant ship. Borrowing and pooling are strictly mutually exclusive for a single ship within the same year.
- **DoC / Penalties:** Unresolved residual deficits block the Document of Compliance issuance, exposing the fleet to monetary penalty settlements.
- **Exception Overrides:** Every manual override to reference master data or calculated overrides MUST capture a mandatory reason code and generate an audit trail.

## 4. Input Sources & Interoperability
- **Manual Data Entry:** Structured entry for immediate parameter tweaks via the frontend.
- **File Imports (CSV / Excel):** First-class support for mapping legacy FuelEU workbook setups to platform domains.
- **XML Imports:** Built-in parsing for incoming voyage & port emissions data (Port code, ATA/ATD, fuel type, consumption amounts, distance traversed).
- **Document Evidence:** Securely store PDFs (such as DNV period statements) related to vessel-years. 

## 5. Security & Access Control
- Integration with standard OIDC/OAuth2 Providers (starting with Keycloak).
- Roles defined: `System Admin`, `Compliance Manager`, `Analyst`, `Commercial Pooling Manager`, `Verifier Liaison`, `Read Only Executive`, `Auditor`
- Role-Based Access Control (RBAC) enforced in the API perimeter with record-level checks where applicable.

## 6. Dashboarding & Analytics
Visualizations that reflect exact calculated facts without any hidden client-side evaluation:
- **Executive Dashboard:** Fleet-wide ICB/ACB/VCB totals, top risk vessels, and total penalty exposure.
- **Commercial Dashboard:** Pool net balances and capacity allocation forecasting.
- **Compliance Operations Dashboard:** Verification roadblocks, missing documentation alerts, exception queues.
