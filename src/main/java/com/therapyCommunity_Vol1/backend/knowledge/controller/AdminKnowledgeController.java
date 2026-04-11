package com.therapyCommunity_Vol1.backend.knowledge.controller;

import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.knowledge.config.KnowledgeProperties;
import com.therapyCommunity_Vol1.backend.knowledge.domain.DocumentStatus;
import com.therapyCommunity_Vol1.backend.knowledge.domain.KnowledgeDocument;
import com.therapyCommunity_Vol1.backend.knowledge.dto.KnowledgeDocumentResponse;
import com.therapyCommunity_Vol1.backend.knowledge.event.KnowledgeDocumentCreatedEvent;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeDocumentRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Tag(name = "지식 관리 (Admin)", description = "RAG용 지식 문서 인입/관리")
@RestController
@RequestMapping("/api/v1/admin/knowledge/documents")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "text/plain", "text/markdown", "text/html"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "txt", "md", "html"
    );

    private final KnowledgeDocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final KnowledgeProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "지식 문서 업로드", description = "PDF/txt/md/html 문서를 업로드하고 인덱싱을 시작합니다")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) TherapyArea therapyArea,
            @RequestParam(defaultValue = "UPLOAD") String sourceType,
            @RequestParam(defaultValue = "LICENSED") String rightsStatus
    ) {
        validateKnowledgeDocument(file);

        String checksum = computeChecksum(file);
        if (documentRepository.existsByChecksum(checksum)) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        StoredFileInfo storedFile = fileStorageService.storeKnowledgeDocument(file);

        KnowledgeDocument document = KnowledgeDocument.create(
                sourceType, title, therapyArea, rightsStatus, checksum, "TEXT",
                storedFile.getStoredPath(), storedFile.getOriginalFilename(),
                storedFile.getContentType(), file.getSize()
        );

        if (!properties.isEnabled()) {
            document.markFailed("FEATURE_DISABLED", "Knowledge ingestion is disabled");
        }

        KnowledgeDocument saved = documentRepository.save(document);

        if (properties.isEnabled()) {
            eventPublisher.publishEvent(new KnowledgeDocumentCreatedEvent(saved.getId()));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(KnowledgeDocumentResponse.from(saved)));
    }

    @Operation(summary = "지식 문서 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<KnowledgeDocumentResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<KnowledgeDocument> result = documentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        List<KnowledgeDocumentResponse> items = result.getContent().stream()
                .map(KnowledgeDocumentResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result, items)));
    }

    @Operation(summary = "지식 문서 상세")
    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> detail(@PathVariable Long documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.success(KnowledgeDocumentResponse.from(doc)));
    }

    @Operation(summary = "실패한 문서 재인덱싱")
    @PostMapping("/{documentId}/retry")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> retry(@PathVariable Long documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (doc.getStatus() != DocumentStatus.FAILED) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        doc.retry();
        documentRepository.save(doc);
        eventPublisher.publishEvent(new KnowledgeDocumentCreatedEvent(doc.getId()));

        return ResponseEntity.ok(ApiResponse.success(KnowledgeDocumentResponse.from(doc)));
    }

    private void validateKnowledgeDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex >= 0) {
                String ext = filename.substring(dotIndex + 1).toLowerCase();
                if (!ALLOWED_EXTENSIONS.contains(ext)) {
                    throw new CustomException(ErrorCode.INVALID_INPUT);
                }
            }
        }
    }

    private String computeChecksum(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
