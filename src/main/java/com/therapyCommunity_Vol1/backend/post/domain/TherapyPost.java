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

    @Column(name = "title_choseong", length = 200)
    private String titleChoseong;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility;

    @Column(name = "popularity_score", nullable = false)
    private Long popularityScore = 0L;

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
        this.popularityScore = java.time.Instant.now().getEpochSecond() / 8640;
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
