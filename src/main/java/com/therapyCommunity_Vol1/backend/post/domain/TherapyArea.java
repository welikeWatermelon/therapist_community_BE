package com.therapyCommunity_Vol1.backend.post.domain;

public enum TherapyArea {

    SENSORY_INTEGRATION("감각통합"),
    SPEECH("언어치료"),
    OCCUPATIONAL("작업치료"),
    COGNITIVE("인지치료"),
    PHYSICAL("물리치료"),
    ART("미술치료"),
    MUSIC("음악치료"),
    PLAY("놀이치료"),
    BEHAVIOR("행동치료");

    private final String description;

    TherapyArea(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
