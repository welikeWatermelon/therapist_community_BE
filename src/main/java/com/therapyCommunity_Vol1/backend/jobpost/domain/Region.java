package com.therapyCommunity_Vol1.backend.jobpost.domain;

import lombok.Getter;

@Getter
public enum Region {
    SEOUL("서울"), BUSAN("부산"), DAEGU("대구"), INCHEON("인천"),
    GWANGJU("광주"), DAEJEON("대전"), ULSAN("울산"), SEJONG("세종"),
    GYEONGGI("경기"), GANGWON("강원"), CHUNGBUK("충북"), CHUNGNAM("충남"),
    JEONBUK("전북"), JEONNAM("전남"), GYEONGBUK("경북"), GYEONGNAM("경남"),
    JEJU("제주"), REMOTE("재택근무"), NATIONWIDE("전국");

    private final String description;

    Region(String description) {
        this.description = description;
    }
}
