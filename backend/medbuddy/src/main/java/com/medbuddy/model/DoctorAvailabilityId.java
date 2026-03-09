package com.medbuddy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite primary key for DoctorAvailability: (doctor_id, available_date).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAvailabilityId implements Serializable {

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "available_date", nullable = false)
    private LocalDate availableDate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoctorAvailabilityId that)) return false;
        return Objects.equals(doctorId, that.doctorId) &&
               Objects.equals(availableDate, that.availableDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(doctorId, availableDate);
    }
}
