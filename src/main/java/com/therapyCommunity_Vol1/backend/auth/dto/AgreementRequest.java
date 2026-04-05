package com.therapyCommunity_Vol1.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AgreementRequest {

    @NotNull(message = "약관 타입은 필수입니다.")
    @Pattern(regexp = "SERVICE_TERMS|PRIVACY_POLICY|MARKETING",
            message = "유효하지 않은 약관 타입입니다. (SERVICE_TERMS, PRIVACY_POLICY, MARKETING)")
    private String type;

    @NotBlank(message = "약관 버전은 필수입니다.")
    private String version;

    private boolean agreed;
}
