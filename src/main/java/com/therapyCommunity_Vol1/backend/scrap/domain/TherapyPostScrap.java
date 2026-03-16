package com.therapyCommunity_Vol1.backend.scrap.domain;

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
        name = "therapy_post_scraps",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_therapy_post_scraps_post_user",
                        columnNames = {"post_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPostScrap extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private TherapyPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private TherapyPostScrap(TherapyPost post, User user) {
        this.post = post;
        this.user = user;
    }

    public static TherapyPostScrap create(TherapyPost post, User user) {
        return new TherapyPostScrap(post,user);
    }
}
