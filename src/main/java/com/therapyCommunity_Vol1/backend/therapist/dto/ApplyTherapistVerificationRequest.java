package com.therapyCommunity_Vol1.backend.therapist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class ApplyTherapistVerificationRequest {

    @NotBlank(message = "치료사 번호는 필수입니다.")
    private String licenseCode;

    @NotNull(message = "치료사 증빙 이미지는 필수입니다.")
    private MultipartFile licenseImage;
}
