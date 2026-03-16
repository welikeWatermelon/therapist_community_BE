package com.therapyCommunity_Vol1.backend.user.domain;

import lombok.Getter;

@Getter
public enum UserRole {

    USER("USER"),
    THERAPIST("THERAPIST"),
    ADMIN("ADMIN");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }
}
