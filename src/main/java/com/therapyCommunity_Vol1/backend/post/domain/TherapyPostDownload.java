package com.therapyCommunity_Vol1.backend.post.domain;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "therapy_post_downloads",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_therapy_post_downloads_post_user",
                        columnNames = {"post_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPostDownload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private TherapyPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "first_downloaded_at", nullable = false)
    private LocalDateTime firstDownloadedAt;

    @Column(name = "last_downloaded_at", nullable = false)
    private LocalDateTime lastDownloadedAt;

    @Column(name = "download_count", nullable = false)
    private long downloadCount;

    private TherapyPostDownload(
            TherapyPost post,
            User user,
            LocalDateTime firstDownloadedAt,
            LocalDateTime lastDownloadedAt,
            long downloadCount
    ) {
        this.post = post;
        this.user = user;
        this.firstDownloadedAt = firstDownloadedAt;
        this.lastDownloadedAt = lastDownloadedAt;
        this.downloadCount = downloadCount;
    }

    public static TherapyPostDownload create(TherapyPost post, User user) {
        LocalDateTime now = LocalDateTime.now();
        return new TherapyPostDownload(post, user, now, now, 1L);
    }

    public void recordDownload() {
        this.lastDownloadedAt = LocalDateTime.now();
        this.downloadCount = this.downloadCount + 1;
    }
}
