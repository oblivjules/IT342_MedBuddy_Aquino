package com.medbuddy.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.SpecializationDto;
import com.medbuddy.repository.SpecializationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecializationService {

    private final SpecializationRepository specializationRepository;

    @Transactional(readOnly = true)
    public List<SpecializationDto> getAll() {
        return specializationRepository.findAllByOrderByNameAsc().stream()
                .map(s -> SpecializationDto.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .build())
                .collect(Collectors.toList());
    }
}

