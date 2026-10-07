package com.hjh.assignhub.common;

import java.net.URI;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    // 첨부파일 용량 초과 — 원래 화면으로 돌려보내고 알림 표시 (flash는 redirect 시 RedirectView가 저장)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(HttpServletRequest request) {
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("errorMessage", "첨부파일은 10MB 이하만 올릴 수 있습니다.");
        return "redirect:" + backPath(request);
    }

    // Referer의 경로만 사용 — 외부 주소로 redirect 되지 않도록
    private String backPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "/";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
