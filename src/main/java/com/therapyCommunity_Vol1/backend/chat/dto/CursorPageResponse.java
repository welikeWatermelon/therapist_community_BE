package com.therapyCommunity_Vol1.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CursorPageResponse<T> {

    private List<T> items;
    private int size;
    private boolean hasNext;
    private Long nextCursor;
}
