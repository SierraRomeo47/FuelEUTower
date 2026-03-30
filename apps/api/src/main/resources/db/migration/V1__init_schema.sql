CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Reference Data & Registry
CREATE TABLE company (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_imo VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_state (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    iso_code VARCHAR(3) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE vessel (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    imo_number VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    vessel_type VARCHAR(100) NOT NULL,
    ice_class VARCHAR(50),
    tenant_id UUID REFERENCES company(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reporting_period (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    year INTEGER UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, LOCKED, ARCHIVED
    ghg_limit NUMERIC(10, 4) NOT NULL
);

-- 2. Compliance Engine
CREATE TABLE vessel_year (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    vessel_id UUID NOT NULL REFERENCES vessel(id),
    reporting_period_id UUID NOT NULL REFERENCES reporting_period(id),
    doc_status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(vessel_id, reporting_period_id)
);

CREATE TABLE compliance_calculation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    vessel_year_id UUID UNIQUE NOT NULL REFERENCES vessel_year(id),
    energy_in_scope NUMERIC(15, 4) NOT NULL DEFAULT 0.0,
    icb_value NUMERIC(15, 4),
    acb_value NUMERIC(15, 4),
    vcb_value NUMERIC(15, 4),
    borrowing_cap NUMERIC(15, 4),
    recalculation_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Flexibility Mechanisms
CREATE TABLE banking_record (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    vessel_year_id UUID NOT NULL REFERENCES vessel_year(id),
    banked_amount NUMERIC(15, 4) NOT NULL,
    status VARCHAR(50) DEFAULT 'PROPOSED'
);

CREATE TABLE borrowing_record (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    vessel_year_id UUID NOT NULL REFERENCES vessel_year(id),
    borrowed_amount NUMERIC(15, 4) NOT NULL,
    penalty_multiplier NUMERIC(5, 2) DEFAULT 1.10,
    repayment_target_year_id UUID REFERENCES reporting_period(id)
);

CREATE TABLE pool (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporting_period_id UUID NOT NULL REFERENCES reporting_period(id),
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) DEFAULT 'DRAFT',
    net_balance NUMERIC(15, 4) DEFAULT 0.0
);

CREATE TABLE pool_participant (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pool_id UUID NOT NULL REFERENCES pool(id),
    vessel_year_id UUID UNIQUE NOT NULL REFERENCES vessel_year(id)
);

CREATE TABLE pool_allocation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pool_participant_id UUID UNIQUE NOT NULL REFERENCES pool_participant(id),
    allocated_compliance_transfer NUMERIC(15, 4) NOT NULL
);

-- 4. Audit & History
CREATE TABLE audit_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    before_payload JSONB,
    after_payload JSONB,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
