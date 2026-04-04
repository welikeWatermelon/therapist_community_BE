package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class TherapyPostDetailResponse {

    private Long id;
    private String content;
    private PostType postType;
    private Long authorId;
    private String authorNickname;
    private TherapyArea therapyArea;
    private Visibility visibility;
    private Long viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean canEdit;
    private boolean canDelete;
    private List<PostAttachmentResponse> attachments;

    // 첨부파일이 없는 생성/수정 응답에서 사용
    public static TherapyPostDetailResponse from(
            TherapyPost post,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        return from(post, List.of(), currentUserId, currentUserRole);
    }

    // 상세 조회처럼 첨부파일과 권한 정보를 함께 담아 응답 객체로 변환
    public static TherapyPostDetailResponse from(
            TherapyPost post,
            List<PostAttachmentResponse> attachments,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        // 클라이언트가 작성자/관리자 권한을 다시 계산하지 않도록 수정/삭제 가능 여부를 함께 내려준다.
        boolean canManage = canManage(post, currentUserId, currentUserRole);
        return new TherapyPostDetailResponse(
                post.getId(),
                post.getContent(),
                post.getPostType(),
                post.getAuthor().getId(),
                post.getAuthor().getDisplayNickname(),
                post.getTherapyArea(),
                post.getVisibility(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                canManage,
                canManage,
                attachments
        );
    }

    // 관리자이거나, 내 글이면 수정/삭제 가능
    private static boolean canManage(
            TherapyPost post,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        boolean isAdmin = currentUserRole == UserRole.ADMIN;
        boolean isAuthor = post.getAuthor().getId().equals(currentUserId);
        return isAdmin || isAuthor;
    }
}
