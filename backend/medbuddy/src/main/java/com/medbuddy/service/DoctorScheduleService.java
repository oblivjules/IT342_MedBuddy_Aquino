package com.medbuddy.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.medbuddy.repository.AppointmentSlotRepository;
import com.medbuddy.repository.DoctorAvailabilityRepository;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.DoctorScheduleTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private static final Logger log = LoggerFactory.getLogger(DoctorScheduleService.class);
    private static final int SLOT_MINUTES = 30;

    private final DoctorScheduleTemplateRepository templateRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
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

        slotGenerationJob.generateSlotsForDoctor(doctorId, 7);

        eventPublisher.publishEvent(new TemplateChangedEvent(doctorId));
    }

    @Transactional
    public DoctorAvailability saveException(Long doctorId, DoctorAvailabilityRequest request) {
        try {
            log.debug("[SERVICE] saveException called - doctorId: {}", doctorId);
            log.debug("[SERVICE] Request details - availableDate: {}, startTime: {}, endTime: {}, status: {}",
                    request.getAvailableDate(), request.getStartTime(), request.getEndTime(), request.getStatus());
            
            Doctor doctor = doctorRepository.findById(doctorId)
                    .orElseThrow(() -> new IllegalArgumentException("Doctor not found with id: " + doctorId));
            log.debug("[SERVICE] Doctor found - id: {}, name: {} {}", 
                    doctor.getId(), doctor.getFirstName(), doctor.getLastName());

            DoctorAvailability availability = availabilityRepository
                    .findByDoctor_IdAndAvailableDate(doctorId, request.getAvailableDate())
                    .orElseGet(() -> {
                        log.debug("[SERVICE] No existing availability found, creating new entity");
                        return DoctorAvailability.builder()
                                .doctor(doctor)
                                .availableDate(request.getAvailableDate())
                                .build();
                    });

            log.debug("[SERVICE] Existing availability found: {}", availability.getId() != null);

            AvailabilityStatus status = request.getStatus() != null
                    ? request.getStatus()
                    : AvailabilityStatus.AVAILABLE;
            log.debug("[SERVICE] Resolved status: {}", status);

            availability.setDoctor(doctor);
            availability.setAvailableDate(request.getAvailableDate());
            availability.setStartTime(request.getStartTime());
            availability.setEndTime(request.getEndTime());
            availability.setStatus(status);

            log.debug("[SERVICE] Entity prepared before save - id: {}, date: {}, startTime: {}, endTime: {}, status: {}",
                    availability.getId(), availability.getAvailableDate(), availability.getStartTime(), 
                    availability.getEndTime(), availability.getStatus());
            log.info("[SERVICE] Custom Hours - date: {}, startTime: {}, endTime: {} (for status: {})",
                    request.getAvailableDate(), request.getStartTime(), request.getEndTime(), status);

            DoctorAvailability saved = availabilityRepository.saveAndFlush(availability);
            log.debug("[SERVICE] Entity saved successfully - id: {}, date: {}, status: {}", 
                    saved.getId(), saved.getAvailableDate(), saved.getStatus());
            log.info("[SERVICE] Custom Hours persisted - entity id: {}, date: {}, startTime: {}, endTime: {}, status: {}",
                    saved.getId(), saved.getAvailableDate(), saved.getStartTime(), saved.getEndTime(), saved.getStatus());
            
            // Bug Fix #1: Handle appointment slots for the exception date
            // Delete existing available slots for this date, then generate new ones based on the exception
            log.debug("[SERVICE] Managing appointment slots for exception date: {}", request.getAvailableDate());
            appointmentSlotRepository.deleteUnbookedSlotsForDoctorAndDate(doctorId, request.getAvailableDate());
            log.debug("[SERVICE] Deleted unbooked slots for doctorId: {} on date: {}", doctorId, request.getAvailableDate());

            // Generate new slots based on exception status
            if (status == AvailabilityStatus.UNAVAILABLE) {
                log.debug("[SERVICE] Exception is Day Off - no slots generated for date: {}", request.getAvailableDate());
            } else if (status == AvailabilityStatus.AVAILABLE) {
                // Bug Fix #2 & Log Persistence: Use the custom start/end times from the request
                log.debug("[SERVICE] Exception is Custom Hours - generating slots from {} to {} on {}",
                        request.getStartTime(), request.getEndTime(), request.getAvailableDate());
                generateSlotsForException(doctorId, request.getAvailableDate(), request.getStartTime(), request.getEndTime());
            }

            // Bug Fix #3: Do NOT publish TemplateChangedEvent here
            // This event should only be published when the weekly template changes, not for exception saves
            // The TemplateChangedEvent listener will NOT be triggered, preventing unnecessary slot regeneration
            
            return saved;
        } catch (IllegalArgumentException e) {
            log.error("[SERVICE] Doctor not found - doctorId: {}", doctorId, e);
            throw e;
        } catch (Exception e) {
            log.error("[SERVICE] Exception occurred while saving exception - doctorId: {}", doctorId, e);
            throw e;
        }
    }

    /**
     * Generate appointment slots for an exception date with custom hours.
     * Used only for exceptions, not for template-based slots.
     */
    private void generateSlotsForException(Long doctorId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            log.warn("[SERVICE] Invalid time range for exception slots - doctorId: {}, date: {}, start: {}, end: {}",
                    doctorId, date, startTime, endTime);
            return;
        }

        LocalTime pointer = startTime;
        LocalTime latestStart = endTime.minusMinutes(SLOT_MINUTES);
        int slotsGenerated = 0;

        while (!pointer.isAfter(latestStart)) {
            LocalTime slotEnd = pointer.plusMinutes(SLOT_MINUTES);
            appointmentSlotRepository.insertIfNotExists(doctorId, date, pointer, slotEnd);
            slotsGenerated++;
            pointer = slotEnd;
        }

        log.debug("[SERVICE] Generated {} appointment slots for exception - doctorId: {}, date: {}, range: {} to {}",
                slotsGenerated, doctorId, date, startTime, endTime);
    }

    @Transactional
    public void deleteException(Long doctorId, LocalDate date) {
        try {
            log.debug("[SERVICE] deleteException called - doctorId: {}, date: {}", doctorId, date);
            
            availabilityRepository.findByDoctor_IdAndAvailableDate(doctorId, date)
                    .ifPresentOrElse(
                        availability -> {
                            log.debug("[SERVICE] Found availability to delete - id: {}, status: {}", 
                                    availability.getId(), availability.getStatus());
                            availabilityRepository.delete(availability);
                            log.debug("[SERVICE] Availability deleted successfully");
                            
                            // When an exception is deleted, delete its associated slots
                            // The weekly template slots will be regenerated on next template save or scheduled job
                            log.debug("[SERVICE] Deleting appointment slots for removed exception - doctorId: {}, date: {}", 
                                    doctorId, date);
                            appointmentSlotRepository.deleteUnbookedSlotsForDoctorAndDate(doctorId, date);
                            log.debug("[SERVICE] Deleted unbooked slots for removed exception");
                        },
                        () -> log.debug("[SERVICE] No availability found for doctorId: {} on date: {}", doctorId, date)
                    );
            
            // Do NOT publish TemplateChangedEvent - only template saves should trigger slot regeneration
        } catch (Exception e) {
            log.error("[SERVICE] Exception occurred while deleting exception - doctorId: {}, date: {}", doctorId, date, e);
            throw e;
        }
    }

    @Async
    @EventListener
    public void onTemplateChanged(TemplateChangedEvent event) {
        slotGenerationJob.generateSlotsForDoctor(event.getDoctorId(), 14);
    }
}
