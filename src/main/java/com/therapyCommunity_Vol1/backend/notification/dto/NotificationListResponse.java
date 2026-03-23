package com.therapyCommunity_Vol1.backend.notification.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class NotificationListResponse {
    private List<NotificationResponse> notifications;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private boolean hasNext;

    public static NotificationListResponse from(Page<NotificationResponse> page) {
        return NotificationListResponse.builder()
                .notifications(page.getContent())
                .currentPage(page.getNumber())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .hasNext(page.hasNext())
                .build();
    }
}
