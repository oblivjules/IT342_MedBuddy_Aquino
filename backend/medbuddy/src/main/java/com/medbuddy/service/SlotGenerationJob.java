package com.medbuddy.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.medbuddy.model.AvailabilityStatus;
import com.medbuddy.model.DoctorAvailability;
import com.medbuddy.model.DoctorScheduleTemplate;
import com.medbuddy.repository.AppointmentSlotRepository;
import com.medbuddy.repository.DoctorAvailabilityRepository;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.DoctorScheduleTemplateRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SlotGenerationJob {

    private static final int SLOT_MINUTES = 30;

    private final DoctorScheduleTemplateRepository templateRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final DoctorRepository doctorRepository;

    @Scheduled(cron = "0 0 1 * * *")
    public void generateSlotsForAllDoctors() {
        List<Long> doctorIds = doctorRepository.findAllIds();
        for (Long doctorId : doctorIds) {
            generateSlotsForDoctor(doctorId, 14);
        }
    }

    public void generateSlotsForDoctor(Long doctorId, int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(daysAhead);

        List<DoctorScheduleTemplate> templates = templateRepository.findByDoctorIdAndIsActiveTrue(doctorId);
        Map<Integer, DoctorScheduleTemplate> templatesByDay = templates.stream()
                .collect(Collectors.toMap(DoctorScheduleTemplate::getDayOfWeek, Function.identity(), (left, right) -> left));

        Map<LocalDate, DoctorAvailability> exceptionsByDate = availabilityRepository
                .findByDoctor_IdAndAvailableDateBetweenOrderByAvailableDateAsc(doctorId, today, endDate)
                .stream()
                .collect(Collectors.toMap(DoctorAvailability::getAvailableDate, Function.identity(), (left, right) -> right));

        LocalDate date = today;
        while (!date.isAfter(endDate)) {
            DoctorAvailability exception = exceptionsByDate.get(date);
            if (exception != null && exception.getStatus() == AvailabilityStatus.UNAVAILABLE) {
                appointmentSlotRepository.deleteUnbookedSlotsForDoctorAndDate(doctorId, date);
                date = date.plusDays(1);
                continue;
            }

            LocalTime start;
            LocalTime end;

            if (exception != null && exception.getStatus() == AvailabilityStatus.AVAILABLE) {
                start = exception.getStartTime();
                end = exception.getEndTime();
            } else {
                int dayIndex = date.getDayOfWeek().getValue() - 1;
                DoctorScheduleTemplate template = templatesByDay.get(dayIndex);
                if (template == null || !template.isActive()) {
                    date = date.plusDays(1);
                    continue;
                }
                start = template.getStartTime();
                end = template.getEndTime();
            }

            generateSlots(doctorId, date, start, end);
            date = date.plusDays(1);
        }
    }

    private void generateSlots(Long doctorId, LocalDate date, LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            return;
        }

        LocalTime pointer = start;
        LocalTime latestStart = end.minusMinutes(SLOT_MINUTES);

        while (!pointer.isAfter(latestStart)) {
            LocalTime slotEnd = pointer.plusMinutes(SLOT_MINUTES);
            appointmentSlotRepository.insertIfNotExists(doctorId, date, pointer, slotEnd);
            pointer = slotEnd;
        }
    }
}
