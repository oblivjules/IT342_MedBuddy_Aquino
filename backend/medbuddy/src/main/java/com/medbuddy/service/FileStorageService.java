package com.medbuddy.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StorageUploadResult store(MultipartFile file, String folder);

    void delete(String storagePath);

    default String createSignedUrl(String storageReference) {
        return createSignedUrl(storageReference, 3600);
    }

    String createSignedUrl(String storageReference, int expiresInSeconds);
}

