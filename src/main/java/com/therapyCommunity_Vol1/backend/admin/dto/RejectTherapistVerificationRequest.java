package com.therapyCommunity_Vol1.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RejectTherapistVerificationRequest {

    @NotBlank(message = "거절 사유는 필수입니다.")
    private String rejectReason;
}
