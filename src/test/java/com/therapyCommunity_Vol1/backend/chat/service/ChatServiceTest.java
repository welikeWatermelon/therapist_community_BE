package com.therapyCommunity_Vol1.backend.chat.service;

import com.therapyCommunity_Vol1.backend.chat.domain.Conversation;
import com.therapyCommunity_Vol1.backend.chat.domain.Message;
import com.therapyCommunity_Vol1.backend.chat.dto.*;
import com.therapyCommunity_Vol1.backend.chat.repository.ConversationRepository;
import com.therapyCommunity_Vol1.backend.chat.repository.MessageRepository;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private ConversationRepository conversationRepository;
    private MessageRepository messageRepository;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private ChatService chatService;

    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(MessageRepository.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        chatService = new ChatService(conversationRepository, messageRepository, userRepository, eventPublisher);

        user1 = User.builder()
                .id(1L).email("user1@test.com").nickname("유저1").role(UserRole.THERAPIST)
                .build();
        user2 = User.builder()
                .id(2L).email("user2@test.com").nickname("유저2").role(UserRole.THERAPIST)
                .build();
        user3 = User.builder()
                .id(3L).email("user3@test.com").nickname("유저3").role(UserRole.THERAPIST)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user3));
    }

    @Nested
    class 정상_플로우 {

        @Test
        void 새_대화_생성시_conversation과_message가_저장된다() {
            // given
            when(conversationRepository.findByParticipants(1L, 2L)).thenReturn(Optional.empty());
            when(conversationRepository.saveAndFlush(any(Conversation.class))).thenAnswer(invocation -> {
                Conversation c = invocation.getArgument(0);
                ReflectionTestUtils.setField(c, "id", 100L);
                return c;
            });
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 200L);
                return m;
            });

            CreateConversationRequest request = new CreateConversationRequest(2L, "안녕하세요");

            // when
            ChatService.ConversationWithCreatedFlag result = chatService.createConversation(1L, request);

            // then
            assertThat(result.created()).isTrue();
            assertThat(result.conversation().getId()).isEqualTo(100L);
            assertThat(result.conversation().getOtherUserNickname()).isEqualTo("유저2");

            verify(conversationRepository).saveAndFlush(any(Conversation.class));
            verify(messageRepository).save(any(Message.class));

            ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).saveAndFlush(convCaptor.capture());
            Conversation savedConv = convCaptor.getValue();
            assertThat(savedConv.getParticipant1().getId()).isEqualTo(1L);
            assertThat(savedConv.getParticipant2().getId()).isEqualTo(2L);
            assertThat(savedConv.getLastMessageContent()).isEqualTo("안녕하세요");
        }

        @Test
        void 기존_대화가_있으면_메시지만_추가하고_last_message가_업데이트된다() {
            // given
            Conversation existing = Conversation.create(user1, user2, "이전 메시지");
            ReflectionTestUtils.setField(existing, "id", 100L);
            LocalDateTime oldMessageAt = existing.getLastMessageAt();

            when(conversationRepository.findByParticipants(1L, 2L)).thenReturn(Optional.of(existing));
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 201L);
                return m;
            });

            CreateConversationRequest request = new CreateConversationRequest(2L, "새 메시지");

            // when
            ChatService.ConversationWithCreatedFlag result = chatService.createConversation(1L, request);

            // then
            assertThat(result.created()).isFalse();
            verify(conversationRepository, never()).saveAndFlush(any(Conversation.class));
            verify(messageRepository).save(any(Message.class));
            assertThat(existing.getLastMessageContent()).isEqualTo("새 메시지");
            assertThat(existing.getLastMessageAt()).isAfterOrEqualTo(oldMessageAt);
        }

        @Test
        void 기존_대화에_sendMessage로_메시지_전송시_last_message가_업데이트된다() {
            // given
            Conversation conversation = Conversation.create(user1, user2, "이전 ���시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);

            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 300L);
                return m;
            });

            SendMessageRequest request = new SendMessageRequest("추가 메시지");

            // when
            MessageResponse response = chatService.sendMessage(1L, 100L, request);

            // then
            assertThat(response.getId()).isEqualTo(300L);
            assertThat(response.getContent()).isEqualTo("추가 메시지");
            assertThat(response.getSenderNickname()).isEqualTo("유저1");
            assertThat(conversation.getLastMessageContent()).isEqualTo("추가 메시지");
        }

        @Test
        void 읽음_처리시_bulk_update가_호출된다() {
            // given
            Conversation conversation = Conversation.create(user1, user2, "메시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);

            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
            when(messageRepository.markAllAsReadInConversation(100L, 2L)).thenReturn(3);

            // when
            chatService.markAsRead(2L, 100L);

            // then
            verify(messageRepository).markAllAsReadInConversation(100L, 2L);
        }
    }

    @Nested
    class 접근_제어 {

        @Test
        void 참여자가_아닌_유저가_메시지_조회시_예외_발생() {
            // given
            Conversation conversation = Conversation.create(user1, user2, "메시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);

            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));

            // when / then
            assertThatThrownBy(() -> chatService.getMessages(3L, 100L, null, 20))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        }

        @Test
        void 참여자가_아닌_유저가_메시지_전송시_예외_발생() {
            // given
            Conversation conversation = Conversation.create(user1, user2, "메시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);

            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));

            // when / then
            assertThatThrownBy(() -> chatService.sendMessage(3L, 100L, new SendMessageRequest("침입")))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        }

        @Test
        void 참여자가_아닌_유저가_읽음_처리시_예외_발생() {
            // given
            Conversation conversation = Conversation.create(user1, user2, "메시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);

            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));

            // when / then
            assertThatThrownBy(() -> chatService.markAsRead(3L, 100L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        }
    }

    @Nested
    class 예외_케이스 {

        @Test
        void 자기_자신에게_메시지_전송시_예외_발생() {
            // given
            CreateConversationRequest request = new CreateConversationRequest(1L, "나에게");

            // when / then
            assertThatThrownBy(() -> chatService.createConversation(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CANNOT_MESSAGE_SELF));
        }

        @Test
        void 탈퇴한_유저에게_대화_생성시_예외_발생() {
            // given
            user2.withdraw();

            when(conversationRepository.findByParticipants(1L, 2L)).thenReturn(Optional.empty());

            CreateConversationRequest request = new CreateConversationRequest(2L, "안녕하세요");

            // when / then
            assertThatThrownBy(() -> chatService.createConversation(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CANNOT_MESSAGE_WITHDRAWN_USER));
        }

        @Test
        void 탈퇴한_유저에게_기존_대화에서_메시지_전송시_예외_발생() {
            // given
            Conversation conversation = Conversation.create(user1, user2, "이전 메시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);
            user2.withdraw();

            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));

            // when / then
            assertThatThrownBy(() -> chatService.sendMessage(1L, 100L, new SendMessageRequest("메시지")))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CANNOT_MESSAGE_WITHDRAWN_USER));
        }
    }

    @Nested
    class 알림_연동 {

        @Test
        void 대화_생성시_NEW_MESSAGE_알림_이벤트가_발행된다() {
            // given
            when(conversationRepository.findByParticipants(1L, 2L)).thenReturn(Optional.empty());
            when(conversationRepository.saveAndFlush(any(Conversation.class))).thenAnswer(invocation -> {
                Conversation c = invocation.getArgument(0);
                ReflectionTestUtils.setField(c, "id", 100L);
                return c;
            });
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 200L);
                return m;
            });

            CreateConversationRequest request = new CreateConversationRequest(2L, "안녕하세요");

            // when
            chatService.createConversation(1L, request);

            // then
            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            NotificationEvent event = captor.getValue();
            assertThat(event.getType()).isEqualTo(NotificationType.NEW_MESSAGE);
            assertThat(event.getSenderId()).isEqualTo(1L);
            assertThat(event.getReceiverIds()).containsExactly(2L);
            assertThat(event.getReferenceId()).isEqualTo(100L);
        }

        @Test
        void sendMessage시_상대방에게_알림_이벤트가_발행된다() {
            // given
            Conversation conversation = Conversation.create(user1, user2, "이전 메시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);

            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 300L);
                return m;
            });

            // when
            chatService.sendMessage(1L, 100L, new SendMessageRequest("메시지"));

            // then
            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            NotificationEvent event = captor.getValue();
            assertThat(event.getType()).isEqualTo(NotificationType.NEW_MESSAGE);
            assertThat(event.getSenderId()).isEqualTo(1L);
            assertThat(event.getReceiverIds()).containsExactly(2L);
            assertThat(event.getReferenceId()).isEqualTo(100L);
        }

        @Test
        void 알림_이벤트의_receiverId는_senderId와_다르다() {
            // given — user1(id=1)이 user2(id=2)에게 전송
            Conversation conversation = Conversation.create(user1, user2, "이전 메시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);

            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 300L);
                return m;
            });

            // when
            chatService.sendMessage(1L, 100L, new SendMessageRequest("메시지"));

            // then — senderId(1)과 receiverId(2)가 다름을 확인
            // NotificationService.createAndSend()에서 senderId == receiverId 필터링 적용됨
            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            NotificationEvent event = captor.getValue();
            assertThat(event.getSenderId()).isNotEqualTo(event.getReceiverIds().get(0));
        }
    }

    @Nested
    class participant_정렬 {

        @Test
        void 큰_ID가_sender일_때도_participant1에_작은_ID가_배치된다() {
            // given — user2(id=2)가 user1(id=1)에게 대화 시작
            when(conversationRepository.findByParticipants(1L, 2L)).thenReturn(Optional.empty());
            when(conversationRepository.saveAndFlush(any(Conversation.class))).thenAnswer(invocation -> {
                Conversation c = invocation.getArgument(0);
                ReflectionTestUtils.setField(c, "id", 100L);
                return c;
            });
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 200L);
                return m;
            });

            CreateConversationRequest request = new CreateConversationRequest(1L, "안녕하세요");

            // when
            chatService.createConversation(2L, request);

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).saveAndFlush(captor.capture());

            Conversation saved = captor.getValue();
            assertThat(saved.getParticipant1().getId()).isEqualTo(1L);
            assertThat(saved.getParticipant2().getId()).isEqualTo(2L);
        }
    }

    @Nested
    class 동시성_처리 {

        @Test
        void 동시_대화_생성시_UNIQUE_제약_위반이면_기존_대화를_반환한다() {
            // given
            when(conversationRepository.findByParticipants(1L, 2L))
                    .thenReturn(Optional.empty());
            when(conversationRepository.saveAndFlush(any(Conversation.class)))
                    .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

            Conversation raceWinner = Conversation.create(user1, user2, "먼저 생성된 메시지");
            ReflectionTestUtils.setField(raceWinner, "id", 100L);

            // 두 번째 findByParticipants 호출 (catch 블록)에서 기존 대화 반환
            when(conversationRepository.findByParticipants(1L, 2L))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(raceWinner));

            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                Message m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 200L);
                return m;
            });

            CreateConversationRequest request = new CreateConversationRequest(2L, "안녕하세요");

            // when
            ChatService.ConversationWithCreatedFlag result = chatService.createConversation(1L, request);

            // then — 500 에러 대신 정상 반환, created=false
            assertThat(result.created()).isFalse();
            assertThat(result.conversation().getId()).isEqualTo(100L);
            verify(messageRepository).save(any(Message.class));
        }
    }

    @Nested
    class cursor_페이징 {

        private Conversation conversation;

        @BeforeEach
        void setUpConversation() {
            conversation = Conversation.create(user1, user2, "메시지");
            ReflectionTestUtils.setField(conversation, "id", 100L);
            when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
        }

        private Message createMessage(Long id, String content) {
            Message m = Message.create(conversation, user1, content);
            ReflectionTestUtils.setField(m, "id", id);
            return m;
        }

        @Test
        void before가_null이면_최신_메시지부터_반환한다() {
            // given — ID 역순으로 3개 반환 (size=3 요청, 3+1=4개 쿼리, 3개만 반환됨 → hasNext=false)
            List<Message> messages = List.of(
                    createMessage(30L, "세 번째"),
                    createMessage(20L, "두 번째"),
                    createMessage(10L, "첫 번째")
            );
            when(messageRepository.findByConversationIdOrderByIdDesc(eq(100L), any(Pageable.class)))
                    .thenReturn(messages);

            // when
            CursorPageResponse<MessageResponse> result = chatService.getMessages(1L, 100L, null, 3);

            // then — reverse되어 시간순(오래된 → 최신)으로 반환
            assertThat(result.getItems()).hasSize(3);
            assertThat(result.getItems().get(0).getId()).isEqualTo(10L);
            assertThat(result.getItems().get(1).getId()).isEqualTo(20L);
            assertThat(result.getItems().get(2).getId()).isEqualTo(30L);
            assertThat(result.isHasNext()).isFalse();
            assertThat(result.getNextCursor()).isNull();
        }

        @Test
        void before가_있으면_해당_ID_이전_메시지만_반환한다() {
            // given — before=30, size=2 → ID < 30인 것 중 최신 2+1=3개 쿼리
            List<Message> messages = List.of(
                    createMessage(20L, "두 번째"),
                    createMessage(10L, "첫 번째"),
                    createMessage(5L, "가장 오래된")
            );
            when(messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(
                    eq(100L), eq(30L), any(Pageable.class)))
                    .thenReturn(messages);

            // when
            CursorPageResponse<MessageResponse> result = chatService.getMessages(1L, 100L, 30L, 2);

            // then — 3개 반환됨 > size(2) → hasNext=true, 2개만 반환, reverse 적용
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getItems().get(0).getId()).isEqualTo(10L);
            assertThat(result.getItems().get(1).getId()).isEqualTo(20L);
            assertThat(result.isHasNext()).isTrue();
            assertThat(result.getNextCursor()).isEqualTo(10L);
        }

        @Test
        void 메시지가_size_이하이면_hasNext는_false이다() {
            // given — size=5 요청, 2개만 존재
            List<Message> messages = List.of(
                    createMessage(20L, "두 번째"),
                    createMessage(10L, "첫 번째")
            );
            when(messageRepository.findByConversationIdOrderByIdDesc(eq(100L), any(Pageable.class)))
                    .thenReturn(messages);

            // when
            CursorPageResponse<MessageResponse> result = chatService.getMessages(1L, 100L, null, 5);

            // then
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.isHasNext()).isFalse();
            assertThat(result.getNextCursor()).isNull();
        }
    }
}
