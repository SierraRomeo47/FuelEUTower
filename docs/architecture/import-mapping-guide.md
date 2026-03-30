# Import Mapping Guide

The platform replaces legacy Excel-based operations without breaking the user data-flow habits. Data ingested across multiple schemas translates down into our normalized Domain structure.

## 1. Structured Excel Workbooks (Legacy FuelEU Workbook)
- **Sheet `Vessel Master`** -> Translates to Bounded Context: `Registry` -> Entity: `Vessel`
  - Columns: `IMO number`, `Vessel Name`, `Ship type`, `Ice Class` mapped to specific lookup tables.
- **Sheet `Consumption Summary`** -> Translates to `Data Ingestion` -> `FuelEUInput`
  - Columns: `Fuel Type`, `Energy MJ`, `GHG Intensity WT`

## 2. Port & Voyage XML Ingestion
XML parsers must execute deterministic evaluation.
- `XML Node <port_emissions><port_code>` -> Maps to reference table resolution (`AdministeringState` / `Ports`).
- `XML Node <voyage_emissions><fuel_type>` -> Validates dynamically via `RegulatoryParameter` version limits.

## 3. Transient Validations
For both methods, files enter an `IMPORT_BATCH` record with a `DRAFT` status.
- Soft Warnings: "Data older than 6 months."
- Hard Validation errors: "Missing IMO Number."
- A user must **COMMIT** the batch to transition `DRAFT` rules into `COMMITTED` master data updates. This operation inserts an `AuditEvent` with the correlated batch ID.
