-- SQL script: create_indexes.sql
-- Run these statements manually in Neon (do NOT run automatically).

-- Indexes for appointments
CREATE INDEX IF NOT EXISTS idx_appointments_patient_id ON appointments(patient_id);
CREATE INDEX IF NOT EXISTS idx_appointments_doctor_id ON appointments(doctor_id);
CREATE INDEX IF NOT EXISTS idx_appointments_status ON appointments(status);

-- Indexes for payments
CREATE INDEX IF NOT EXISTS idx_payments_appointment_id ON payments(appointment_id);
CREATE INDEX IF NOT EXISTS idx_payments_payment_status ON payments(payment_status);

-- Indexes for medical_records
CREATE INDEX IF NOT EXISTS idx_medical_records_appointment_id ON medical_records(appointment_id);

-- Indexes for file_uploads
CREATE INDEX IF NOT EXISTS idx_file_uploads_appointment_id ON file_uploads(appointment_id);

-- Indexes for doctor_ratings (rating/feedback table)
CREATE INDEX IF NOT EXISTS idx_doctor_ratings_doctor_id ON doctor_ratings(doctor_id);
CREATE INDEX IF NOT EXISTS idx_doctor_ratings_appointment_id ON doctor_ratings(appointment_id);
