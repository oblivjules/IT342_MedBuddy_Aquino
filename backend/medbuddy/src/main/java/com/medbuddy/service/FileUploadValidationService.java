package com.medbuddy.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileUploadValidationService {

    public static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png");
    public static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    public static final Set<String> ALLOWED_DOCUMENT_MIME_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    public static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");

    public ValidatedFileMetadata validate(MultipartFile file, Set<String> allowedMimeTypes, Set<String> allowedExtensions) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (extension == null) {
            throw new IllegalArgumentException("File extension is required.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read uploaded file.", ex);
        }

        String mimeType = detectMimeType(content);
        if (mimeType == null) {
            mimeType = normalizeContentType(file.getContentType());
        }
        if (mimeType == null) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed MIME types: image/jpeg, image/png, application/pdf. Allowed extensions: jpg, jpeg, png, pdf.");
        }

        if (!allowedMimeTypes.contains(mimeType)) {
            throw new IllegalArgumentException(
                    "Unsupported file MIME type: " + mimeType + ". Allowed MIME types: " + String.join(", ", allowedMimeTypes));
        }

        if (!allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported file extension: " + extension + ". Allowed extensions: " + String.join(", ", allowedExtensions));
        }

        if (!mimeTypeMatchesExtension(mimeType, extension)) {
            throw new IllegalArgumentException(
                    "File content does not match its extension. The detected MIME type is " + mimeType
                            + " and the file extension is " + extension + ".");
        }

        return new ValidatedFileMetadata(mimeType, extension);
    }

    private String extractExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }

        String cleanedName = StringUtils.cleanPath(originalFilename);
        int dotIndex = cleanedName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == cleanedName.length() - 1) {
            return null;
        }

        return cleanedName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String detectMimeType(byte[] content) {
        if (content.length >= 8
                && content[0] == (byte) 0x89
                && content[1] == 0x50
                && content[2] == 0x4E
                && content[3] == 0x47
                && content[4] == 0x0D
                && content[5] == 0x0A
                && content[6] == 0x1A
                && content[7] == 0x0A) {
            return "image/png";
        }

        if (content.length >= 3
                && content[0] == (byte) 0xFF
                && content[1] == (byte) 0xD8
                && content[2] == (byte) 0xFF) {
            return "image/jpeg";
        }

        if (containsPdfHeader(content)) {
            return "application/pdf";
        }

        return null;
    }

    private boolean mimeTypeMatchesExtension(String mimeType, String extension) {
        return switch (mimeType) {
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            case "application/pdf" -> extension.equals("pdf");
            default -> false;
        };
    }

    private boolean containsPdfHeader(byte[] content) {
        int maxOffset = Math.min(content.length - 5, 1024);
        for (int i = 0; i <= maxOffset; i++) {
            if (content[i] == 0x25
                    && content[i + 1] == 0x50
                    && content[i + 2] == 0x44
                    && content[i + 3] == 0x46
                    && content[i + 4] == 0x2D) {
                return true;
            }
        }
        return false;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }

        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "image/jpeg", "image/jpg", "image/pjpeg" -> "image/jpeg";
            case "image/png", "image/x-png" -> "image/png";
            case "application/pdf", "application/x-pdf", "application/acrobat", "applications/vnd.pdf", "text/pdf" -> "application/pdf";
            default -> null;
        };
    }

    public record ValidatedFileMetadata(String mimeType, String extension) {
    }
}