package com.therapyCommunity_Vol1.backend.reaction.domain;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "therapy_post_comment_reactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_therapy_post_comment_reactions_comment_user",
                        columnNames = {"comment_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPostCommentReaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id")
    private TherapyPostComment comment;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 50)
    private CommentReactionType reactionType;

    private TherapyPostCommentReaction(
            TherapyPostComment comment,
            User user,
            CommentReactionType reactionType
    ) {
        this.comment = comment;
        this.user = user;
        this.reactionType = reactionType;
    }

    public static TherapyPostCommentReaction create(
            TherapyPostComment comment,
            User user,
            CommentReactionType reactionType
    ) {
        return new TherapyPostCommentReaction(comment, user, reactionType);
    }

    public void changeReactionType(CommentReactionType reactionType) {
        this.reactionType = reactionType;
    }
}
