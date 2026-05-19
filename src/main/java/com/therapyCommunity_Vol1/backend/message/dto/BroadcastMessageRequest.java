package com.therapyCommunity_Vol1.backend.message.dto;

import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastMessageRequest {

    @NotBlank(message = "쪽지 내용은 필수입니다.")
    @Size(max = 1000, message = "쪽지 내용은 1000자 이내여야 합니다.")
    private String content;

    private UserRole targetRole;
}
