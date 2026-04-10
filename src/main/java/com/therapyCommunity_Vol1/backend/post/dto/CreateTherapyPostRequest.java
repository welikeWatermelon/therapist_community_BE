package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTherapyPostRequest {

    @NotBlank(message = "내용은 필수입니다")
    private String content;

    private TherapyArea therapyArea;

    private Visibility visibility;

    private Boolean requestAutoComment;
}
