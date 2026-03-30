package com.medbuddy.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.medbuddy.model.Specialization;
import com.medbuddy.repository.SpecializationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seeds the {@code specializations} table when empty so clients (web/mobile) can load IDs.
 */
@Component
@RequiredArgsConstructor
public class SpecializationDataLoader implements CommandLineRunner {

    private final SpecializationRepository specializationRepository;

    private static final List<String> DEFAULT_NAMES = List.of(
            "Cardiology",
            "Dermatology",
            "Endocrinology",
            "Gastroenterology",
            "General Practice",
            "Neurology",
            "Obstetrics & Gynecology",
            "Oncology",
            "Ophthalmology",
            "Orthopedics",
            "Otolaryngology (ENT)",
            "Pediatrics",
            "Psychiatry",
            "Pulmonology",
            "Urology");

    @Override
    public void run(String... args) {
        if (specializationRepository.count() > 0) {
            return;
        }
        for (String name : DEFAULT_NAMES) {
            if (!specializationRepository.existsByNameIgnoreCase(name)) {
                specializationRepository.save(
                        Specialization.builder().name(name).build());
            }
        }
    }
}
