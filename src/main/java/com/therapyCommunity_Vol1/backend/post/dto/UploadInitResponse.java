package com.therapyCommunity_Vol1.backend.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class UploadInitResponse {

    private String uploadUrl;
    private String storedKey;
    private Instant expiresAt;
}
