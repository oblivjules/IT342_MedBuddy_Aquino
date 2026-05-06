package com.medbuddy.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medbuddy.shared.model.Appointment;
import com.medbuddy.shared.model.Payment;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayMongoService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${paymongo.secret-key}")
    private String secretKey;

    @Value("${paymongo.webhook-secret}")
    private String webhookSecret;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private String basicAuthHeader;

    @PostConstruct
    @SuppressWarnings("unused")
    void init() {
    if (secretKey != null) secretKey = secretKey.trim();
    if (webhookSecret != null) webhookSecret = webhookSecret.trim();
    log.info("PayMongo secretKey loaded: [{}]", secretKey);
    if (secretKey != null && !secretKey.isBlank()) {
        String token = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        basicAuthHeader = "Basic " + token;
        log.info("Basic auth header built successfully");
    } else {
        log.error("PayMongo secretKey is EMPTY — check application.properties");
    }
}

    public JsonNode createCheckoutSession(Payment payment, Appointment appointment) {
        try {
            String url = "https://api.paymongo.com/v1/checkout_sessions";

            long amountCentavos = payment.getFeeAmount()
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .longValueExact();

            String successUrl = frontendUrl + "/payment/success?appointmentId=" + appointment.getId();
            String cancelUrl  = frontendUrl + "/payment/cancel?appointmentId=" + appointment.getId();
            String failedUrl  = frontendUrl + "/payment/failed?appointmentId=" + appointment.getId();

            String jsonBody = String.format("""
                    {
                      "data": {
                        "attributes": {
                          "line_items": [
                            {
                              "currency": "PHP",
                              "amount": %d,
                              "name": "Consultation Fee",
                              "quantity": 1
                            }
                          ],
                          "payment_method_types": ["card", "gcash"],
                          "success_url": "%s",
                          "cancel_url": "%s",
                          "failed_url": "%s",
                          "description": "MedBuddy Appointment Payment"
                        }
                      }
                    }
                    """, amountCentavos, successUrl, cancelUrl, failedUrl);

            log.info("PayMongo checkout URLs - Success: {}, Cancel: {}, Failed: {}", successUrl, cancelUrl, failedUrl);
            log.info("PayMongo request body: {}", jsonBody);
            log.info("PayMongo auth header prefix: {}",
                    basicAuthHeader != null ? basicAuthHeader.substring(0, 20) + "..." : "NULL");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
            headers.set(HttpHeaders.AUTHORIZATION, buildBasicAuthHeader());

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);

            log.info("PayMongo response status: {} body: {}", resp.getStatusCode(), resp.getBody());

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return objectMapper.readTree(resp.getBody());
            }

            log.warn("Unexpected PayMongo response: status={} body={}", resp.getStatusCode(), resp.getBody());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("PayMongo API rejected request: status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (JsonProcessingException e) {
            log.error("PayMongo response parse error: {}", e.getMessage());
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("PayMongo connection error: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected PayMongo error: {}", e.getMessage(), e);
        }
        return null;
    }

    public boolean verifyWebhookSignature(String rawPayload, String sigHeader) {
        if (webhookSecret == null || webhookSecret.isBlank() || sigHeader == null) {
            return false;
        }
        try {
            Map<String, String> parts = parseSignatureHeader(sigHeader);
            String timestamp = parts.get("t");
            String te = parts.get("te");
            String li = parts.get("li");

            if (timestamp == null || timestamp.isBlank() || rawPayload == null) {
                return false;
            }

            String signedPayload = timestamp + "." + rawPayload;
            String computedHex = hmacSha256Hex(signedPayload, webhookSecret);

            return MessageDigestIsEqual(computedHex, te) || MessageDigestIsEqual(computedHex, li);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying PayMongo webhook signature", e);
            return false;
        }
    }

    private Map<String, String> parseSignatureHeader(String sigHeader) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String token : sigHeader.split(",")) {
            String trimmed = token.trim();
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex > 0 && equalsIndex < trimmed.length() - 1) {
                String key = trimmed.substring(0, equalsIndex).trim();
                String value = trimmed.substring(equalsIndex + 1).trim();
                values.put(key, value);
            }
        }
        return values;
    }

    private String buildBasicAuthHeader() {
        if (secretKey == null || secretKey.isBlank()) {
            return null;
        }
        if (basicAuthHeader == null) {
            String token = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
            basicAuthHeader = "Basic " + token;
        }
        return basicAuthHeader;
    }

    private String hmacSha256Hex(String signedPayload, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] computed = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : computed) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // Constant-time compare
    private boolean MessageDigestIsEqual(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
