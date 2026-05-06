package com.medbuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.medbuddy.shared.model.RatingAndFeedback;

import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

@Repository
public interface RatingAndFeedbackRepository extends JpaRepository<RatingAndFeedback, Long> {

    /** All ratings submitted by a specific patient profile. */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user"})
    List<RatingAndFeedback> findByPatient_IdOrderByCreatedAtDesc(Long patientId);

    /** All ratings received by a specific doctor profile. */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user"})
    List<RatingAndFeedback> findByDoctor_IdOrderByCreatedAtDesc(Long doctorId);

    /** Check if feedback already exists for a given appointment. */
    boolean existsByAppointment_Id(Long appointmentId);

    /** Find feedback for a specific appointment. */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user"})
    java.util.Optional<RatingAndFeedback> findByAppointment_Id(Long appointmentId);

    @Query("select avg(r.ratingScore) from RatingAndFeedback r where r.doctor.id = :doctorId")
    Double findAverageRatingByDoctorId(Long doctorId);
}
