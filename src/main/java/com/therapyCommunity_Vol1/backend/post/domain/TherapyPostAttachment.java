package com.therapyCommunity_Vol1.backend.post.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "therapy_post_attachments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPostAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private TherapyPost post;

    @Column(name = "stored_path", nullable = false)
    private String storedPath;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "extension", nullable = false, length = 20)
    private String extension;

    private TherapyPostAttachment(
            TherapyPost post,
            String storedPath,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String extension
    ) {
        this.post = post;
        this.storedPath = storedPath;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.extension = extension;
    }

    public static TherapyPostAttachment create(
            TherapyPost post,
            String storedPath,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String extension
    ) {
        return new TherapyPostAttachment(
                post,
                storedPath,
                originalFilename,
                contentType,
                sizeBytes,
                extension
        );
    }
}
