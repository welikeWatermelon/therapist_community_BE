package com.therapyCommunity_Vol1.backend.reaction.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "therapy_post_reactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_therapy_post_reactions_post_user",
                        columnNames = {"post_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPostReaction extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private TherapyPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 50)
    private PostReactionType reactionType;

    private TherapyPostReaction(
            TherapyPost post,
            User user,
            PostReactionType reactionType
    ) {
        this.post = post;
        this.user = user;
        this.reactionType = reactionType;
    }

    public static TherapyPostReaction create(
            TherapyPost post,
            User user,
            PostReactionType reactionType
    ) {
        return new TherapyPostReaction(post, user, reactionType);
    }

    public void changeReactionType(PostReactionType reactionType) {
        this.reactionType = reactionType;
    }
}
