package com.therapyCommunity_Vol1.backend.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

@Getter
@AllArgsConstructor
public class CursorPagedResponse<T> {

    private List<T> items;
    private String nextCursor;
    private boolean hasNext;
    private int size;

    public static <T> CursorPagedResponse<T> of(List<T> items, int requestedSize, Function<T, String> cursorExtractor) {
        boolean hasNext = items.size() > requestedSize;
        List<T> trimmed = hasNext ? items.subList(0, requestedSize) : items;
        String nextCursor = hasNext ? cursorExtractor.apply(trimmed.get(trimmed.size() - 1)) : null;
        return new CursorPagedResponse<>(trimmed, nextCursor, hasNext, requestedSize);
    }
}
