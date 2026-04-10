package com.therapyCommunity_Vol1.backend.post.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;

import java.util.Base64;

public record PopularCursor(
        Double score,
        Long id
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String encode() {
        try {
            String json = MAPPER.writeValueAsString(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    public static PopularCursor decode(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            PopularCursor popularCursor = MAPPER.readValue(decoded, PopularCursor.class);
            if (popularCursor.score() == null || popularCursor.id() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            return popularCursor;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
