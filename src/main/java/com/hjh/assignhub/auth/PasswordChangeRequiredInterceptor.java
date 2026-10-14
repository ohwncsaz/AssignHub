package com.hjh.assignhub.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 강사가 만든 학생 계정은 초기 비밀번호(학번)를 바꾸기 전까지 비밀번호 변경 화면만 쓸 수 있다
// 화면 링크를 숨기는 것만으로는 주소창 직접 입력을 막을 수 없으므로 서버에서 모든 요청을 검사
@Component
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    static final String PASSWORD_PAGE = "/profile/password";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)
                || !loginUser.isPasswordChangeRequired()) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // 비밀번호 변경 화면의 상단바 아바타(GET /profile/image)도 보여야 하므로 사진 보기만 함께 허용
        boolean avatar = path.equals("/profile/image") && "GET".equals(request.getMethod());
        if (path.equals(PASSWORD_PAGE) || avatar || path.startsWith("/error")) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + PASSWORD_PAGE);
        return false;
    }
}
