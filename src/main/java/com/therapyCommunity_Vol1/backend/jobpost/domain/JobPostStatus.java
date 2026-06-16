package com.therapyCommunity_Vol1.backend.jobpost.domain;

import lombok.Getter;

@Getter
public enum JobPostStatus {
    OPEN("모집중"),
    CLOSED("마감");

    private final String description;

    JobPostStatus(String description) {
        this.description = description;
    }
}
