package com.medbuddy.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    // ── Key ────────────────────────────────────────────────────────
    private SecretKey getSigningKey() {
        byte[] keyBytes;

        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (DecodingException ex) {
            try {
                keyBytes = Decoders.BASE64URL.decode(jwtSecret);
            } catch (DecodingException ignored) {
                // Fallback for plain-text secrets used in local/dev configs.
                keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            }
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Generate ────────────────────────────────────────────────────
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Extract ─────────────────────────────────────────────────────
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    // ── Validate ────────────────────────────────────────────────────
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }

    // ── Internal ─────────────────────────────────────────────────────
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
