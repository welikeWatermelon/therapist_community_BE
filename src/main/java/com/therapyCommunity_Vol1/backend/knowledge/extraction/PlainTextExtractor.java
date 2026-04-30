package com.therapyCommunity_Vol1.backend.knowledge.extraction;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class PlainTextExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "text/plain", "text/markdown", "text/html"
    );

    @Override
    public boolean supports(String contentType) {
        return contentType != null && SUPPORTED.contains(contentType.toLowerCase());
    }

    @Override
    public ExtractedDocument extract(InputStream inputStream, String contentType) throws Exception {
        String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        String plainText;
        if ("text/html".equalsIgnoreCase(contentType)) {
            plainText = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        } else {
            plainText = text;
        }

        return new ExtractedDocument(plainText, text);
    }
}
