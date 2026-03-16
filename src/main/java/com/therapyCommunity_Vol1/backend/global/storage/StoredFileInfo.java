package com.therapyCommunity_Vol1.backend.global.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoredFileInfo {

    private String storedPath;
    private String originalFilename;
    private String contentType;
}
