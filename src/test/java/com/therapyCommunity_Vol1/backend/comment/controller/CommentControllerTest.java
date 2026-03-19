package com.therapyCommunity_Vol1.backend.comment.controller;

import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentControllerTest {

    @Test
    void 댓글_목록_조회시_nested_replies를_그대로_반환한다() {
        CommentService commentService = mock(CommentService.class);
        CommentController commentController = new CommentController(commentService);
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();
        CommentResponse reply = new CommentResponse(
                2L,
                10L,
                1L,
                2L,
                "reply-user",
                "USER",
                "대댓글",
                false,
                false,
                false,
                LocalDateTime.of(2026, 3, 16, 10, 5),
                LocalDateTime.of(2026, 3, 16, 10, 5),
                List.of()
        );
        CommentResponse root = new CommentResponse(
                1L,
                10L,
                null,
                1L,
                "tester",
                "THERAPIST",
                "부모 댓글",
                false,
                true,
                true,
                LocalDateTime.of(2026, 3, 16, 10, 0),
                LocalDateTime.of(2026, 3, 16, 10, 0),
                List.of(reply)
        );

        when(commentService.getComments(1L, UserRole.THERAPIST, 10L)).thenReturn(List.of(root));

        ResponseEntity<ApiResponse<List<CommentResponse>>> response = commentController.getComments(
                new CustomUserDetails(user),
                10L
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getReplies()).hasSize(1);
        verify(commentService).getComments(1L, UserRole.THERAPIST, 10L);
    }
}
