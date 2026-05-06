package com.medbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    private static final DateTimeFormatter DATE_ONLY_FMT =
            DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    private static final DateTimeFormatter TIME_ONLY_FMT =
            DateTimeFormatter.ofPattern("hh:mm a");

    @PostConstruct
    void logMailConfigStatus() {
        boolean hasUsername = smtpUsername != null && !smtpUsername.isBlank();
        boolean hasPassword = smtpPassword != null && !smtpPassword.isBlank();
        boolean hasFrom = fromAddress != null && !fromAddress.isBlank();

        log.info("Email config status | usernameSet={} passwordSet={} fromSet={}",
                hasUsername, hasPassword, hasFrom);
    }

    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String to, String firstName) {
        String subject = "Welcome to MedBuddy 🩺";
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#d95a8b;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;">MedBuddy</h2>
                                        <p style="margin:6px 0 0 0;font-size:14px;line-height:20px;color:#ffffff;">Your health, simplified</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <h3 style="margin:0 0 10px 0;font-size:20px;line-height:28px;color:#111827;">Welcome, {{FIRST_NAME}}!</h3>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Your account is now ready. You can start booking appointments with ease.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:20px auto 24px auto;">
                                            <tr>
                                                <td align="center" bgcolor="#d95a8b" style="border-radius:8px;">
                                                    <a href="https://medbuddy.app/dashboard" style="display:inline-block;padding:12px 20px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">Go to Dashboard</a>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0;font-size:14px;line-height:22px;color:#4b5563;">If you need help, we're always here for you.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{FIRST_NAME}}", firstName);
        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendDoctorWelcomeEmail(String to, String firstName) {
        String subject = "Welcome to MedBuddy 🩺";
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#c2587a;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">MedBuddy</h2>
                                        <p style="margin:6px 0 0 0;font-size:14px;line-height:20px;color:#ffffff;">Your health, simplified</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <h3 style="margin:0 0 10px 0;font-size:20px;line-height:28px;color:#111827;">Welcome, Dr. {{FIRST_NAME}}!</h3>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Your doctor account is now ready. You can review and manage incoming patient appointments.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:20px auto 24px auto;">
                                            <tr>
                                                <td align="center" bgcolor="#c2587a" style="border-radius:8px;">
                                                    <a href="https://medbuddy.app/doctor/dashboard" style="display:inline-block;padding:12px 20px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">Go to Doctor Dashboard</a>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0;font-size:14px;line-height:22px;color:#4b5563;">Thank you for joining MedBuddy and supporting patient care.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{FIRST_NAME}}", firstName);
        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendAppointmentConfirmationEmail(String to,
                                                 String patientName,
                                                 String doctorName,
                                                 LocalDateTime dateTime) {
        String subject = "Appointment Booked ✨";
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#d95a8b;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Appointment Requested</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Your appointment with <strong>Dr. {{DOCTOR_NAME}}</strong> has been requested.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#dbeafe;border-radius:8px;font-size:14px;line-height:22px;color:#1e3a5f;">
                                                    <strong>Date:</strong><br/>
                                                    {{DATE_TIME}}
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Status: <strong style="color:#d97706;">Pending Approval</strong></p>
                                        <p style="margin:0;font-size:14px;line-height:22px;color:#4b5563;">You will be notified once the doctor confirms your schedule.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{DATE_TIME}}", dateTime.format(DATE_FMT));
        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendAppointmentApprovedEmail(String to,
                                             String patientName,
                                             String doctorName,
                                             LocalDateTime dateTime) {
        String subject = "Appointment Confirmed ✅";
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#d95a8b;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Appointment Confirmed</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Your appointment with <strong>Dr. {{DOCTOR_NAME}}</strong> is confirmed.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#dbeafe;border-radius:8px;font-size:14px;line-height:22px;color:#1e3a5f;">
                                                    <strong>Schedule:</strong><br/>
                                                    {{DATE_TIME}}
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Status: <strong style="color:#059669;">Approved</strong></p>
                                        <p style="margin:0 0 20px 0;font-size:14px;line-height:22px;color:#4b5563;">Please arrive a few minutes early for check-in.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:0 auto;">
                                            <tr>
                                                <td align="center" bgcolor="#2e86c1" style="border-radius:8px;">
                                                    <a href="https://medbuddy.app/appointments" style="display:inline-block;padding:12px 20px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">View Appointment</a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{DATE_TIME}}", dateTime.format(DATE_FMT));
        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendAppointmentReminderEmail(String to,
                                             String patientName,
                                             String doctorName,
                                             LocalDateTime dateTime) {
        String subject = "Upcoming Appointment Reminder ⏰";
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#d95a8b;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Reminder &#x23F0;</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">This is a reminder for your upcoming appointment:</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#dbeafe;border-radius:8px;font-size:14px;line-height:22px;color:#1e3a5f;">
                                                    <strong>Dr. {{DOCTOR_NAME}}</strong><br/>
                                                    {{DATE_TIME}}
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0;font-size:14px;line-height:22px;color:#4b5563;">Please do not forget to attend. We look forward to seeing you!</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{DATE_TIME}}", dateTime.format(DATE_FMT));
        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendDoctorAppointmentReminderEmail(String to,
                                                   String doctorName,
                                                   String patientName,
                                                   LocalDateTime dateTime) {
        String subject = "Upcoming Appointment Reminder ⏰";
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#c2587a;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Appointment Reminder &#x23F0;</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi Dr. {{DOCTOR_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">You have an upcoming appointment:</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#f3dbe7;border-radius:8px;font-size:14px;line-height:22px;color:#5f3a52;">
                                                    <strong>Patient:</strong> {{PATIENT_NAME}}<br/>
                                                    <strong>Time:</strong> {{DATE_TIME}}
                                                </td>
                                            </tr>
                                        </table>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:0 auto;">
                                            <tr>
                                                <td align="center" bgcolor="#c2587a" style="border-radius:8px;">
                                                    <a href="https://medbuddy.app/doctor/appointments" style="display:inline-block;padding:12px 20px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">View Appointments</a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DATE_TIME}}", dateTime.format(DATE_FMT));
        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendDoctorFeedbackAlertEmail(String to,
                                             String doctorName,
                                             String patientName,
                                             Integer rating,
                                             String comment) {
        String subject = "New Patient Feedback ⭐";
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#c2587a;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">New Patient Review ⭐</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi Dr. {{DOCTOR_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">You have received new feedback from {{PATIENT_NAME}}:</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#f3dbe7;border-radius:8px;font-size:14px;line-height:22px;color:#5f3a52;">
                                                    <strong>Rating:</strong> {{RATING}} / 5 ⭐<br/>
                                                    <strong style="display:block;margin-top:8px;">Comment:</strong>
                                                    <p style="margin:4px 0 0 0;font-style:italic;">{{COMMENT}}</p>
                                                </td>
                                            </tr>
                                        </table>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:0 auto;">
                                            <tr>
                                                <td align="center" bgcolor="#c2587a" style="border-radius:8px;">
                                                    <a href="https://medbuddy.app/doctor/dashboard" style="display:inline-block;padding:12px 20px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">View Dashboard</a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{RATING}}", rating != null ? rating.toString() : "N/A")
                    .replace("{{COMMENT}}", comment != null && !comment.isBlank() ? comment : "No written comment provided.");
        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendAppointmentCancelledEmail(String to,
                                              String patientName,
                                              String doctorName,
                                              LocalDateTime dateTime,
                                              String cancellationReason) {
        String subject = "Appointment Cancelled";
        String reason = (cancellationReason != null && !cancellationReason.isBlank())
                ? cancellationReason
                : "No reason provided.";
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#dc2626;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Appointment Cancelled</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Your appointment with <strong>Dr. {{DOCTOR_NAME}}</strong> has been cancelled.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#fee2e2;border-radius:8px;font-size:14px;line-height:22px;color:#7f1d1d;">
                                                    <strong>Schedule:</strong><br/>
                                                    {{DATE_TIME}}<br/>
                                                    <strong style="display:block;margin-top:10px;">Reason:</strong>
                                                    {{CANCELLATION_REASON}}
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0;font-size:14px;line-height:22px;color:#4b5563;">You can check your appointments page for the latest updates.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{DATE_TIME}}", dateTime != null ? dateTime.format(DATE_FMT) : "N/A")
                    .replace("{{CANCELLATION_REASON}}", reason);

        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendPaymentReceipt(String to,
                                   String patientName,
                                   String doctorName,
                                   java.time.LocalDateTime appointmentDateTime,
                                   java.math.BigDecimal feeAmount,
                                   String transactionId,
                                   java.time.LocalDateTime paidAt) {
        String subject = "✅ Payment Receipt — MedBuddy";
        String body = "<div style=\"font-family:Arial,sans-serif;color:#111827;\">"
                + "<h2 style=\"color:#111827;\">Payment Receipt</h2>"
                + "<p>Hi " + patientName + ",</p>"
                + "<p>Thank you for your payment. Here are the details:</p>"
                + "<ul>"
                + "<li><strong>Doctor:</strong> " + doctorName + "</li>"
                + "<li><strong>Appointment:</strong> " + (appointmentDateTime != null ? appointmentDateTime.format(DATE_FMT) : "N/A") + "</li>"
                + "<li><strong>Amount:</strong> PHP " + feeAmount + "</li>"
                + "<li><strong>Transaction ID:</strong> " + (transactionId != null ? transactionId : "N/A") + "</li>"
                + "<li><strong>Paid At:</strong> " + (paidAt != null ? paidAt.format(DATE_FMT) : "N/A") + "</li>"
                + "</ul>"
                + "<p>If you have questions, contact support.</p>"
                + "</div>";
        send(to, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendAppointmentReminderEmail(String toEmail,
                                             String patientName,
                                             String doctorName,
                                             String specialization,
                                             LocalDateTime appointmentDateTime,
                                             String clinicAddress) {
        String subject = "Reminder: Appointment Tomorrow ⏰";
        String dateStr = appointmentDateTime.format(DATE_ONLY_FMT);
        String timeStr = appointmentDateTime.format(TIME_ONLY_FMT);
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#E91E8C;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Appointment Reminder ⏰</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">This is a friendly reminder that you have an appointment scheduled for <strong>tomorrow</strong>:</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#fbe7f2;border-radius:8px;font-size:14px;line-height:24px;color:#5f3a52;">
                                                    <strong>Doctor:</strong> Dr. {{DOCTOR_NAME}}<br/>
                                                    <strong>Specialization:</strong> {{SPECIALIZATION}}<br/>
                                                    <strong>Date:</strong> {{DATE}}<br/>
                                                    <strong>Time:</strong> {{TIME}}<br/>
                                                    <strong>Clinic Address:</strong> {{CLINIC_ADDRESS}}
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0 0 16px 0;font-size:14px;line-height:22px;color:#4b5563;background-color:#fef3c7;padding:12px;border-radius:6px;border-left:4px solid #f59e0b;">
                                            <strong>Note:</strong> Please arrive 10–15 minutes early for check-in.
                                        </p>
                                        <p style="margin:0;font-size:14px;line-height:22px;color:#4b5563;">We look forward to seeing you!</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{SPECIALIZATION}}", specialization != null ? specialization : "General")
                    .replace("{{DATE}}", dateStr)
                    .replace("{{TIME}}", timeStr)
                    .replace("{{CLINIC_ADDRESS}}", clinicAddress != null ? clinicAddress : "To be announced");
        send(toEmail, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendAppointmentCompletedEmail(String toEmail,
                                              String patientName,
                                              String doctorName,
                                              LocalDate completedDate) {
        String subject = "Appointment Completed ✓";
        String dateStr = completedDate.format(DATE_ONLY_FMT);
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#E91E8C;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Appointment Completed ✓</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Your appointment with <strong>Dr. {{DOCTOR_NAME}}</strong> on <strong>{{DATE}}</strong> has been marked as completed.</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Thank you for trusting MedBuddy for your healthcare needs. We hope you received the care and attention you deserved.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#dbeafe;border-radius:8px;font-size:14px;line-height:22px;color:#1e3a5f;">
                                                    <strong>📄 Medical Records:</strong><br/>
                                                    Your medical records from this visit are now available. You can view them anytime in the MedBuddy patient portal.
                                                </td>
                                            </tr>
                                        </table>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:0 auto;">
                                            <tr>
                                                <td align="center" bgcolor="#E91E8C" style="border-radius:8px;">
                                                    <a href="https://medbuddy.app/records" style="display:inline-block;padding:12px 20px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">View Medical Records</a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{DATE}}", dateStr);
        send(toEmail, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendReservationPaymentReminderEmail(String toEmail,
                                                    String patientName,
                                                    String doctorName,
                                                    LocalDateTime appointmentDateTime,
                                                    String paymentUrl,
                                                    BigDecimal amount) {
        String subject = "Payment Required to Confirm Your Appointment";
        String dateStr = appointmentDateTime.format(DATE_ONLY_FMT);
        String timeStr = appointmentDateTime.format(TIME_ONLY_FMT);
        String amountStr = "₱" + (amount != null ? amount.toPlainString() : "0.00");
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#E91E8C;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Reservation Payment Reminder</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">You have a pending reservation fee for your upcoming appointment:</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#fbe7f2;border-radius:8px;font-size:14px;line-height:24px;color:#5f3a52;">
                                                    <strong>Doctor:</strong> Dr. {{DOCTOR_NAME}}<br/>
                                                    <strong>Date:</strong> {{DATE}}<br/>
                                                    <strong>Time:</strong> {{TIME}}<br/>
                                                    <strong>Amount Due:</strong> <span style="font-size:16px;font-weight:700;color:#E91E8C;">{{AMOUNT}}</span>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0 0 16px 0;font-size:14px;line-height:22px;color:#4b5563;background-color:#fef3c7;padding:12px;border-radius:6px;border-left:4px solid #f59e0b;">
                                            <strong>Important:</strong> Your appointment slot will not be held without payment. Please settle the reservation fee to confirm your booking.
                                        </p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:0 auto;">
                                            <tr>
                                                <td align="center" bgcolor="#E91E8C" style="border-radius:8px;">
                                                    <a href="{{PAYMENT_URL}}" style="display:inline-block;padding:12px 24px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">Pay Now</a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{DATE}}", dateStr)
                    .replace("{{TIME}}", timeStr)
                    .replace("{{AMOUNT}}", amountStr)
                    .replace("{{PAYMENT_URL}}", paymentUrl != null ? paymentUrl : "#");
        send(toEmail, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendPostDiagnosisPaymentReminderEmail(String toEmail,
                                                      String patientName,
                                                      String doctorName,
                                                      LocalDate diagnosisDate,
                                                      String paymentUrl,
                                                      BigDecimal amount) {
        String subject = "Payment Due — Medical Record Released";
        String dateStr = diagnosisDate.format(DATE_ONLY_FMT);
        String amountStr = "₱" + (amount != null ? amount.toPlainString() : "0.00");
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#E91E8C;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">Payment Due</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Your medical record/diagnosis from <strong>Dr. {{DOCTOR_NAME}}</strong> on <strong>{{DATE}}</strong> has been released. A payment is now due:</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:20px 0;">
                                            <tr>
                                                <td style="padding:15px;background-color:#fbe7f2;border-radius:8px;font-size:14px;line-height:24px;color:#5f3a52;">
                                                    <strong>Doctor:</strong> Dr. {{DOCTOR_NAME}}<br/>
                                                    <strong>Diagnosis Date:</strong> {{DATE}}<br/>
                                                    <strong>Amount Due:</strong> <span style="font-size:16px;font-weight:700;color:#E91E8C;">{{AMOUNT}}</span>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0 0 20px 0;font-size:14px;line-height:22px;color:#4b5563;">Please settle this payment at your earliest convenience to keep your account in good standing.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:0 auto;">
                                            <tr>
                                                <td align="center" bgcolor="#E91E8C" style="border-radius:8px;">
                                                    <a href="{{PAYMENT_URL}}" style="display:inline-block;padding:12px 24px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">Pay Now</a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{DATE}}", dateStr)
                    .replace("{{AMOUNT}}", amountStr)
                    .replace("{{PAYMENT_URL}}", paymentUrl != null ? paymentUrl : "#");
        send(toEmail, subject, body);
    }

    @Async("emailTaskExecutor")
    public void sendFeedbackReminderEmail(String toEmail,
                                          String patientName,
                                          String doctorName,
                                          Long appointmentId,
                                          String feedbackUrl) {
        String subject = "How was your visit with Dr. " + doctorName + "?";
        String effectiveUrl = (feedbackUrl != null && !feedbackUrl.isBlank())
                ? feedbackUrl
                : (appointmentId != null ? "https://medbuddy.app/appointments/" + appointmentId + "/feedback" : "https://medbuddy.app/feedback");
        String body = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="margin:0;padding:0;background-color:#f7f8fa;font-family:Arial,sans-serif;color:#1f2937;">
                    <tr>
                        <td align="center" style="padding:28px 12px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="520" style="width:100%;max-width:520px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;">
                                <tr>
                                    <td align="center" style="padding:20px;background-color:#E91E8C;color:#ffffff;">
                                        <h2 style="margin:0;font-size:24px;line-height:30px;font-weight:700;color:#ffffff;">We'd Love Your Feedback! ⭐</h2>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;">
                                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Hi {{PATIENT_NAME}},</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Thank you for visiting <strong>Dr. {{DOCTOR_NAME}}</strong>. We hope you had a great experience!</p>
                                        <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#374151;">Your feedback helps us improve our services and helps other patients make informed decisions. It only takes a minute to share your experience.</p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:20px auto;">
                                            <tr>
                                                <td align="center" bgcolor="#E91E8C" style="border-radius:8px;">
                                                    <a href="{{FEEDBACK_URL}}" style="display:inline-block;padding:12px 24px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">Leave a Rating & Review</a>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0;font-size:14px;line-height:22px;color:#4b5563;">Thank you for being part of the MedBuddy community!</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:14px;background-color:#f3f4f6;font-size:12px;line-height:18px;color:#6b7280;">
                                        &copy; 2026 MedBuddy
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                """.replace("{{PATIENT_NAME}}", patientName)
                    .replace("{{DOCTOR_NAME}}", doctorName)
                    .replace("{{FEEDBACK_URL}}", effectiveUrl);
        send(toEmail, subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            if (Objects.isNull(smtpUsername) || smtpUsername.isBlank()
                    || Objects.isNull(smtpPassword) || smtpPassword.isBlank()) {
                log.warn("Skipping email send because SMTP credentials are not configured. "
                        + "Set BREVO_SMTP_LOGIN and BREVO_SMTP_PASSWORD env vars | to={} subject='{}'",
                        to, subject);
                return;
            }

            String effectiveFrom = (fromAddress != null && !fromAddress.isBlank())
                    ? fromAddress
                    : smtpUsername;

            if (effectiveFrom.isBlank()) {
                log.warn("Skipping email send because sender address is missing. "
                        + "Set BREVO_MAIL_FROM or BREVO_SMTP_LOGIN env var | to={} subject='{}'",
                        to, subject);
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(effectiveFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Email sent | to={} subject='{}'", to, subject);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email | to={} subject='{}' reason={}",
                    to, subject, e.getMessage(), e);
        }
    }
}