package com.medbuddy.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class SupabaseStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final String supabaseUrl;
    private final String supabaseKey;
    private final String bucket;
    private final RestClient restClient;

    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.key}") String supabaseKey,
            @Value("${supabase.bucket}") String bucket) {
        this.supabaseUrl = trimTrailingSlash(supabaseUrl);
        this.supabaseKey = supabaseKey;
        this.bucket = bucket;
        this.restClient = RestClient.create(this.supabaseUrl);
        
        log.info("[SUPABASE][INIT] SupabaseStorageService initialized");
        log.info("[SUPABASE][INIT] supabaseUrl={}", this.supabaseUrl);
        log.info("[SUPABASE][INIT] bucket={}", this.bucket);
        log.info("[SUPABASE][INIT] supabaseKey (first 30 chars)={}", 
                supabaseKey != null && supabaseKey.length() > 30 ? supabaseKey.substring(0, 30) + "..." : "NOT SET");
        log.info("[SUPABASE][INIT] RestClient created successfully");
    }

    @Override
    public StorageUploadResult store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }

        String objectPath = buildObjectPath(folder, file.getOriginalFilename());
        String endpoint = "/storage/v1/object/" + bucket + "/" + objectPath;

        try {
            log.info("[SUPABASE][UPLOAD] start bucket={} objectPath={} size={} bytes", bucket, objectPath, file.getSize());

            HttpStatusCode status = restClient.post()
                    .uri(endpoint)
                    .header("apikey", supabaseKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseKey)
                    .header("x-upsert", "true")
                    .contentType(resolveContentType(file))
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode();

            String publicUrl = buildPublicUrl(objectPath);
            String storagePath = bucket + "/" + objectPath;

            log.info("[SUPABASE][UPLOAD] done status={} storagePath={}", status.value(), storagePath);
            return new StorageUploadResult(publicUrl, storagePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read file bytes for Supabase upload.", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to upload file to Supabase Storage.", ex);
        }
    }

    @Override
    public void delete(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }

        String objectPath = toObjectPath(storagePath);
        String endpoint = "/storage/v1/object/" + bucket + "/" + objectPath;

        try {
            HttpStatusCode status = restClient.delete()
                    .uri(endpoint)
                    .header("apikey", supabaseKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseKey)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode();

            log.info("[SUPABASE][DELETE] done status={} storagePath={}", status.value(), storagePath);
        } catch (RestClientException ex) {
            log.error("[SUPABASE][DELETE] failed for storagePath={}", storagePath, ex);
        }
    }

    @Override
    public String createSignedUrl(String storageReference, int expiresInSeconds) {
        if (!StringUtils.hasText(storageReference)) {
            return null;
        }

        // Normalize the incoming storage reference so callers may pass
        // values with a leading slash, a bucket prefix, or even a full
        // public URL. The signer expects only the object path relative
        // to the bucket (no leading slash, no bucket name).
        String objectPath = normalizeObjectPath(storageReference, bucket);
        if (!StringUtils.hasText(objectPath)) {
            return null;
        }

        String endpoint = "/storage/v1/object/sign/" + bucket + "/" + objectPath;

        try {
            log.debug("[SUPABASE][SIGN] start bucket={} objectPath={} expiresIn={}", bucket, objectPath, expiresInSeconds);

            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .header("apikey", supabaseKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", expiresInSeconds))
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && response.has("signedURL")) {
                String signedUrl = response.get("signedURL").asText();
                // Ensure /storage/v1/ is present in the signed URL path
                if (signedUrl.startsWith("/object/sign/")) {
                    signedUrl = "/storage/v1" + signedUrl;
                }
                String fullSignedUrl = supabaseUrl + signedUrl;
                log.debug("[SUPABASE][SIGN] done objectPath={} fullSignedUrl={}", objectPath, fullSignedUrl);
                return fullSignedUrl;
            }

            log.warn("[SUPABASE][SIGN] failed: 'signedURL' field missing in response. objectPath={}", objectPath);
            return null;
        } catch (RestClientException ex) {
            log.error("[SUPABASE][SIGN] failed: RestClientException for objectPath={}", objectPath, ex);
            return null;
        }
    }

    private String toObjectPath(String storagePath) {
        return storagePath.startsWith(bucket + "/") ? storagePath.substring(bucket.length() + 1) : storagePath;
    }


    /**
     * Normalize an incoming storage reference so it becomes the object path
     * expected by Supabase signing APIs. Handles nulls, leading slashes,
     * optional bucket name prefixes, and full public URL values.
     */
    private String normalizeObjectPath(String rawPath, String bucket) {
        if (rawPath == null || rawPath.isBlank()) return rawPath;
        String path = rawPath.trim();
        // Strip leading slash
        if (path.startsWith("/")) path = path.substring(1);
        // Strip bucket name prefix if present
        if (path.startsWith(bucket + "/")) path = path.substring((bucket + "/").length());
        // Strip full public URL prefix if someone stored the whole URL
        String publicPrefix = supabaseUrl + "/storage/v1/object/public/" + bucket + "/";
        if (path.startsWith(publicPrefix)) path = path.substring(publicPrefix.length());
        return path;
    }



    private String buildObjectPath(String folder, String originalFilename) {
        String cleanedName = cleanFileName(originalFilename);
        String extension = extractExtension(cleanedName);
        String generatedName = UUID.randomUUID() + extension;
        return (folder != null ? folder + "/" : "") + generatedName;
    }

    private String cleanFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "file";
        }
        String normalized = originalFilename.trim().replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String baseName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        String safeName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        return StringUtils.hasText(safeName) ? safeName : "file";
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private MediaType resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM;
    }

    private String buildPublicUrl(String objectPath) {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
