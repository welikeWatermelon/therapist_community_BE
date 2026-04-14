package com.therapyCommunity_Vol1.backend.admin.dto;

import java.util.List;

public record DashboardPostStatsResponse(
        List<TherapyAreaStatDto> postsByTherapyArea,
        List<TherapyAreaAvgViewDto> avgViewsByTherapyArea,
        List<AgeGroupStatDto> postsByAgeGroup,
        List<PostTypeStatDto> postsByPostType
) {
    public record TherapyAreaStatDto(String therapyArea, long count) {}
    public record TherapyAreaAvgViewDto(String therapyArea, double avgViewCount) {}
    public record AgeGroupStatDto(String ageGroup, long count) {}
    public record PostTypeStatDto(String postType, long count) {}
}
