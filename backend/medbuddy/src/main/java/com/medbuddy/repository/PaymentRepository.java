package com.medbuddy.repository;

import com.medbuddy.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByAppointment_Id(Long appointmentId);

    Optional<Payment> findByPaymongoSessionId(String paymongoSessionId);

    boolean existsByAppointment_Id(Long appointmentId);
}
