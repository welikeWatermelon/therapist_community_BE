package com.therapyCommunity_Vol1.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AgreementRequest {

    @NotNull(message = "약관 타입은 필수입니다.")
    private String type;

    @NotBlank(message = "약관 버전은 필수입니다.")
    private String version;

    private boolean agreed;
}
