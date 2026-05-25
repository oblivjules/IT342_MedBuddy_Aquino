ALTER TABLE medical_record_files
    ADD COLUMN appointment_id BIGINT REFERENCES appointments(id) ON DELETE SET NULL;

CREATE INDEX idx_medical_record_files_appointment_id
    ON medical_record_files(appointment_id);
