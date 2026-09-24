CREATE TABLE test_rig (
    id UUID PRIMARY KEY,
    rig_code VARCHAR(40) NOT NULL UNIQUE CHECK (rig_code ~ '^RIG-SYN-[0-9]+$'),
    description TEXT NOT NULL CHECK (description ILIKE '%fictional%' OR description ILIKE '%synthetic%'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE test_cycle (
    id UUID PRIMARY KEY,
    cycle_code VARCHAR(60) NOT NULL UNIQUE CHECK (cycle_code ~ '^CYC-[0-9]+$'),
    rig_id UUID NOT NULL REFERENCES test_rig (id),
    recorded_at TIMESTAMPTZ NOT NULL,
    cycle_type VARCHAR(40) NOT NULL CHECK (cycle_type LIKE 'synthetic-%'),
    synthetic_label BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE measurement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cycle_id UUID NOT NULL REFERENCES test_cycle (id) ON DELETE CASCADE,
    feature_name VARCHAR(60) NOT NULL CHECK (
        feature_name IN (
            'extension_time_ms',
            'pressure_kpa',
            'vibration_rms',
            'temperature_c',
            'cycle_duration_ms'
        )
    ),
    "value" DOUBLE PRECISION NOT NULL CHECK (
        "value" > '-Infinity'::DOUBLE PRECISION AND "value" < 'Infinity'::DOUBLE PRECISION
    ),
    unit VARCHAR(24) NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_measurement_cycle_feature UNIQUE (cycle_id, feature_name)
);

CREATE TABLE analysis_run (
    id UUID PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    model_name VARCHAR(80) NOT NULL,
    model_version VARCHAR(40) NOT NULL,
    config_json JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    error_message TEXT,
    CONSTRAINT ck_analysis_run_completed_after_started
        CHECK (completed_at IS NULL OR completed_at >= started_at)
);

CREATE TABLE anomaly_result (
    id UUID PRIMARY KEY,
    analysis_run_id UUID NOT NULL REFERENCES analysis_run (id),
    cycle_id UUID NOT NULL REFERENCES test_cycle (id),
    score DOUBLE PRECISION NOT NULL CHECK (score >= 0 AND score <= 1),
    threshold DOUBLE PRECISION NOT NULL CHECK (threshold >= 0 AND threshold <= 1),
    is_flagged BOOLEAN NOT NULL,
    explanation_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_anomaly_result_run_cycle UNIQUE (analysis_run_id, cycle_id),
    CONSTRAINT ck_anomaly_result_threshold CHECK (is_flagged = (score >= threshold))
);

CREATE INDEX ix_test_cycle_recorded_at ON test_cycle (recorded_at DESC);
CREATE INDEX ix_test_cycle_rig_recorded_at ON test_cycle (rig_id, recorded_at DESC);
CREATE INDEX ix_measurement_cycle_feature ON measurement (cycle_id, feature_name);
CREATE INDEX ix_anomaly_result_run_flag_score
    ON anomaly_result (analysis_run_id, is_flagged, score DESC);
CREATE INDEX ix_anomaly_result_cycle_created_at
    ON anomaly_result (cycle_id, created_at DESC);
