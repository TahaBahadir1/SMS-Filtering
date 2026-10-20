CREATE TABLE senders (
    id BIGSERIAL PRIMARY KEY,
    sender_value VARCHAR(32) NOT NULL UNIQUE,
    risk_score NUMERIC(3,2) NOT NULL DEFAULT 0,
    risk_level VARCHAR(20) NOT NULL CHECK (risk_level IN ('risksiz', 'orta_riskli', 'riskli')),
    is_finalized BOOLEAN NOT NULL DEFAULT false,
    first_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sender_signals (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES senders(id) ON DELETE CASCADE,
    source VARCHAR(20) NOT NULL CHECK (source IN ('buyuk_veri', 'yapay_zeka')),
    score NUMERIC(3,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE keyword_categories (
    id SMALLSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE keywords (
    id BIGSERIAL PRIMARY KEY,
    category_id SMALLINT NOT NULL REFERENCES keyword_categories(id) ON DELETE CASCADE,
    keyword VARCHAR(100) NOT NULL,
    CONSTRAINT uq_category_keyword UNIQUE (category_id, keyword)
);

CREATE TABLE keyword_list_version (
    version INTEGER PRIMARY KEY,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
