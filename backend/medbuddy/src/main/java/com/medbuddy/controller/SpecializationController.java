package com.medbuddy.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.dto.SpecializationDto;
import com.medbuddy.service.SpecializationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/specializations")
@RequiredArgsConstructor
public class SpecializationController {

    private final SpecializationService specializationService;

    @GetMapping
    public ResponseEntity<List<SpecializationDto>> getAll() {
        return ResponseEntity.ok(specializationService.getAll());
    }
}

