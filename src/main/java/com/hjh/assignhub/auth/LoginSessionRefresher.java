package com.hjh.assignhub.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.hjh.assignhub.user.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 프로필(이름·이메일)이나 비밀번호를 바꾼 뒤, 세션에 저장된 로그인 정보(LoginUser)를 새 값으로 교체
// 이렇게 하지 않으면 다시 로그인할 때까지 상단바에 예전 이름이 보이고, 바뀐 이메일과 세션 정보가 어긋난다
@Component
public class LoginSessionRefresher {

    // Security 6부터는 SecurityContext를 세션에 명시적으로 저장해야 다음 요청에도 유지된다
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public void refresh(User user, HttpServletRequest request, HttpServletResponse response) {
        LoginUser loginUser = new LoginUser(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                loginUser, null, loginUser.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
