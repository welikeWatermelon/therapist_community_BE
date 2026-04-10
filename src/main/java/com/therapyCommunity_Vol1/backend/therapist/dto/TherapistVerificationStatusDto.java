package com.therapyCommunity_Vol1.backend.therapist.dto;

import java.time.LocalDateTime;

public record TherapistVerificationStatusDto(
        String status,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        String rejectionReason
) {}
