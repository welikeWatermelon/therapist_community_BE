package com.therapyCommunity_Vol1.backend.file.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoredFileInfo {

    private String storedPath;
    private String originalFilename;
    private String contentType;
}
