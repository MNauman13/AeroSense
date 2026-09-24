BEGIN;

DO $$
DECLARE
    sample_rig UUID := '00000000-0000-4000-8000-000000000001';
    missing_rig UUID := '00000000-0000-4000-8000-000000000099';
    first_cycle UUID := '00000000-0000-4000-8000-000000000101';
BEGIN
    BEGIN
        INSERT INTO test_cycle (id, cycle_code, rig_id, recorded_at, cycle_type, synthetic_label)
        VALUES (
            first_cycle,
            'CYC-999998',
            missing_rig,
            CURRENT_TIMESTAMP,
            'synthetic-smoke-check',
            FALSE
        );
        RAISE EXCEPTION 'Expected a missing-rig foreign key violation';
    EXCEPTION WHEN foreign_key_violation THEN
        NULL;
    END;

    INSERT INTO test_rig (id, rig_code, description)
    VALUES (
        sample_rig,
        'RIG-SYN-999999',
        'Fictional migration smoke-test rig; synthetic data only.'
    );
    INSERT INTO test_cycle (id, cycle_code, rig_id, recorded_at, cycle_type, synthetic_label)
    VALUES (
        first_cycle,
        'CYC-999999',
        sample_rig,
        CURRENT_TIMESTAMP,
        'synthetic-smoke-check',
        FALSE
    );

    BEGIN
        INSERT INTO test_cycle (id, cycle_code, rig_id, recorded_at, cycle_type, synthetic_label)
        VALUES (
            '00000000-0000-4000-8000-000000000102',
            'CYC-999999',
            sample_rig,
            CURRENT_TIMESTAMP,
            'synthetic-smoke-check',
            FALSE
        );
        RAISE EXCEPTION 'Expected a duplicate cycle-code violation';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;
END
$$;

ROLLBACK;
