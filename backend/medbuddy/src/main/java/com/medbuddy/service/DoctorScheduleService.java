package com.medbuddy.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.DoctorAvailabilityRequest;
import com.medbuddy.dto.TemplateRequest;
import com.medbuddy.event.TemplateChangedEvent;
import com.medbuddy.model.AvailabilityStatus;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.DoctorAvailability;
import com.medbuddy.model.DoctorScheduleTemplate;
import com.medbuddy.repository.DoctorAvailabilityRepository;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.DoctorScheduleTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private static final LocalTime UNAVAILABLE_DEFAULT_START = LocalTime.MIDNIGHT;
    private static final LocalTime UNAVAILABLE_DEFAULT_END = LocalTime.of(0, 30);

    private final DoctorScheduleTemplateRepository templateRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;
    private final SlotGenerationJob slotGenerationJob;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<TemplateRequest> getTemplates(Long doctorId) {
        return templateRepository.findByDoctorIdAndIsActiveTrue(doctorId)
                .stream()
                .map(template -> TemplateRequest.builder()
                        .dayOfWeek(template.getDayOfWeek())
                        .startTime(template.getStartTime())
                        .endTime(template.getEndTime())
                        .build())
                .toList();
    }

    @Transactional
    public void saveTemplate(Long doctorId, List<TemplateRequest> days) {
        Map<Integer, DoctorScheduleTemplate> existingByDay = templateRepository.findByDoctorId(doctorId)
                .stream()
                .collect(Collectors.toMap(DoctorScheduleTemplate::getDayOfWeek, Function.identity(), (left, right) -> left));

        for (DoctorScheduleTemplate existingTemplate : existingByDay.values()) {
            existingTemplate.setActive(false);
        }

        for (TemplateRequest day : days) {
            DoctorScheduleTemplate template = existingByDay.get(day.getDayOfWeek());
            if (template == null) {
                template = DoctorScheduleTemplate.builder()
                        .doctorId(doctorId)
                        .dayOfWeek(day.getDayOfWeek())
                        .build();
            }

            template.setStartTime(day.getStartTime());
            template.setEndTime(day.getEndTime());
            template.setActive(true);

            existingByDay.put(day.getDayOfWeek(), template);
        }

        templateRepository.saveAll(existingByDay.values());

        eventPublisher.publishEvent(new TemplateChangedEvent(doctorId));
    }

    @Transactional
    public DoctorAvailability saveException(Long doctorId, DoctorAvailabilityRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found with id: " + doctorId));

        DoctorAvailability availability = availabilityRepository
                .findByDoctor_IdAndAvailableDate(doctorId, request.getAvailableDate())
                .orElseGet(() -> DoctorAvailability.builder()
                        .doctor(doctor)
                        .availableDate(request.getAvailableDate())
                        .build());

        AvailabilityStatus status = request.getStatus() != null
                ? request.getStatus()
                : AvailabilityStatus.AVAILABLE;

        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();

        if (status == AvailabilityStatus.UNAVAILABLE && (startTime == null || endTime == null)) {
            startTime = UNAVAILABLE_DEFAULT_START;
            endTime = UNAVAILABLE_DEFAULT_END;
        }

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required for available exceptions.");
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        availability.setDoctor(doctor);
        availability.setAvailableDate(request.getAvailableDate());
        availability.setStartTime(startTime);
        availability.setEndTime(endTime);
        availability.setStatus(status);

        DoctorAvailability saved = availabilityRepository.saveAndFlush(availability);
        eventPublisher.publishEvent(new TemplateChangedEvent(doctorId));
        return saved;
    }

    @Transactional
    public void deleteException(Long doctorId, LocalDate date) {
        availabilityRepository.findByDoctor_IdAndAvailableDate(doctorId, date)
                .ifPresent(availabilityRepository::delete);
        eventPublisher.publishEvent(new TemplateChangedEvent(doctorId));
    }

    @Async
    @EventListener
    public void onTemplateChanged(TemplateChangedEvent event) {
        slotGenerationJob.generateSlotsForDoctor(event.getDoctorId(), 14);
    }
}
