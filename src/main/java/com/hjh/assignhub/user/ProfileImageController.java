package com.hjh.assignhub.user;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.auth.LoginSessionRefresher;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.user.ProfileImageService.ProfileImage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// 프로필 사진 변경 · 삭제 · 보기 — 항상 로그인한 본인 사진만 다룬다 (다른 사용자 id를 받지 않음)
@Controller
@RequestMapping("/profile/image")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageService profileImageService;
    private final LoginSessionRefresher loginSessionRefresher;

    @PostMapping
    public String change(@AuthenticationPrincipal LoginUser loginUser,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {
        try {
            User updated = profileImageService.change(loginUser.getId(), image);
            loginSessionRefresher.refresh(updated, request, response); // 상단바 아바타 바로 반영
            redirectAttributes.addFlashAttribute("successMessage", "프로필 사진을 변경했습니다.");
        } catch (FormFieldException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/delete")
    public String remove(@AuthenticationPrincipal LoginUser loginUser,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {
        User updated = profileImageService.remove(loginUser.getId());
        loginSessionRefresher.refresh(updated, request, response);
        redirectAttributes.addFlashAttribute("successMessage", "프로필 사진을 삭제했습니다.");
        return "redirect:/profile";
    }

    // 이미지 주소에 ?v=(경로 해시)를 붙여 쓰므로, 사진을 바꾸면 주소가 달라져 캐시가 자동으로 갱신된다
    @GetMapping
    public ResponseEntity<Resource> show(@AuthenticationPrincipal LoginUser loginUser) {
        ProfileImage image = profileImageService.load(loginUser.getId());
        return ResponseEntity.ok()
                .contentType(image.mediaType())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(image.resource());
    }
}
