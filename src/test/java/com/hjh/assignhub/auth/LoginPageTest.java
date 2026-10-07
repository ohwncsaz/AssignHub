package com.hjh.assignhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

// 로그인 화면 — SB Admin 2 원본 구조(왼쪽 그림 + 오른쪽 폼)와 "로그인 상태 유지"(remember-me)
@SpringBootTest
@AutoConfigureMockMvc
class LoginPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.instructor.email}")
    private String instructorEmail;

    @Value("${app.instructor.password}")
    private String instructorPassword;

    @Test
    @DisplayName("로그인 화면은 원본처럼 왼쪽 그림 영역과 '로그인 상태 유지' 체크박스가 있다")
    void layout() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("bg-login-image")))
                .andExpect(content().string(Matchers.containsString("name=\"remember-me\"")))
                .andExpect(content().string(Matchers.containsString("학생 회원가입")));
    }

    @Test
    @DisplayName("왼쪽 그림은 로그인 전에도 열린다 (/img는 Spring Boot 기본 정적 경로가 아니라 따로 허용)")
    void sideImageIsPublic() throws Exception {
        mockMvc.perform(get("/img/login-side.svg"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<svg")));
    }

    @Test
    @DisplayName("로그인 상태 유지를 체크하면 remember-me 쿠키가 생기고, 세션 없이 쿠키만으로 로그인된다")
    void rememberMe() throws Exception {
        Cookie rememberMe = mockMvc.perform(post("/login").with(csrf())
                        .param("email", instructorEmail)
                        .param("password", instructorPassword)
                        .param("remember-me", "on"))
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists("remember-me"))
                .andReturn().getResponse().getCookie("remember-me");

        assertThat(rememberMe.getMaxAge()).isEqualTo(14 * 24 * 60 * 60);
        // 새 브라우저(세션 없음)에서 쿠키만 가지고 접속
        mockMvc.perform(get("/").cookie(rememberMe))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("체크하지 않으면 remember-me 쿠키가 생기지 않는다")
    void withoutRememberMe() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("email", instructorEmail)
                        .param("password", instructorPassword))
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().doesNotExist("remember-me"));
    }
}
