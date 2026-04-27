package com.therapyCommunity_Vol1.backend.post.domain;

public enum AgeGroup {

    UNSPECIFIED(""),

    AGE_0_2("0세 2세"),
    AGE_3_5("3세 5세"),
    AGE_6_12("6세 12세"),
    AGE_13_18("13세 18세"),
    AGE_19_64("19세 64세"),
    AGE_65_PLUS("65세 이상");

    private final String description;

    AgeGroup(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
