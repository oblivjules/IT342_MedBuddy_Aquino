-- ============================================================================
-- DIAGNOSTIC SCRIPT: Exception Date Save Debugging
-- ============================================================================
-- Purpose: Verify database schema and constraints for doctor_availability table
-- Run this to check if there are any schema issues preventing exception dates
-- from being saved.
-- ============================================================================

-- 1. Check if doctor_availability table exists and its structure
\d doctor_availability

-- 2. Verify all required columns are present
SELECT 
    column_name, 
    data_type, 
    is_nullable, 
    column_default
FROM information_schema.columns
WHERE table_name = 'doctor_availability'
ORDER BY ordinal_position;

-- 3. Check for unique constraint on (doctor_id, available_date)
SELECT 
    constraint_name,
    constraint_type,
    column_name
FROM information_schema.key_column_usage
WHERE table_name = 'doctor_availability'
ORDER BY constraint_name, ordinal_position;

-- 4. Check foreign key relationships
SELECT 
    constraint_name,
    table_name,
    column_name,
    referenced_table_name,
    referenced_column_name
FROM information_schema.referential_constraints
WHERE table_name = 'doctor_availability'
  OR referenced_table_name = 'doctor_availability';

-- 5. List recent doctor_availability records (last 10)
SELECT 
    id, 
    doctor_id, 
    available_date, 
    start_time, 
    end_time, 
    status,
    created_at,
    updated_at
FROM doctor_availability
ORDER BY id DESC
LIMIT 10;

-- 6. Check if there are any pending transactions or locks
SELECT 
    pid,
    usename,
    application_name,
    state,
    query,
    query_start,
    state_change
FROM pg_stat_activity
WHERE state != 'idle'
  AND query NOT LIKE '%pg_stat_activity%'
ORDER BY query_start DESC;

-- 7. Verify doctors table has data (should have at least one doctor for testing)
SELECT 
    id, 
    first_name, 
    last_name, 
    user_id,
    created_at
FROM doctors
ORDER BY id DESC
LIMIT 5;

-- 8. Check for any constraint violations or errors in recent logs
-- Note: This varies by database; for PostgreSQL, check pg_log or application logs

-- 9. Test insert (if you want to manually verify the save works)
-- Uncomment and modify to test:
-- INSERT INTO doctor_availability (doctor_id, available_date, start_time, end_time, status)
-- VALUES (1, '2026-05-10', '09:00:00', '17:00:00', 'UNAVAILABLE');

-- 10. Verify appointment_slot table for any orphaned records
SELECT 
    COUNT(*) as total_slots,
    COUNT(CASE WHEN status = 'AVAILABLE' THEN 1 END) as available,
    COUNT(CASE WHEN status = 'BOOKED' THEN 1 END) as booked,
    COUNT(CASE WHEN status = 'UNAVAILABLE' THEN 1 END) as unavailable
FROM appointment_slot;
