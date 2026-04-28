package com.therapyCommunity_Vol1.backend.knowledge.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.knowledge.domain.KnowledgeDocument;
import com.therapyCommunity_Vol1.backend.knowledge.dto.KnowledgeDocumentResponse;
import com.therapyCommunity_Vol1.backend.knowledge.service.KnowledgeDocumentService;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    private final KnowledgeDocumentService documentService;

    @Operation(summary = "지식 문서 업로드")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) TherapyArea therapyArea,
            @RequestParam(defaultValue = "UPLOAD") String sourceType,
            @RequestParam(defaultValue = "LICENSED") String rightsStatus
    ) {
        validateKnowledgeDocument(file);
        KnowledgeDocumentResponse response = documentService.upload(file, title, therapyArea, sourceType, rightsStatus);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "지식 문서 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<KnowledgeDocumentResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<KnowledgeDocument> result = documentService.list(PageRequest.of(page, size));
        List<KnowledgeDocumentResponse> items = result.getContent().stream()
                .map(KnowledgeDocumentResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result, items)));
    }

    @Operation(summary = "지식 문서 상세")
    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> detail(@PathVariable Long documentId) {
        return ResponseEntity.ok(ApiResponse.success(
                KnowledgeDocumentResponse.from(documentService.findOrThrow(documentId))));
    }

    @Operation(summary = "실패한 문서 재인덱싱")
    @PostMapping("/{documentId}/retry")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> retry(@PathVariable Long documentId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.retry(documentId)));
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
}
