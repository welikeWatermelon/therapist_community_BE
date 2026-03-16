package com.therapyCommunity_Vol1.backend.scrap.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ScrapStatusResponse {

    private Long postId;
    private boolean scrapped;
}
