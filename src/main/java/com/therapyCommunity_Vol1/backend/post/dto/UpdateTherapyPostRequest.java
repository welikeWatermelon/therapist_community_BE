package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTherapyPostRequest {

    @NotBlank(message = "내용은 필수입니다")
    private String content;

    private TherapyArea therapyArea;

    private AgeGroup ageGroup;

    @Size(max = 10, message = "진단명은 최대 10개까지 가능합니다")
    private List<@Size(max = 100, message = "각 진단명은 100자를 초과할 수 없습니다") String> diagnoses;

    @Size(max = 200, message = "기타 사항은 200자를 초과할 수 없습니다")
    private String otherNotes;

    private Visibility visibility;
}
