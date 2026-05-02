package com.therapyCommunity_Vol1.backend.autocomment.service;

import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiUserIdentifier {

    private final AiCommentProperties properties;

    public boolean isAiUser(String email) {
        return properties.getAiUserEmail() != null
                && properties.getAiUserEmail().equals(email);
    }
}
