package com.therapyCommunity_Vol1.backend.comment.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "therapy_post_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPostComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private TherapyPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private TherapyPostComment parentComment;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private TherapyPostComment(
            TherapyPost post,
            User author,
            TherapyPostComment parentComment,
            String content
    ) {
        this.post = post;
        this.author = author;
        this.parentComment = parentComment;
        this.content = content;
    }

    public static TherapyPostComment createRoot(
            TherapyPost post,
            User author,
            String content
    ) {
        return new TherapyPostComment(post, author, null, content);
    }

    public static TherapyPostComment createReply(
            TherapyPost post,
            User author,
            TherapyPostComment parentComment,
            String content
    ) {
        return new TherapyPostComment(post, author, parentComment, content);
    }

    public void update(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isReply() {
        return this.parentComment != null;
    }
}