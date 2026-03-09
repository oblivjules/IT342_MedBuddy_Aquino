package com.medbuddy.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "doctor_availability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorAvailability {

    @EmbeddedId
    private DoctorAvailabilityId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("doctorId")
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /** Optional availability status — defaults to AVAILABLE. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private AvailabilityStatus status = AvailabilityStatus.AVAILABLE;
}
