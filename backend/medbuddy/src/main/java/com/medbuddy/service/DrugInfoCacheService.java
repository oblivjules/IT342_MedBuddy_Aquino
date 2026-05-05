package com.medbuddy.service;

import com.medbuddy.dto.DrugInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Small wrapper service that performs OpenFDA lookup and is cacheable.
 * Kept separate so that Spring's proxy-based caching works reliably.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DrugInfoCacheService {

    private static final String OPENFDA_API_URL = "https://api.fda.gov/drug/label.json";
    private final RestTemplate restTemplate;

    @Cacheable(value = "drugInfo", key = "#drugName.toLowerCase().trim()", unless = "#result == null")
    public DrugInfoDto lookupByDrugName(String drugName) {
        if (drugName == null || drugName.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> response = lookupOpenFda("openfda.brand_name", drugName);
            if (response == null) {
                response = lookupOpenFda("openfda.generic_name", drugName);
            }

            if (response == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                return null;
            }

            Map<String, Object> drugData = results.get(0);
            return mapToDrugInfoDto(drugData);
        } catch (RestClientException e) {
            log.warn("OpenFDA lookup failed for drug '{}': {}", drugName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Unexpected drug-info lookup failure for '{}': {}", drugName, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lookupOpenFda(String field, String drugName) {
        try {
            String url = UriComponentsBuilder.fromUriString(OPENFDA_API_URL)
                    .queryParam("search", field + ":" + drugName)
                    .queryParam("limit", 1)
                    .build()
                    .encode()
                    .toUriString();

            return (Map<String, Object>) restTemplate.getForObject(url, Map.class);
        } catch (RestClientException e) {
            log.warn("OpenFDA lookup failed for {} '{}': {}", field, drugName, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private DrugInfoDto mapToDrugInfoDto(Map<String, Object> drugData) {
        DrugInfoDto.DrugInfoDtoBuilder builder = DrugInfoDto.builder();
        Object indications = drugData.get("indications_and_usage");
        if (indications instanceof List && !((List<?>) indications).isEmpty()) {
            builder.indicationsAndUsage(((List<String>) indications).get(0));
        }
        Object warnings = drugData.get("warnings");
        if (warnings instanceof List && !((List<?>) warnings).isEmpty()) {
            builder.warnings(((List<String>) warnings).get(0));
        }
        Object dosage = drugData.get("dosage_and_administration");
        if (dosage instanceof List && !((List<?>) dosage).isEmpty()) {
            builder.dosageAndAdministration(((List<String>) dosage).get(0));
        }
        Object description = drugData.get("description");
        if (description instanceof List && !((List<?>) description).isEmpty()) {
            builder.description(((List<String>) description).get(0));
        }
        return builder.build();
    }
}
