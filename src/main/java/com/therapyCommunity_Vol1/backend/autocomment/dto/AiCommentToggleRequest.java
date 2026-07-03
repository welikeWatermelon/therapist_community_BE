package com.therapyCommunity_Vol1.backend.autocomment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiCommentToggleRequest {

    @NotNull(message = "enabled 값은 필수입니다.")
    private Boolean enabled;
}
