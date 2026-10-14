package com.hjh.assignhub.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

// 스프링 없이 FileStorage만 단독으로 검사
class FileStorageTest {

    @TempDir
    Path uploadDir;

    private FileStorage fileStorage;

    @BeforeEach
    void setUp() {
        fileStorage = new FileStorage(uploadDir.toString());
    }

    @Test
    @DisplayName("허용된 확장자는 대소문자와 상관없이 저장된다")
    void store_allowedExtension() {
        String path = fileStorage.store(file("보고서.PDF"), "assignments");

        assertThat(path).startsWith("assignments/").endsWith("/보고서.PDF");
        assertThat(fileStorage.load(path).exists()).isTrue();
    }

    @Test
    @DisplayName("실행 파일·웹 페이지 등 허용되지 않은 확장자는 첨부 입력칸 에러로 거부된다")
    void store_blockedExtension() {
        for (String name : new String[] {"virus.exe", "page.html", "script.js", "noextension"}) {
            assertThatThrownBy(() -> fileStorage.store(file(name), "assignments"))
                    .as(name)
                    .isInstanceOf(FormFieldException.class)
                    .extracting("field").isEqualTo(FileStorage.FILE_FIELD);
        }
    }

    @Test
    @DisplayName("파일 이름이 비어 있으면 500이 아니라 첨부 입력칸 에러로 거부된다")
    void store_blankFileName() {
        assertThatThrownBy(() -> fileStorage.store(file(""), "assignments"))
                .isInstanceOf(FormFieldException.class)
                .hasMessage("올바르지 않은 파일 이름입니다.");
    }

    @Test
    @DisplayName("경로가 섞인 파일 이름은 마지막 이름만 사용해 업로드 폴더 밖으로 나가지 않는다")
    void store_stripsDirectories() {
        String path = fileStorage.store(file("../../evil.txt"), "assignments");

        assertThat(path).endsWith("/evil.txt").doesNotContain("..");
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream",
                "data".getBytes(StandardCharsets.UTF_8));
    }
}
