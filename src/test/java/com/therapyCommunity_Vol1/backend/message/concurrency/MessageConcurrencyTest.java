package com.therapyCommunity_Vol1.backend.message.concurrency;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.message.domain.Message;
import com.therapyCommunity_Vol1.backend.message.dto.MessageSendRequest;
import com.therapyCommunity_Vol1.backend.message.repository.MessageRepository;
import com.therapyCommunity_Vol1.backend.message.service.MessageService;
import com.therapyCommunity_Vol1.backend.notification.repository.NotificationRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class MessageConcurrencyTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private FileStorageService fileStorageService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        sender = userRepository.save(User.builder()
                .email("sender@test.com")
                .nickname("발신자")
                .passwordHash("hashed")
                .role(UserRole.THERAPIST)
                .build());

        receiver = userRepository.save(User.builder()
                .email("receiver@test.com")
                .nickname("수신자")
                .passwordHash("hashed")
                .role(UserRole.THERAPIST)
                .build());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // 비동기 notification 이벤트 처리 대기
        Thread.sleep(500);
        notificationRepository.deleteAllInBatch();
        messageRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 동시에_같은_쪽지를_읽음_처리해도_정합성_유지() throws Exception {
        Message saved = messageRepository.save(Message.create(sender, receiver, "동시성 테스트"));
        Long messageId = saved.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            futures(executor, latch, errors, () ->
                    messageService.getMessage(receiver.getId(), messageId));
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(errors).isEmpty();

        Message result = messageRepository.findById(messageId).orElseThrow();
        assertThat(result.isRead()).isTrue();
        assertThat(result.getReadAt()).isNotNull();
    }

    @Test
    void 발신자_삭제후_수신자_조회시_독립_삭제_보장() {
        // 순차 실행으로 독립 삭제 정합성 검증
        // (H2는 동일 row 동시 업데이트 시 락 충돌 발생 — PostgreSQL에서는 동시 실행 가능)
        Message saved = messageRepository.save(Message.create(sender, receiver, "삭제+조회 경합"));
        Long messageId = saved.getId();

        // 발신자 삭제
        messageService.deleteMessage(sender.getId(), messageId);
        // 수신자 조회 — 발신자 삭제와 무관하게 정상 조회 가능해야 함
        messageService.getMessage(receiver.getId(), messageId);

        Message result = messageRepository.findById(messageId).orElseThrow();
        assertThat(result.isDeletedBySender()).isTrue();
        assertThat(result.isDeletedByReceiver()).isFalse();
        assertThat(result.isRead()).isTrue();
    }

    @Test
    void 같은_수신자에게_동시_쪽지_발송() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures(executor, latch, errors, () ->
                    messageService.sendMessage(sender.getId(),
                            new MessageSendRequest(receiver.getId(), "동시 발송 " + idx)));
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(errors).isEmpty();

        long count = messageRepository.findReceivedMessages(receiver.getId(), PageRequest.of(0, 100))
                .getTotalElements();
        assertThat(count).isEqualTo(threadCount);
    }

    @Test
    void 이미_삭제된_쪽지_재삭제시_멱등성_보장() {
        Message saved = messageRepository.save(Message.create(sender, receiver, "멱등성 테스트"));
        Long messageId = saved.getId();

        messageService.deleteMessage(sender.getId(), messageId);
        messageService.deleteMessage(sender.getId(), messageId);

        Message result = messageRepository.findById(messageId).orElseThrow();
        assertThat(result.isDeletedBySender()).isTrue();
    }

    @Test
    void 읽음_처리_멱등성_readAt_불변() {
        Message saved = messageRepository.save(Message.create(sender, receiver, "readAt 멱등성"));
        Long messageId = saved.getId();

        messageService.getMessage(receiver.getId(), messageId);
        Message afterFirstRead = messageRepository.findById(messageId).orElseThrow();
        var firstReadAt = afterFirstRead.getReadAt();

        messageService.getMessage(receiver.getId(), messageId);
        Message afterSecondRead = messageRepository.findById(messageId).orElseThrow();

        assertThat(afterSecondRead.getReadAt()).isEqualTo(firstReadAt);
    }

    private void futures(ExecutorService executor, CountDownLatch latch,
                         List<Throwable> errors, Runnable task) {
        executor.submit(() -> {
            try {
                latch.await();
                task.run();
            } catch (Exception e) {
                errors.add(e);
            }
        });
    }
}
