package com.therapyCommunity_Vol1.backend.message.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class BroadcastResponse {

    private UUID broadcastId;
    private int recipientCount;
}
