package com.therapyCommunity_Vol1.backend.autocomment.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AutoCommentRequestedEvent {
    private final Long jobId;
}
