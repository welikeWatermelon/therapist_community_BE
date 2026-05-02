package com.therapyCommunity_Vol1.backend.knowledge.extraction;

import java.io.InputStream;

public interface DocumentExtractor {

    boolean supports(String contentType);

    ExtractedDocument extract(InputStream inputStream, String contentType) throws Exception;
}
