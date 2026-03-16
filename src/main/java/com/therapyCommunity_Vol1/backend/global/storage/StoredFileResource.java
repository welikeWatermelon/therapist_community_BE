package com.therapyCommunity_Vol1.backend.global.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.io.Resource;

@Getter
@AllArgsConstructor
public class StoredFileResource {

    private Resource resource;
    private String contentType;
    private String originalFilename;
}
