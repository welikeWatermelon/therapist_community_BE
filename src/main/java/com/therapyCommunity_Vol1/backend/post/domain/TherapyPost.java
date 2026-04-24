package com.therapyCommunity_Vol1.backend.post.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Table(name = "therapy_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "therapy_area", length = 50)
    private TherapyArea therapyArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", length = 50)
    private AgeGroup ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 50)
    private PostType postType;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;


    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility;

    @Column(name = "popularity_score", nullable = false)
    private Long popularityScore = 0L;

    /**
     * 시간 가중치 분모. 86400초(1일) / 10 → 약 2.4시간마다 점수 1점 자연 증가.
     * 반응 30점 / 스크랩 20점 스케일과 균형을 맞추기 위한 값.
     * V25 마이그레이션 및 TherapyPostRepository.recalculatePopularityScore 쿼리와 동일하게 유지해야 함.
     */
    private static final long TIME_SCORE_DIVISOR = 8640L;

    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    private TherapyPost(
            String content,
            TherapyArea therapyArea,
            Visibility visibility,
            User author
    ) {
        this.content = content;
        this.therapyArea = therapyArea != null ? therapyArea : TherapyArea.UNSPECIFIED;
        this.postType = PostType.COMMUNITY;
        this.visibility = visibility != null ? visibility : Visibility.PUBLIC;
        this.author = author;
        this.viewCount = 0L;
        this.searchText = buildSearchText(this.content, this.therapyArea);
        this.popularityScore = java.time.Instant.now().getEpochSecond() / TIME_SCORE_DIVISOR;
        this.searchText = buildSearchText(this.title, this.content, this.therapyArea, this.ageGroup);
    }

    public static TherapyPost create(
            String content,
            TherapyArea therapyArea,
            Visibility visibility,
            User author
    ) {
        return new TherapyPost(content, therapyArea, visibility, author);
    }

    public void increaseViewCount() {
        this.viewCount = this.viewCount + 1;
    }

    public void update(
            String content,
            TherapyArea therapyArea,
            Visibility visibility
    ) {
        this.content = content;
        this.therapyArea = therapyArea != null ? therapyArea : TherapyArea.UNSPECIFIED;
        this.visibility = visibility != null ? visibility : this.visibility;
        this.searchText = buildSearchText(this.content, this.therapyArea);
    }

    private static String buildSearchText(
            String content,
            TherapyArea therapyArea
    ) {
        String c = content == null
                ? ""
                : content.substring(0, Math.min(100, content.length()));
        String a = therapyArea == null ? "" : therapyArea.getDescription();
        return (c + " " + a).trim();
    }

    public void updatePostType(PostType postType) {
        this.postType = postType;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
