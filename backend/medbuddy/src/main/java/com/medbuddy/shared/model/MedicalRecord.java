package com.medbuddy.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medical_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String diagnosis;

    @Column(length = 2000)
    private String prescriptionDetails;

    @Column(name = "medicine_name", length = 255)
    private String medicineName;

    @Column(length = 255)
    private String dosage;

    @Column(length = 255)
    private String route;

    @Column(length = 255)
    private String frequency;

    @Column(length = 255)
    private String duration;

    @Column(name = "prescription_notes", length = 500)
    private String prescriptionNotes;

    /** One appointment → one medical record (one-to-one). */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;
}
