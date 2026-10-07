package com.hjh.assignhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

import com.hjh.assignhub.common.FilePreview.Preview;
import com.hjh.assignhub.common.FilePreview.Type;

// 스프링 없이 미리보기 형식 판별 · 텍스트 읽기만 검사
class FilePreviewTest {

    @Test
    @DisplayName("형식 판별: 이미지 · PDF · 텍스트(코드 포함) · 미지원(hwp · docx · zip)")
    void typeOf() {
        assertThat(FilePreview.typeOf("photo.PNG")).isEqualTo(Type.IMAGE);
        assertThat(FilePreview.typeOf("report.pdf")).isEqualTo(Type.PDF);
        assertThat(FilePreview.typeOf("Main.java")).isEqualTo(Type.TEXT);
        assertThat(FilePreview.typeOf("README.md")).isEqualTo(Type.TEXT);
        assertThat(FilePreview.typeOf("과제.hwp")).isEqualTo(Type.NONE);
        assertThat(FilePreview.typeOf("project.zip")).isEqualTo(Type.NONE);
        assertThat(FilePreview.inlineMediaType("report.pdf")).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(FilePreview.inlineMediaType("Main.java")).isNull();
    }

    @Test
    @DisplayName("한글 텍스트: UTF-8(BOM 포함) · 메모장 기본 인코딩(MS949) 모두 깨지지 않는다")
    void decodeKorean() {
        String text = "안녕하세요 과제입니다\nint 점수 = 100;";
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        byte[] utf8WithBom = new byte[bom.length + utf8.length];
        System.arraycopy(bom, 0, utf8WithBom, 0, bom.length);
        System.arraycopy(utf8, 0, utf8WithBom, bom.length, utf8.length);

        assertThat(read("a.txt", utf8).text()).isEqualTo(text);
        assertThat(read("a.txt", utf8WithBom).text()).isEqualTo(text);
        assertThat(read("a.txt", text.getBytes(Charset.forName("MS949"))).text()).isEqualTo(text);
    }

    @Test
    @DisplayName("200KB가 넘으면 앞부분만 읽고, 한글 글자 중간에서 잘려도 UTF-8로 올바르게 읽는다")
    void truncateLargeFile() {
        // "가"(UTF-8 3바이트)를 반복해 200KB 경계가 글자 중간에 걸리게 만든다
        byte[] big = "가".repeat(FilePreview.MAX_TEXT_BYTES / 3 + 100).getBytes(StandardCharsets.UTF_8);

        Preview preview = read("big.txt", big);

        assertThat(preview.truncated()).isTrue();
        assertThat(preview.text()).matches("가+");
        assertThat(preview.text().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(FilePreview.MAX_TEXT_BYTES);
    }

    @Test
    @DisplayName("이미지 · PDF · 미지원 형식은 내용을 읽지 않는다")
    void nonTextNotRead() {
        Preview pdf = read("report.pdf", new byte[] {1, 2, 3});
        assertThat(pdf.type()).isEqualTo(Type.PDF);
        assertThat(pdf.text()).isNull();
        assertThat(read("x.hwp", new byte[] {1}).type()).isEqualTo(Type.NONE);
    }

    private Preview read(String name, byte[] bytes) {
        return FilePreview.of(name, new ByteArrayResource(Arrays.copyOf(bytes, bytes.length)));
    }
}
