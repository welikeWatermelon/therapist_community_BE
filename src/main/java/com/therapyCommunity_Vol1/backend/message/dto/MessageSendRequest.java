package com.therapyCommunity_Vol1.backend.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {

    @NotNull(message = "수신자 ID는 필수입니다.")
    private Long receiverId;

    @NotBlank(message = "쪽지 내용은 필수입니다.")
    @Size(max = 1000, message = "쪽지 내용은 1000자 이내여야 합니다.")
    private String content;
}
