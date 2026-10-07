package com.hjh.assignhub.user;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.common.FormFieldException;

import lombok.RequiredArgsConstructor;

// 프로필 사진 — 본인 것만 변경·삭제·조회 (컨트롤러가 로그인 사용자 id만 넘긴다)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileImageService {

    public static final long MAX_SIZE = 2 * 1024 * 1024; // 2MB
    private static final String FIELD = "image";
    private static final String CATEGORY = "profiles";

    // SVG는 이미지처럼 보여도 안에 스크립트를 넣을 수 있어 제외 (브라우저에서 열면 XSS)
    private static final Map<String, MediaType> TYPES = Map.of(
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF);

    private final UserRepository userRepository;
    private final FileStorage fileStorage;

    public record ProfileImage(Resource resource, MediaType mediaType) {
    }

    @Transactional
    public User change(Long userId, MultipartFile file) {
        validate(file);
        User user = userRepository.findById(userId).orElseThrow();
        String oldPath = user.getProfileImagePath();
        user.changeProfileImage(fileStorage.store(file, CATEGORY));
        fileStorage.delete(oldPath);
        return user;
    }

    @Transactional
    public User remove(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        String oldPath = user.getProfileImagePath();
        user.changeProfileImage(null);
        fileStorage.delete(oldPath);
        return user;
    }

    // 사진이 없으면 FileStorage가 404
    public ProfileImage load(Long userId) {
        String path = userRepository.findById(userId).orElseThrow().getProfileImagePath();
        Resource resource = fileStorage.load(path);
        return new ProfileImage(resource, TYPES.get(extension(path)));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FormFieldException(FIELD, "사진 파일을 선택하세요.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new FormFieldException(FIELD, "프로필 사진은 2MB 이하만 올릴 수 있습니다.");
        }
        if (!TYPES.containsKey(extension(file.getOriginalFilename()))) {
            throw new FormFieldException(FIELD, "png, jpg, gif 형식의 사진만 올릴 수 있습니다.");
        }
        // 확장자만 바꾼 가짜 이미지 차단 — 실제로 이미지로 읽히는지 확인
        try (InputStream in = file.getInputStream()) {
            if (ImageIO.read(in) == null) {
                throw new FormFieldException(FIELD, "이미지 파일이 아니거나 손상된 파일입니다.");
            }
        } catch (IOException e) {
            throw new FormFieldException(FIELD, "이미지 파일이 아니거나 손상된 파일입니다.");
        }
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
