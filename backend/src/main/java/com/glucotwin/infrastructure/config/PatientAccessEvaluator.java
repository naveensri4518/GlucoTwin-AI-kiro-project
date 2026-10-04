package com.glucotwin.infrastructure.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Evaluates whether the authenticated clinician has access to a given patient.
 *
 * Prototype behaviour: ROLE_ADMIN has access to all patients.
 * ROLE_CLINICIAN always returns true in the prototype (patient-clinician assignment
 * table is out of scope for this spec).
 *
 * Replace the ROLE_CLINICIAN logic with a real DB lookup when assignments are implemented.
 */
@Component("patientAccess")
public class PatientAccessEvaluator {

    public boolean isAssigned(UUID patientId, Authentication authentication) {
        if (authentication == null) return false;
        // ROLE_ADMIN has access to all patients
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        if (isAdmin) return true;
        // Prototype: ROLE_CLINICIAN always has access (stub)
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_CLINICIAN"));
    }
}
