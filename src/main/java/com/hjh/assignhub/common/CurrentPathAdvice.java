package com.hjh.assignhub.common;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

// 모든 화면에 현재 요청 경로를 넘겨 사이드바에서 지금 메뉴를 강조한다
// (Thymeleaf 3.1부터 템플릿에서 #request를 직접 쓸 수 없어 모델로 전달)
@ControllerAdvice
public class CurrentPathAdvice {

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
