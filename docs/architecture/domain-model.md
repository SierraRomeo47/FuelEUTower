# Domain Model Map: FuelEU Control Tower

## 1. Relational Model & Key Schematics

This application is strictly relational. Below are the distinct Bounded Contexts.

### Bounded Context: Reference Data
* **Vessel:** `id` (PK), `imo_number` (Natural Key), `name`, `type`, `ice_class`, `tenant_id`
* **Company / ISMCompany:** `id` (PK), `company_imo`, `name`, `country_code`
* **AdministeringState:** `id` (PK), `iso_code`, `name`
* **ReportingPeriod:** `id` (PK), `year` (Natural Key), `status` (OPEN, LOCKED, ARCHIVED)

### Bounded Context: Compliance Calculation
* **VesselYear:** `id` (PK), `vessel_id` (FK), `reporting_period_id` (FK), `doc_status`. *This is the aggregate root for a ship in a given year.*
* **FuelEUInput:** `id` (PK), `vessel_year_id` (FK), `fuel_type`, `energy_amount_mj`, `ghg_intensity`, `well_to_tank_factor`
* **ComplianceCalculation:** `id` (PK), `vessel_year_id` (FK), `icb_value`, `acb_value`, `vcb_value`, `energy_in_scope`, `borrowing_cap` (computed), `recalculation_timestamp`.

### Bounded Context: Flexibility Mechanisms
* **BankingRecord:** `id` (PK), `vessel_year_id` (FK), `banked_amount`, `status` (PROPOSED, COMMITTED, REJECTED)
* **BorrowingRecord:** `id` (PK), `vessel_year_id` (FK), `borrowed_amount`, `penalty_multiplier` (1.10), `repayment_target_year_id` (FK)
* **Pool:** `id` (PK), `reporting_period_id` (FK), `name`, `status`, `net_balance`
* **PoolParticipant:** `id` (PK), `pool_id` (FK), `vessel_year_id` (FK)
* **PoolAllocation:** `id` (PK), `pool_participant_id` (FK), `allocated_compliance_transfer` (positive or negative int)

### Bounded Context: Workflow & Audit
* **ValidationException:** `id` (PK), `vessel_year_id` (FK), `rule_code`, `severity` (HARD, SOFT), `status`, `override_reason`
* **WorkflowMilestone:** `id` (PK), `vessel_year_id` (FK), `milestone_type` (e.g., VERIFICATION_STARTED, DOC_ISSUED), `timestamp`, `actor_id`
* **AuditEvent:** `id` (PK), `entity_type`, `entity_id`, `actor_id`, `action`, `before_payload` (JSON), `after_payload` (JSON), `timestamp`

## 2. Hard Constraints Enforced at DB/Domain Layer

1. **Unique Pool Assignment:** A `VesselYear` aggregate can have exactly *one* active `PoolParticipant` record. (Unique Constraint)
2. **Mutually Exclusive Flexibilities:** Database check constraint tracking `VesselYear` status ensures if `BorrowingRecord` is active, a `PoolParticipant` record is prevented, and vice versa.
3. **Audit Immutability:** The `AuditEvent` table allows `INSERT` operations only. `UPDATE` and `DELETE` are denied at the database trigger level to ensure unimpeachable history.
4. **Soft Deletes:** Standardized via a `deleted_at` timestamp on non-transactional metadata; hard deletions are heavily restricted.
