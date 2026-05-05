ALTER TABLE medical_records
    ADD COLUMN medicine_name VARCHAR(255),
    ADD COLUMN dosage VARCHAR(255),
    ADD COLUMN route VARCHAR(255),
    ADD COLUMN frequency VARCHAR(255),
    ADD COLUMN duration VARCHAR(255),
    ADD COLUMN prescription_notes VARCHAR(500);