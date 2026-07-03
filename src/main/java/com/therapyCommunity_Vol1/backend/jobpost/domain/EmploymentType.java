package com.therapyCommunity_Vol1.backend.jobpost.domain;

import lombok.Getter;

@Getter
public enum EmploymentType {
    FULL_TIME("정규직"),
    CONTRACT("계약직"),
    PART_TIME("파트타임"),
    FREELANCE("프리랜서"),
    INTERN("인턴");

    private final String description;

    EmploymentType(String description) {
        this.description = description;
    }
}
