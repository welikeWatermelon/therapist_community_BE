package com.therapyCommunity_Vol1.backend.knowledge.repository;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class KnowledgeChunkSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public record ChunkSearchResult(Long chunkId, Long documentId, String content, String title, double score) {}

    public List<ChunkSearchResult> findSimilarChunks(float[] queryEmbedding, TherapyArea therapyArea, int topK) {
        String vectorStr = toVectorString(queryEmbedding);

        String sql;
        Object[] params;

        if (therapyArea != null) {
            sql = """
                SELECT c.id, c.document_id, c.content, d.title,
                       1 - (c.embedding <=> ?::vector) AS score
                FROM knowledge_chunks c
                JOIN knowledge_documents d ON d.id = c.document_id
                WHERE d.status = 'READY'
                ORDER BY
                    CASE WHEN d.therapy_area = ? THEN 0 ELSE 1 END,
                    c.embedding <=> ?::vector
                LIMIT ?
                """;
            params = new Object[]{vectorStr, therapyArea.name(), vectorStr, topK};
        } else {
            sql = """
                SELECT c.id, c.document_id, c.content, d.title,
                       1 - (c.embedding <=> ?::vector) AS score
                FROM knowledge_chunks c
                JOIN knowledge_documents d ON d.id = c.document_id
                WHERE d.status = 'READY'
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """;
            params = new Object[]{vectorStr, vectorStr, topK};
        }

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ChunkSearchResult(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getString("content"),
                rs.getString("title"),
                rs.getDouble("score")
        ));
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
