package com.therapyCommunity_Vol1.backend.knowledge.dto;

public record ChunkSearchResult(Long chunkId, Long documentId, String content, String title, double score) {}
