package com.hjh.assignhub.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// 첨부파일을 로컬 폴더에 저장 — DB에는 업로드 폴더 기준 상대 경로만 저장한다
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    // {category}/{uuid}/{원본파일명} — 원본 이름은 다운로드 시 그대로 쓰고, uuid 폴더로 이름 충돌을 피한다
    public String store(MultipartFile file, String category) {
        String relative = category + "/" + UUID.randomUUID() + "/" + safeFileName(file.getOriginalFilename());
        Path target = resolve(relative);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("첨부파일 저장에 실패했습니다.", e);
        }
        return relative;
    }

    public Resource load(String relative) {
        if (relative == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path path = resolve(relative);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    public void delete(String relative) {
        if (relative == null) {
            return;
        }
        Path path = resolve(relative);
        try {
            Files.deleteIfExists(path);
            Files.deleteIfExists(path.getParent());
        } catch (IOException e) {
            // 파일이 남아도 기능에는 영향이 없으므로 삭제 실패는 무시
        }
    }

    private String safeFileName(String originalName) {
        try {
            Path name = Paths.get(originalName == null ? "" : originalName).getFileName();
            if (name == null || name.toString().isBlank() || name.toString().equals("..")) {
                throw new BusinessException("올바르지 않은 파일 이름입니다.");
            }
            return name.toString();
        } catch (InvalidPathException e) {
            throw new BusinessException("올바르지 않은 파일 이름입니다.");
        }
    }

    // 경로 조작(../)으로 업로드 폴더 밖을 가리키지 못하게 막는다
    private Path resolve(String relative) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return path;
    }
}
