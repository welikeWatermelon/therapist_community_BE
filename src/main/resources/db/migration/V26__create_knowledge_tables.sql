-- 지식 문서 메타
CREATE TABLE knowledge_documents (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    therapy_area VARCHAR(50),
    rights_status VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    source_uri TEXT,
    checksum VARCHAR(64) NOT NULL,
    extraction_mode VARCHAR(50) NOT NULL DEFAULT 'TEXT',
    stored_path TEXT,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    last_error_code VARCHAR(50),
    last_error_message TEXT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT uk_knowledge_documents_checksum UNIQUE (checksum)
);

CREATE INDEX idx_knowledge_documents_status_area
    ON knowledge_documents(status, therapy_area, next_attempt_at);

-- 지식 청크 (검색용)
CREATE TABLE knowledge_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT,
    embedding_model VARCHAR(100),
    embedding vector(768),
    metadata_json JSONB,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_knowledge_chunks_document
        FOREIGN KEY (document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_chunks_document_index
    ON knowledge_chunks(document_id, chunk_index);

CREATE INDEX idx_knowledge_chunks_embedding
    ON knowledge_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 문서 추출 결과 보존
CREATE TABLE knowledge_document_artifacts (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    artifact_type VARCHAR(50) NOT NULL,
    content_text TEXT,
    content_json JSONB,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_knowledge_artifacts_document
        FOREIGN KEY (document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_artifacts_document
    ON knowledge_document_artifacts(document_id);
