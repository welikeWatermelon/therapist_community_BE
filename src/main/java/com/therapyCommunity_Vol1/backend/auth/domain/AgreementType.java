package com.therapyCommunity_Vol1.backend.auth.domain;

import lombok.Getter;

@Getter
public enum AgreementType {

    SERVICE_TERMS("이용약관", true),
    PRIVACY_POLICY("개인정보처리방침", true),
    MARKETING("마케팅 수신 동의", false);

    private final String description;
    private final boolean required;

    AgreementType(String description, boolean required) {
        this.description = description;
        this.required = required;
    }
}
