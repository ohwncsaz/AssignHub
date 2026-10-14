package com.hjh.assignhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// 강사가 만든 학생 계정: 초기 비밀번호(학번)로 로그인 → 비밀번호를 바꾸기 전까지 다른 화면 이용 불가
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordChangeRequiredTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.save(User.createStudentByInstructor("new@test.com", passwordEncoder.encode("20269999"), "신규학생", "20269999"));
    }

    @Test
    @DisplayName("초기 비밀번호로 로그인하면 어느 화면을 열어도 비밀번호 변경 화면으로 보낸다")
    void redirectsToPasswordPageUntilChanged() throws Exception {
        MockHttpSession session = login("new@test.com", "20269999");

        for (String path : new String[] {"/", "/student/assignments", "/student/courses/join", "/profile"}) {
            mockMvc.perform(get(path).session(session))
                    .andExpect(redirectedUrl("/profile/password"));
        }
        mockMvc.perform(get("/profile/password").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("초기 비밀번호(학번)를 새 비밀번호로 변경해야")));
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 바로 다른 화면을 이용할 수 있다")
    void unlockedAfterChange() throws Exception {
        MockHttpSession session = login("new@test.com", "20269999");

        mockMvc.perform(post("/profile/password").session(session).with(csrf())
                        .param("currentPassword", "20269999")
                        .param("newPassword", "mynewpass1")
                        .param("newPasswordConfirm", "mynewpass1"))
                .andExpect(redirectedUrl("/profile"));

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmail("new@test.com").orElseThrow().isPasswordChangeRequired()).isFalse();
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
}
