package com.hjh.assignhub.auth;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// CSRF 토큰 오류는 대부분 "로그인 화면이나 폼을 오래 열어 둬서 세션이 만료된 경우"다
// 이때 "권한 없음(403)"을 보여주면 사용자가 이유를 알 수 없으므로 로그인 화면으로 보내 다시 시도하게 한다
// 그 외 권한 오류(역할이 맞지 않음, 남의 강좌 등)는 기존처럼 403 페이지
public class SessionExpiredAwareAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandlerImpl forbiddenPage = new AccessDeniedHandlerImpl();

    public SessionExpiredAwareAccessDeniedHandler(String errorPage) {
        forbiddenPage.setErrorPage(errorPage);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (accessDeniedException instanceof CsrfException) {
            response.sendRedirect(request.getContextPath() + "/login?expired");
            return;
        }
        forbiddenPage.handle(request, response, accessDeniedException);
    }
}
