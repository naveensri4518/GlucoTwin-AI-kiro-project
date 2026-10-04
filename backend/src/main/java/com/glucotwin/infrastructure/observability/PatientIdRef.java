package com.glucotwin.infrastructure.observability;

import com.glucotwin.domain.shared.PatientId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Produces a SHA-256 hash of a PatientId for safe logging.
 * Raw PatientId UUIDs must NEVER appear in log output.
 */
public final class PatientIdRef {

    private PatientIdRef() {}

    public static String hash(PatientId patientId) {
        if (patientId == null) return "unknown";
        return sha256(patientId.value().toString());
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16); // first 16 chars — sufficient for log correlation
        } catch (NoSuchAlgorithmException e) {
            return "hash-error";
        }
    }
}
