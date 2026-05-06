package com.medbuddy.features.prescription;

import com.medbuddy.features.prescription.DrugInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Lightweight service that extracts a primary drug token and delegates
 * the remote lookup to a cache-backed component. This keeps the existing
 * behavior but enables caching without changing how callers invoke this service.
 */
@Service
@RequiredArgsConstructor
public class DrugInfoService {

    private final DrugInfoCacheService cacheService;

    public DrugInfoDto getDrugInfo(String medicineName) {
        if (medicineName == null || medicineName.isBlank()) {
            return null;
        }

        return cacheService.lookupByDrugName(medicineName.trim());
    }
}
