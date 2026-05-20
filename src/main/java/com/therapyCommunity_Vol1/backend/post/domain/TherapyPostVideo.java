package com.therapyCommunity_Vol1.backend.post.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "therapy_post_videos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPostVideo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private TherapyPost post;

    @Column(name = "stored_path", nullable = false)
    private String storedPath;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

    private TherapyPostVideo(
            TherapyPost post,
            String storedPath,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Integer durationSeconds
    ) {
        this.post = post;
        this.storedPath = storedPath;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.durationSeconds = durationSeconds;
    }

    public static TherapyPostVideo create(
            TherapyPost post,
            String storedPath,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Integer durationSeconds
    ) {
        return new TherapyPostVideo(post, storedPath, originalFilename, contentType, sizeBytes, durationSeconds);
    }
}
