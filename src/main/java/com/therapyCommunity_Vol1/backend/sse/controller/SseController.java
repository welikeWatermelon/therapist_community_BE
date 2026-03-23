package com.therapyCommunity_Vol1.backend.sse.controller;

import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.sse.manager.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterManager sseEmitterManager;

    // 프론트 흐름 : 로그인 성공 -> JWT 토큰 저장 -> SSE 연결 시작(해당 메서드) -> 알림 수신 대기
    // 재연결도 프론트에서
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return sseEmitterManager.createEmitter(userDetails.getUser().getId());
    }
}
