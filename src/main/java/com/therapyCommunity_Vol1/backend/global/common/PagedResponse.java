package com.therapyCommunity_Vol1.backend.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class PagedResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;

    public static <T> PagedResponse<T> from(Page<?> pageResult, List<T> items) {
        return new PagedResponse<>(
                items,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.hasNext()
        );
    }
}
