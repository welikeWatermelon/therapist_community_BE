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
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409","이미 존재하는 데이터입니다"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404","사용자를 찾을 수 없습니다."),
    NICKNAME_ALREADY_USED(HttpStatus.CONFLICT, "USER_409_NICKNAME", "이미 사용 중인 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_401", "비밀번호가 올바르지 않습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH_INVALID", "유효하지 않은 리프레시 토큰입니다"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH_EXPIRED", "만료된 리프레시 토큰입니다."),


    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_404", "게시글을 찾을 수 없습니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_403", "게시글에 대한 권한이 없습니다."),
    INVALID_POST_IMAGE(HttpStatus.BAD_REQUEST, "POST_400_IMAGE", "유효하지 않은 게시글 이미지입니다."),
    INVALID_POST_ATTACHMENT(HttpStatus.BAD_REQUEST, "POST_400_ATTACHMENT", "유효하지 않은 첨부 파일입니다."),
    POST_ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_ATTACHMENT_404", "첨부 파일을 찾을 수 없습니다."),
    POST_ATTACHMENT_RESOURCE_ONLY(HttpStatus.BAD_REQUEST, "POST_400_RESOURCE_ONLY", "자료형 게시글에만 첨부 파일을 업로드할 수 있습니다."),
    INVALID_POST_VIDEO(HttpStatus.BAD_REQUEST, "POST_400_VIDEO", "유효하지 않은 게시글 영상입니다."),
    POST_VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_VIDEO_404", "게시글 영상을 찾을 수 없습니다."),
    CONCERN_CARD_UPLOAD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "POST_400_CONCERN_UPLOAD", "고민카드 게시글에는 파일을 업로드할 수 없습니다."),
    POST_MEDIA_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "POST_400_MEDIA_LIMIT", "게시글당 미디어 개수 한도를 초과했습니다."),
    INVALID_UPLOAD_KIND(HttpStatus.BAD_REQUEST, "POST_400_UPLOAD_KIND", "유효하지 않은 업로드 유형입니다."),
    UPLOAD_NOT_FOUND_IN_S3(HttpStatus.BAD_REQUEST, "POST_400_UPLOAD_MISSING", "업로드된 객체가 스토리지에 존재하지 않습니다."),

    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_404", "댓글을 찾을 수 없습니다."),
    COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "COMMENT_403", "댓글에 대한 권한이 없습니다."),
    INVALID_PARENT_COMMENT(HttpStatus.BAD_REQUEST, "COMMENT_400_PARENT", "유효하지 않은 부모 댓글입니다."),
    COMMENT_DEPTH_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "COMMENT_400_DEPTH", "대댓글까지만 작성할 수 있습니다."),

    THERAPIST_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "THERAPIST_404", "치료사 인증 신청을 찾을 수 없습니다."),
    THERAPIST_VERIFICATION_ALREADY_PENDING(HttpStatus.CONFLICT, "THERAPIST_409_PENDING", "이미 심사 중인 치료사 인증 신청이 있습니다."),
    THERAPIST_ALREADY_VERIFIED(HttpStatus.CONFLICT, "THERAPIST_409_VERIFIED", "이미 치료사 인증이 완료된 사용자입니다."),
    LICENSE_CODE_ALREADY_USED(HttpStatus.CONFLICT, "THERAPIST_409_LICENSE_CODE", "이미 사용 중인 치료사 번호입니다."),
    INVALID_LICENSE_IMAGE(HttpStatus.BAD_REQUEST, "THERAPIST_400_IMAGE", "유효하지 않은 치료사 증빙 이미지입니다."),
    THERAPIST_VERIFICATION_NOT_PENDING(HttpStatus.CONFLICT, "THERAPIST_409_NOT_PENDING", "대기 중(PENDING) 상태의 신청만 처리할 수 있습니다."),

    THERAPIST_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_403_THERAPIST", "치료사 인증이 필요합니다."),

    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_500", "파일 저장/삭제 중 오류가 발생했습니다."),
    UPLOAD_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "UPLOAD_429_RATE", "업로드 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    UPLOAD_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "UPLOAD_429_DAILY", "오늘의 업로드 한도를 초과했습니다."),
    UPLOAD_MIME_MISMATCH(HttpStatus.BAD_REQUEST, "UPLOAD_400_MIME", "파일 내용과 선언된 타입이 일치하지 않습니다."),
    UPLOAD_CONFIRM_CONFLICT(HttpStatus.CONFLICT, "UPLOAD_409_CONFIRM", "업로드 confirm이 이미 처리 중이거나 완료되었습니다. 같은 storedKey로 다시 시도해주세요."),

    ACCOUNT_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429", "로그인 시도가 너무 많습니다. 30분 후 다시 시도해주세요."),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_404", "알림을 찾을 수 없습니다."),
    SSE_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SSE_500", "SSE 연결 중 오류가 발생했습니다."),

    INVALID_SORT_TYPE(HttpStatus.BAD_REQUEST, "POST_400_SORT", "이 엔드포인트에서 지원하지 않는 정렬 방식입니다."),

    FOLLOW_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FOLLOW_400_SELF", "자기 자신을 팔로우할 수 없습니다."),
    FOLLOW_TARGET_NOT_THERAPIST(HttpStatus.BAD_REQUEST, "FOLLOW_400_TARGET", "치료사만 팔로우할 수 있습니다."),
    FOLLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "FOLLOW_404", "팔로우 관계를 찾을 수 없습니다."),

    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE_404", "쪽지를 찾을 수 없습니다."),
    MESSAGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "MESSAGE_403", "쪽지에 대한 권한이 없습니다."),
    CANNOT_SEND_MESSAGE_TO_SELF(HttpStatus.BAD_REQUEST, "MESSAGE_400_SELF", "자기 자신에게 쪽지를 보낼 수 없습니다."),
    BROADCAST_NO_RECIPIENTS(HttpStatus.BAD_REQUEST, "MESSAGE_400_BROADCAST", "공지 쪽지 수신 대상이 없습니다."),

    JOB_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "JOBPOST_404", "구인공고를 찾을 수 없습니다."),
    JOB_POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "JOBPOST_403", "구인공고에 대한 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
