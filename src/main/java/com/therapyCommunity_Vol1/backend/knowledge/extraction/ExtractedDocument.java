package com.therapyCommunity_Vol1.backend.knowledge.extraction;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExtractedDocument {

    private final String plainText;
    private final String normalizedHtml;
}
