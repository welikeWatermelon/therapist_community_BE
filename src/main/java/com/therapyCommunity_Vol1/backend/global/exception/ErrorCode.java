package com.therapyCommunity_Vol1.backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_403", "접근이 권한 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409","이미 존재하 데이터입니다"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404","사용자를 찾을 수 없습니다."),
    NICKNAME_ALREADY_USED(HttpStatus.CONFLICT, "USER_409_NICKNAME", "이미 사용 중인 닉네임입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_401", "비밀번호가 올바르지 않습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH_INVALID", "유효하지 않은 리프레시 토큰입니다"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH_EXPIRED", "만료된 리프레시 토큰입니다."),


    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_404", "게시글을 찾을 수 없습니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_403", "게시글에 대한 권한이 없습니다."),
    INVALID_POST_ATTACHMENT(HttpStatus.BAD_REQUEST, "POST_400_ATTACHMENT", "유효하지 않은 첨부 파일입니다."),
    POST_ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_ATTACHMENT_404", "첨부 파일을 찾을 수 없습니다."),
    POST_ATTACHMENT_RESOURCE_ONLY(HttpStatus.BAD_REQUEST, "POST_400_RESOURCE_ONLY", "자료형 게시글에만 첨부 파일을 업로드할 수 있습니다."),

    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_404", "댓글을 찾을 수 없습니다."),
    COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "COMMENT_403", "댓글에 대한 권한이 없습니다."),
    INVALID_PARENT_COMMENT(HttpStatus.BAD_REQUEST, "COMMENT_400_PARENT", "유효하지 않은 부모 댓글입니다."),
    COMMENT_DEPTH_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "COMMENT_400_DEPTH", "대댓글까지만 작성할 수 있습니다."),

    THERAPIST_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "THERAPIST_404", "치료사 인증 신청을 찾을 수 없습니다."),
    THERAPIST_VERIFICATION_ALREADY_PENDING(HttpStatus.CONFLICT, "THERAPIST_409_PENDING", "이미 심사 중인 치료사 인증 신청이 있습니다."),
    THERAPIST_ALREADY_VERIFIED(HttpStatus.CONFLICT, "THERAPIST_409_VERIFIED", "이미 치료사 인증이 완료된 사용자입니다."),
    LICENSE_CODE_ALREADY_USED(HttpStatus.CONFLICT, "THERAPIST_409_LICENSE_CODE", "이미 사용 중인 치료사 번호입니다."),
    INVALID_LICENSE_IMAGE(HttpStatus.BAD_REQUEST, "THERAPIST_400_IMAGE", "유효하지 않은 치료사 증빙 이미지입니다."),
    THERAPIST_VERIFICATION_NOT_PENDING(HttpStatus.CONFLICT, "THERAPIST_409_NOT_PENDING", "대기 중(PENDING) 상태의 신청만 처리할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
