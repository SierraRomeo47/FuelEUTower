# Role-Based Access Control (RBAC) Matrix

## Overview
Access within the platform is governed strictly by the roles configured via Identity Provider (OIDC/Keycloak). Scopes restrict operation limits per Bounded Context.

## Role Definitions
- **`System Admin`:** Unrestricted Technical Access. Can alter fundamental Regulatory Parameters and Rule Assumptions. Can alter Roles and Tenants.
- **`Compliance Manager`:** Full Read/Write on Calculations, Imports, Bankings, Borrowings, Pool Assignments. Cannot alter System Master Reference. Approves workflow milestones (e.g., DoC readiness).
- **`Analyst`:** Read/Write structured manual entry, XML file imports, and resolving validation warnings. Read-Only on computed Compliance modules.
- **`Commercial Pooling Manager`:** Exclusive Read/Write access on the `Pool` bounded context. Can formulate groups and edit `allocated_compliance_transfer`. Read-Only to Vessel fleet overviews.
- **`Verifier Liaison`:** Dedicated Read/Write for `EvidenceDocument` vault uploads and tracking Verification `WorkflowMilestone` flags. Read-Only on standard compliance math blocks to trace validation parity.
- **`Read Only Executive`:** Read-Only access to BI modules, executive dashboards, scenario engine what-if outputs, and fleet-wide KPI summaries.
- **`Auditor`:** Absolute Read-Only access to all historical states, audit table exports, immutable calculation snapshots, and overridden validation exception justifications. Never allowed to mutate any record.
