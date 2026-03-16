package com.therapyCommunity_Vol1.backend.therapist.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TherapistVerificationStatus {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED");

    private final String code;

    TherapistVerificationStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TherapistVerificationStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("TherapistVerificationStatus code is blank");
        }

        for (TherapistVerificationStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TherapistVerificationStatus code: " + code);
    }
}
