package com.therapyCommunity_Vol1.backend.notification.service;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.notification.repository.NotificationRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class NotificationRetryIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private FileStorageService fileStorageService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        sender = userRepository.save(User.builder()
                .email("retry-sender@test.com")
                .nickname("재시도발신자")
                .passwordHash("hashed")
                .role(UserRole.THERAPIST)
                .build());

        receiver = userRepository.save(User.builder()
                .email("retry-receiver@test.com")
                .nickname("재시도수신자")
                .passwordHash("hashed")
                .role(UserRole.THERAPIST)
                .build());
    }

    @AfterEach
    void tearDown() {
        reset(notificationRepository);
        notificationRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void DB_장애시_3회_재시도_후_DataAccessException이_전파된다() {
        // given — repository.save()가 매번 실패
        doThrow(new DataAccessResourceFailureException("DB 커넥션 풀 소진"))
                .when(notificationRepository).save(any());

        NotificationEvent event = NotificationEvent.of(
                sender.getId(), receiver.getId(),
                NotificationType.NEW_COMMENT, 10L);

        // when & then — @Recover 없으므로 최종 예외가 그대로 전파됨
        assertThatThrownBy(() -> notificationService.createNotifications(event))
                .isInstanceOf(DataAccessException.class);

        // then — 3회 재시도 확인 (maxAttempts = 3)
        verify(notificationRepository, times(3)).save(any());
    }

}
