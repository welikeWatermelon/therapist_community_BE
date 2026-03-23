package com.therapyCommunity_Vol1.backend.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.outbox.domain.OutboxEvent;
import com.therapyCommunity_Vol1.backend.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEvent createEvent(String aggregateType, String aggregateId,
                                    String eventType, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent event = OutboxEvent.create(aggregateType, aggregateId, eventType, payloadJson);
            return outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload", e);
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }

    @Transactional
    public void markAsCompleted(OutboxEvent event) {
        event.markAsCompleted();
        outboxEventRepository.save(event);
    }

    @Transactional
    public void markAsFailed(OutboxEvent event) {
        event.markAsFailed();
        outboxEventRepository.save(event);
    }
}
