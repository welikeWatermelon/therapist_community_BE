package com.therapyCommunity_Vol1.backend.post.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "therapy_post_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapyPostImage {

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

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static TherapyPostImage create(
            TherapyPost post,
            String storedPath,
            String originalFilename,
            String contentType,
            long sizeBytes,
            int displayOrder
    ) {
        TherapyPostImage image = new TherapyPostImage();
        image.post = post;
        image.storedPath = storedPath;
        image.originalFilename = originalFilename;
        image.contentType = contentType;
        image.sizeBytes = sizeBytes;
        image.displayOrder = displayOrder;
        image.createdAt = LocalDateTime.now();
        return image;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
