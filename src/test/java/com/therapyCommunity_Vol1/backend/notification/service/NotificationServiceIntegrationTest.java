package com.therapyCommunity_Vol1.backend.notification.service;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.notification.domain.Notification;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class NotificationServiceIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
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
                .email("sender@test.com")
                .nickname("댓글작성자")
                .passwordHash("hashed")
                .role(UserRole.THERAPIST)
                .build());

        receiver = userRepository.save(User.builder()
                .email("receiver@test.com")
                .nickname("게시글작성자")
                .passwordHash("hashed")
                .role(UserRole.THERAPIST)
                .build());
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 댓글_알림_이벤트_처리시_DB에_저장된다() {
        NotificationEvent event = NotificationEvent.of(
                sender.getId(), receiver.getId(),
                NotificationType.NEW_COMMENT, 10L);

        notificationService.createNotifications(event);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);

        Notification saved = notifications.get(0);
        assertThat(saved.getReceiver().getId()).isEqualTo(receiver.getId());
        assertThat(saved.getSender().getId()).isEqualTo(sender.getId());
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.NEW_COMMENT);
        assertThat(saved.getReferenceId()).isEqualTo(10L);
        assertThat(saved.getContent()).isEqualTo("댓글작성자님이 회원님의 게시글에 댓글을 남겼습니다.");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void 대댓글_알림_content가_올바르게_생성된다() {
        NotificationEvent event = NotificationEvent.of(
                sender.getId(), receiver.getId(),
                NotificationType.NEW_REPLY, 10L);

        notificationService.createNotifications(event);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getContent())
                .isEqualTo("댓글작성자님이 회원님의 댓글에 답글을 남겼습니다.");
    }

    @Test
    void 반응_알림_content에_반응_라벨이_포함된다() {
        NotificationEvent event = NotificationEvent.of(
                sender.getId(), receiver.getId(),
                NotificationType.NEW_POST_REACTION, 10L,
                "좋아요");

        notificationService.createNotifications(event);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);

        Notification saved = notifications.get(0);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.NEW_POST_REACTION);
        assertThat(saved.getContent())
                .isEqualTo("댓글작성자님이 회원님의 게시글에 좋아요 반응을 남겼습니다.");
    }

    @Test
    void 댓글_반응_알림_content에_반응_라벨이_포함된다() {
        NotificationEvent event = NotificationEvent.of(
                sender.getId(), receiver.getId(),
                NotificationType.NEW_COMMENT_REACTION, 20L,
                "싫어요");

        notificationService.createNotifications(event);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getContent())
                .isEqualTo("댓글작성자님이 회원님의 댓글에 싫어요 반응을 남겼습니다.");
    }

    @Test
    void 자기_자신에게는_알림을_보내지_않는다() {
        NotificationEvent event = NotificationEvent.of(
                sender.getId(), sender.getId(),
                NotificationType.NEW_COMMENT, 10L);

        notificationService.createNotifications(event);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).isEmpty();
    }

    @Test
    void 스크랩_알림이_정상_저장된다() {
        NotificationEvent event = NotificationEvent.of(
                sender.getId(), receiver.getId(),
                NotificationType.NEW_SCRAP, 10L);

        notificationService.createNotifications(event);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getContent())
                .isEqualTo("댓글작성자님이 회원님의 게시글을 스크랩했습니다.");
    }

    @Test
    void 삭제된_sender는_알수없는사용자로_표시된다() {
        NotificationEvent event = NotificationEvent.of(
                9999L, receiver.getId(),
                NotificationType.NEW_COMMENT, 10L);

        notificationService.createNotifications(event);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getSender()).isNull();
        assertThat(notifications.get(0).getContent())
                .isEqualTo("알 수 없는 사용자님이 회원님의 게시글에 댓글을 남겼습니다.");
    }
}
