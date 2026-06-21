package com.therapyCommunity_Vol1.backend.global.featureflag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 런타임 기능 토글 플래그. flag_key 단위로 on/off 상태를 DB에 보관한다.
 * 재배포 없이 관리자 API로 값을 바꿔 즉시 반영하는 용도.
 */
@Entity
@Table(name = "feature_flags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeatureFlag {

    @Id
    @Column(name = "flag_key", length = 100)
    private String flagKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public FeatureFlag(String flagKey, boolean enabled) {
        this.flagKey = flagKey;
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }
}
