package com.therapyCommunity_Vol1.backend.jobpost.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;

import java.time.LocalDate;
import java.util.Base64;

public record JobPostCursor(
        LocalDate deadlineDate,
        Long id
) {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public String encode() {
        try {
            String json = MAPPER.writeValueAsString(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    public static JobPostCursor decode(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            JobPostCursor c = MAPPER.readValue(decoded, JobPostCursor.class);
            if (c.deadlineDate() == null || c.id() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            return c;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
