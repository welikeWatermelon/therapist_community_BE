package com.therapyCommunity_Vol1.backend.message.repository;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.message.domain.Message;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private FileStorageService fileStorageService;

    private User sender;
    private User receiver;
    private Statistics statistics;

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

        for (int i = 0; i < 5; i++) {
            messageRepository.save(Message.create(sender, receiver, "쪽지 " + i));
        }

        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        statistics.clear();
    }

    @Test
    @Transactional(readOnly = true)
    void 받은쪽지함_조회시_쿼리_횟수_검증() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        Page<Message> result = messageRepository.findReceivedMessages(receiver.getId(), PageRequest.of(0, 20));

        // JOIN FETCH로 sender, receiver 모두 로딩되어야 함
        result.getContent().forEach(m -> {
            m.getSender().getDisplayNickname();
            m.getReceiver().getDisplayNickname();
        });

        // 데이터 쿼리 1 + 카운트 쿼리 1 = 최대 2
        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(2);
        assertThat(result.getContent()).hasSize(5);
    }

    @Test
    @Transactional(readOnly = true)
    void 보낸쪽지함_조회시_쿼리_횟수_검증() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        Page<Message> result = messageRepository.findSentMessages(sender.getId(), PageRequest.of(0, 20));

        result.getContent().forEach(m -> {
            m.getSender().getDisplayNickname();
            m.getReceiver().getDisplayNickname();
        });

        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(2);
        assertThat(result.getContent()).hasSize(5);
    }

    @Test
    @Transactional(readOnly = true)
    void 안읽은_쪽지_수_조회_쿼리_횟수_검증() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        long count = messageRepository.countUnreadMessages(receiver.getId());

        assertThat(statistics.getQueryExecutionCount()).isEqualTo(1);
        assertThat(count).isEqualTo(5);
    }

    @Test
    @Transactional
    void 삭제된_쪽지는_받은쪽지함에서_제외된다() {
        Message firstMessage = messageRepository.findReceivedMessages(receiver.getId(), PageRequest.of(0, 1))
                .getContent().get(0);
        firstMessage.deleteByReceiver();
        entityManager.flush();
        entityManager.clear();

        Page<Message> result = messageRepository.findReceivedMessages(receiver.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(4);
    }
}
