package com.therapyCommunity_Vol1.backend.post.domain;

public enum TherapyArea {

    UNSPECIFIED("미지정"),

    OCCUPATIONAL("작업치료"),
    SPEECH("언어치료"),
    COGNITIVE("인지치료"),
    PLAY("놀이치료");

    private final String description;

    TherapyArea(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
