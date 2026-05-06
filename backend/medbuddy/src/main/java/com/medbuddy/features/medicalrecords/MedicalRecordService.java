package com.medbuddy.features.medicalrecords;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.features.medicalrecords.MedicalRecordRequest;
import com.medbuddy.features.medicalrecords.MedicalRecordResponse;
import com.medbuddy.features.appointment.AppointmentRepository;
import com.medbuddy.features.payment.PaymentRepository;
import com.medbuddy.repository.UserRepository;
import com.medbuddy.features.medicalrecords.MedicalRecordRepository;
import com.medbuddy.shared.model.Appointment;
import com.medbuddy.shared.model.MedicalRecord;
import com.medbuddy.shared.model.PaymentStatus;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public MedicalRecordResponse create(String doctorEmail, MedicalRecordRequest request) {
        User doctorUser = requireDoctor(doctorEmail);

        if (medicalRecordRepository.existsByAppointment_Id(request.getAppointmentId())) {
            throw new IllegalStateException(
                    "A medical record already exists for appointment id: "
                            + request.getAppointmentId());
        }

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with id: " + request.getAppointmentId()));

        if (!appointment.getDoctor().getUser().getId().equals(doctorUser.getId())) {
            throw new AccessDeniedException("You can only create records for your appointments.");
        }

        MedicalRecord record = MedicalRecord.builder()
                .diagnosis(request.getDiagnosis())
                .prescriptionDetails(request.getPrescriptionDetails())
            .medicineName(request.getMedicineName())
            .dosage(request.getDosage())
            .route(request.getRoute())
            .frequency(request.getFrequency())
            .duration(request.getDuration())
            .prescriptionNotes(request.getPrescriptionNotes())
                .appointment(appointment)
                .build();

        return toResponse(medicalRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public MedicalRecordResponse getByAppointment(String requesterEmail, Long appointmentId) {
        User requester = findUser(requesterEmail);
        // Return null gracefully if no medical record exists yet instead of throwing exception
        return medicalRecordRepository.findByAppointment_Id(appointmentId)
                .map(record -> {
                    enforceCanAccess(requester, record.getAppointment());
                    assertPatientCanViewRecord(requester, record.getAppointment());
                    return toResponse(record);
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> getMyRecords(String requesterEmail) {
        User user = findUser(requesterEmail);

        if (user.getRole() == Role.PATIENT) {
            return medicalRecordRepository.findByAppointment_Patient_User_IdOrderByIdDesc(user.getId())
                    .stream()
                    .filter(record -> isPatientRecordUnlocked(record.getAppointment()))
                    .map(this::toResponse)
                    .toList();
        }

        if (user.getRole() == Role.DOCTOR) {
            return medicalRecordRepository.findByAppointment_Doctor_User_IdOrderByIdDesc(user.getId())
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        throw new AccessDeniedException("You are not allowed to access medical records.");
    }

    @Transactional(readOnly = true)
    public MedicalRecordResponse getById(String requesterEmail, Long id) {
        User requester = findUser(requesterEmail);
        return medicalRecordRepository.findById(id)
                .map(record -> {
                    enforceCanAccess(requester, record.getAppointment());
                    assertPatientCanViewRecord(requester, record.getAppointment());
                    return toResponse(record);
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        "Medical record not found with id: " + id));
    }

    @Transactional
    public MedicalRecordResponse update(String doctorEmail, Long id, MedicalRecordRequest request) {
        User doctorUser = requireDoctor(doctorEmail);

        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Medical record not found with id: " + id));

        if (!record.getAppointment().getDoctor().getUser().getId().equals(doctorUser.getId())) {
            throw new AccessDeniedException("You can only update records for your appointments.");
        }

        record.setDiagnosis(request.getDiagnosis());
        record.setPrescriptionDetails(request.getPrescriptionDetails());
        record.setMedicineName(request.getMedicineName());
        record.setDosage(request.getDosage());
        record.setRoute(request.getRoute());
        record.setFrequency(request.getFrequency());
        record.setDuration(request.getDuration());
        record.setPrescriptionNotes(request.getPrescriptionNotes());
        return toResponse(medicalRecordRepository.save(record));
    }

    @Transactional
    public void delete(String doctorEmail, Long id) {
        User doctorUser = requireDoctor(doctorEmail);
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Medical record not found with id: " + id));
        if (!record.getAppointment().getDoctor().getUser().getId().equals(doctorUser.getId())) {
            throw new AccessDeniedException("You can only delete records for your appointments.");
        }
        medicalRecordRepository.delete(record);
    }

    private User requireDoctor(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));
        if (user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can manage medical records.");
        }
        return user;
    }

    private void enforceCanAccess(User user, Appointment appointment) {
        if (user.getRole() == Role.PATIENT
                && appointment.getPatient().getUser().getId().equals(user.getId())) {
            return;
        }

        if (user.getRole() == Role.DOCTOR
                && appointment.getDoctor().getUser().getId().equals(user.getId())) {
            return;
        }

        throw new AccessDeniedException("You are not allowed to access this medical record.");
    }

    private void assertPatientCanViewRecord(User user, Appointment appointment) {
        if (user.getRole() != Role.PATIENT) {
            return;
        }

        if (!isPatientRecordUnlocked(appointment)) {
            throw new AccessDeniedException("Medical record is locked until the appointment balance is fully paid.");
        }
    }

    private boolean isPatientRecordUnlocked(Appointment appointment) {
        var payment = paymentRepository.findByAppointment_Id(appointment.getId()).orElse(null);
        return payment != null && payment.getPaymentStatus() == PaymentStatus.PAID;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));
    }

    private MedicalRecordResponse toResponse(MedicalRecord r) {
        return MedicalRecordResponse.builder()
                .id(r.getId())
                .appointmentId(r.getAppointment().getId())
                .diagnosis(r.getDiagnosis())
                .prescriptionDetails(r.getPrescriptionDetails())
            .medicineName(r.getMedicineName())
            .dosage(r.getDosage())
            .route(r.getRoute())
            .frequency(r.getFrequency())
            .duration(r.getDuration())
            .prescriptionNotes(r.getPrescriptionNotes())
                .build();
    }
}
