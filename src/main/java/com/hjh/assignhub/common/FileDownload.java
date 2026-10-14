package com.hjh.assignhub.common;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class FileDownload {

    private FileDownload() {
    }

    // 한글 파일명이 깨지지 않도록 UTF-8로 인코딩
    public static ResponseEntity<Resource> attachment(Resource resource, String fileName) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // 미리보기 — 내려받지 않고 브라우저 안에서 연다 (이미지 · PDF만 사용)
    public static ResponseEntity<Resource> inline(Resource resource, String fileName, MediaType mediaType) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(resource);
    }
}
