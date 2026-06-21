package com.therapyCommunity_Vol1.backend.autocomment.service;

import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.global.featureflag.FeatureFlag;
import com.therapyCommunity_Vol1.backend.global.featureflag.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiCommentToggleServiceTest {

    private FeatureFlagRepository featureFlagRepository;
    private AiCommentProperties properties;
    private AiCommentToggleService toggleService;

    @BeforeEach
    void setUp() {
        featureFlagRepository = mock(FeatureFlagRepository.class);
        properties = new AiCommentProperties();
        toggleService = new AiCommentToggleService(featureFlagRepository, properties);
    }

    @Test
    void flag_row가_없으면_프로퍼티_기본값으로_fallback_한다() {
        when(featureFlagRepository.findById("ai_comment")).thenReturn(Optional.empty());

        properties.setEnabled(false);
        assertThat(toggleService.isEnabled()).isFalse();

        properties.setEnabled(true);
        assertThat(toggleService.isEnabled()).isTrue();
    }

    @Test
    void flag_row가_있으면_프로퍼티보다_DB값이_우선한다() {
        properties.setEnabled(false); // 프로퍼티는 off
        when(featureFlagRepository.findById("ai_comment"))
                .thenReturn(Optional.of(new FeatureFlag("ai_comment", true))); // DB는 on

        assertThat(toggleService.isEnabled()).isTrue();
    }

    @Test
    void setEnabled는_row가_없으면_새로_생성해_저장한다() {
        when(featureFlagRepository.findById("ai_comment")).thenReturn(Optional.empty());

        boolean result = toggleService.setEnabled(true);

        assertThat(result).isTrue();
        ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
        verify(featureFlagRepository).save(captor.capture());
        assertThat(captor.getValue().getFlagKey()).isEqualTo("ai_comment");
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    @Test
    void setEnabled는_기존_row가_있으면_값을_갱신한다() {
        FeatureFlag existing = new FeatureFlag("ai_comment", true);
        when(featureFlagRepository.findById("ai_comment")).thenReturn(Optional.of(existing));

        boolean result = toggleService.setEnabled(false);

        assertThat(result).isFalse();
        assertThat(existing.isEnabled()).isFalse();
        verify(featureFlagRepository).save(existing);
    }
}
