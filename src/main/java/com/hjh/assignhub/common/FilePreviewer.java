package com.hjh.assignhub.common;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.common.FilePreview.Preview;

import lombok.RequiredArgsConstructor;

// 화면에 넣을 미리보기 정보와 inline 응답을 만든다 — 권한 확인(B5, 본인 제출)은 호출하는 컨트롤러가 먼저 한다
@Component
@RequiredArgsConstructor
public class FilePreviewer {

    private final FileStorage fileStorage;

    // 첨부가 없거나 저장 폴더에서 사라졌으면 null → 미리보기 영역을 그리지 않는다 (화면 전체가 404가 되지 않게)
    public Preview previewOf(String filePath, String fileName) {
        if (filePath == null) {
            return null;
        }
        try {
            return FilePreview.of(fileName, fileStorage.load(filePath));
        } catch (ResponseStatusException e) {
            return null;
        }
    }

    // 이미지 · PDF를 브라우저 안에서 열도록 inline으로 응답 (그 외 형식은 404)
    public ResponseEntity<Resource> inline(String filePath, String fileName) {
        MediaType mediaType = FilePreview.inlineMediaType(fileName);
        if (mediaType == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return FileDownload.inline(fileStorage.load(filePath), fileName, mediaType);
    }
}
