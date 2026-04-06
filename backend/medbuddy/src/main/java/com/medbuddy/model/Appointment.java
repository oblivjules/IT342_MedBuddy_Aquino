package com.medbuddy.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The patient profile who booked this appointment. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** The doctor profile this appointment is with. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private AppointmentSlot slot;

    // Legacy columns kept for compatibility with existing DB constraints.
    @Column(name = "date", nullable = false)
    private LocalDate date;

    // Legacy columns kept for compatibility with existing DB constraints.
    @Column(name = "time", nullable = false)
    private LocalTime time;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    @SuppressWarnings("unused")
    private void syncLegacyDateTimeColumns() {
        if (slot != null) {
            if (date == null) {
                date = slot.getSlotDate();
            }
            if (time == null) {
                time = slot.getSlotStartTime();
            }
            if (dateTime == null) {
                dateTime = LocalDateTime.of(slot.getSlotDate(), slot.getSlotStartTime());
            }
            return;
        }

        if (dateTime != null) {
            if (date == null) {
                date = dateTime.toLocalDate();
            }
            if (time == null) {
                time = dateTime.toLocalTime();
            }
        }
    }
}
