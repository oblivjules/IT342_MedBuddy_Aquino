-- V20260409_01__rename_file_uploads_cloudinary_columns.sql
-- Rename legacy Cloudinary columns to provider-agnostic Supabase storage columns.

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'file_uploads'
          AND column_name = 'cloudinary_public_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'file_uploads'
          AND column_name = 'storage_path'
    ) THEN
        ALTER TABLE file_uploads RENAME COLUMN cloudinary_public_id TO storage_path;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'file_uploads'
          AND column_name = 'cloudinary_url'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'file_uploads'
          AND column_name = 'file_url'
    ) THEN
        ALTER TABLE file_uploads RENAME COLUMN cloudinary_url TO file_url;
    END IF;
END $$;

COMMIT;
