package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class TherapyPostSummaryResponse {

    private Long id;
    private PostType postType;
    private String contentPreview;
    private String authorNickname;
    private String authorProfileImageUrl;
    private TherapyArea therapyArea;
    private AgeGroup ageGroup;
    private List<String> diagnoses;
    private String otherNotes;
    private Visibility visibility;
    private Long viewCount;
    private Long likeCount;
    private Long curiousCount;
    private Long usefulCount;
    private Long commentCount;
    private LocalDateTime createdAt;
    private boolean isScrapped;
    private PostReactionType myReactionType;
    /**
     * 현재 사용자가 이 게시글의 본문/이미지를 볼 수 없는 상태인지.
     * PRIVATE 게시글 + 인증되지 않은 USER role일 때 true.
     * 메타데이터(authorNickname/therapyArea/카운트/createdAt)는 그대로 노출되어
     * 인증 유도(가입/치료사 인증)를 위한 hook으로 사용됨.
     * 프론트는 true일 때 contentPreview/이미지를 마스킹 처리하고 클릭 시 인증 유도 모달.
     */
    private boolean accessLocked;
    private List<String> imageUrls;

    public TherapyPostSummaryResponse(
            Long id,
            PostType postType,
            String contentPreview,
            String authorNickname,
            String authorProfileImageUrl,
            TherapyArea therapyArea,
            AgeGroup ageGroup,
            List<String> diagnoses,
            String otherNotes,
            Visibility visibility,
            Long viewCount,
            Long likeCount,
            Long curiousCount,
            Long usefulCount,
            Long commentCount,
            LocalDateTime createdAt,
            boolean isScrapped,
            PostReactionType myReactionType,
            boolean accessLocked,
            List<String> imageUrls
    ) {
        this.id = id;
        this.postType = postType;
        this.contentPreview = contentPreview;
        this.authorNickname = authorNickname;
        this.authorProfileImageUrl = authorProfileImageUrl;
        this.therapyArea = therapyArea;
        this.ageGroup = ageGroup;
        this.diagnoses = diagnoses;
        this.otherNotes = otherNotes;
        this.visibility = visibility;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.curiousCount = curiousCount;
        this.usefulCount = usefulCount;
        this.commentCount = commentCount;
        this.createdAt = createdAt;
        this.isScrapped = isScrapped;
        this.myReactionType = myReactionType;
        this.accessLocked = accessLocked;
        this.imageUrls = imageUrls;
    }

    private static final String PRIVATE_CONTENT_MESSAGE = "비공개 글입니다";

    /**
     * 테스트 헬퍼 — 카운트/프로필/이미지/내 반응 정보가 없을 때.
     * accessLocked는 보수적으로 PRIVATE 여부만으로 판단.
     */
    public static TherapyPostSummaryResponse from(TherapyPost post, boolean isScrapped) {
        return from(post, 0L, 0L, 0L, 0L, isScrapped, false, null, List.of(), null);
    }

    public static TherapyPostSummaryResponse from(
            TherapyPost post,
            Long likeCount,
            Long curiousCount,
            Long usefulCount,
            Long commentCount,
            boolean isScrapped,
            boolean canViewPrivate,
            String authorProfileImageUrl,
            List<String> imageUrls,
            PostReactionType myReactionType
    ) {
        boolean accessLocked = post.getVisibility() == Visibility.PRIVATE && !canViewPrivate;
        String preview = accessLocked
                ? PRIVATE_CONTENT_MESSAGE
                : makePreview(post.getContent());
        // PRIVATE 게시글의 이미지 URL은 권한 없는 사용자에게 노출하지 않음
        List<String> safeImageUrls = accessLocked ? List.of() : imageUrls;
        // 진단명은 THERAPIST+ (canViewPrivate) 에게만 노출
        List<String> safeDiagnoses = canViewPrivate ? post.getDiagnoses() : null;
        String safeOtherNotes = canViewPrivate ? post.getOtherNotes() : null;
        return new TherapyPostSummaryResponse(
                post.getId(),
                post.getPostType(),
                preview,
                post.getAuthor().getDisplayNickname(),
                authorProfileImageUrl,
                post.getTherapyArea(),
                post.getAgeGroup(),
                safeDiagnoses,
                safeOtherNotes,
                post.getVisibility(),
                post.getViewCount(),
                likeCount,
                curiousCount,
                usefulCount,
                commentCount,
                post.getCreatedAt(),
                isScrapped,
                myReactionType,
                accessLocked,
                safeImageUrls
        );
    }

    public void markScrapped(boolean scrapped) {
        this.isScrapped = scrapped;
    }

    public void markMyReactionType(PostReactionType type) {
        this.myReactionType = type;
    }

    private static String makePreview(String htmlContent) {
        if(htmlContent == null || htmlContent.isBlank()) {
            return "";
        }

        String plainText = htmlContent
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>\\s*<p[^>]*>", "\n")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[^\\S\\n]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return plainText.length() > 200
                ? plainText.substring(0, 200) + "..."
                : plainText;
    }
}
