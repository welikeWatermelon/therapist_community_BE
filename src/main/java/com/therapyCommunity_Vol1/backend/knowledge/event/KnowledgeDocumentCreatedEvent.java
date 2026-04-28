package com.therapyCommunity_Vol1.backend.knowledge.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KnowledgeDocumentCreatedEvent {
    private final Long documentId;
}
