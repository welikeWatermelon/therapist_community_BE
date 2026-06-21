package com.therapyCommunity_Vol1.backend.autocomment.service;

import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.global.featureflag.FeatureFlag;
import com.therapyCommunity_Vol1.backend.global.featureflag.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동답글 기능의 런타임 on/off 상태를 관리한다.
 * DB 플래그(feature_flags.ai_comment)가 있으면 그 값을, 없으면 프로퍼티(app.ai-comment.enabled)
 * 기본값을 사용한다. 이를 통해 재배포 없이 관리자 API로 즉시 토글할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class AiCommentToggleService {

    static final String FLAG_KEY = "ai_comment";

    private final FeatureFlagRepository featureFlagRepository;
    private final AiCommentProperties properties;

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return featureFlagRepository.findById(FLAG_KEY)
                .map(FeatureFlag::isEnabled)
                .orElseGet(properties::isEnabled);
    }

    @Transactional
    public boolean setEnabled(boolean enabled) {
        FeatureFlag flag = featureFlagRepository.findById(FLAG_KEY)
                .orElseGet(() -> new FeatureFlag(FLAG_KEY, enabled));
        flag.updateEnabled(enabled);
        featureFlagRepository.save(flag);
        return enabled;
    }
}
