CREATE TABLE cb_market_trade (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    vessel_year_id UUID NOT NULL REFERENCES vessel_year(id),
    cb_amount_gco2eq NUMERIC(15, 4) NOT NULL,
    unit_price_eur_per_gco2eq NUMERIC(15, 8) NOT NULL,
    total_cost_eur NUMERIC(15, 2) NOT NULL,
    market_source VARCHAR(100) NOT NULL,
    market_symbol VARCHAR(100) NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cb_market_rate_snapshot (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    market_source VARCHAR(100) NOT NULL,
    market_symbol VARCHAR(100) NOT NULL,
    quoted_price_eur_per_gco2eq NUMERIC(15, 8) NOT NULL,
    quoted_price_usd_per_token NUMERIC(15, 8),
    fx_eur_usd NUMERIC(15, 8),
    captured_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
