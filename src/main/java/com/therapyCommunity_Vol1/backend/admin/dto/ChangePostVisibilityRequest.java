package com.therapyCommunity_Vol1.backend.admin.dto;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChangePostVisibilityRequest {

    @NotNull(message = "공개 여부는 필수입니다.")
    private Visibility visibility;
}
