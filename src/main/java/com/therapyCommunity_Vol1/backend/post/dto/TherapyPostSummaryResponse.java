package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TherapyPostSummaryResponse {

    private Long id;
    private PostType postType;
    private String contentPreview;
    private String authorNickname;
    private TherapyArea therapyArea;
    private Visibility visibility;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private LocalDateTime createdAt;
    private boolean isScrapped;
    /**
     * 현재 사용자가 이 게시글의 본문/이미지를 볼 수 없는 상태인지.
     * PRIVATE 게시글 + 인증되지 않은 USER role일 때 true.
     * 메타데이터(authorNickname/therapyArea/카운트/createdAt)는 그대로 노출되어
     * 인증 유도(가입/치료사 인증)를 위한 hook으로 사용됨.
     * 프론트는 true일 때 contentPreview/이미지를 마스킹 처리하고 클릭 시 인증 유도 모달.
     */
    private boolean accessLocked;

    public TherapyPostSummaryResponse(
            Long id,
            PostType postType,
            String contentPreview,
            String authorNickname,
            TherapyArea therapyArea,
            Visibility visibility,
            Long viewCount,
            Long likeCount,
            Long commentCount,
            LocalDateTime createdAt,
            boolean isScrapped,
            boolean accessLocked
    ) {
        this.id = id;
        this.postType = postType;
        this.contentPreview = contentPreview;
        this.authorNickname = authorNickname;
        this.therapyArea = therapyArea;
        this.visibility = visibility;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.createdAt = createdAt;
        this.isScrapped = isScrapped;
        this.accessLocked = accessLocked;
    }

    private static final String PRIVATE_CONTENT_MESSAGE = "비공개 글입니다";

    /**
     * canViewPrivate 정보가 없는 호출처용 (테스트 헬퍼 등).
     * accessLocked는 보수적으로 PRIVATE 여부만으로 판단 — 결과적으로 가장 안전한 마스킹.
     */
    public static TherapyPostSummaryResponse from(TherapyPost post, boolean isScrapped) {
        return from(post, 0L, 0L, isScrapped, false);
    }

    public static TherapyPostSummaryResponse from(
            TherapyPost post,
            Long likeCount,
            Long commentCount,
            boolean isScrapped,
            boolean canViewPrivate
    ) {
        boolean accessLocked = post.getVisibility() == Visibility.PRIVATE && !canViewPrivate;
        String preview = accessLocked
                ? PRIVATE_CONTENT_MESSAGE
                : makePreview(post.getContent());
        return new TherapyPostSummaryResponse(
                post.getId(),
                post.getPostType(),
                preview,
                post.getAuthor().getDisplayNickname(),
                post.getTherapyArea(),
                post.getVisibility(),
                post.getViewCount(),
                likeCount,
                commentCount,
                post.getCreatedAt(),
                isScrapped,
                accessLocked
        );
    }

    public void markScrapped(boolean scrapped) {
        this.isScrapped = scrapped;
    }

    private static String makePreview(String htmlContent) {
        if(htmlContent == null || htmlContent.isBlank()) {
            return "";
        }

        String plainText = htmlContent
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return plainText.length() > 200
                ? plainText.substring(0,200)
                : plainText;
    }
}
