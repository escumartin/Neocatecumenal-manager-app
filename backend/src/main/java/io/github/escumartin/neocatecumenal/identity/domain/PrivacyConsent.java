package io.github.escumartin.neocatecumenal.identity.domain;

import java.time.Instant;

public record PrivacyConsent(String noticeVersion, Instant acceptedAt) {

    public PrivacyConsent {
        if (noticeVersion == null || noticeVersion.isBlank() || acceptedAt == null) {
            throw new DomainException("REQUIRED");
        }
    }
}
