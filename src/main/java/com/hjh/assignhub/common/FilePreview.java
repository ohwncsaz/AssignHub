package com.hjh.assignhub.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

// 첨부파일 미리보기 — 형식별로 보여주는 방법이 다르다
//  IMAGE · PDF : 브라우저가 직접 그리므로 inline 응답으로 내려준다
//  TEXT        : 서버에서 읽어 화면에 글자로 출력 (th:text로 이스케이프 → 파일 안의 <script>도 실행되지 않음)
//  NONE        : hwp · docx · zip 등 브라우저가 열 수 없는 형식 → 다운로드 안내
public final class FilePreview {

    public enum Type { IMAGE, PDF, TEXT, NONE }

    public static final int MAX_TEXT_BYTES = 200 * 1024; // 200KB 넘으면 앞부분만

    private static final Map<String, MediaType> INLINE_TYPES = Map.of(
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF,
            "pdf", MediaType.APPLICATION_PDF);

    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "java", "csv");

    // 메모장 등 Windows 기본 한글 인코딩 (EUC-KR 확장)
    private static final Charset KOREAN_WINDOWS = Charset.forName("MS949");

    public record Preview(Type type, String text, boolean truncated) {
    }

    private FilePreview() {
    }

    public static Type typeOf(String fileName) {
        String ext = extension(fileName);
        if (TEXT_EXTENSIONS.contains(ext)) {
            return Type.TEXT;
        }
        if ("pdf".equals(ext)) {
            return Type.PDF;
        }
        return INLINE_TYPES.containsKey(ext) ? Type.IMAGE : Type.NONE;
    }

    // 이미지 · PDF만 inline으로 내려준다 (그 외는 null → 미리보기 주소에서 404)
    public static MediaType inlineMediaType(String fileName) {
        return INLINE_TYPES.get(extension(fileName));
    }

    public static Preview of(String fileName, Resource resource) {
        Type type = typeOf(fileName);
        if (type != Type.TEXT) {
            return new Preview(type, null, false);
        }
        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_TEXT_BYTES + 1);
            boolean truncated = bytes.length > MAX_TEXT_BYTES;
            if (truncated) {
                bytes = Arrays.copyOf(bytes, MAX_TEXT_BYTES);
            }
            return new Preview(Type.TEXT, decode(bytes, truncated), truncated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // UTF-8로 먼저 읽어 보고, 맞지 않으면 MS949(메모장 한글)로 읽는다
    static String decode(byte[] bytes, boolean truncated) {
        int start = hasUtf8Bom(bytes) ? 3 : 0;
        // 200KB에서 잘랐다면 마지막 글자가 중간에 끊겼을 수 있어 1~3바이트를 줄여 가며 시도
        int maxCut = truncated ? 3 : 0;
        for (int cut = 0; cut <= maxCut && bytes.length - start - cut >= 0; cut++) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, start, bytes.length - start - cut))
                        .toString();
            } catch (CharacterCodingException e) {
                // 다음 방법으로
            }
        }
        return new String(bytes, KOREAN_WINDOWS);
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
