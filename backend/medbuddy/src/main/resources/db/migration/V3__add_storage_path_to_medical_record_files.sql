-- Add storagePath column to medical_record_files table for signed URL generation
-- Previously, only fileUrl was stored, which was a public URL that became invalid when bucket was made private
-- Now we store storagePath to enable signed URL generation for private buckets

ALTER TABLE medical_record_files
ADD COLUMN storage_path varchar(1000);

-- For existing records, we'll extract the storage path from the fileUrl
-- fileUrl format: https://gcvtswpohtmbjfqqvnns.supabase.co/storage/v1/object/public/medbuddy/medical-records/patient-123/uuid.pdf
-- We need to extract: medbuddy/medical-records/patient-123/uuid.pdf

UPDATE medical_record_files
SET storage_path = SUBSTRING(
    file_url, 
    POSITION('/medbuddy/' IN file_url),
    LENGTH(file_url)
)
WHERE storage_path IS NULL AND file_url LIKE '%/medbuddy/%';

-- For any records that couldn't be migrated, set a default (this shouldn't happen, but just in case)
UPDATE medical_record_files
SET storage_path = CONCAT('medical-records/file-', id, '.tmp')
WHERE storage_path IS NULL;

-- Now make the column NOT NULL
ALTER TABLE medical_record_files
ALTER COLUMN storage_path SET NOT NULL;
