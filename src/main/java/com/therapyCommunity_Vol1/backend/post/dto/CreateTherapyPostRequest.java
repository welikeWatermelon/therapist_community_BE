package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTherapyPostRequest {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    private String content;

    @NotNull(message = "게시글 타입은 필수입니다.")
    private PostType postType;

    @NotNull(message = "치료 종류는 필수입니다.")
    private TherapyArea therapyArea;

    @NotNull(message = "연령대는 필수입니다.")
    private AgeGroup ageGroup;
}
