CREATE TABLE compliance_ledger_override (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    vessel_year_id UUID UNIQUE NOT NULL REFERENCES vessel_year(id),
    energy_in_scope NUMERIC(15, 4),
    actual_intensity NUMERIC(15, 6),
    target_intensity NUMERIC(15, 6),
    icb_value NUMERIC(15, 4),
    vcb_value NUMERIC(15, 4),
    banked_amount NUMERIC(15, 4),
    borrowed_amount NUMERIC(15, 4),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
