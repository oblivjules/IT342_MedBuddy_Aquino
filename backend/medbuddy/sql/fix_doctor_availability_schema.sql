-- ============================================================================
-- REMEDIATION SCRIPT: Ensure doctor_availability table is correctly configured
-- ============================================================================
-- Purpose: Add any missing columns, constraints, or indexes required for
-- exception date saves to work correctly.
-- 
-- IMPORTANT: Review and test in development BEFORE running on production!
--
-- Usage:
--   psql -h your-neon-host -U username -d medbuddy -f fix_doctor_availability_schema.sql
-- ============================================================================

BEGIN;

-- 1) Ensure status column exists on doctor_availability
ALTER TABLE doctor_availability
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'AVAILABLE';

-- Add NOT NULL constraint if needed (do this in separate transaction to avoid data issues)
-- ALTER TABLE doctor_availability ALTER COLUMN status SET NOT NULL;

-- 2) Ensure all required columns exist
ALTER TABLE doctor_availability
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE doctor_availability
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 3) Ensure foreign key constraint exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.conname = 'fk_doctor_availability_doctor'
          AND t.relname = 'doctor_availability'
    ) THEN
        ALTER TABLE doctor_availability
            ADD CONSTRAINT fk_doctor_availability_doctor
            FOREIGN KEY (doctor_id) REFERENCES doctors(id);
        RAISE NOTICE 'Added foreign key constraint';
    ELSE
        RAISE NOTICE 'Foreign key constraint already exists';
    END IF;
END $$;

-- 4) Ensure unique constraint on (doctor_id, available_date) exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.conname = 'uk_doctor_availability_doctor_date'
          AND t.relname = 'doctor_availability'
    ) THEN
        ALTER TABLE doctor_availability
            ADD CONSTRAINT uk_doctor_availability_doctor_date
            UNIQUE (doctor_id, available_date);
        RAISE NOTICE 'Added unique constraint on (doctor_id, available_date)';
    ELSE
        RAISE NOTICE 'Unique constraint already exists';
    END IF;
END $$;

-- 5) Create indexes for query performance
CREATE INDEX IF NOT EXISTS idx_doctor_availability_doctor_id
    ON doctor_availability(doctor_id);

CREATE INDEX IF NOT EXISTS idx_doctor_availability_date
    ON doctor_availability(available_date);

CREATE INDEX IF NOT EXISTS idx_doctor_availability_status
    ON doctor_availability(status);

-- 6) Verify the table structure
-- List all columns
\echo '--- Current doctor_availability table structure ---'
\d doctor_availability

-- List constraints
\echo '--- Current constraints ---'
SELECT 
    constraint_name,
    constraint_type
FROM information_schema.table_constraints
WHERE table_name = 'doctor_availability';

-- List columns with types
\echo '--- Current columns ---'
SELECT 
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'doctor_availability'
ORDER BY ordinal_position;

-- 7) Check for any data integrity issues
\echo '--- Data integrity checks ---'

-- Check for NULL doctor_ids
SELECT COUNT(*) as null_doctor_ids
FROM doctor_availability
WHERE doctor_id IS NULL;

-- Check for NULL dates
SELECT COUNT(*) as null_dates
FROM doctor_availability
WHERE available_date IS NULL;

-- Check for duplicate (doctor_id, available_date) pairs
SELECT 
    doctor_id,
    available_date,
    COUNT(*) as count
FROM doctor_availability
GROUP BY doctor_id, available_date
HAVING COUNT(*) > 1;

-- Check for orphaned doctor_ids (referencing deleted doctors)
SELECT COUNT(*) as orphaned_records
FROM doctor_availability da
WHERE NOT EXISTS (
    SELECT 1 FROM doctors d WHERE d.id = da.doctor_id
);

COMMIT;

\echo '--- Remediation script completed ---'
